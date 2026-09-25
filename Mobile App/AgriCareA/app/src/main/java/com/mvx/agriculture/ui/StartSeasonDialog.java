package com.mvx.agriculture.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.mvx.agriculture.R;
import com.mvx.agriculture.data.EpochDays;
import com.mvx.agriculture.data.Field;
import com.mvx.agriculture.data.FieldRepository;
import com.mvx.agriculture.data.JourneyTemplate;
import com.mvx.agriculture.data.JourneyTemplates;
import com.mvx.agriculture.data.Season;
import com.mvx.agriculture.data.SeasonRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Asks what was planted, how and when, then starts the season. */
public final class StartSeasonDialog {

    public interface OnStarted {
        void started(long seasonId);
    }

    private StartSeasonDialog() {
    }

    public static void show(Fragment host, Field field, OnStarted onStarted) {
        Context context = host.requireContext();
        Map<String, JourneyTemplate> journeys = JourneyTemplates.all(context);
        if (journeys.isEmpty()) {
            Toast.makeText(context, R.string.journey_none_available, Toast.LENGTH_LONG).show();
            return;
        }

        View view = host.getLayoutInflater().inflate(R.layout.dialog_start_season, null);
        MaterialAutoCompleteTextView cropInput = view.findViewById(R.id.seasonCropInput);
        MaterialButtonToggleGroup methodGroup = view.findViewById(R.id.seasonMethodGroup);
        TextInputLayout ageLayout = view.findViewById(R.id.seasonAgeLayout);
        TextInputEditText ageInput = view.findViewById(R.id.seasonAgeInput);
        TextInputEditText dateInput = view.findViewById(R.id.seasonDateInput);

        List<String> crops = new ArrayList<>(journeys.keySet());
        cropInput.setShowSoftInputOnFocus(false);
        cropInput.setSimpleItems(crops.toArray(new String[0]));
        final String[] crop = {journeys.containsKey(field.crop) ? field.crop : crops.get(0)};
        cropInput.setText(crop[0], false);

        final JourneyTemplate.Method[] method = new JourneyTemplate.Method[1];
        methodGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            method[0] = (JourneyTemplate.Method) group.findViewById(checkedId).getTag();
            boolean seedlings = method[0].seedlingAgeDays > 0;
            ageLayout.setVisibility(seedlings ? View.VISIBLE : View.GONE);
            ageInput.setText(seedlings ? String.valueOf(method[0].seedlingAgeDays) : "");
        });
        Runnable bindMethods = () -> {
            methodGroup.removeAllViews();
            for (JourneyTemplate.Method m : journeys.get(crop[0]).methods) {
                MaterialButton button = new MaterialButton(context, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
                button.setId(View.generateViewId());
                button.setText(methodLabel(context, m.id));
                button.setTag(m);
                methodGroup.addView(button, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            }
            methodGroup.check(methodGroup.getChildAt(0).getId());
        };
        bindMethods.run();
        cropInput.setOnItemClickListener((parent, v, position, id) -> {
            crop[0] = crops.get(position);
            bindMethods.run();
        });

        // Planted days or weeks ago is the common case; past tasks then show as
        // "before you started tracking" rather than a wall of overdue ones.
        final long[] planted = {EpochDays.today()};
        dateInput.setText(EpochDays.format(planted[0]));
        View.OnClickListener pickDate = v -> {
            MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText(R.string.journey_planted_on)
                    .setSelection(EpochDays.toUtcMillis(planted[0]))
                    .build();
            picker.addOnPositiveButtonClickListener(selection -> {
                planted[0] = EpochDays.fromUtcMillis(selection);
                dateInput.setText(EpochDays.format(planted[0]));
            });
            picker.show(host.getChildFragmentManager(), "planting_date");
        };
        dateInput.setOnClickListener(pickDate);
        ((TextInputLayout) dateInput.getParent().getParent()).setEndIconOnClickListener(pickDate);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.journey_start_title)
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.journey_start, (dialog, which) -> {
                    Season season = new Season();
                    season.fieldId = field.id;
                    season.crop = crop[0];
                    season.method = method[0].id;
                    season.seedlingAge = method[0].seedlingAgeDays > 0
                            ? parseAge(ageInput, method[0].seedlingAgeDays) : 0;
                    season.plantedDay = planted[0];
                    season.trackingStartDay = EpochDays.today();
                    long id = new SeasonRepository(context).start(season);

                    // Keep the field's crop label in step with what is actually planted.
                    if (!crop[0].equals(field.crop)) {
                        field.crop = crop[0];
                        new FieldRepository(context).save(field);
                    }
                    Toast.makeText(context, R.string.journey_started, Toast.LENGTH_SHORT).show();
                    onStarted.started(id);
                })
                .show();
    }

    private static int parseAge(TextInputEditText input, int fallback) {
        CharSequence text = input.getText();
        if (TextUtils.isEmpty(text)) {
            return fallback;
        }
        try {
            return Math.max(0, Math.min(365, Integer.parseInt(text.toString().trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static String methodLabel(Context context, String id) {
        switch (id) {
            case "setts":
                return context.getString(R.string.journey_method_setts);
            case "seed":
                return context.getString(R.string.journey_method_seed);
            case "seedlings":
                return context.getString(R.string.journey_method_seedlings);
            case "tubers":
                return context.getString(R.string.journey_method_tubers);
            default:
                return id;
        }
    }
}
