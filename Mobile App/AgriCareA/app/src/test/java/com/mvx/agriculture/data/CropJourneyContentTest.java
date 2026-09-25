package com.mvx.agriculture.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Checks assets/crop_journeys.json itself. Farmers act on these schedules, so a
 * typo in a method id or a gap between stages must fail the build, not the season.
 */
public class CropJourneyContentTest {

    private static final List<String> TYPES = Arrays.asList(
            "manure", "fertilizer", "seed", "weeding", "care", "irrigation",
            "pest_watch", "pest_control", "disease_watch", "goli", "harvest");

    private static JSONObject crops() throws Exception {
        String json = new String(Files.readAllBytes(Paths.get("src/main/assets/crop_journeys.json")), StandardCharsets.UTF_8);
        return new JSONObject(json).getJSONObject("crops");
    }

    @Test
    public void everyCropIsWellFormed() throws Exception {
        JSONObject crops = crops();
        assertTrue("at least one crop", crops.length() > 0);
        for (Iterator<String> it = crops.keys(); it.hasNext(); ) {
            String name = it.next();
            JSONObject crop = crops.getJSONObject(name);

            assertTrue(name + ": cite a source", crop.getJSONArray("sources").length() > 0);
            for (int i = 0; i < crop.getJSONArray("sources").length(); i++) {
                assertTrue(name + ": source url", crop.getJSONArray("sources").getJSONObject(i).getString("url").startsWith("https://"));
            }

            Set<String> methods = new HashSet<>();
            JSONArray methodArray = crop.getJSONArray("methods");
            for (int i = 0; i < methodArray.length(); i++) {
                assertTrue(name + ": duplicate method", methods.add(methodArray.getJSONObject(i).getString("id")));
            }

            JSONArray stages = crop.getJSONArray("stages");
            int expectedFrom = 0;
            for (int i = 0; i < stages.length(); i++) {
                JSONObject stage = stages.getJSONObject(i);
                if (stage.getInt("fromDay") != expectedFrom) {
                    fail(name + ": stage " + stage.getString("id") + " should start on day " + expectedFrom);
                }
                assertTrue(name + ": stage ends after it starts", stage.getInt("toDay") >= stage.getInt("fromDay"));
                expectedFrom = stage.getInt("toDay") + 1;
            }

            Set<String> ids = new HashSet<>();
            JSONArray tasks = crop.getJSONArray("tasks");
            assertTrue(name + ": has tasks", tasks.length() > 0);
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject task = tasks.getJSONObject(i);
                String id = task.getString("id");
                assertTrue(name + ": duplicate task " + id, ids.add(id));
                assertTrue(name + "/" + id + ": unknown type " + task.getString("type"), TYPES.contains(task.getString("type")));
                assertFalse(name + "/" + id + ": title", task.getString("title").trim().isEmpty());
                assertFalse(name + "/" + id + ": detail", task.getString("detail").trim().isEmpty());
                int day = task.getInt("day");
                assertTrue(name + "/" + id + ": day " + day + " out of range", day >= -60 && day <= 730);
                JSONArray taskMethods = task.getJSONArray("methods");
                assertTrue(name + "/" + id + ": list its methods", taskMethods.length() > 0);
                for (int j = 0; j < taskMethods.length(); j++) {
                    assertTrue(name + "/" + id + ": unknown method " + taskMethods.getString(j), methods.contains(taskMethods.getString(j)));
                }
                String basis = task.optString("basis", "planting");
                assertTrue(name + "/" + id + ": basis", basis.equals("planting") || basis.equals("age"));
            }
        }
    }
}
