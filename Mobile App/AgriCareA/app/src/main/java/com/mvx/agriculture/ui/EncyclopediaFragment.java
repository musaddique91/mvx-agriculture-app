package com.mvx.agriculture.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mvx.agriculture.MainShellActivity;
import com.mvx.agriculture.R;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Offline disease reference, searchable, with a detail sheet per entry. */
public class EncyclopediaFragment extends Fragment {

    private final List<JSONObject> allDiseases = new ArrayList<>();
    private final List<JSONObject> visible = new ArrayList<>();
    private DiseaseAdapter adapter;
    private TextView emptyLabel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_encyclopedia, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        emptyLabel = view.findViewById(R.id.emptyLabel);
        RecyclerView list = view.findViewById(R.id.listView);
        adapter = new DiseaseAdapter();
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        loadDiseaseData();
        filter("");

        TextView search = view.findViewById(R.id.searchView);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        Bundle args = getArguments();
        String query = args == null ? null : args.getString("dis");
        if (!TextUtils.isEmpty(query)) {
            search.setText(query);
        }
    }

    private void loadDiseaseData() {
        try (InputStream is = requireContext().getAssets().open("diseases.json");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }

            JSONArray entries = new JSONObject(json.toString()).getJSONArray("diseases");
            for (int i = 0; i < entries.length(); i++) {
                allDiseases.add(entries.getJSONObject(i));
            }
        } catch (IOException | org.json.JSONException e) {
            android.util.Log.e("encyclo", "Could not read diseases.json", e);
        }
    }

    private void filter(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        visible.clear();
        for (JSONObject disease : allDiseases) {
            if (needle.isEmpty() || disease.optString("name").toLowerCase(Locale.ROOT).contains(needle)) {
                visible.add(disease);
            }
        }
        adapter.notifyDataSetChanged();
        emptyLabel.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Resolves the drawable named in the JSON, falling back to a generic leaf. */
    private int imageFor(JSONObject disease) {
        int id = requireContext().getResources().getIdentifier(
                disease.optString("link"), "drawable", requireContext().getPackageName());
        return id == 0 ? R.drawable.leaf : id;
    }

    private void showDetails(JSONObject disease) {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_disease_details, null);
        sheet.setContentView(view);

        // Open fully: half-height would hide the actions below the fold.
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);

        // The sheet draws behind the navigation bar, so lift the buttons above it.
        View actions = view.findViewById(R.id.sheetActions);
        ViewCompat.setOnApplyWindowInsetsListener(actions, (v, insets) -> {
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    getResources().getDimensionPixelSize(R.dimen.space_m) + navBottom);
            return insets;
        });

        ((ImageView) view.findViewById(R.id.tvPicture)).setImageResource(imageFor(disease));
        ((TextView) view.findViewById(R.id.tvName)).setText(disease.optString("name"));
        ((TextView) view.findViewById(R.id.tvDefinition)).setText(disease.optString("definition"));
        ((TextView) view.findViewById(R.id.tvHistory)).setText(disease.optString("history"));
        ((TextView) view.findViewById(R.id.tvSymptoms)).setText(disease.optString("symptoms"));
        ((TextView) view.findViewById(R.id.tvCauses)).setText(disease.optString("causes"));
        ((TextView) view.findViewById(R.id.tvSolutions)).setText(disease.optString("solutions"));

        ((MaterialButton) view.findViewById(R.id.btnClose)).setOnClickListener(v -> sheet.dismiss());
        ((MaterialButton) view.findViewById(R.id.btnAskBot)).setOnClickListener(v -> {
            sheet.dismiss();
            ((MainShellActivity) requireActivity())
                    .openChatAbout(disease.optString("name"));
        });

        sheet.show();
    }

    private class DiseaseAdapter extends RecyclerView.Adapter<DiseaseAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_disease, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            JSONObject disease = visible.get(position);
            holder.name.setText(disease.optString("name"));
            holder.blurb.setText(disease.optString("definition"));
            holder.thumb.setImageResource(imageFor(disease));
            holder.itemView.setOnClickListener(v -> showDetails(disease));
        }

        @Override
        public int getItemCount() {
            return visible.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView blurb;
            final ShapeableImageView thumb;

            Holder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.diseaseName);
                blurb = itemView.findViewById(R.id.diseaseBlurb);
                thumb = itemView.findViewById(R.id.diseaseThumb);
            }
        }
    }
}
