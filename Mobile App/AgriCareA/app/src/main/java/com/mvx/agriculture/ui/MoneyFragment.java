package com.mvx.agriculture.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.mvx.agriculture.MainShellActivity;
import com.mvx.agriculture.R;
import com.mvx.agriculture.data.EpochDays;
import com.mvx.agriculture.data.Field;
import com.mvx.agriculture.data.FieldRepository;
import com.mvx.agriculture.data.Money;
import com.mvx.agriculture.data.MoneyEntry;
import com.mvx.agriculture.data.MoneyLedger;
import com.mvx.agriculture.data.MoneyRepository;
import com.mvx.agriculture.data.MoneySplit;
import com.mvx.agriculture.data.Season;
import com.mvx.agriculture.data.SeasonRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Farm money: tracking what was spent on seed, fertiliser, labour, machinery, etc.,
 * and what was earned from selling harvests.
 */
public class MoneyFragment extends Fragment {

    public static final String ARG_FILTER_FIELD_ID = "filter_field_id";

    private MoneyRepository moneyRepo;
    private FieldRepository fieldRepo;
    private SeasonRepository seasonRepo;

    private List<Field> allFields = new ArrayList<>();
    private List<MoneyEntry> allEntries = new ArrayList<>();
    private final Map<Integer, Field> fieldMap = new HashMap<>();

    private TextView moneyProfit;
    private TextView moneySpentReceived;
    private TextView moneyPending;
    private TextView moneyEmpty;
    private LinearLayout moneyFields;
    private LinearLayout moneyEntries;

    private int filterFieldId = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_money, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        moneyRepo = new MoneyRepository(requireContext());
        fieldRepo = new FieldRepository(requireContext());
        seasonRepo = new SeasonRepository(requireContext());

        if (getArguments() != null) {
            filterFieldId = getArguments().getInt(ARG_FILTER_FIELD_ID, 0);
        }

        moneyProfit = view.findViewById(R.id.moneyProfit);
        moneySpentReceived = view.findViewById(R.id.moneySpentReceived);
        moneyPending = view.findViewById(R.id.moneyPending);
        moneyEmpty = view.findViewById(R.id.moneyEmpty);
        moneyFields = view.findViewById(R.id.moneyFields);
        moneyEntries = view.findViewById(R.id.moneyEntries);

        ExtendedFloatingActionButton addButton = view.findViewById(R.id.moneyAddButton);
        addButton.setOnClickListener(v -> showAddEntryDialog());

        reload();
    }

    private void reload() {
        allFields = fieldRepo.all();
        fieldMap.clear();
        for (Field f : allFields) {
            fieldMap.put(f.id, f);
        }

        allEntries = moneyRepo.entries();
        List<MoneyLedger.Line> lines = moneyRepo.lines();

        renderSummary(currentSeasonLines(lines));
        renderFieldCards(lines);
        renderEntriesList();
    }

    /** Lines belonging to each field's standing season, or its no-crop bucket when nothing is planted. */
    private List<MoneyLedger.Line> currentSeasonLines(List<MoneyLedger.Line> lines) {
        Map<Integer, Long> active = new HashMap<>();
        for (Field f : allFields) {
            Season season = seasonRepo.activeFor(f.id);
            active.put(f.id, season == null ? null : season.id);
        }
        List<MoneyLedger.Line> out = new ArrayList<>();
        for (MoneyLedger.Line line : lines) {
            if (!active.containsKey(line.fieldId)) {
                continue;   // field since deleted
            }
            Long seasonId = active.get(line.fieldId);
            if (seasonId == null ? line.seasonId == null : seasonId.equals(line.seasonId)) {
                out.add(line);
            }
        }
        return out;
    }

    private void renderSummary(List<MoneyLedger.Line> lines) {
        MoneyLedger.Totals totals = MoneyLedger.totalsAll(lines);
        long profit = totals.profitReceived();

        if (profit >= 0) {
            moneyProfit.setText(getString(R.string.money_profit_label) + " " + Money.format(profit));
        } else {
            moneyProfit.setText(getString(R.string.money_loss_label) + " " + Money.format(Math.abs(profit)));
        }

        moneySpentReceived.setText(getString(R.string.money_card_body,
                Money.format(totals.spent), Money.format(totals.received)));

        if (totals.pending > 0) {
            moneyPending.setVisibility(View.VISIBLE);
            moneyPending.setText(getString(R.string.money_card_pending, Money.format(totals.pending)));
        } else {
            moneyPending.setVisibility(View.GONE);
        }
    }

    private void renderFieldCards(List<MoneyLedger.Line> lines) {
        moneyFields.removeAllViews();
        if (allFields.isEmpty()) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Field f : allFields) {
            Season season = seasonRepo.activeFor(f.id);
            Long seasonId = season == null ? null : season.id;
            MoneyLedger.Totals t = MoneyLedger.totals(lines, f.id, seasonId);

            View card = inflater.inflate(R.layout.item_money_row, moneyFields, false);
            ImageView icon = card.findViewById(R.id.rowIcon);
            icon.setImageResource(R.drawable.ic_fields);

            TextView title = card.findViewById(R.id.rowTitle);
            TextView sub = card.findViewById(R.id.rowSubtitle);
            TextView amount = card.findViewById(R.id.rowAmount);

            title.setText(f.name);
            String seasonLabel = season != null
                    ? getString(R.string.money_field_season, season.crop, EpochDays.format(season.plantedDay))
                    : getString(R.string.money_field_no_season, f.name);
            sub.setText(seasonLabel + "\n" + getString(R.string.money_card_body,
                    Money.format(t.spent), Money.format(t.received)));

            long prof = t.profitReceived();
            amount.setText(Money.format(prof));
            if (prof >= 0) {
                amount.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_scan));
            } else {
                amount.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_error));
            }

            card.setOnClickListener(v -> {
                if (getActivity() instanceof MainShellActivity) {
                    ((MainShellActivity) getActivity()).openFieldDetail(f.id);
                }
            });

            moneyFields.addView(card);
        }
    }

    private void renderEntriesList() {
        moneyEntries.removeAllViews();
        if (allEntries.isEmpty()) {
            moneyEmpty.setVisibility(View.VISIBLE);
            return;
        }
        moneyEmpty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (MoneyEntry entry : allEntries) {
            View card = inflater.inflate(R.layout.item_money_row, moneyEntries, false);
            ImageView icon = card.findViewById(R.id.rowIcon);
            TextView title = card.findViewById(R.id.rowTitle);
            TextView sub = card.findViewById(R.id.rowSubtitle);
            TextView amount = card.findViewById(R.id.rowAmount);

            boolean isSpent = entry.isSpent();
            if (isSpent) {
                icon.setImageResource(R.drawable.ic_payments);
                icon.setImageTintList(ContextCompat.getColorStateList(requireContext(), R.color.md_error));
                title.setText(getCategoryLabel(entry.category));
                amount.setText("-" + Money.format(entry.amountPaise));
                amount.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_error));
            } else {
                icon.setImageResource(R.drawable.ic_savings);
                icon.setImageTintList(ContextCompat.getColorStateList(requireContext(), R.color.accent_scan));
                title.setText(entry.crop != null && !entry.crop.isEmpty() ? entry.crop : getString(R.string.money_kind_received));
                amount.setText("+" + Money.format(entry.amountPaise));
                amount.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_scan));
            }

            StringBuilder fieldNames = new StringBuilder();
            for (MoneyEntry.Allocation a : entry.allocations) {
                Field f = fieldMap.get(a.fieldId);
                if (f != null) {
                    if (fieldNames.length() > 0) fieldNames.append(", ");
                    fieldNames.append(f.name);
                }
            }

            String dateStr = EpochDays.format(entry.day);
            String meta = fieldNames.length() > 0
                    ? getString(R.string.money_entry_meta, dateStr, fieldNames.toString())
                    : dateStr;
            if (!isSpent && !entry.received) {
                meta = meta + " · " + getString(R.string.money_pending_badge);
            }
            sub.setText(meta);

            card.setOnClickListener(v -> showEntryActions(entry));
            moneyEntries.addView(card);
        }
    }

    private void showEntryActions(MoneyEntry entry) {
        List<String> items = new ArrayList<>();
        if (!entry.isSpent() && !entry.received) {
            items.add(getString(R.string.money_mark_received));
        }
        items.add(getString(R.string.money_delete));

        new MaterialAlertDialogBuilder(requireContext())
                .setItems(items.toArray(new String[0]), (dialog, which) -> {
                    String action = items.get(which);
                    if (action.equals(getString(R.string.money_mark_received))) {
                        moneyRepo.markReceived(entry.id);
                        reload();
                    } else if (action.equals(getString(R.string.money_delete))) {
                        confirmDelete(entry);
                    }
                })
                .show();
    }

    private void confirmDelete(MoneyEntry entry) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.money_delete)
                .setMessage(R.string.money_delete_confirm)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    moneyRepo.delete(entry.id);
                    reload();
                })
                .show();
    }

    private void showAddEntryDialog() {
        if (allFields.isEmpty()) {
            Toast.makeText(requireContext(), R.string.money_no_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_money_entry, null);
        MaterialButtonToggleGroup kindGroup = dialogView.findViewById(R.id.moneyKind);
        View categoryLayout = dialogView.findViewById(R.id.moneyCategoryLayout);
        MaterialAutoCompleteTextView categoryInput = dialogView.findViewById(R.id.moneyCategory);
        View incomeSection = dialogView.findViewById(R.id.moneyIncomeSection);
        TextInputEditText cropInput = dialogView.findViewById(R.id.moneyCrop);
        TextInputEditText quantityInput = dialogView.findViewById(R.id.moneyQuantity);
        MaterialAutoCompleteTextView unitInput = dialogView.findViewById(R.id.moneyUnit);
        TextInputEditText rateInput = dialogView.findViewById(R.id.moneyRate);
        TextInputEditText buyerInput = dialogView.findViewById(R.id.moneyBuyer);
        MaterialSwitch receivedNowSwitch = dialogView.findViewById(R.id.moneyReceivedNow);

        TextInputEditText amountInput = dialogView.findViewById(R.id.moneyAmount);
        TextInputEditText dateInput = dialogView.findViewById(R.id.moneyDate);
        TextInputEditText noteInput = dialogView.findViewById(R.id.moneyNote);

        LinearLayout fieldChecksContainer = dialogView.findViewById(R.id.moneyFieldChecks);
        MaterialButtonToggleGroup splitGroup = dialogView.findViewById(R.id.moneySplit);
        LinearLayout sharesContainer = dialogView.findViewById(R.id.moneyShares);

        // Date init
        final long[] selectedDay = {EpochDays.today()};
        dateInput.setText(EpochDays.format(selectedDay[0]));
        dateInput.setOnClickListener(v -> {
            MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                    .setSelection(EpochDays.toUtcMillis(selectedDay[0]))
                    .build();
            picker.addOnPositiveButtonClickListener(selection -> {
                selectedDay[0] = EpochDays.fromUtcMillis(selection);
                dateInput.setText(EpochDays.format(selectedDay[0]));
            });
            picker.show(getParentFragmentManager(), "money_date_picker");
        });

        // Categories setup
        String[] catKeys = {"seed", "fertilizer", "pesticide", "goli", "labour", "machinery", "irrigation", "transport", "other"};
        String[] catLabels = new String[catKeys.length];
        for (int i = 0; i < catKeys.length; i++) {
            catLabels[i] = getCategoryLabel(catKeys[i]);
        }
        categoryInput.setSimpleItems(catLabels);
        categoryInput.setText(catLabels[1], false); // default fertiliser

        // Units setup
        String[] units = {getString(R.string.money_unit_quintal), getString(R.string.money_unit_tonne), getString(R.string.money_unit_kg)};
        unitInput.setSimpleItems(units);
        unitInput.setText(units[0], false);

        // Kind toggle
        kindGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.moneyKindSpent) {
                categoryLayout.setVisibility(View.VISIBLE);
                incomeSection.setVisibility(View.GONE);
            } else {
                categoryLayout.setVisibility(View.GONE);
                incomeSection.setVisibility(View.VISIBLE);
            }
        });

        // Income amount follows quantity x rate until the farmer types an amount themselves
        final boolean[] amountTyped = {false};
        final boolean[] settingAmount = {false};
        Runnable fillFromRate = () -> {
            if (amountTyped[0]) return;
            long rate = Money.toPaise(text(rateInput));
            double qty = parseDouble(text(quantityInput));
            if (rate > 0 && qty > 0) {
                settingAmount[0] = true;
                amountInput.setText(Money.toInput(Math.round(rate * qty)));
                settingAmount[0] = false;
            }
        };

        // Field checkboxes, all ticked unless opened from one field
        List<CheckBox> checkBoxes = new ArrayList<>();
        List<TextInputEditText> customInputs = new ArrayList<>();
        Runnable refreshShares = () -> {
            sharesContainer.removeAllViews();
            customInputs.clear();
            List<Field> selected = checkedFields(checkBoxes);
            boolean custom = splitGroup.getCheckedButtonId() == R.id.moneySplitCustom;
            splitGroup.setVisibility(selected.size() > 1 ? View.VISIBLE : View.GONE);
            if (selected.size() < 2) return;
            long total = Money.toPaise(text(amountInput));
            long[] shares = total > 0 ? shares(total, selected, splitGroup.getCheckedButtonId()) : null;
            for (int i = 0; i < selected.size(); i++) {
                Field f = selected.get(i);
                if (custom) {
                    com.google.android.material.textfield.TextInputLayout layout =
                            new com.google.android.material.textfield.TextInputLayout(requireContext(), null,
                                    com.google.android.material.R.attr.textInputOutlinedStyle);
                    layout.setHint(f.name);
                    TextInputEditText input = new TextInputEditText(layout.getContext());
                    input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    if (shares != null) input.setText(Money.toInput(shares[i]));
                    layout.addView(input);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.topMargin = getResources().getDimensionPixelSize(R.dimen.space_s);
                    sharesContainer.addView(layout, lp);
                    customInputs.add(input);
                } else {
                    TextView row = new TextView(requireContext());
                    row.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
                    row.setText(f.name + ": " + (shares == null ? "—" : Money.format(shares[i])));
                    row.setPadding(0, getResources().getDimensionPixelSize(R.dimen.space_xs), 0, 0);
                    sharesContainer.addView(row);
                }
            }
        };
        for (Field f : allFields) {
            CheckBox cb = new CheckBox(requireContext());
            cb.setText(f.name + (f.areaAcres > 0 ? " (" + String.format(java.util.Locale.ROOT, "%.1f ac", f.areaAcres) + ")" : ""));
            cb.setChecked(filterFieldId == 0 || f.id == filterFieldId);
            cb.setTag(f);
            cb.setOnCheckedChangeListener((b, checked) -> refreshShares.run());
            fieldChecksContainer.addView(cb);
            checkBoxes.add(cb);
        }
        splitGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) refreshShares.run();
        });
        amountInput.addTextChangedListener(new SimpleWatcher(() -> {
            if (!settingAmount[0]) amountTyped[0] = amountInput.hasFocus();
            // Typed shares are the farmer's own; only re-split previews
            if (splitGroup.getCheckedButtonId() != R.id.moneySplitCustom) refreshShares.run();
        }));
        rateInput.addTextChangedListener(new SimpleWatcher(fillFromRate));
        quantityInput.addTextChangedListener(new SimpleWatcher(fillFromRate));
        ((com.google.android.material.textfield.TextInputLayout) dialogView.findViewById(R.id.moneyDateLayout))
                .setEndIconOnClickListener(v -> dateInput.performClick());
        refreshShares.run();

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.money_add)
                .setView(dialogView)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, null)
                .create();
        // Checked in the click listener so a mistake keeps what was typed
        dialog.setOnShowListener(d -> dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            long totalPaise = Money.toPaise(text(amountInput));
            if (totalPaise <= 0) {
                Toast.makeText(requireContext(), R.string.money_error_amount, Toast.LENGTH_SHORT).show();
                return;
            }
            List<Field> selectedFields = checkedFields(checkBoxes);
            if (selectedFields.isEmpty()) {
                Toast.makeText(requireContext(), R.string.money_error_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            int n = selectedFields.size();
            int splitId = n == 1 ? R.id.moneySplitEqual : splitGroup.getCheckedButtonId();
            long[] shares;
            if (splitId == R.id.moneySplitCustom) {
                shares = new long[n];
                long sum = 0;
                for (int i = 0; i < n; i++) {
                    shares[i] = Math.max(0, Money.toPaise(text(customInputs.get(i))));
                    sum += shares[i];
                }
                if (sum != totalPaise) {
                    Toast.makeText(requireContext(), getString(R.string.money_error_custom, Money.format(totalPaise)),
                            Toast.LENGTH_LONG).show();
                    return;
                }
            } else {
                shares = shares(totalPaise, selectedFields, splitId);
            }

            boolean isSpent = kindGroup.getCheckedButtonId() == R.id.moneyKindSpent;
            MoneyEntry entry = new MoneyEntry();
            entry.kind = isSpent ? MoneyLedger.SPENT : MoneyLedger.RECEIVED;
            entry.amountPaise = totalPaise;
            entry.day = selectedDay[0];
            entry.note = text(noteInput);
            entry.splitMode = splitId == R.id.moneySplitCustom ? "custom"
                    : splitId == R.id.moneySplitArea ? "area" : "equal";

            if (isSpent) {
                String selectedCat = categoryInput.getText().toString();
                entry.category = "other";
                for (int i = 0; i < catLabels.length; i++) {
                    if (catLabels[i].equals(selectedCat)) {
                        entry.category = catKeys[i];
                        break;
                    }
                }
            } else {
                entry.crop = text(cropInput);
                entry.quantity = parseDouble(text(quantityInput));
                // Stored as a fixed key, so switching language does not change old entries
                String[] unitKeys = {"quintal", "tonne", "kg"};
                entry.unit = unitKeys[0];
                for (int i = 0; i < units.length; i++) {
                    if (units[i].equals(unitInput.getText().toString())) {
                        entry.unit = unitKeys[i];
                    }
                }
                entry.ratePaise = Math.max(0, Money.toPaise(text(rateInput)));
                entry.buyer = text(buyerInput);
                entry.received = receivedNowSwitch.isChecked();
            }

            for (int i = 0; i < n; i++) {
                Field f = selectedFields.get(i);
                Season season = seasonRepo.activeFor(f.id);
                entry.allocations.add(new MoneyEntry.Allocation(f.id, season == null ? null : season.id, shares[i]));
            }

            moneyRepo.save(entry);
            Toast.makeText(requireContext(), R.string.money_saved, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            reload();
        }));
        dialog.show();
    }

    private static List<Field> checkedFields(List<CheckBox> boxes) {
        List<Field> out = new ArrayList<>();
        for (CheckBox cb : boxes) {
            if (cb.isChecked()) out.add((Field) cb.getTag());
        }
        return out;
    }

    private static long[] shares(long totalPaise, List<Field> fields, int splitId) {
        if (splitId == R.id.moneySplitArea) {
            double[] acres = new double[fields.size()];
            for (int i = 0; i < acres.length; i++) acres[i] = fields.get(i).areaAcres;
            return MoneySplit.byArea(totalPaise, acres);
        }
        return MoneySplit.equally(totalPaise, fields.size());
    }

    private static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static final class SimpleWatcher implements android.text.TextWatcher {
        private final Runnable onChange;

        SimpleWatcher(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
        @Override public void afterTextChanged(android.text.Editable s) { onChange.run(); }
    }

    private String getCategoryLabel(String catKey) {
        if ("seed".equals(catKey)) return getString(R.string.money_cat_seed);
        if ("fertilizer".equals(catKey)) return getString(R.string.money_cat_fertilizer);
        if ("pesticide".equals(catKey)) return getString(R.string.money_cat_pesticide);
        if ("goli".equals(catKey)) return getString(R.string.money_cat_goli);
        if ("labour".equals(catKey)) return getString(R.string.money_cat_labour);
        if ("machinery".equals(catKey)) return getString(R.string.money_cat_machinery);
        if ("irrigation".equals(catKey)) return getString(R.string.money_cat_irrigation);
        if ("transport".equals(catKey)) return getString(R.string.money_cat_transport);
        return getString(R.string.money_cat_other);
    }
}
