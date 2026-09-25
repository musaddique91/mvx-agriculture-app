package com.mvx.agriculture.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Where the farmer last left the field map.
 *
 * Without this the map snapped back to their registered town on every visit, so
 * mapping a second plot in the same corner of a village meant panning there again
 * from scratch each time.
 */
public final class MapPositionStore {

    private static final String PREFS = "agricare_prefs";
    private static final String KEY_LAT = "map_lat";
    private static final String KEY_LON = "map_lon";
    private static final String KEY_ZOOM = "map_zoom";

    private MapPositionStore() {
    }

    public static void save(Context context, double lat, double lon, double zoom) {
        prefs(context).edit()
                .putFloat(KEY_LAT, (float) lat)
                .putFloat(KEY_LON, (float) lon)
                .putFloat(KEY_ZOOM, (float) zoom)
                .apply();
    }

    public static boolean has(Context context) {
        return prefs(context).contains(KEY_LAT);
    }

    /** {latitude, longitude, zoom}, or null when nothing has been stored. */
    public static double[] load(Context context) {
        SharedPreferences prefs = prefs(context);
        if (!prefs.contains(KEY_LAT)) {
            return null;
        }
        return new double[]{
                prefs.getFloat(KEY_LAT, 0f),
                prefs.getFloat(KEY_LON, 0f),
                prefs.getFloat(KEY_ZOOM, 17f)};
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
