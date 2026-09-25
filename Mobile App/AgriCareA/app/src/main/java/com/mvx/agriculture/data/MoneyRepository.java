package com.mvx.agriculture.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.mvx.agriculture.database.DatabaseHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Money entries and their per-field shares. Local only, like fields and seasons. */
public class MoneyRepository {

    private final DatabaseHelper helper;

    public MoneyRepository(Context context) {
        this.helper = new DatabaseHelper(context.getApplicationContext());
    }

    /** Saves the entry and all its shares together, or nothing at all. */
    public long save(MoneyEntry entry) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("kind", entry.kind);
            v.put("amount_paise", entry.amountPaise);
            v.put("day", entry.day);
            v.put("category", entry.category);
            v.put("crop", entry.crop);
            v.put("quantity", entry.quantity);
            v.put("unit", entry.unit);
            v.put("rate_paise", entry.ratePaise);
            v.put("buyer", entry.buyer);
            v.put("received", entry.received ? 1 : 0);
            v.put("note", entry.note);
            v.put("split_mode", entry.splitMode);
            v.put("created_at", System.currentTimeMillis());
            long id = db.insert("money_entries", null, v);
            for (MoneyEntry.Allocation a : entry.allocations) {
                ContentValues av = new ContentValues();
                av.put("entry_id", id);
                av.put("field_id", a.fieldId);
                if (a.seasonId == null) {
                    av.putNull("season_id");
                } else {
                    av.put("season_id", a.seasonId);
                }
                av.put("amount_paise", a.amountPaise);
                db.insert("money_allocations", null, av);
            }
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    /** Newest first, each with its field shares. */
    public List<MoneyEntry> entries() {
        Map<Long, MoneyEntry> byId = new LinkedHashMap<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM money_entries ORDER BY day DESC, id DESC", null)) {
            while (c.moveToNext()) {
                MoneyEntry e = new MoneyEntry();
                e.id = c.getLong(c.getColumnIndexOrThrow("id"));
                e.kind = c.getString(c.getColumnIndexOrThrow("kind"));
                e.amountPaise = c.getLong(c.getColumnIndexOrThrow("amount_paise"));
                e.day = c.getLong(c.getColumnIndexOrThrow("day"));
                e.category = c.getString(c.getColumnIndexOrThrow("category"));
                e.crop = c.getString(c.getColumnIndexOrThrow("crop"));
                e.quantity = c.getDouble(c.getColumnIndexOrThrow("quantity"));
                e.unit = c.getString(c.getColumnIndexOrThrow("unit"));
                e.ratePaise = c.getLong(c.getColumnIndexOrThrow("rate_paise"));
                e.buyer = c.getString(c.getColumnIndexOrThrow("buyer"));
                e.received = c.getInt(c.getColumnIndexOrThrow("received")) == 1;
                e.note = c.getString(c.getColumnIndexOrThrow("note"));
                e.splitMode = c.getString(c.getColumnIndexOrThrow("split_mode"));
                byId.put(e.id, e);
            }
        }
        try (Cursor c = db.rawQuery("SELECT entry_id, field_id, season_id, amount_paise FROM money_allocations", null)) {
            while (c.moveToNext()) {
                MoneyEntry e = byId.get(c.getLong(0));
                if (e != null) {
                    e.allocations.add(new MoneyEntry.Allocation(c.getInt(1), c.isNull(2) ? null : c.getLong(2), c.getLong(3)));
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    /** Every field share as a ledger line, for totals. */
    public List<MoneyLedger.Line> lines() {
        List<MoneyLedger.Line> out = new ArrayList<>();
        for (MoneyEntry e : entries()) {
            for (MoneyEntry.Allocation a : e.allocations) {
                MoneyLedger.Line line = new MoneyLedger.Line();
                line.entryId = e.id;
                line.kind = e.kind;
                line.category = e.category;
                line.amountPaise = a.amountPaise;
                line.received = e.received;
                line.fieldId = a.fieldId;
                line.seasonId = a.seasonId;
                out.add(line);
            }
        }
        return out;
    }

    public void markReceived(long entryId) {
        ContentValues v = new ContentValues();
        v.put("received", 1);
        helper.getWritableDatabase().update("money_entries", v, "id = ?", new String[]{String.valueOf(entryId)});
    }

    public void delete(long entryId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("money_allocations", "entry_id = ?", new String[]{String.valueOf(entryId)});
            db.delete("money_entries", "id = ?", new String[]{String.valueOf(entryId)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
