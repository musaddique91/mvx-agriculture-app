package com.mvx.agriculture.data;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Government scheme summaries from assets/schemes.json. */
public class SchemeData {

    private static final String TAG = "SchemeData";

    public static class Scheme {
        public String name;
        public String summary;
        public String benefit;
        public String eligibility;
        public String how;
        public String url;
    }

    private final List<Scheme> schemes = new ArrayList<>();

    public SchemeData(Context context) {
        try (InputStream is = context.getAssets().open("schemes.json");
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {

            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            JSONArray array = new JSONObject(json.toString()).getJSONArray("schemes");
            for (int i = 0; i < array.length(); i++) {
                JSONObject row = array.getJSONObject(i);
                Scheme scheme = new Scheme();
                scheme.name = row.optString("name");
                scheme.summary = row.optString("summary");
                scheme.benefit = row.optString("benefit");
                scheme.eligibility = row.optString("eligibility");
                scheme.how = row.optString("how");
                scheme.url = row.optString("url");
                schemes.add(scheme);
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not read schemes.json", e);
        }
    }

    public List<Scheme> all() {
        return schemes;
    }
}
