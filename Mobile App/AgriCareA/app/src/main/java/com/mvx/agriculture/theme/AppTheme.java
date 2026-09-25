package com.mvx.agriculture.theme;

import android.content.Context;
import android.content.SharedPreferences;

import com.mvx.agriculture.R;

/** Which visual style the app wears. Remembered across launches. */
public enum AppTheme {

    /** The default: Material 3, agricultural green. */
    GREEN(R.string.theme_green, R.style.Theme_AgriCareA),

    /** Soft UI: one flat surface, everything moulded out of it. */
    SOFT(R.string.theme_soft, R.style.Theme_AgriCareA_Soft);

    public final int labelRes;
    public final int styleRes;

    AppTheme(int labelRes, int styleRes) {
        this.labelRes = labelRes;
        this.styleRes = styleRes;
    }

    private static final String PREFS = "agricare_prefs";
    private static final String KEY = "app_theme";

    public static AppTheme current(Context context) {
        String name = prefs(context).getString(KEY, GREEN.name());
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return GREEN;
        }
    }

    public static void set(Context context, AppTheme theme) {
        prefs(context).edit().putString(KEY, theme.name()).apply();
    }

    public static CharSequence[] labels(Context context) {
        AppTheme[] values = values();
        CharSequence[] labels = new CharSequence[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = context.getString(values[i].labelRes);
        }
        return labels;
    }

    public static int currentIndex(Context context) {
        return current(context).ordinal();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
