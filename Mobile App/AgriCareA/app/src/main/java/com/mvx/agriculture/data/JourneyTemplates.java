package com.mvx.agriculture.data;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads crop journeys from assets/crop_journeys.json, once per process. */
public final class JourneyTemplates {

    private static final String TAG = "JourneyTemplates";
    private static Map<String, JourneyTemplate> cache;

    private JourneyTemplates() {
    }

    /** The journey for a crop name as saved on a field, or null when none is written yet. */
    public static JourneyTemplate forCrop(Context context, String crop) {
        if (crop == null) {
            return null;
        }
        return all(context).get(crop.trim());
    }

    public static synchronized Map<String, JourneyTemplate> all(Context context) {
        if (cache != null) {
            return cache;
        }
        Map<String, JourneyTemplate> out = new LinkedHashMap<>();
        try (InputStream is = context.getApplicationContext().getAssets().open("crop_journeys.json");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line).append('\n');
            }
            JSONObject crops = new JSONObject(json.toString()).getJSONObject("crops");
            for (Iterator<String> names = crops.keys(); names.hasNext(); ) {
                String name = names.next();
                out.put(name, parse(name, crops.getJSONObject(name)));
            }
        } catch (IOException | JSONException e) {
            // A broken asset should cost the journeys, never the field screen.
            Log.e(TAG, "Could not load crop journeys", e);
        }
        cache = out;
        return out;
    }

    private static JourneyTemplate parse(String name, JSONObject o) throws JSONException {
        JourneyTemplate t = new JourneyTemplate();
        t.crop = name;
        t.harvestAgeDays = o.optInt("harvestAgeDays");
        JSONArray sources = o.optJSONArray("sources");
        for (int i = 0; sources != null && i < sources.length(); i++) {
            JourneyTemplate.Source s = new JourneyTemplate.Source();
            s.title = sources.getJSONObject(i).getString("title");
            s.url = sources.getJSONObject(i).getString("url");
            t.sources.add(s);
        }
        JSONArray methods = o.getJSONArray("methods");
        for (int i = 0; i < methods.length(); i++) {
            JourneyTemplate.Method m = new JourneyTemplate.Method();
            m.id = methods.getJSONObject(i).getString("id");
            m.seedlingAgeDays = methods.getJSONObject(i).optInt("seedlingAgeDays");
            t.methods.add(m);
        }
        JSONArray stages = o.optJSONArray("stages");
        for (int i = 0; stages != null && i < stages.length(); i++) {
            JSONObject so = stages.getJSONObject(i);
            JourneyTemplate.Stage s = new JourneyTemplate.Stage();
            s.id = so.getString("id");
            s.name = so.getString("name");
            s.water = so.optString("water", null);
            s.fromDay = so.getInt("fromDay");
            s.toDay = so.getInt("toDay");
            t.stages.add(s);
        }
        JSONArray tasks = o.getJSONArray("tasks");
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject to = tasks.getJSONObject(i);
            JourneyTemplate.TaskTemplate task = new JourneyTemplate.TaskTemplate();
            task.id = to.getString("id");
            task.day = to.getInt("day");
            task.ageBasis = "age".equals(to.optString("basis"));
            task.type = to.getString("type");
            task.optional = to.optBoolean("optional");
            task.title = to.getString("title");
            task.detail = to.getString("detail");
            JSONArray taskMethods = to.optJSONArray("methods");
            List<String> ids = new ArrayList<>();
            for (int j = 0; taskMethods != null && j < taskMethods.length(); j++) {
                ids.add(taskMethods.getString(j));
            }
            task.methods = ids;
            t.tasks.add(task);
        }
        return t;
    }
}
