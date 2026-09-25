package com.mvx.agriculture.data;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Place search, so a farmer can jump the map to their village instead of
 * dragging across the country.
 *
 * Nominatim is OpenStreetMap's own geocoder: keyless, and it knows Indian
 * villages and hamlets, not just cities. It requires an identifying user agent
 * and asks callers not to hammer it, hence the debounce in the fragment.
 */
public class GeocodeClient {

    private static final String TAG = "GeocodeClient";

    public static class Place {
        public final String name;
        public final double lat;
        public final double lon;

        Place(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public interface Listener {
        void onPlaces(List<Place> places);

        void onError();
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Call inFlight;

    /** Cancels any previous lookup so fast typing does not stack requests. */
    public void search(String query, Listener listener) {
        if (inFlight != null) {
            inFlight.cancel();
        }
        final String url;
        try {
            url = "https://nominatim.openstreetmap.org/search?q="
                    + URLEncoder.encode(query, "UTF-8")
                    + "&format=json&limit=5&addressdetails=0";
        } catch (java.io.UnsupportedEncodingException e) {
            main.post(listener::onError);
            return;
        }

        inFlight = client.newCall(new Request.Builder()
                .url(url)
                .header("User-Agent", "AgriCareAi/1.0 (Android farm mapping)")
                .build());

        inFlight.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (call.isCanceled()) {
                    return;      // superseded by a newer query
                }
                Log.e(TAG, "Place search failed", e);
                main.post(listener::onError);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        main.post(listener::onError);
                        return;
                    }
                    List<Place> places = parse(new JSONArray(body.string()));
                    main.post(() -> listener.onPlaces(places));
                } catch (IOException | JSONException e) {
                    Log.e(TAG, "Could not read places", e);
                    main.post(listener::onError);
                }
            }
        });
    }

    private List<Place> parse(JSONArray array) throws JSONException {
        List<Place> places = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.getJSONObject(i);
            String full = row.optString("display_name");
            places.add(new Place(shorten(full),
                    row.getDouble("lat"), row.getDouble("lon")));
        }
        return places;
    }

    /** Nominatim returns the whole address chain; the first few parts identify it. */
    private static String shorten(String displayName) {
        String[] parts = displayName.split(",");
        StringBuilder shortened = new StringBuilder();
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            if (i > 0) {
                shortened.append(',');
            }
            shortened.append(parts[i].trim().isEmpty() ? "" : (i > 0 ? " " : "") + parts[i].trim());
        }
        return shortened.toString();
    }
}
