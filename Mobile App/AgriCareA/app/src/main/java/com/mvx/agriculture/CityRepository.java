package com.mvx.agriculture;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the bundled region/city list (assets/cities.csv) and exposes it grouped
 * by region, so a screen can offer a region picker that drives a city picker.
 *
 * The CSV is "Region,City,Latitude,Longitude" with a header row. Regions keep the
 * order they first appear in the file, and the coordinates feed the weather and
 * field-map screens.
 */
public class CityRepository {

    private static final String TAG = "CityRepository";
    private static final String ASSET = "cities.csv";

    /** "Region|City" -> {latitude, longitude}, for the weather and map screens. */
    private final Map<String, double[]> coordinates = new HashMap<>();
    private final Map<String, List<String>> citiesByRegion;

    public CityRepository(Context context) {
        citiesByRegion = load(context);
    }

    /** Coordinates of a city, or null when the CSV has no fix for it. */
    public double[] coordinatesOf(String region, String city) {
        return coordinates.get(region + "|" + city);
    }

    /** Region names, in file order. Empty only if the asset is missing or unreadable. */
    public List<String> getRegions() {
        return new ArrayList<>(citiesByRegion.keySet());
    }

    /** Cities for a region, alphabetically; empty list for an unknown region. */
    public List<String> getCities(String region) {
        List<String> cities = citiesByRegion.get(region);
        return cities == null ? Collections.<String>emptyList() : new ArrayList<>(cities);
    }

    /**
     * The closest known district to a map point, as {region, city}.
     *
     * Picking an arbitrary point on a map would otherwise strand the account with
     * a place the weather and mandi lookups know nothing about, so a pick is
     * snapped to the nearest district we actually hold coordinates for.
     */
    public String[] nearestTo(double lat, double lon) {
        String bestKey = null;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<String, double[]> entry : coordinates.entrySet()) {
            double[] point = entry.getValue();
            // Equirectangular approximation: fine for ranking candidates.
            double dLat = point[0] - lat;
            double dLon = (point[1] - lon) * Math.cos(Math.toRadians(lat));
            double distance = dLat * dLat + dLon * dLon;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestKey = entry.getKey();
            }
        }
        return bestKey == null ? null : bestKey.split("\\|", 2);
    }

    /** The region a city belongs to, or null when it is not in the list. */
    public String findRegionOf(String city) {
        if (city == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : citiesByRegion.entrySet()) {
            if (entry.getValue().contains(city)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private Map<String, List<String>> load(Context context) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        InputStream inputStream = null;
        try {
            inputStream = context.getAssets().open(ASSET);
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {           // skip the "Region,City" header
                    firstLine = false;
                    continue;
                }
                String[] tokens = line.split(",");
                if (tokens.length < 2) {
                    continue;
                }
                String region = tokens[0].trim();
                String city = tokens[1].trim();
                if (region.isEmpty() || city.isEmpty()) {
                    continue;
                }
                if (tokens.length >= 4) {
                    try {
                        coordinates.put(region + "|" + city, new double[]{
                                Double.parseDouble(tokens[2].trim()),
                                Double.parseDouble(tokens[3].trim())});
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Bad coordinates for " + city);
                    }
                }
                List<String> cities = result.get(region);
                if (cities == null) {
                    cities = new ArrayList<>();
                    result.put(region, cities);
                }
                if (!cities.contains(city)) {
                    cities.add(city);
                }
            }
            reader.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not read " + ASSET, e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                    // nothing useful to do on close failure
                }
            }
        }
        return result;
    }
}
