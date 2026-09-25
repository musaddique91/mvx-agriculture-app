package com.mvx.agriculture.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mvx.agriculture.R;
import com.mvx.agriculture.data.CropData;
import com.mvx.agriculture.data.FieldStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.Locale;

/**
 * Turns "how much do I need" into numbers: fertiliser bags, seed, and how many
 * sprayer tank loads a plot takes.
 */
public class CalculatorFragment extends Fragment {

    /** Straight-fertiliser nutrient content, as sold in India. */
    private static final double UREA_N = 0.46;
    private static final double DAP_P = 0.46;
    private static final double DAP_N = 0.18;
    private static final double MOP_K = 0.60;

    private static final double ACRES_PER_HECTARE = 2.47105;
    private static final double ACRES_PER_GUNTHA = 0.025;

    /** Typical knapsack spray volume. */
    private static final double SPRAY_LITRES_PER_ACRE = 200;

    private TextInputEditText areaInput, doseInput, tankInput;
    private MaterialAutoCompleteTextView unitInput, cropInput;
    private TextView fertiliserFor, fertiliserNote;
    private CropData cropData;
    private String[] units;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calculator, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        areaInput = view.findViewById(R.id.areaInput);
        doseInput = view.findViewById(R.id.doseInput);
        tankInput = view.findViewById(R.id.tankInput);
        unitInput = view.findViewById(R.id.unitInput);
        cropInput = view.findViewById(R.id.cropInput);
        fertiliserFor = view.findViewById(R.id.fertiliserFor);
        fertiliserNote = view.findViewById(R.id.fertiliserNote);

        label(view, R.id.doseUrea, R.string.calc_urea);
        label(view, R.id.doseDap, R.string.calc_dap);
        label(view, R.id.doseMop, R.string.calc_mop);
        label(view, R.id.doseSeed, R.string.calc_seed_needed);
        label(view, R.id.sprayWater, R.string.calc_spray_water);
        label(view, R.id.sprayProduct, R.string.calc_spray_product);
        label(view, R.id.sprayTanks, R.string.calc_tanks_label);

        units = new String[]{
                getString(R.string.calc_unit_acre),
                getString(R.string.calc_unit_hectare),
                getString(R.string.calc_unit_guntha)};
        unitInput.setShowSoftInputOnFocus(false);
        unitInput.setSimpleItems(units);
        unitInput.setText(units[0], false);
        unitInput.setOnItemClickListener((p, v, position, id) -> recalculate());

        cropData = new CropData(requireContext());
        List<String> crops = cropData.names();
        cropInput.setShowSoftInputOnFocus(false);
        cropInput.setSimpleItems(crops.toArray(new String[0]));
        if (!crops.isEmpty()) {
            cropInput.setText(crops.get(0), false);
        }
        cropInput.setOnItemClickListener((p, v, position, id) -> recalculate());

        SimpleTextWatcher watcher = new SimpleTextWatcher(text -> recalculate());
        areaInput.addTextChangedListener(watcher);
        doseInput.addTextChangedListener(watcher);
        tankInput.addTextChangedListener(watcher);

        MaterialButton useField = view.findViewById(R.id.useFieldButton);
        double savedAcres = FieldStore.acres(requireContext());
        useField.setVisibility(savedAcres > 0 ? View.VISIBLE : View.GONE);
        useField.setOnClickListener(v -> {
            unitInput.setText(units[0], false);        // the map saves acres
            areaInput.setText(String.format(Locale.US, "%.2f", savedAcres));
        });

        recalculate();
    }

    private void recalculate() {
        double acres = acres();
        CropData.Crop crop = cropData.byName(text(cropInput));

        if (acres <= 0 || crop == null) {
            fertiliserFor.setText(R.string.calc_enter_area);
            fertiliserNote.setText("");
            clearDose(R.id.doseUrea);
            clearDose(R.id.doseDap);
            clearDose(R.id.doseMop);
            clearDose(R.id.doseSeed);
            clearDose(R.id.sprayWater);
            clearDose(R.id.sprayProduct);
            clearDose(R.id.sprayTanks);
            return;
        }

        double hectares = acres / ACRES_PER_HECTARE;

        // DAP supplies phosphorus and carries nitrogen with it, so take that
        // nitrogen off the urea rather than double-dosing N.
        double dapKg = (crop.p * hectares) / DAP_P;
        double nitrogenFromDap = dapKg * DAP_N;
        double ureaKg = Math.max(0, (crop.n * hectares - nitrogenFromDap) / UREA_N);
        double mopKg = (crop.k * hectares) / MOP_K;
        double seedKg = crop.seedKgPerAcre * acres;

        setDose(R.id.doseUrea, getString(R.string.calc_kg, round(ureaKg)));
        setDose(R.id.doseDap, getString(R.string.calc_kg, round(dapKg)));
        setDose(R.id.doseMop, getString(R.string.calc_kg, round(mopKg)));
        setDose(R.id.doseSeed, getString(R.string.calc_kg, round(seedKg)));

        fertiliserFor.setText(getString(R.string.calc_result_for,
                String.format(Locale.getDefault(), "%.2f %s · %s",
                        acres, getString(R.string.calc_unit_acre), crop.name)));
        fertiliserNote.setText(getString(R.string.calc_npk_note,
                crop.n + "-" + crop.p + "-" + crop.k + " kg/ha"));

        // Spray mix
        double litres = acres * SPRAY_LITRES_PER_ACRE;
        double dosePerLitre = number(doseInput);
        double tankSize = number(tankInput);
        setDose(R.id.sprayWater, getString(R.string.calc_litres, round(litres)));
        setDose(R.id.sprayProduct, dosePerLitre > 0
                ? formatProduct(litres * dosePerLitre)
                : "—");
        setDose(R.id.sprayTanks, tankSize > 0
                ? String.format(Locale.getDefault(), "%.0f", Math.ceil(litres / tankSize))
                : "—");
    }

    /** Millilitres below a litre read better than "0.4 L". */
    private String formatProduct(double millilitres) {
        return millilitres >= 1000
                ? getString(R.string.calc_litres, round(millilitres / 1000))
                : String.format(Locale.getDefault(), "%.0f ml", millilitres);
    }

    private double acres() {
        double value = number(areaInput);
        String unit = text(unitInput);
        if (unit.equals(units[1])) {
            return value * ACRES_PER_HECTARE;
        }
        if (unit.equals(units[2])) {
            return value * ACRES_PER_GUNTHA;
        }
        return value;
    }

    private static String round(double value) {
        return value >= 100
                ? String.format(Locale.getDefault(), "%.0f", value)
                : String.format(Locale.getDefault(), "%.1f", value);
    }

    private void label(View root, int id, int labelRes) {
        ((TextView) root.findViewById(id).findViewById(R.id.doseLabel)).setText(labelRes);
    }

    private void setDose(int id, String value) {
        ((TextView) requireView().findViewById(id).findViewById(R.id.doseValue)).setText(value);
    }

    private void clearDose(int id) {
        setDose(id, "—");
    }

    private static String text(MaterialAutoCompleteTextView field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private static double number(TextInputEditText field) {
        try {
            return Double.parseDouble(field.getText() == null ? "" : field.getText().toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
