package com.mvx.agriculture.ui;

import android.content.Context;

import com.mvx.agriculture.R;
import com.mvx.agriculture.data.WeatherLocation;

/**
 * Turns a {@link WeatherLocation} into farmer-facing text.
 *
 * Lives apart from both screens on purpose: the Home card and the Weather
 * dropdown must never disagree about what a place is called.
 */
public final class WeatherLocationLabels {

    private WeatherLocationLabels() {
    }

    public static String title(Context context, WeatherLocation loc) {
        if (loc.kind == WeatherLocation.Kind.GPS) {
            return context.getString(loc.available
                    ? R.string.weather_location_gps
                    : R.string.weather_location_gps_unavailable);
        }
        return loc.name == null
                ? context.getString(R.string.weather_location_saved_point)
                : loc.name;
    }

    public static String subtitle(Context context, WeatherLocation loc) {
        switch (loc.kind) {
            case FIELD:
                String crop = loc.crop == null ? "" : loc.crop;
                return context.getString(R.string.weather_location_field_sub, crop, loc.areaAcres);
            case CITY:
                return context.getString(R.string.weather_location_city_sub);
            case CUSTOM:
                return context.getString(R.string.weather_location_saved_point);
            default:
                return "";
        }
    }
}
