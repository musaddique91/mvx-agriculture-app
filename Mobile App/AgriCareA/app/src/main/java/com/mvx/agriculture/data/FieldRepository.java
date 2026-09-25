package com.mvx.agriculture.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.mvx.agriculture.database.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Fields and their scouting notes.
 *
 * Everything is local: a farmer standing in a field with no signal can still drop
 * notes, and nothing needs a server to be useful.
 */
public class FieldRepository {

    private final DatabaseHelper helper;

    public FieldRepository(Context context) {
        this.helper = new DatabaseHelper(context.getApplicationContext());
    }

    // ------------------------------------------------------------------ fields

    public long save(Field field) {
        ContentValues values = new ContentValues();
        values.put("name", field.name);
        values.put("crop", field.crop);
        values.put("sown_date", field.sownDate);
        values.put("area_acres", field.areaAcres);
        values.put("boundary", field.boundaryJson());
        values.put("created_at", field.createdAt == 0
                ? System.currentTimeMillis() : field.createdAt);

        SQLiteDatabase db = helper.getWritableDatabase();
        if (field.id > 0) {
            db.update("fields", values, "id = ?", new String[]{String.valueOf(field.id)});
            return field.id;
        }
        return db.insert("fields", null, values);
    }

    public List<Field> all() {
        List<Field> fields = new ArrayList<>();
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT * FROM fields ORDER BY created_at DESC", null)) {
            while (cursor.moveToNext()) {
                fields.add(read(cursor));
            }
        }
        return fields;
    }

    public Field byId(int id) {
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT * FROM fields WHERE id = ?", new String[]{String.valueOf(id)})) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }

    public void delete(int id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete("scouting_notes", "field_id = ?", new String[]{String.valueOf(id)});
        db.delete("fields", "id = ?", new String[]{String.valueOf(id)});
    }

    private static Field read(Cursor cursor) {
        Field field = new Field();
        field.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
        field.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
        field.crop = cursor.getString(cursor.getColumnIndexOrThrow("crop"));
        field.sownDate = cursor.getString(cursor.getColumnIndexOrThrow("sown_date"));
        field.areaAcres = cursor.getDouble(cursor.getColumnIndexOrThrow("area_acres"));
        field.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        field.setBoundaryFromJson(cursor.getString(cursor.getColumnIndexOrThrow("boundary")));
        return field;
    }

    // ------------------------------------------------------------------- notes

    public long addNote(ScoutingNote note) {
        ContentValues values = new ContentValues();
        values.put("field_id", note.fieldId);
        values.put("lat", note.lat);
        values.put("lon", note.lon);
        values.put("category", note.category.name());
        values.put("text", note.text);
        values.put("photo_path", note.photoPath);
        values.put("created_at", note.createdAt == 0
                ? System.currentTimeMillis() : note.createdAt);
        return helper.getWritableDatabase().insert("scouting_notes", null, values);
    }

    public List<ScoutingNote> notesFor(int fieldId) {
        List<ScoutingNote> notes = new ArrayList<>();
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT * FROM scouting_notes WHERE field_id = ? ORDER BY created_at DESC",
                new String[]{String.valueOf(fieldId)})) {
            while (cursor.moveToNext()) {
                ScoutingNote note = new ScoutingNote();
                note.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                note.fieldId = cursor.getInt(cursor.getColumnIndexOrThrow("field_id"));
                note.lat = cursor.getDouble(cursor.getColumnIndexOrThrow("lat"));
                note.lon = cursor.getDouble(cursor.getColumnIndexOrThrow("lon"));
                note.text = cursor.getString(cursor.getColumnIndexOrThrow("text"));
                note.photoPath = cursor.getString(cursor.getColumnIndexOrThrow("photo_path"));
                note.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
                try {
                    note.category = ScoutingNote.Category.valueOf(
                            cursor.getString(cursor.getColumnIndexOrThrow("category")));
                } catch (IllegalArgumentException | NullPointerException e) {
                    note.category = ScoutingNote.Category.OTHER;
                }
                notes.add(note);
            }
        }
        return notes;
    }

    public void deleteNote(int id) {
        helper.getWritableDatabase().delete("scouting_notes", "id = ?",
                new String[]{String.valueOf(id)});
    }
}
