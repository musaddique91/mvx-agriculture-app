package com.mvx.agriculture.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.mvx.agriculture.MainShellActivity;
import com.mvx.agriculture.R;
import com.mvx.agriculture.User;
import com.mvx.agriculture.data.Weather;
import com.mvx.agriculture.data.WeatherLocation;
import com.mvx.agriculture.data.WeatherLocationProvider;
import com.mvx.agriculture.data.WeatherLocationStore;
import com.mvx.agriculture.data.WeatherRepository;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Seven-day forecast plus the advisories that turn it into a decision. */
public class WeatherFragment extends Fragment {

    private SwipeRefreshLayout swipeRefresh;
    private TextView nowTemp, errorLabel;
    private LinearLayout advisories;
    private RecyclerView forecastList;
    private WeatherLocationProvider locations;
    private WeatherLocation current;
    private User user;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_weather, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        nowTemp = view.findViewById(R.id.nowTemp);
        errorLabel = view.findViewById(R.id.errorLabel);
        advisories = view.findViewById(R.id.advisories);
        forecastList = view.findViewById(R.id.forecastList);
        forecastList.setLayoutManager(new LinearLayoutManager(requireContext()));

        user = ((MainShellActivity) requireActivity()).currentUser();
        locations = new WeatherLocationProvider(requireContext());
        TextView place = view.findViewById(R.id.placeLabel);
        place.setVisibility(View.GONE);   // the dropdown now names the place
        setUpLocationPicker(view);

        labelStat(view, R.id.statHumidity, R.drawable.ic_humidity, R.string.weather_humidity);
        labelStat(view, R.id.statWind, R.drawable.ic_wind, R.string.weather_wind);
        labelStat(view, R.id.statRain, R.drawable.ic_rain, R.string.weather_rain_today);
        labelStat(view, R.id.statSoilTemp, R.drawable.ic_soil_temp, R.string.weather_soil_temp);
        labelStat(view, R.id.statSoilMoisture, R.drawable.ic_water, R.string.weather_soil_moisture);

        swipeRefresh.setOnRefreshListener(this::load);
        load();

        // Refine "My current location" in the background: never asks for the
        // permission, just upgrades a stale/missing fix if one is already allowed.
        locations.requestFreshFix(() -> {
            if (!isAdded() || getView() == null) {
                return;
            }
            setUpLocationPicker(requireView());
            if (current != null && current.kind == WeatherLocation.Kind.GPS) {
                load();
            }
        });
    }

    @Override
    public void onDestroyView() {
        if (locations != null) {
            locations.stopFreshFixRequest();
        }
        super.onDestroyView();
    }

    private void setUpLocationPicker(View view) {
        MaterialAutoCompleteTextView picker = view.findViewById(R.id.locationPicker);
        final List<WeatherLocation> all = locations.list(user);

        List<String> labels = new ArrayList<>();
        for (WeatherLocation loc : all) {
            String title = WeatherLocationLabels.title(requireContext(), loc);
            String subtitle = WeatherLocationLabels.subtitle(requireContext(), loc);
            labels.add(subtitle.isEmpty()
                    ? title
                    : getString(R.string.weather_location_title_subtitle, title, subtitle));
        }
        labels.add(getString(R.string.weather_location_pick_on_map));
        picker.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, labels));

        current = locations.selectedFrom(all);
        if (current != null) {
            picker.setText(WeatherLocationLabels.title(requireContext(), current), false);
        }

        picker.setOnItemClickListener((parent, v, position, id) -> {
            if (position == all.size()) {   // the trailing "Choose on map…" entry
                picker.setText(current == null
                        ? "" : WeatherLocationLabels.title(requireContext(), current), false);
                new LocationPickerDialog(requireActivity(), (region, city, lat, lon) -> {
                    WeatherLocationStore store = new WeatherLocationStore(requireContext());
                    String addedId = store.addCustom(city, lat, lon);
                    if (addedId == null) {
                        // Nothing was actually persisted (finding 3): selecting a
                        // never-saved id would make the pick silently vanish.
                        Toast.makeText(requireContext(),
                                R.string.weather_location_save_failed, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    locations.select(addedId);
                    setUpLocationPicker(requireView());   // rebuild, so the new point appears
                    load();
                }).show();
                return;
            }
            WeatherLocation picked = all.get(position);
            if (!picked.available) {
                // Shown so the farmer knows the option exists; not selectable.
                picker.setText(current == null
                        ? "" : WeatherLocationLabels.title(requireContext(), current), false);
                return;
            }
            current = picked;
            locations.select(picked.id);
            picker.setText(WeatherLocationLabels.title(requireContext(), picked), false);
            load();
        });
    }

    private void load() {
        if (current == null) {
            swipeRefresh.setRefreshing(false);
            errorLabel.setVisibility(View.VISIBLE);
            clearReadings();
            return;
        }
        // Captured so a reply that lands after the farmer has already picked a
        // different location can be told apart from one that still matches (finding 1):
        // OkHttp calls are not cancelled, so a stale reply for a superseded location
        // must never bind its numbers/advisories under the new location's name.
        final WeatherLocation requested = current;
        swipeRefresh.setRefreshing(true);
        errorLabel.setVisibility(View.GONE);
        // The previous location's numbers must not linger on screen under the new
        // name while the new location's reply is still in flight.
        clearReadings();
        new WeatherRepository().load(requested.lat, requested.lon,
                new WeatherRepository.Listener() {
                    @Override
                    public void onWeather(Weather weather) {
                        if (!isAdded() || getView() == null || requested != current) {
                            return;
                        }
                        swipeRefresh.setRefreshing(false);
                        errorLabel.setVisibility(View.GONE);
                        bind(weather);
                    }

                    @Override
                    public void onError() {
                        if (!isAdded() || getView() == null || requested != current) {
                            return;
                        }
                        swipeRefresh.setRefreshing(false);
                        errorLabel.setVisibility(View.VISIBLE);
                        clearReadings();
                    }
                });
    }

    /** Hides the previous location's readings while a new one loads or has failed. */
    private void clearReadings() {
        if (getView() == null) {
            return;
        }
        nowTemp.setText("");
        View root = requireView();
        setStat(root, R.id.statHumidity, "");
        setStat(root, R.id.statWind, "");
        setStat(root, R.id.statRain, "");
        setStat(root, R.id.statSoilTemp, "");
        setStat(root, R.id.statSoilMoisture, "");
        advisories.removeAllViews();
        forecastList.setAdapter(null);
    }

    private void bind(Weather weather) {
        View root = requireView();
        nowTemp.setText(String.format(Locale.getDefault(), "%.0f°C", weather.nowC));

        setStat(root, R.id.statHumidity, String.format(Locale.getDefault(), "%d%%", weather.humidity));
        setStat(root, R.id.statWind, String.format(Locale.getDefault(), "%.0f km/h", weather.windKph));
        setStat(root, R.id.statRain, String.format(Locale.getDefault(), "%.1f mm", weather.rainTodayMm));
        setStat(root, R.id.statSoilTemp, String.format(Locale.getDefault(), "%.0f°C", weather.soilTempC));
        setStat(root, R.id.statSoilMoisture,
                String.format(Locale.getDefault(), "%.0f%%", weather.soilMoisture * 100));

        buildAdvisories(weather);
        forecastList.setAdapter(new ForecastAdapter(weather));
    }

    /** Turns the numbers into the two or three things worth doing today. */
    private void buildAdvisories(Weather weather) {
        advisories.removeAllViews();

        if (weather.rainComing()) {
            addAdvisory(R.drawable.ic_warning, R.color.md_error,
                    R.string.weather_spray_rain, getString(R.string.weather_spray_rain_body));
        } else if (weather.tooWindyToSpray()) {
            addAdvisory(R.drawable.ic_wind, R.color.accent_market,
                    R.string.weather_spray_wind, getString(R.string.weather_spray_wind_body));
        } else {
            addAdvisory(R.drawable.ic_check_circle, R.color.accent_scan,
                    R.string.weather_spray_ok, getString(R.string.weather_spray_ok_body));
        }

        if (weather.needsIrrigation()) {
            addAdvisory(R.drawable.ic_irrigation, R.color.accent_weather,
                    R.string.weather_irrigate, getString(R.string.weather_irrigate_body));
        } else {
            addAdvisory(R.drawable.ic_check_circle, R.color.accent_scan,
                    R.string.weather_no_irrigate,
                    getString(R.string.weather_no_irrigate_body, weather.rainWeekMm));
        }

        if (weather.heatStress()) {
            addAdvisory(R.drawable.ic_warning, R.color.md_error,
                    R.string.weather_heat, getString(R.string.weather_heat_body));
        }
    }

    private void addAdvisory(int iconRes, int tintRes, int titleRes, String body) {
        View card = getLayoutInflater().inflate(R.layout.item_advisory, advisories, false);
        ImageView icon = card.findViewById(R.id.advisoryIcon);
        icon.setImageResource(iconRes);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), tintRes)));
        ((TextView) card.findViewById(R.id.advisoryTitle)).setText(titleRes);
        ((TextView) card.findViewById(R.id.advisoryBody)).setText(body);
        advisories.addView(card);
    }

    private void labelStat(View root, int id, int iconRes, int labelRes) {
        View stat = root.findViewById(id);
        ((ImageView) stat.findViewById(R.id.statIcon)).setImageResource(iconRes);
        ((TextView) stat.findViewById(R.id.statLabel)).setText(labelRes);
    }

    private void setStat(View root, int id, String value) {
        ((TextView) root.findViewById(id).findViewById(R.id.statValue)).setText(value);
    }

    private static class ForecastAdapter extends RecyclerView.Adapter<ForecastAdapter.Holder> {

        private final Weather weather;
        private final SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        private final SimpleDateFormat dayFormat = new SimpleDateFormat("EEE d MMM", Locale.getDefault());

        ForecastAdapter(Weather weather) {
            this.weather = weather;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_forecast_day, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Weather.Day day = weather.days.get(position);
            holder.name.setText(formatDay(day.date, position));
            holder.rain.setText(String.format(Locale.getDefault(), "%.1f mm · %d%%",
                    day.rainMm, day.rainChance));
            holder.temp.setText(String.format(Locale.getDefault(), "%.0f° / %.0f°",
                    day.maxC, day.minC));
        }

        private String formatDay(String raw, int position) {
            if (position == 0) {
                return dayFormat.format(Calendar.getInstance().getTime());
            }
            try {
                Date date = parser.parse(raw);
                return date == null ? raw : dayFormat.format(date);
            } catch (ParseException e) {
                return raw;
            }
        }

        @Override
        public int getItemCount() {
            return weather.days.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView name, rain, temp;

            Holder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.dayName);
                rain = itemView.findViewById(R.id.dayRain);
                temp = itemView.findViewById(R.id.dayTemp);
            }
        }
    }
}
