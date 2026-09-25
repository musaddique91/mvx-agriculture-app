package com.mvx.agriculture.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mvx.agriculture.MainShellActivity;
import com.mvx.agriculture.R;
import com.mvx.agriculture.User;
import com.mvx.agriculture.data.MandiFilter;
import com.mvx.agriculture.data.MandiPrice;
import com.mvx.agriculture.data.MandiRepository;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Daily Agmarknet rates for one state, narrowed on the phone by district, mandi,
 * commodity and variety, and sorted by name or price.
 *
 * The state is fetched once; every other filter works on those rows (see
 * {@link MandiFilter}), so each list only offers names that have rates today and
 * changing a filter is instant.
 */
public class MandiFragment extends Fragment {

    /** data.gov.in briefly returns nothing while it replaces the day's rates; one quiet retry covers it. */
    private static final long EMPTY_RETRY_MS = 2500;

    private interface OnPick {
        /** @param value the chosen name, or null for "all" */
        void picked(String value);
    }

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView list;
    private TextView messageLabel;
    private TextView summaryLabel;
    private Chip chipState, chipDistrict, chipMandi, chipCommodity, chipVariety, chipSort;
    private final List<MandiPrice> prices = new ArrayList<>();
    private PriceAdapter adapter;

    /** Today's rows for the chosen state, before any filter. */
    private List<MandiPrice> stateRows = new ArrayList<>();
    private final MandiFilter.Selection selection = new MandiFilter.Selection();
    private String state;
    private String homeState;
    private String homeDistrict;
    /** Bumped per request, so a slow reply for a state the farmer has since left is ignored. */
    private int requestId;
    private boolean retriedEmpty;
    private final Runnable retry = this::fetch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mandi, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        list = view.findViewById(R.id.priceList);
        messageLabel = view.findViewById(R.id.messageLabel);
        summaryLabel = view.findViewById(R.id.summaryLabel);
        chipState = view.findViewById(R.id.chipState);
        chipDistrict = view.findViewById(R.id.chipDistrict);
        chipMandi = view.findViewById(R.id.chipMandi);
        chipCommodity = view.findViewById(R.id.chipCommodity);
        chipVariety = view.findViewById(R.id.chipVariety);
        chipSort = view.findViewById(R.id.chipSort);

        adapter = new PriceAdapter();
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        User user = ((MainShellActivity) requireActivity()).currentUser();
        if (user != null && MandiRepository.STATES.contains(user.getRegion())) {
            homeState = user.getRegion();
            // Signup city names often differ from Agmarknet's district names.
            homeDistrict = MandiFilter.agmarknetDistrict(user.getCity());
        }
        state = homeState;
        selection.district = homeDistrict;

        onTap(chipState, this::pickState);
        onTap(chipDistrict, this::pickDistrict);
        onTap(chipMandi, this::pickMandi);
        onTap(chipCommodity, this::pickCommodity);
        onTap(chipVariety, this::pickVariety);
        onTap(chipSort, this::pickSort);

        swipeRefresh.setOnRefreshListener(() -> {
            retriedEmpty = false;
            fetch();
        });
        renderChips();
        fetch();
    }

    @Override
    public void onDestroyView() {
        list.removeCallbacks(retry);
        super.onDestroyView();
    }

    /** The dropdown arrow is part of the chip, so tapping either opens the list. */
    private static void onTap(Chip chip, Runnable action) {
        chip.setOnClickListener(v -> action.run());
        chip.setOnCloseIconClickListener(v -> action.run());
    }

    private void fetch() {
        list.removeCallbacks(retry);
        if (state == null) {
            // Signed out, or a region the Indian feed does not cover: ask rather than guess.
            swipeRefresh.setRefreshing(false);
            stateRows = new ArrayList<>();
            prices.clear();
            adapter.notifyDataSetChanged();
            renderChips();
            summaryLabel.setVisibility(View.GONE);
            showMessage(R.string.market_pick_state);
            return;
        }
        swipeRefresh.setRefreshing(true);
        messageLabel.setVisibility(View.GONE);
        final int id = ++requestId;

        new MandiRepository().loadState(state, new MandiRepository.Listener() {
            @Override
            public void onPrices(List<MandiPrice> results) {
                if (!isAdded() || getView() == null || id != requestId) {
                    return;
                }
                if (results.isEmpty() && !retriedEmpty) {
                    retriedEmpty = true;
                    list.postDelayed(retry, EMPTY_RETRY_MS);
                    return;   // keep the spinner; an empty reply is usually the feed mid-refresh
                }
                retriedEmpty = false;
                swipeRefresh.setRefreshing(false);
                stateRows = results;
                revalidate();
                applyFilters();
            }

            @Override
            public void onError(boolean missingKey) {
                if (!isAdded() || getView() == null || id != requestId) {
                    return;
                }
                swipeRefresh.setRefreshing(false);
                stateRows = new ArrayList<>();
                prices.clear();
                adapter.notifyDataSetChanged();
                renderChips();
                summaryLabel.setVisibility(View.GONE);
                showMessage(missingKey ? R.string.market_no_key : R.string.market_failed);
            }
        });
    }

    /** Drops any pick that today's rows no longer offer, outermost filter first. */
    private void revalidate() {
        selection.district = MandiFilter.keepIfOffered(selection.district,
                MandiFilter.districts(stateRows));
        selection.market = MandiFilter.keepIfOffered(selection.market,
                MandiFilter.markets(stateRows, selection.district));
        selection.commodity = MandiFilter.keepIfOffered(selection.commodity,
                MandiFilter.commodities(stateRows, selection.district, selection.market));
        selection.variety = MandiFilter.keepIfOffered(selection.variety,
                MandiFilter.varieties(stateRows, selection.district, selection.market, selection.commodity));
    }

    private void applyFilters() {
        prices.clear();
        prices.addAll(MandiFilter.apply(stateRows, selection));
        adapter.notifyDataSetChanged();
        list.scrollToPosition(0);
        renderChips();

        if (stateRows.isEmpty()) {
            summaryLabel.setVisibility(View.GONE);
            showMessage(R.string.market_empty);
            return;
        }
        summaryLabel.setText(getString(R.string.market_summary, stateRows.get(0).date, prices.size()));
        summaryLabel.setVisibility(View.VISIBLE);
        showMessage(prices.isEmpty() ? R.string.market_empty : 0);
    }

    private void renderChips() {
        boolean haveRows = !stateRows.isEmpty();
        chipState.setText(state == null ? getString(R.string.market_state) : state);
        chipDistrict.setText(orAll(selection.district, R.string.market_all_districts));
        chipMandi.setText(orAll(selection.market, R.string.market_all_mandis));
        chipCommodity.setText(orAll(selection.commodity, R.string.market_all_commodities));
        chipVariety.setText(orAll(selection.variety, R.string.market_all_varieties));
        chipSort.setText(sortLabel(selection.sort));

        chipDistrict.setEnabled(haveRows);
        chipMandi.setEnabled(haveRows);
        chipCommodity.setEnabled(haveRows);
        chipSort.setEnabled(haveRows);
        // Variety only matters once a commodity is chosen and it actually comes in more than one.
        boolean varieties = MandiFilter.varieties(stateRows, selection.district, selection.market,
                selection.commodity).size() > 1;
        chipVariety.setVisibility(varieties ? View.VISIBLE : View.GONE);
    }

    private String orAll(String pick, @StringRes int allLabel) {
        return pick == null ? getString(allLabel) : pick;
    }

    private void pickState() {
        choose(R.string.market_state, MandiRepository.STATES, 0, state, picked -> {
            if (picked == null || picked.equals(state)) {
                return;
            }
            state = picked;
            // Back home, the farmer's own district again; elsewhere start state-wide.
            // The commodity is kept, so maize can be compared across a border.
            selection.district = picked.equals(homeState) ? homeDistrict : null;
            selection.market = null;
            stateRows = new ArrayList<>();
            prices.clear();
            adapter.notifyDataSetChanged();
            summaryLabel.setVisibility(View.GONE);
            retriedEmpty = false;
            renderChips();
            fetch();
        });
    }

    private void pickDistrict() {
        choose(R.string.market_district, MandiFilter.districts(stateRows), R.string.market_all_districts,
                selection.district, picked -> {
                    selection.district = picked;
                    selection.market = null;
                    revalidate();
                    applyFilters();
                });
    }

    private void pickMandi() {
        choose(R.string.market_mandi, MandiFilter.markets(stateRows, selection.district),
                R.string.market_all_mandis, selection.market, picked -> {
                    selection.market = picked;
                    revalidate();
                    applyFilters();
                });
    }

    private void pickCommodity() {
        choose(R.string.market_commodity,
                MandiFilter.commodities(stateRows, selection.district, selection.market),
                R.string.market_all_commodities, selection.commodity, picked -> {
                    selection.commodity = picked;
                    selection.variety = null;
                    applyFilters();
                });
    }

    private void pickVariety() {
        choose(R.string.market_variety, MandiFilter.varieties(stateRows, selection.district,
                        selection.market, selection.commodity),
                R.string.market_all_varieties, selection.variety, picked -> {
                    selection.variety = picked;
                    applyFilters();
                });
    }

    private void pickSort() {
        MandiFilter.Sort[] sorts = MandiFilter.Sort.values();
        List<String> labels = new ArrayList<>();
        for (MandiFilter.Sort sort : sorts) {
            labels.add(sortLabel(sort));
        }
        choose(R.string.market_sort, labels, 0, sortLabel(selection.sort), picked -> {
            selection.sort = sorts[labels.indexOf(picked)];
            applyFilters();
        });
    }

    private String sortLabel(MandiFilter.Sort sort) {
        switch (sort) {
            case PRICE_HIGH:
                return getString(R.string.market_sort_price_high);
            case PRICE_LOW:
                return getString(R.string.market_sort_price_low);
            default:
                return getString(R.string.market_sort_name);
        }
    }

    /**
     * A single-choice list. When {@code allLabel} is non-zero an "All …" row
     * comes first and picking it reports null.
     */
    private void choose(@StringRes int title, List<String> options, @StringRes int allLabel,
                        String current, OnPick onPick) {
        int offset = allLabel != 0 ? 1 : 0;
        List<String> labels = new ArrayList<>();
        if (allLabel != 0) {
            labels.add(getString(allLabel));
        }
        labels.addAll(options);

        int checked;
        if (current == null) {
            checked = allLabel != 0 ? 0 : -1;
        } else {
            String match = MandiFilter.keepIfOffered(current, options);
            checked = match == null ? -1 : options.indexOf(match) + offset;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(labels.toArray(new CharSequence[0]), checked, (dialog, which) -> {
                    dialog.dismiss();
                    onPick.picked(which < offset ? null : options.get(which - offset));
                })
                .show();
    }

    private void showMessage(int stringRes) {
        if (stringRes == 0) {
            messageLabel.setVisibility(View.GONE);
            list.setVisibility(View.VISIBLE);
        } else {
            messageLabel.setText(stringRes);
            messageLabel.setVisibility(View.VISIBLE);
            list.setVisibility(View.GONE);
        }
    }

    private class PriceAdapter extends RecyclerView.Adapter<PriceAdapter.Holder> {

        private final NumberFormat rupees = NumberFormat.getIntegerInstance(new Locale("en", "IN"));

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_mandi_price, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            MandiPrice price = prices.get(position);
            String variety = MandiFilter.shownVariety(price);
            // Two "Maize" rows at one mandi are different varieties at different prices.
            holder.commodity.setText(variety == null
                    ? price.commodity
                    : getString(R.string.market_commodity_variety, price.commodity, variety));
            holder.market.setText(price.market + " · " + price.district);
            holder.range.setText(getString(R.string.market_min_max,
                    "₹" + rupees.format(price.minPrice), "₹" + rupees.format(price.maxPrice)));
            holder.modal.setText("₹" + rupees.format(price.modalPrice));
        }

        @Override
        public int getItemCount() {
            return prices.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView commodity, market, range, modal;

            Holder(@NonNull View itemView) {
                super(itemView);
                commodity = itemView.findViewById(R.id.priceCommodity);
                market = itemView.findViewById(R.id.priceMarket);
                range = itemView.findViewById(R.id.priceRange);
                modal = itemView.findViewById(R.id.priceModal);
            }
        }
    }
}
