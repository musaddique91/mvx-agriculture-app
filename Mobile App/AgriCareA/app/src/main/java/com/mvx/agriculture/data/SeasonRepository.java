package com.mvx.agriculture.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.mvx.agriculture.database.DatabaseHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Crop seasons and the tasks the farmer has ticked off in them. Local only, like fields. */
public class SeasonRepository {

    private final DatabaseHelper helper;

    public SeasonRepository(Context context) {
        this.helper = new DatabaseHelper(context.getApplicationContext());
    }

    public long start(Season season) {
        ContentValues values = new ContentValues();
        values.put("field_id", season.fieldId);
        values.put("crop", season.crop);
        values.put("method", season.method);
        values.put("seedling_age", season.seedlingAge);
        values.put("planted_day", season.plantedDay);
        values.put("tracking_start_day", season.trackingStartDay);
        values.put("created_at", System.currentTimeMillis());
        return helper.getWritableDatabase().insert("seasons", null, values);
    }

    /** The field's standing crop, or null when nothing is planted. */
    public Season activeFor(int fieldId) {
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT * FROM seasons WHERE field_id = ? AND ended_day IS NULL ORDER BY id DESC LIMIT 1",
                new String[]{String.valueOf(fieldId)})) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    public Season byId(long id) {
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT * FROM seasons WHERE id = ?", new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    /** Every standing crop, for the daily reminder check. */
    public List<Season> allActive() {
        List<Season> out = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT * FROM seasons WHERE ended_day IS NULL", null)) {
            while (c.moveToNext()) {
                out.add(read(c));
            }
        }
        return out;
    }

    public void end(long seasonId, long day) {
        ContentValues values = new ContentValues();
        values.put("ended_day", day);
        helper.getWritableDatabase().update("seasons", values, "id = ?", new String[]{String.valueOf(seasonId)});
    }

    public Map<String, CropJourneyPlan.Record> records(long seasonId) {
        Map<String, CropJourneyPlan.Record> out = new HashMap<>();
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT task_id, state, day FROM season_tasks WHERE season_id = ?",
                new String[]{String.valueOf(seasonId)})) {
            while (c.moveToNext()) {
                out.put(c.getString(0), new CropJourneyPlan.Record(c.getString(1), c.getLong(2)));
            }
        }
        return out;
    }

    /** Marks a task done or skipped, or clears it again when {@code state} is null. */
    public void record(long seasonId, String taskId, String state, long day) {
        SQLiteDatabase db = helper.getWritableDatabase();
        if (state == null) {
            db.delete("season_tasks", "season_id = ? AND task_id = ?",
                    new String[]{String.valueOf(seasonId), taskId});
            return;
        }
        ContentValues values = new ContentValues();
        values.put("season_id", seasonId);
        values.put("task_id", taskId);
        values.put("state", state);
        values.put("day", day);
        db.insertWithOnConflict("season_tasks", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static Season read(Cursor c) {
        Season s = new Season();
        s.id = c.getLong(c.getColumnIndexOrThrow("id"));
        s.fieldId = c.getInt(c.getColumnIndexOrThrow("field_id"));
        s.crop = c.getString(c.getColumnIndexOrThrow("crop"));
        s.method = c.getString(c.getColumnIndexOrThrow("method"));
        s.seedlingAge = c.getInt(c.getColumnIndexOrThrow("seedling_age"));
        s.plantedDay = c.getLong(c.getColumnIndexOrThrow("planted_day"));
        s.trackingStartDay = c.getLong(c.getColumnIndexOrThrow("tracking_start_day"));
        int ended = c.getColumnIndexOrThrow("ended_day");
        s.endedDay = c.isNull(ended) ? null : c.getLong(ended);
        return s;
    }
}
