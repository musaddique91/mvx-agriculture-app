package com.mvx.agriculture.data;

import com.mvx.agriculture.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the weather location list and decides which one is showing.
 *
 * Pure: no Context, no SharedPreferences, no Android types at all. Everything
 * it needs arrives as an argument, which is what lets the fallback chain — the
 * only part with real behaviour — be covered by fast JVM tests.
 */
public final class WeatherLocations {

    public static final String ID_CITY = "city";
    public static final String ID_GPS = "gps";

    private WeatherLocations() {
    }

    /**
     * The dropdown, in display order: mapped plots newest first, the registered
     * town, the current position, then saved points.
     *
     * @param cityCoords the registered town's fix, or null when the CSV has none
     * @param gpsFix     the latest position, or null when there is none yet
     */
    public static List<WeatherLocation> build(List<Field> fields, User user,
                                              double[] cityCoords, double[] gpsFix,
                                              List<WeatherLocation> customs) {
        List<WeatherLocation> out = new ArrayList<>();

        List<Field> sorted = new ArrayList<>(fields);
        // Newest first, so "my latest plot" is what a farmer lands on.
        Collections.sort(sorted, new Comparator<Field>() {
            @Override
            public int compare(Field a, Field b) {
                return Long.compare(b.createdAt, a.createdAt);
            }
        });

        for (Field field : sorted) {
            Field.Point centre = field.centroid();
            if (centre == null) {
                continue;   // saved but never drawn — we have no fix to ask about
            }
            out.add(new WeatherLocation(WeatherLocation.Kind.FIELD, "field:" + field.id,
                    field.name, field.crop, field.areaAcres, centre.lat, centre.lon, true));
        }

        if (cityCoords != null && user != null && user.getCity() != null) {
            out.add(new WeatherLocation(WeatherLocation.Kind.CITY, ID_CITY,
                    user.getCity(), null, 0, cityCoords[0], cityCoords[1], true));
        }

        boolean hasFix = gpsFix != null;
        out.add(new WeatherLocation(WeatherLocation.Kind.GPS, ID_GPS, null, null, 0,
                hasFix ? gpsFix[0] : 0, hasFix ? gpsFix[1] : 0, hasFix));

        out.addAll(customs);
        return out;
    }

    /**
     * The location to show: what they last chose, else their newest plot, else
     * their town, else anything usable. Null only when nothing is usable at all.
     *
     * Anything unavailable is skipped, so a remembered GPS entry with no fix
     * quietly yields to a real plot instead of showing an empty screen.
     */
    public static WeatherLocation resolveSelected(List<WeatherLocation> all, String rememberedId) {
        if (rememberedId != null) {
            for (WeatherLocation loc : all) {
                if (rememberedId.equals(loc.id) && loc.available) {
                    return loc;
                }
            }
        }
        WeatherLocation firstAvailable = null;
        for (WeatherLocation loc : all) {
            if (!loc.available) {
                continue;
            }
            if (loc.kind == WeatherLocation.Kind.FIELD) {
                return loc;   // list is already newest-first
            }
            if (firstAvailable == null) {
                firstAvailable = loc;
            }
        }
        return firstAvailable;
    }
}
