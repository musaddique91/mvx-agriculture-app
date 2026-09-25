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

import com.mvx.agriculture.R;
import com.mvx.agriculture.data.CropData;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Sowing and harvest windows, crops currently in season listed first. */
public class CropCalendarFragment extends Fragment {

    private final List<CropData.Crop> visible = new ArrayList<>();
    private List<CropData.Crop> all;
    private CropAdapter adapter;
    private TextView emptyLabel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        emptyLabel = view.findViewById(R.id.emptyLabel);
        emptyLabel.setText(R.string.empty_search);

        ((TextInputLayout) view.findViewById(R.id.searchLayout)).setHint(R.string.calc_crop);

        all = new CropData(requireContext()).all();
        // In-season crops are the ones a farmer is deciding about today.
        all.sort((a, b) -> Boolean.compare(b.inSeasonNow(), a.inSeasonNow()));

        RecyclerView list = view.findViewById(R.id.list);
        adapter = new CropAdapter();
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        filter("");

        TextInputEditText search = view.findViewById(R.id.searchInput);
        search.addTextChangedListener(new SimpleTextWatcher(text -> filter(text)));
    }

    private void filter(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        visible.clear();
        for (CropData.Crop crop : all) {
            if (needle.isEmpty() || crop.name.toLowerCase(Locale.ROOT).contains(needle)) {
                visible.add(crop);
            }
        }
        adapter.notifyDataSetChanged();
        emptyLabel.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private int seasonLabel(String season) {
        switch (season) {
            case "rabi":
                return R.string.calendar_season_rabi;
            case "zaid":
                return R.string.calendar_season_zaid;
            default:
                return R.string.calendar_season_kharif;
        }
    }

    private class CropAdapter extends RecyclerView.Adapter<CropAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_crop, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            CropData.Crop crop = visible.get(position);
            holder.name.setText(crop.name);
            holder.sow.setText(crop.sow);
            holder.harvest.setText(crop.harvest);
            holder.water.setText(crop.water);
            holder.tip.setText(crop.tip);

            holder.season.setText(crop.inSeasonNow()
                    ? getString(R.string.calendar_now)
                    : getString(seasonLabel(crop.season)));
            holder.season.setChipBackgroundColorResource(crop.inSeasonNow()
                    ? R.color.accent_scan_bg
                    : R.color.md_surface_container_high);
        }

        @Override
        public int getItemCount() {
            return visible.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView name, sow, harvest, water, tip;
            final Chip season;

            Holder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.cropName);
                sow = itemView.findViewById(R.id.cropSow);
                harvest = itemView.findViewById(R.id.cropHarvest);
                water = itemView.findViewById(R.id.cropWater);
                tip = itemView.findViewById(R.id.cropTip);
                season = itemView.findViewById(R.id.cropSeason);
            }
        }
    }
}
