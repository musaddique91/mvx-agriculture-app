package com.mvx.agriculture.data;

import android.content.Context;
import android.content.SharedPreferences;

/** The last field area the farmer measured, so the calculators can reuse it. */
public final class FieldStore {

    private static final String PREFS = "agricare_prefs";
    private static final String KEY_ACRES = "field_acres";

    private FieldStore() {
    }

    public static void saveAcres(Context context, double acres) {
        prefs(context).edit().putFloat(KEY_ACRES, (float) acres).apply();
    }

    /** Saved area in acres, or 0 when nothing has been measured yet. */
    public static double acres(Context context) {
        return prefs(context).getFloat(KEY_ACRES, 0f);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
