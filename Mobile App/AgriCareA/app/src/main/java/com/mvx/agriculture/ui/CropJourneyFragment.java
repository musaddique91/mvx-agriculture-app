package com.mvx.agriculture.ui;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mvx.agriculture.MainShellActivity;
import com.mvx.agriculture.R;
import com.mvx.agriculture.data.CropJourneyPlan;
import com.mvx.agriculture.data.EpochDays;
import com.mvx.agriculture.data.Field;
import com.mvx.agriculture.data.FieldRepository;
import com.mvx.agriculture.data.JourneyTemplate;
import com.mvx.agriculture.data.JourneyTemplates;
import com.mvx.agriculture.data.Season;
import com.mvx.agriculture.data.SeasonRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * One field's standing crop from planting to harvest: what needs doing now, what
 * comes next, and what is done, with the growth stage and how to water in it.
 */
public class CropJourneyFragment extends Fragment {

    public static final String ARG_FIELD_ID = "field_id";

    /** How many upcoming tasks to show before the rest fold under "Later". */
    private static final int NEXT_COUNT = 4;

    private Field field;
    private Season season;
    private JourneyTemplate template;
    private SeasonRepository seasons;
    private LinearLayout sections;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_crop_journey, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MainShellActivity shell = (MainShellActivity) requireActivity();
        int fieldId = getArguments() == null ? 0 : getArguments().getInt(ARG_FIELD_ID);
        field = new FieldRepository(requireContext()).byId(fieldId);
        seasons = new SeasonRepository(requireContext());
        season = field == null ? null : seasons.activeFor(field.id);
        template = season == null ? null : JourneyTemplates.forCrop(requireContext(), season.crop);
        if (field == null || season == null || template == null) {
            if (field != null) {
                shell.openFieldDetail(field.id);
            } else {
                shell.openDestination(R.id.nav_fields);
            }
            return;
        }

        sections = view.findViewById(R.id.journeySections);

        StringBuilder sources = new StringBuilder();
        for (JourneyTemplate.Source source : template.sources) {
            if (sources.length() > 0) {
                sources.append("\n\n");
            }
            sources.append(source.title).append('\n').append(source.url);
        }
        TextView sourcesView = view.findViewById(R.id.journeySources);
        sourcesView.setText(sources);
        Linkify.addLinks(sourcesView, Linkify.WEB_URLS);
        sourcesView.setMovementMethod(LinkMovementMethod.getInstance());

        view.findViewById(R.id.journeyEnd).setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.journey_end_season)
                .setMessage(R.string.journey_end_confirm)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.journey_end, (dialog, which) -> {
                    seasons.end(season.id, EpochDays.today());
                    shell.openFieldDetail(field.id);
                })
                .show());

        render(view);
    }

    private void render(View view) {
        long today = EpochDays.today();
        List<CropJourneyPlan.Item> items = CropJourneyPlan.plan(template, season.method, season.seedlingAge,
                season.plantedDay, season.trackingStartDay, today, seasons.records(season.id));

        ((TextView) view.findViewById(R.id.journeyHeading)).setText(field.name + " · " + season.crop);
        ((TextView) view.findViewById(R.id.journeyPlanted)).setText(getString(R.string.journey_planted_line,
                StartSeasonDialog.methodLabel(requireContext(), season.method), EpochDays.format(season.plantedDay)));

        long dap = today - season.plantedDay;
        JourneyTemplate.Stage stage = CropJourneyPlan.stage(template, season.plantedDay, today);
        TextView day = view.findViewById(R.id.journeyDay);
        if (dap < 0) {
            day.setText(getString(R.string.journey_starts_in, -dap));
        } else if (stage != null) {
            day.setText(getString(R.string.journey_day_stage, dap, stage.name));
        } else {
            day.setText(getString(R.string.journey_day, dap));
        }
        TextView water = view.findViewById(R.id.journeyWater);
        water.setText(stage == null ? null : stage.water);
        water.setVisibility(stage == null || stage.water == null ? View.GONE : View.VISIBLE);

        List<CropJourneyPlan.Item> attention = new ArrayList<>();
        List<CropJourneyPlan.Item> upcoming = new ArrayList<>();
        List<CropJourneyPlan.Item> finished = new ArrayList<>();
        List<CropJourneyPlan.Item> earlier = new ArrayList<>();
        for (CropJourneyPlan.Item item : items) {
            switch (item.status) {
                case OVERDUE:
                case DUE:
                    attention.add(item);
                    break;
                case UPCOMING:
                    upcoming.add(item);
                    break;
                case EARLIER:
                    earlier.add(item);
                    break;
                default:
                    finished.add(item);
            }
        }
        ((TextView) view.findViewById(R.id.journeyProgress)).setText(
                getString(R.string.journey_progress, finished.size(), items.size()));

        sections.removeAllViews();
        addSection(R.string.journey_section_attention, attention, true, today, R.string.journey_nothing_due);
        addSection(R.string.journey_section_next, upcoming.subList(0, Math.min(NEXT_COUNT, upcoming.size())), true, today, 0);
        addSection(R.string.journey_section_later, upcoming.subList(Math.min(NEXT_COUNT, upcoming.size()), upcoming.size()), false, today, 0);
        addSection(R.string.journey_section_earlier, earlier, false, today, 0);
        addSection(R.string.journey_section_done, finished, false, today, 0);
    }

    /** A heading and its tasks. Folded sections open when their heading is tapped. */
    private void addSection(@StringRes int title, List<CropJourneyPlan.Item> items, boolean open,
                            long today, @StringRes int emptyText) {
        if (items.isEmpty() && emptyText == 0) {
            return;
        }
        TextView heading = new TextView(requireContext(), null, 0, R.style.Text_GroupHeading);
        heading.setText(open ? getString(title)
                : getString(R.string.journey_section_count, getString(title), items.size()));
        sections.addView(heading);

        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        sections.addView(body);
        if (!open) {
            heading.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_drop_down, 0);
            heading.setOnClickListener(v -> body.setVisibility(
                    body.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        }

        if (items.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(emptyText);
            empty.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
            body.addView(empty);
            return;
        }
        for (CropJourneyPlan.Item item : items) {
            body.addView(taskCard(body, item, today));
        }
    }

    private View taskCard(ViewGroup parent, CropJourneyPlan.Item item, long today) {
        View card = getLayoutInflater().inflate(R.layout.item_journey_task, parent, false);
        ((ImageView) card.findViewById(R.id.taskIcon)).setImageResource(iconFor(item.task.type));
        ((TextView) card.findViewById(R.id.taskTitle)).setText(item.task.title);
        card.findViewById(R.id.taskOptional).setVisibility(item.task.optional ? View.VISIBLE : View.GONE);

        TextView meta = card.findViewById(R.id.taskMeta);
        meta.setText(getString(R.string.journey_meta, item.daysAfterPlanting,
                EpochDays.format(item.dueEpochDay), statusText(item, today)));
        if (item.status == CropJourneyPlan.Status.OVERDUE) {
            meta.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_error));
        }

        TextView detail = card.findViewById(R.id.taskDetail);
        detail.setText(item.task.detail);
        // Doses sit in the detail, so the whole of it opens with one tap.
        card.setOnClickListener(v -> detail.setMaxLines(detail.getMaxLines() == 2 ? Integer.MAX_VALUE : 2));

        boolean recorded = item.record != null;
        MaterialButton done = card.findViewById(R.id.taskDone);
        MaterialButton skip = card.findViewById(R.id.taskSkip);
        MaterialButton undo = card.findViewById(R.id.taskUndo);
        done.setVisibility(recorded ? View.GONE : View.VISIBLE);
        skip.setVisibility(recorded ? View.GONE : View.VISIBLE);
        undo.setVisibility(recorded ? View.VISIBLE : View.GONE);
        done.setOnClickListener(v -> record(item, CropJourneyPlan.Record.DONE));
        skip.setOnClickListener(v -> record(item, CropJourneyPlan.Record.SKIPPED));
        undo.setOnClickListener(v -> record(item, null));
        return card;
    }

    private void record(CropJourneyPlan.Item item, String state) {
        seasons.record(season.id, item.task.id, state, EpochDays.today());
        render(requireView());
    }

    private String statusText(CropJourneyPlan.Item item, long today) {
        switch (item.status) {
            case OVERDUE:
                return getString(R.string.journey_status_overdue);
            case DUE:
                return getString(R.string.journey_status_due);
            case DONE:
                return getString(R.string.journey_status_done, EpochDays.format(item.record.epochDay));
            case SKIPPED:
                return getString(R.string.journey_status_skipped);
            case EARLIER:
                return getString(R.string.journey_status_earlier);
            default:
                return getString(R.string.journey_status_in_days, item.dueEpochDay - today);
        }
    }

    static int iconFor(String type) {
        switch (type) {
            case "manure":
                return R.drawable.ic_soil;
            case "fertilizer":
                return R.drawable.ic_nutrient;
            case "seed":
                return R.drawable.ic_crop;
            case "weeding":
                return R.drawable.ic_weed;
            case "irrigation":
                return R.drawable.ic_irrigation;
            case "pest_watch":
                return R.drawable.ic_pest;
            case "pest_control":
                return R.drawable.ic_spray;
            case "disease_watch":
                return R.drawable.ic_disease;
            case "goli":
                return R.drawable.ic_goli;
            case "harvest":
                return R.drawable.ic_market;
            default:
                return R.drawable.ic_fields;
        }
    }
}
