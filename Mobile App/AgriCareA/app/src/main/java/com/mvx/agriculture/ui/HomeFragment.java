package com.mvx.agriculture.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.content.res.ColorStateList;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.mvx.agriculture.MainShellActivity;
import com.mvx.agriculture.R;
import com.mvx.agriculture.User;
import com.mvx.agriculture.data.DailyTip;
import com.mvx.agriculture.data.Field;
import com.mvx.agriculture.data.FieldRepository;
import com.mvx.agriculture.data.Weather;
import com.mvx.agriculture.data.WeatherLocation;
import com.mvx.agriculture.data.WeatherLocationProvider;
import com.mvx.agriculture.data.WeatherRepository;
import com.google.android.material.card.MaterialCardView;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** The signed-in landing screen: who you are, what the sky is doing, and every tool. */
public class HomeFragment extends Fragment {

    private TextView weatherTemp, weatherSummary, weatherAdvice;
    private final java.util.List<Field> fields = new java.util.ArrayList<>();
    private WeatherLocation weatherPlace;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MainShellActivity shell = (MainShellActivity) requireActivity();
        User user = shell.currentUser();
        String name = shell.username();

        ((TextView) view.findViewById(R.id.greeting)).setText(getString(greetingRes(), name));

        TextView subGreeting = view.findViewById(R.id.subGreeting);
        weatherPlace = new WeatherLocationProvider(requireContext()).selected(user);
        if (weatherPlace == null) {
            subGreeting.setText(R.string.app_tagline);
        } else if (weatherPlace.kind == WeatherLocation.Kind.GPS) {
            // "Today where you are" reads better than naming the GPS pseudo-place.
            subGreeting.setText(R.string.home_today_here);
            subGreeting.setOnClickListener(v -> shell.openDestination(R.id.nav_weather));
        } else {
            // "Today at Tomato plot" once a reading can differ from the town's,
            // so a number on this card is never unattributed.
            subGreeting.setText(getString(
                    weatherPlace.kind == WeatherLocation.Kind.CITY
                            ? R.string.home_today
                            : R.string.home_today_at,
                    WeatherLocationLabels.title(requireContext(), weatherPlace)));
            subGreeting.setOnClickListener(v -> shell.openDestination(R.id.nav_weather));
        }

        weatherTemp = view.findViewById(R.id.weatherTemp);
        weatherSummary = view.findViewById(R.id.weatherSummary);
        weatherAdvice = view.findViewById(R.id.weatherAdvice);

        MaterialCardView weatherCard = view.findViewById(R.id.weatherCard);
        weatherCard.setOnClickListener(v -> shell.openDestination(R.id.nav_weather));

        bindTiles(view, shell);

        fields.clear();
        fields.addAll(new FieldRepository(requireContext()).all());
        // Show a tip straight away from what we know offline, then upgrade it once
        // the forecast lands.
        showTip(DailyTip.forToday(requireContext(), null, fields));
        loadWeather(user);
    }

    private void bindTiles(View view, MainShellActivity shell) {
        Tiles.bind(view.findViewById(R.id.tileScan), R.drawable.ic_camera,
                R.string.feature_scan, R.string.feature_scan_desc,
                R.color.accent_scan, R.color.accent_scan_bg,
                v -> shell.openDestination(R.id.nav_scan));

        Tiles.bind(view.findViewById(R.id.tileChat), R.drawable.ic_chat,
                R.string.feature_chat, R.string.feature_chat_desc,
                R.color.accent_chat, R.color.accent_chat_bg,
                v -> shell.openDestination(R.id.nav_chat));

        Tiles.bind(view.findViewById(R.id.tileMarket), R.drawable.ic_rupee,
                R.string.feature_market, R.string.feature_market_desc,
                R.color.accent_market, R.color.accent_market_bg,
                v -> shell.openDestination(R.id.drawer_market));

        Tiles.bind(view.findViewById(R.id.tileCalendar), R.drawable.ic_calendar,
                R.string.feature_calendar, R.string.feature_calendar_desc,
                R.color.accent_calendar, R.color.accent_calendar_bg,
                v -> shell.openDestination(R.id.drawer_calendar));

        Tiles.bind(view.findViewById(R.id.tileCalculator), R.drawable.ic_calculator,
                R.string.feature_calculator, R.string.feature_calculator_desc,
                R.color.accent_calc, R.color.accent_calc_bg,
                v -> shell.openDestination(R.id.drawer_calculator));

        Tiles.bind(view.findViewById(R.id.tileField), R.drawable.ic_map,
                R.string.feature_field, R.string.feature_field_desc,
                R.color.accent_map, R.color.accent_map_bg,
                v -> shell.openDestination(R.id.drawer_field));

        Tiles.bind(view.findViewById(R.id.tileEncyclopedia), R.drawable.ic_book,
                R.string.feature_encyclopedia, R.string.feature_encyclopedia_desc,
                R.color.accent_book, R.color.accent_book_bg,
                v -> shell.openDestination(R.id.drawer_encyclopedia));

        Tiles.bind(view.findViewById(R.id.tileSchemes), R.drawable.ic_scheme,
                R.string.feature_schemes, R.string.feature_schemes_desc,
                R.color.accent_scheme, R.color.accent_scheme_bg,
                v -> shell.openDestination(R.id.drawer_schemes));

        Tiles.bind(view.findViewById(R.id.tileMoney), R.drawable.ic_wallet,
                R.string.feature_money, R.string.feature_money_desc,
                R.color.accent_money, R.color.accent_money_bg,
                v -> shell.openDestination(R.id.drawer_money));
    }

    /** Paints the tip card, and points it at the screen that follows up on it. */
    private void showTip(DailyTip tip) {
        View view = getView();
        if (view == null) {
            return;
        }
        ImageView icon = view.findViewById(R.id.tipIcon);
        icon.setImageResource(tip.topic.iconRes);
        Tiles.applyBadge(icon, tip.topic.colorRes, R.color.md_surface_container_high);

        ((TextView) view.findViewById(R.id.tipTopic)).setText(tip.topic.labelRes);
        ((TextView) view.findViewById(R.id.tipBody)).setText(tip.text);
        ((TextView) view.findViewById(R.id.tipDate)).setText(
                new java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
                        .format(Calendar.getInstance().getTime()));

        view.findViewById(R.id.tipCard).setOnClickListener(v ->
                ((MainShellActivity) requireActivity()).openDestination(destinationFor(tip.topic)));
    }

    /** Tapping a tip should land on the screen that acts on it. */
    private int destinationFor(DailyTip.Topic topic) {
        switch (topic) {
            case MARKET:
                return R.id.drawer_market;
            case CROP:
                return R.id.drawer_calendar;
            case DISEASE:
                return R.id.nav_scan;
            case SCHEME:
                return R.id.drawer_schemes;
            default:
                return R.id.nav_weather;
        }
    }

    private void loadWeather(User user) {
        if (user == null) {
            return;
        }
        double[] coordinates = weatherPlace == null
                ? null
                : new double[]{weatherPlace.lat, weatherPlace.lon};
        if (coordinates == null) {
            weatherSummary.setText(R.string.weather_failed);
            return;
        }
        new WeatherRepository().load(coordinates[0], coordinates[1],
                new WeatherRepository.Listener() {
                    @Override
                    public void onWeather(Weather weather) {
                        if (!isAdded() || getView() == null) {
                            return;
                        }
                        weatherTemp.setText(String.format(Locale.getDefault(), "%.0f°", weather.nowC));
                        weatherSummary.setText(String.format(Locale.getDefault(),
                                "%s %d%%  ·  %s %.0f km/h  ·  %.1f mm",
                                getString(R.string.weather_humidity), weather.humidity,
                                getString(R.string.weather_wind), weather.windKph,
                                weather.rainTodayMm));
                        showHeadlineAdvice(weather);
                        showTip(DailyTip.forToday(requireContext(), weather, fields));
                    }

                    @Override
                    public void onError() {
                        if (isAdded() && getView() != null) {
                            weatherSummary.setText(R.string.weather_failed);
                        }
                    }
                });
    }

    /** The single most actionable line for today. */
    /**
     * Names the day's most useful advice and gives it the same icon the Weather
     * screen uses, so "do not spray" never sits next to a reassuring tick.
     */
    private void showHeadlineAdvice(Weather weather) {
        int text;
        int icon;
        if (weather.rainComing()) {
            text = R.string.weather_spray_rain;
            icon = R.drawable.ic_warning;
        } else if (weather.tooWindyToSpray()) {
            text = R.string.weather_spray_wind;
            icon = R.drawable.ic_wind;
        } else if (weather.needsIrrigation()) {
            text = R.string.weather_irrigate;
            icon = R.drawable.ic_irrigation;
        } else {
            text = R.string.weather_spray_ok;
            icon = R.drawable.ic_check_circle;
        }
        weatherAdvice.setText(text);
        weatherAdvice.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
    }

    private int greetingRes() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) {
            return R.string.greeting_morning;
        }
        return hour < 17 ? R.string.greeting_afternoon : R.string.greeting_evening;
    }
}
