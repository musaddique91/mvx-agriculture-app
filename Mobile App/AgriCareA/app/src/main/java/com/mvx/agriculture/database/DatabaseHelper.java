package com.mvx.agriculture.database;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


import java.util.ArrayList;

import com.mvx.agriculture.User;


public class DatabaseHelper extends SQLiteOpenHelper {

    private	static final String	DATABASE_NAME = "userDB";
    // v2 added the "region" column; v3 added fields and scouting notes.
    private static final int DATABASE_VERSION = 5;
    private	static final String TABLE_CONTACTS = "users";
    private static final String id="id";
    private static final String email = "email";
    private static final String pwd = "pwd";

    private static final String city="city";
    private static final String region="region";
    private static final String birth="date";
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String	CREATE_CONTACTS_TABLE = "CREATE	TABLE " + TABLE_CONTACTS + "(" +id+ " INTEGER PRIMARY KEY,"+ email + " TEXT ," + pwd + " TEXT," + city + " TEXT," + birth + " TEXT," + region + " TEXT"+")";
        db.execSQL(CREATE_CONTACTS_TABLE);
        createFieldTables(db);
        createSeasonTables(db);
        createMoneyTables(db);
    }

    /**
     * What was spent and received. Amounts are whole paise so totals never drift.
     * One entry can cover several fields; each field's share, and the crop season
     * it belonged to at the time, is its own allocation row.
     */
    private void createMoneyTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS money_entries ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "kind TEXT,"                 // spent | received
                + "amount_paise INTEGER,"
                + "day INTEGER,"               // epoch day
                + "category TEXT,"             // spending only
                + "crop TEXT,"                 // income only
                + "quantity REAL,"
                + "unit TEXT,"
                + "rate_paise INTEGER,"
                + "buyer TEXT,"
                + "received INTEGER DEFAULT 1,"
                + "note TEXT,"
                + "split_mode TEXT,"
                + "created_at INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS money_allocations ("
                + "entry_id INTEGER,"
                + "field_id INTEGER,"
                + "season_id INTEGER,"         // null when nothing was planted
                + "amount_paise INTEGER)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_money_alloc_entry ON money_allocations(entry_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_money_alloc_field ON money_allocations(field_id)");
    }

    /**
     * A season is one crop in one field, planting to harvest. Task records hold what
     * the farmer ticked off; the schedule itself lives in assets/crop_journeys.json.
     */
    private void createSeasonTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS seasons ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "field_id INTEGER,"
                + "crop TEXT,"
                + "method TEXT,"
                + "seedling_age INTEGER,"
                + "planted_day INTEGER,"      // epoch day
                + "tracking_start_day INTEGER,"
                + "ended_day INTEGER,"        // null while the crop is standing
                + "created_at INTEGER)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_seasons_field ON seasons(field_id)");
        db.execSQL("CREATE TABLE IF NOT EXISTS season_tasks ("
                + "season_id INTEGER,"
                + "task_id TEXT,"
                + "state TEXT,"               // done | skipped
                + "day INTEGER,"
                + "PRIMARY KEY (season_id, task_id))");
    }

    /** Field boundaries and the notes dropped while walking them. */
    private void createFieldTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS fields ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT,"
                + "crop TEXT,"
                + "sown_date TEXT,"
                + "area_acres REAL,"
                + "boundary TEXT,"
                + "created_at INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS scouting_notes ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "field_id INTEGER,"
                + "lat REAL,"
                + "lon REAL,"
                + "category TEXT,"
                + "text TEXT,"
                + "photo_path TEXT,"
                + "created_at INTEGER)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_field ON scouting_notes(field_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Add the region column in place so existing accounts survive the upgrade.
            db.execSQL("ALTER TABLE " + TABLE_CONTACTS + " ADD COLUMN " + region + " TEXT");
        }
        if (oldVersion < 3) {
            createFieldTables(db);
        }
        if (oldVersion < 4) {
            // New tables only, so fields and notes carry over untouched.
            createSeasonTables(db);
        }
        if (oldVersion < 5) {
            createMoneyTables(db);
        }
    }

    public ArrayList<User> listUsers(){
        String sql = "select * from " + TABLE_CONTACTS;
        SQLiteDatabase db = this.getReadableDatabase();
        ArrayList<User> storeContacts = new ArrayList<>();
        Cursor cursor = db.rawQuery(sql, null);
        if(cursor.moveToFirst()){
            do{
                storeContacts.add(readUser(cursor));
            }while (cursor.moveToNext());
        }
        cursor.close();
        return storeContacts;
    }

    public void addUsers(User user){
        SQLiteDatabase db = this.getWritableDatabase();
        db.insert(TABLE_CONTACTS, null, toValues(user));
    }

    public void updateContacts(User user){
        SQLiteDatabase db = this.getWritableDatabase();
        db.update(TABLE_CONTACTS, toValues(user), id + " = ?", new String[] { String.valueOf(user.getId())});
    }

    /** Replaces only the stored password hash, leaving the rest of the row alone. */
    public void updatePassword(int userId, String hashedPassword) {
        ContentValues values = new ContentValues();
        values.put(pwd, hashedPassword);
        SQLiteDatabase db = this.getWritableDatabase();
        db.update(TABLE_CONTACTS, values, id + " = ?", new String[]{String.valueOf(userId)});
    }

    public User findUsers(String email){
        String query = "SELECT * FROM " + TABLE_CONTACTS + " WHERE " + this.email + " = ?";
        SQLiteDatabase db = this.getWritableDatabase();
        User user = null;
        Cursor cursor = db.rawQuery(query, new String[]{email});
        if (cursor.moveToFirst()) {
            user = readUser(cursor);
        }
        cursor.close();
        return user;
    }

    private ContentValues toValues(User user) {
        ContentValues values = new ContentValues();
        values.put(email, user.getEmail());
        values.put(pwd, user.getPwd());
        values.put(city, user.getCity());
        values.put(region, user.getRegion());
        values.put(birth, user.getBirth());
        return values;
    }

    /** Reads a row by column name, so column order changes stay harmless. */
    private User readUser(Cursor cursor) {
        return new User(
                cursor.getInt(cursor.getColumnIndexOrThrow(id)),
                cursor.getString(cursor.getColumnIndexOrThrow(email)),
                cursor.getString(cursor.getColumnIndexOrThrow(pwd)),
                cursor.getString(cursor.getColumnIndexOrThrow(city)),
                cursor.getString(cursor.getColumnIndexOrThrow(birth)),
                cursor.getString(cursor.getColumnIndexOrThrow(region)));
    }

    public void delteUser(int userId){
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_CONTACTS, id + " = ?", new String[] { String.valueOf(userId)});
    }
    public Boolean checkEmail(String email){
        SQLiteDatabase MyDatabase = this.getWritableDatabase();
        Cursor cursor = MyDatabase.rawQuery("Select * from users where email = ?", new String[]{email});
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }
    public Boolean checkEmailPassword(String email, String password){
        SQLiteDatabase MyDatabase = this.getWritableDatabase();
        Cursor cursor = MyDatabase.rawQuery("Select * from users where email = ? and pwd = ?", new String[]{email, password});
        boolean matches = cursor.getCount() > 0;
        cursor.close();
        return matches;
    }
}
