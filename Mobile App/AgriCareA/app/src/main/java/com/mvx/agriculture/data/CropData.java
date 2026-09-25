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
import java.util.Calendar;
import java.util.List;

/** Sowing windows and nutrient doses from assets/crops.json. */
public class CropData {

    private static final String TAG = "CropData";

    public static class Crop {
        public String name;
        public String season;       // kharif | rabi | zaid
        public String sow;
        public String harvest;
        public String days;
        public String water;
        public int n;               // kg per hectare
        public int p;
        public int k;
        public double seedKgPerAcre;
        public String tip;

        /** True when today falls in this crop's usual season. */
        public boolean inSeasonNow() {
            int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
            switch (season) {
                case "kharif":
                    return month >= 6 && month <= 10;
                case "rabi":
                    return month >= 10 || month <= 3;
                case "zaid":
                    return month >= 1 && month <= 5;
                default:
                    return false;
            }
        }
    }

    private final List<Crop> crops = new ArrayList<>();

    public CropData(Context context) {
        try (InputStream is = context.getAssets().open("crops.json");
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {

            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            JSONArray array = new JSONObject(json.toString()).getJSONArray("crops");
            for (int i = 0; i < array.length(); i++) {
                JSONObject row = array.getJSONObject(i);
                Crop crop = new Crop();
                crop.name = row.optString("name");
                crop.season = row.optString("season");
                crop.sow = row.optString("sow");
                crop.harvest = row.optString("harvest");
                crop.days = row.optString("days");
                crop.water = row.optString("water");
                crop.n = row.optInt("n");
                crop.p = row.optInt("p");
                crop.k = row.optInt("k");
                crop.seedKgPerAcre = row.optDouble("seedKgPerAcre", 0);
                crop.tip = row.optString("tip");
                crops.add(crop);
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not read crops.json", e);
        }
    }

    public List<Crop> all() {
        return crops;
    }

    public List<String> names() {
        List<String> names = new ArrayList<>();
        for (Crop crop : crops) {
            names.add(crop.name);
        }
        return names;
    }

    public Crop byName(String name) {
        for (Crop crop : crops) {
            if (crop.name.equals(name)) {
                return crop;
            }
        }
        return null;
    }
}
