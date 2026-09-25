package com.mvx.agriculture.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Remembers which location the farmer is watching, and any points they saved
 * off the map. Small enough for SharedPreferences; there is no reason to put a
 * handful of coordinates in SQLite.
 */
public class WeatherLocationStore {

    private static final String TAG = "WeatherLocationStore";
    private static final String PREFS = "weather_locations";
    private static final String KEY_SELECTED = "selected_id";
    private static final String KEY_CUSTOMS = "customs";

    private final SharedPreferences prefs;

    public WeatherLocationStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The last choice, or null on a first run. */
    public String selectedId() {
        return prefs.getString(KEY_SELECTED, null);
    }

    public void select(String id) {
        prefs.edit().putString(KEY_SELECTED, id).apply();
    }

    public List<WeatherLocation> customs() {
        List<WeatherLocation> out = new ArrayList<>();
        String raw = prefs.getString(KEY_CUSTOMS, null);
        if (raw == null) {
            return out;
        }
        JSONArray array;
        try {
            array = new JSONArray(raw);
        } catch (JSONException e) {
            // The whole stored value is unreadable: nothing to salvage, screen must not crash.
            Log.w(TAG, "Dropping unreadable saved locations", e);
            return new ArrayList<>();
        }
        // Parse each entry on its own so one bad point does not cost every saved point.
        int skipped = 0;
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject o = array.getJSONObject(i);
                // A null name is stored as JSONObject.NULL (finding 3): isNull() catches
                // that case before getString() would throw and drop the whole entry.
                String name = o.isNull("name") ? null : o.getString("name");
                out.add(new WeatherLocation(WeatherLocation.Kind.CUSTOM,
                        o.getString("id"), name, null, 0,
                        o.getDouble("lat"), o.getDouble("lon"), true));
            } catch (JSONException e) {
                skipped++;
            }
        }
        if (skipped > 0) {
            Log.w(TAG, "Skipped " + skipped + " unreadable saved location(s)");
        }
        return out;
    }

    /**
     * Saves a picked point and returns the id it was given, so the caller can select it
     * without having to re-read the list and guess which entry is the one just added.
     *
     * <p>Returns null when the point could not actually be persisted — either the
     * coordinates are not finite numbers (JSONObject.put throws on NaN/Infinity, so
     * write() would silently drop the entry while this method kept reporting success),
     * or building/appending the entry's JSON otherwise failed. Callers must treat a
     * null return as "nothing was saved" and must not call select(null) with it.
     */
    public String addCustom(String name, double lat, double lon) {
        // JSONObject.put(String, double) throws on NaN/Infinity, so write() would
        // silently skip this entry while we kept reporting the id as saved. Reject
        // unusable coordinates up front instead of promising a selection that vanishes.
        if (Double.isNaN(lat) || Double.isInfinite(lat)
                || Double.isNaN(lon) || Double.isInfinite(lon)) {
            Log.w(TAG, "Refusing to save a location with non-finite coordinates");
            return null;
        }
        String id = "custom:" + UUID.randomUUID();
        JSONObject entry = new JSONObject();
        try {
            entry.put("id", id);
            // On Android, JSONObject.put(name, null) removes the key instead of storing
            // it, so a null name must be written explicitly as JSONObject.NULL for
            // customs()'s isNull() check to read it back as null rather than dropping
            // the whole entry when getString("name") throws.
            entry.put("name", name == null ? JSONObject.NULL : name);
            entry.put("lat", lat);
            entry.put("lon", lon);
        } catch (JSONException e) {
            // Coordinates are already validated above, so this should not happen in
            // practice; still, do not report an id that was never actually built.
            Log.w(TAG, "Could not build the new saved location", e);
            return null;
        }

        List<WeatherLocation> existing = customs();
        existing.add(new WeatherLocation(WeatherLocation.Kind.CUSTOM,
                id, name, null, 0, lat, lon, true));
        write(existing);
        return id;
    }

    public void removeCustom(String id) {
        List<WeatherLocation> kept = new ArrayList<>();
        for (WeatherLocation loc : customs()) {
            if (!loc.id.equals(id)) {
                kept.add(loc);
            }
        }
        write(kept);
    }

    private void write(List<WeatherLocation> locations) {
        JSONArray array = new JSONArray();
        int skipped = 0;
        for (WeatherLocation loc : locations) {
            // One bad entry must not abort the whole save; skip it and keep going.
            try {
                JSONObject o = new JSONObject();
                o.put("id", loc.id);
                // See addCustom(): a plain put(null) here would remove the key.
                o.put("name", loc.name == null ? JSONObject.NULL : loc.name);
                o.put("lat", loc.lat);
                o.put("lon", loc.lon);
                array.put(o);
            } catch (JSONException e) {
                skipped++;
            }
        }
        if (skipped > 0) {
            Log.w(TAG, "Skipped " + skipped + " location(s) that could not be saved");
        }
        prefs.edit().putString(KEY_CUSTOMS, array.toString()).apply();
    }
}
