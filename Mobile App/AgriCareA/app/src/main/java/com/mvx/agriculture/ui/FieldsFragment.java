package com.mvx.agriculture.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mvx.agriculture.MainShellActivity;
import com.mvx.agriculture.R;
import com.mvx.agriculture.data.CropData;
import com.mvx.agriculture.data.Field;
import com.mvx.agriculture.data.FieldRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/** Every plot the farmer has mapped. */
public class FieldsFragment extends Fragment {

    private static final double SQM_PER_ACRE_TO_HA = 0.404686;

    private final List<Field> fields = new ArrayList<>();
    private FieldRepository repository;
    private FieldAdapter adapter;
    private TextView emptyLabel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_fields, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = new FieldRepository(requireContext());
        emptyLabel = view.findViewById(R.id.emptyLabel);

        RecyclerView list = view.findViewById(R.id.fieldList);
        adapter = new FieldAdapter();
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        ExtendedFloatingActionButton newField = view.findViewById(R.id.newFieldButton);
        newField.setOnClickListener(v ->
                ((MainShellActivity) requireActivity()).openDestination(R.id.drawer_field));
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        fields.clear();
        fields.addAll(repository.all());
        adapter.notifyDataSetChanged();
        emptyLabel.setVisibility(fields.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showEditDialog(Field field) {
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
                    reload();
                })
                .show();
    }

    private class FieldAdapter extends RecyclerView.Adapter<FieldAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_field, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Field field = fields.get(position);
            holder.name.setText(field.name);
            holder.area.setText(getString(R.string.field_acres_ha,
                    field.areaAcres, field.areaAcres * SQM_PER_ACRE_TO_HA));
            holder.crop.setText(field.crop);
            holder.crop.setVisibility(field.crop == null || field.crop.isEmpty()
                    ? View.GONE : View.VISIBLE);

            holder.itemView.setOnClickListener(v ->
                    ((MainShellActivity) requireActivity()).openFieldOnMap(field.id));

            holder.edit.setOnClickListener(v -> showEditDialog(field));

            holder.delete.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.field_delete_confirm)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                        repository.delete(field.id);
                        reload();
                    })
                    .show());
        }

        @Override
        public int getItemCount() {
            return fields.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView name, area, crop;
            final MaterialButton edit, delete;

            Holder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.fieldName);
                area = itemView.findViewById(R.id.fieldArea);
                crop = itemView.findViewById(R.id.fieldCrop);
                edit = itemView.findViewById(R.id.fieldEdit);
                delete = itemView.findViewById(R.id.fieldDelete);
            }
        }
    }
}
