package com.mvx.agriculture.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import android.os.Build;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mvx.agriculture.MainShellActivity;
import com.mvx.agriculture.R;
import com.mvx.agriculture.User;
import com.mvx.agriculture.data.CropData;
import com.mvx.agriculture.data.CropJourneyPlan;
import com.mvx.agriculture.data.EpochDays;
import com.mvx.agriculture.data.JourneyTemplate;
import com.mvx.agriculture.data.JourneyTemplates;
import com.mvx.agriculture.data.Season;
import com.mvx.agriculture.data.SeasonRepository;
import com.mvx.agriculture.data.Field;
import com.mvx.agriculture.data.FieldAdvisor;
import com.mvx.agriculture.data.FieldRepository;
import com.mvx.agriculture.data.Money;
import com.mvx.agriculture.data.MoneyLedger;
import com.mvx.agriculture.data.MoneyRepository;
import com.mvx.agriculture.data.ScoutingNote;
import com.mvx.agriculture.data.Weather;
import com.mvx.agriculture.data.WeatherRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** One field: its size, what the AI suggests growing on it, and the scouting log. */
public class FieldDetailFragment extends Fragment {

    public static final String ARG_FIELD_ID = "field_id";
    private static final double ACRE_TO_HA = 0.404686;

    private FieldRepository repository;

    /** Reminders need this on Android 13+. Asked when a season starts, when it is clearly useful. */
    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && isAdded()) {
                    // The check run at season start found notifications off; run it again
                    // so tasks already due are announced now, not a day later.
                    com.mvx.agriculture.CropTaskReminderWorker.runNow(requireContext());
                }
            });
    private Field field;
    private final List<ScoutingNote> notes = new ArrayList<>();
    private NoteAdapter adapter;

    private LinearProgressIndicator adviceProgress;
    private View adviceCard, noNotes;
    private TextView adviceText, detailName, detailCrop;
    private Weather cachedWeather;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_field_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = new FieldRepository(requireContext());

        int fieldId = getArguments() == null ? 0 : getArguments().getInt(ARG_FIELD_ID);
        field = repository.byId(fieldId);
        if (field == null) {
            ((MainShellActivity) requireActivity()).openDestination(R.id.nav_fields);
            return;
        }

        detailName = view.findViewById(R.id.detailName);
        detailCrop = view.findViewById(R.id.detailCrop);

        detailName.setText(field.name);
        ((TextView) view.findViewById(R.id.detailArea)).setText(getString(R.string.field_acres_ha,
                field.areaAcres, field.areaAcres * ACRE_TO_HA));
        updateCropVisibility();

        view.findViewById(R.id.editFieldButton).setOnClickListener(v -> showEditDialog());

        adviceProgress = view.findViewById(R.id.adviceProgress);
        adviceCard = view.findViewById(R.id.adviceCard);
        adviceText = view.findViewById(R.id.adviceText);
        noNotes = view.findViewById(R.id.noNotes);

        RecyclerView noteList = view.findViewById(R.id.noteList);
        adapter = new NoteAdapter();
        noteList.setLayoutManager(new LinearLayoutManager(requireContext()));
        noteList.setAdapter(adapter);

        ((MaterialButton) view.findViewById(R.id.adviceButton))
                .setOnClickListener(v -> requestAdvice());

        ((ExtendedFloatingActionButton) view.findViewById(R.id.addNoteButton))
                .setOnClickListener(v -> showAddNote());

        bindJourneyCard(view);
        bindMoneyCard(view);
        prefetchWeather();
        reloadNotes();
    }

    private void bindJourneyCard(View view) {
        MainShellActivity shell = (MainShellActivity) requireActivity();
        TextView title = view.findViewById(R.id.journeyCardTitle);
        TextView body = view.findViewById(R.id.journeyCardBody);
        MaterialButton button = view.findViewById(R.id.journeyCardButton);

        Season season = new SeasonRepository(requireContext()).activeFor(field.id);
        JourneyTemplate template = season == null ? null : JourneyTemplates.forCrop(requireContext(), season.crop);
        if (season == null || template == null) {
            title.setText(R.string.journey_title);
            body.setText(R.string.journey_card_start_body);
            button.setText(R.string.journey_start_season);
            button.setOnClickListener(v -> StartSeasonDialog.show(this, field, seasonId -> {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
                com.mvx.agriculture.CropTaskReminderWorker.runNow(requireContext());
                shell.openCropJourney(field.id);
            }));
            return;
        }

        long today = EpochDays.today();
        title.setText(getString(R.string.journey_card_active_title, season.crop,
                Math.max(0, today - season.plantedDay)));
        CropJourneyPlan.Item next = null;
        for (CropJourneyPlan.Item item : CropJourneyPlan.plan(template, season.method, season.seedlingAge,
                season.plantedDay, season.trackingStartDay, today, new SeasonRepository(requireContext()).records(season.id))) {
            if (item.status == CropJourneyPlan.Status.OVERDUE || item.status == CropJourneyPlan.Status.DUE
                    || item.status == CropJourneyPlan.Status.UPCOMING) {
                next = item;
                break;
            }
        }
        if (next == null) {
            body.setText(R.string.journey_card_all_done);
        } else {
            String when = next.status == CropJourneyPlan.Status.OVERDUE ? getString(R.string.journey_status_overdue)
                    : next.status == CropJourneyPlan.Status.DUE ? getString(R.string.journey_status_due)
                    : getString(R.string.journey_status_in_days, next.dueEpochDay - today);
            body.setText(getString(R.string.journey_card_next, next.task.title, when));
        }
        button.setText(R.string.journey_open);
        button.setOnClickListener(v -> shell.openCropJourney(field.id));
    }

    private void bindMoneyCard(View view) {
        MainShellActivity shell = (MainShellActivity) requireActivity();
        TextView body = view.findViewById(R.id.moneyCardBody);
        TextView pending = view.findViewById(R.id.moneyCardPending);
        MaterialButton button = view.findViewById(R.id.moneyCardButton);

        Season season = new SeasonRepository(requireContext()).activeFor(field.id);
        Long seasonId = season == null ? null : season.id;
        List<MoneyLedger.Line> lines = new MoneyRepository(requireContext()).lines();
        MoneyLedger.Totals totals = MoneyLedger.totals(lines, field.id, seasonId);

        body.setText(getString(R.string.money_card_body,
                Money.format(totals.spent), Money.format(totals.received)));

        if (totals.pending > 0) {
            pending.setVisibility(View.VISIBLE);
            pending.setText(getString(R.string.money_card_pending, Money.format(totals.pending)));
        } else {
            pending.setVisibility(View.GONE);
        }

        button.setText(R.string.money_open);
        button.setOnClickListener(v -> shell.openMoney(field.id));
    }

    private void updateCropVisibility() {
        detailCrop.setText(field.crop);
        detailCrop.setVisibility(field.crop == null || field.crop.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showEditDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_save_field, null);
        TextInputEditText nameInput = dialogView.findViewById(R.id.fieldNameInput);
        MaterialAutoCompleteTextView cropInput = dialogView.findViewById(R.id.fieldCropInput);
        cropInput.setShowSoftInputOnFocus(false);
        cropInput.setSimpleItems(new CropData(requireContext()).names().toArray(new String[0]));

        nameInput.setText(field.name);
        if (field.crop != null && !field.crop.isEmpty()) {
            cropInput.setText(field.crop, false);
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.field_edit)
                .setView(dialogView)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String name = nameInput.getText() == null
                            ? "" : nameInput.getText().toString().trim();
                    field.name = name.isEmpty() ? getString(R.string.field_new) : name;
                    field.crop = cropInput.getText() == null
                            ? null : cropInput.getText().toString().trim();
                    repository.save(field);
                    detailName.setText(field.name);
                    updateCropVisibility();
                    Toast.makeText(requireContext(), R.string.field_updated, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    /** Warm the forecast so the advice request does not wait on two round trips. */
    private void prefetchWeather() {
        Field.Point centre = field.centroid();
        if (centre == null) {
            return;
        }
        new WeatherRepository().load(centre.lat, centre.lon, new WeatherRepository.Listener() {
            @Override
            public void onWeather(Weather weather) {
                cachedWeather = weather;
            }

            @Override
            public void onError() {
                cachedWeather = null;   // advice still works, just without the forecast
            }
        });
    }

    private void requestAdvice() {
        adviceProgress.setVisibility(View.VISIBLE);
        adviceCard.setVisibility(View.VISIBLE);
        adviceText.setText(R.string.field_advice_loading);

        User user = ((MainShellActivity) requireActivity()).currentUser();
        String region = user == null ? null : user.getRegion();

        new FieldAdvisor(requireContext()).recommend(field, region, cachedWeather, notes,
                new FieldAdvisor.Listener() {
                    @Override
                    public void onAdvice(String advice) {
                        if (!isAdded()) {
                            return;
                        }
                        adviceProgress.setVisibility(View.GONE);
                        adviceText.setText(advice);
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) {
                            return;
                        }
                        adviceProgress.setVisibility(View.GONE);
                        adviceText.setText(message);
                    }
                });
    }

    private void reloadNotes() {
        notes.clear();
        notes.addAll(repository.notesFor(field.id));
        adapter.notifyDataSetChanged();
        noNotes.setVisibility(notes.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showAddNote() {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_note, null);
        ChipGroup categories = view.findViewById(R.id.noteCategories);
        TextInputEditText input = view.findViewById(R.id.noteInput);

        final ScoutingNote.Category[] selected = {ScoutingNote.Category.PEST};
        for (ScoutingNote.Category category : ScoutingNote.Category.values()) {
            Chip chip = new Chip(requireContext());
            chip.setText(category.labelRes);
            chip.setCheckable(true);
            chip.setChecked(category == ScoutingNote.Category.PEST);
            chip.setOnClickListener(v -> selected[0] = category);
            categories.addView(chip);
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.field_add_note)
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    ScoutingNote note = new ScoutingNote();
                    note.fieldId = field.id;
                    note.category = selected[0];
                    note.text = input.getText() == null ? "" : input.getText().toString().trim();

                    // Geotag with the current fix where we have one; the note is still
                    // worth keeping without it, so fall back to the field's centre.
                    Location location = lastKnownLocation();
                    Field.Point centre = field.centroid();
                    if (location != null) {
                        note.lat = location.getLatitude();
                        note.lon = location.getLongitude();
                    } else if (centre != null) {
                        note.lat = centre.lat;
                        note.lon = centre.lon;
                    }

                    repository.addNote(note);
                    reloadNotes();
                    Toast.makeText(requireContext(), R.string.note_saved, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    @SuppressLint("MissingPermission")
    private Location lastKnownLocation() {
        boolean granted = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            return null;
        }
        LocationManager manager =
                (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            return null;
        }
        for (String provider : new String[]{
                LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                Location location = manager.getLastKnownLocation(provider);
                if (location != null) {
                    return location;
                }
            } catch (SecurityException | IllegalArgumentException e) {
                // provider unavailable; try the next
            }
        }
        return null;
    }

    private class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.Holder> {

        private final SimpleDateFormat format =
                new SimpleDateFormat("d MMM, HH:mm", Locale.getDefault());

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_note, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ScoutingNote note = notes.get(position);
            holder.category.setText(note.category.labelRes);
            holder.stripe.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), note.category.colorRes));
            holder.text.setText(note.text);
            holder.text.setVisibility(note.text == null || note.text.isEmpty()
                    ? View.GONE : View.VISIBLE);
            holder.meta.setText(String.format(Locale.getDefault(), "%s · %.4f, %.4f",
                    format.format(new Date(note.createdAt)), note.lat, note.lon));

            holder.itemView.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setMessage(R.string.action_delete)
                        .setNegativeButton(R.string.action_cancel, null)
                        .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                            repository.deleteNote(note.id);
                            reloadNotes();
                        })
                        .show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return notes.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView category, text, meta;
            final View stripe;

            Holder(@NonNull View itemView) {
                super(itemView);
                category = itemView.findViewById(R.id.noteCategory);
                text = itemView.findViewById(R.id.noteText);
                meta = itemView.findViewById(R.id.noteMeta);
                stripe = itemView.findViewById(R.id.noteStripe);
            }
        }
    }
}
