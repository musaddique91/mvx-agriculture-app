package com.mvx.agriculture.ui;

import android.content.Intent;
import android.net.Uri;
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
import com.mvx.agriculture.data.SchemeData;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Scheme summaries that expand in place, each linking to the official portal. */
public class SchemesFragment extends Fragment {

    private final List<SchemeData.Scheme> visible = new ArrayList<>();
    private final java.util.Set<Integer> expanded = new java.util.HashSet<>();
    private List<SchemeData.Scheme> all;
    private SchemeAdapter adapter;
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
        ((TextInputLayout) view.findViewById(R.id.searchLayout)).setHint(R.string.title_schemes);

        all = new SchemeData(requireContext()).all();

        RecyclerView list = view.findViewById(R.id.list);
        adapter = new SchemeAdapter();
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        filter("");

        TextInputEditText search = view.findViewById(R.id.searchInput);
        search.addTextChangedListener(new SimpleTextWatcher(this::filter));
    }

    private void filter(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        visible.clear();
        expanded.clear();
        for (SchemeData.Scheme scheme : all) {
            if (needle.isEmpty()
                    || scheme.name.toLowerCase(Locale.ROOT).contains(needle)
                    || scheme.summary.toLowerCase(Locale.ROOT).contains(needle)) {
                visible.add(scheme);
            }
        }
        adapter.notifyDataSetChanged();
        emptyLabel.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private class SchemeAdapter extends RecyclerView.Adapter<SchemeAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_scheme, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            SchemeData.Scheme scheme = visible.get(position);
            holder.name.setText(scheme.name);
            holder.summary.setText(scheme.summary);
            holder.benefit.setText(scheme.benefit);
            holder.eligibility.setText(scheme.eligibility);
            holder.how.setText(scheme.how);
            holder.link.setText(scheme.url.replace("https://", ""));
            holder.link.setOnClickListener(v ->
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(scheme.url))));

            boolean open = expanded.contains(position);
            holder.details.setVisibility(open ? View.VISIBLE : View.GONE);
            holder.itemView.setOnClickListener(v -> {
                if (expanded.contains(position)) {
                    expanded.remove(position);
                } else {
                    expanded.add(position);
                }
                notifyItemChanged(position);
            });
        }

        @Override
        public int getItemCount() {
            return visible.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView name, summary, benefit, eligibility, how;
            final View details;
            final MaterialButton link;

            Holder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.schemeName);
                summary = itemView.findViewById(R.id.schemeSummary);
                benefit = itemView.findViewById(R.id.schemeBenefit);
                eligibility = itemView.findViewById(R.id.schemeEligibility);
                how = itemView.findViewById(R.id.schemeHow);
                details = itemView.findViewById(R.id.schemeDetails);
                link = itemView.findViewById(R.id.schemeLink);
            }
        }
    }
}
