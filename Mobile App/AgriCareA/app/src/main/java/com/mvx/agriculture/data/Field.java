package com.mvx.agriculture.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** A plot the farmer has drawn or detected, with its boundary and current crop. */
public class Field {

    public static class Point {
        public final double lat;
        public final double lon;

        public Point(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }
    }

    public int id;
    public String name;
    public String crop;
    public String sownDate;
    public double areaAcres;
    public long createdAt;
    public final List<Point> boundary = new ArrayList<>();

    /** Centre of the boundary, used to centre the map and query weather. */
    public Point centroid() {
        if (boundary.isEmpty()) {
            return null;
        }
        double lat = 0;
        double lon = 0;
        for (Point point : boundary) {
            lat += point.lat;
            lon += point.lon;
        }
        return new Point(lat / boundary.size(), lon / boundary.size());
    }

    /** Boundary as JSON, which is how the database stores it. */
    public String boundaryJson() {
        JSONArray array = new JSONArray();
        for (Point point : boundary) {
            JSONArray pair = new JSONArray();
            try {
                pair.put(point.lat);
                pair.put(point.lon);
            } catch (JSONException e) {
                continue;   // a non-finite coordinate cannot be stored; skip it
            }
            array.put(pair);
        }
        return array.toString();
    }

    public void setBoundaryFromJson(String json) {
        boundary.clear();
        if (json == null || json.isEmpty()) {
            return;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONArray pair = array.getJSONArray(i);
                boundary.add(new Point(pair.getDouble(0), pair.getDouble(1)));
            }
        } catch (JSONException e) {
            // A corrupt row should not take the screen down; it just has no shape.
        }
    }
}
