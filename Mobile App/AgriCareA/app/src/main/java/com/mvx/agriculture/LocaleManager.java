package com.mvx.agriculture;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/**
 * Per-app language, remembered across launches.
 *
 * AppCompat applies the list itself on API 33+ and emulates it below, so the whole
 * app — including AgriBot's answers, which read {@link #currentTag} — follows one
 * choice.
 */
public final class LocaleManager {

    /** Tag, English name, and the name in the language itself. */
    public static final String[][] LANGUAGES = {
            {"en", "English", "English"},
            {"hi", "Hindi", "हिन्दी"},
            {"kn", "Kannada", "ಕನ್ನಡ"},
            {"mr", "Marathi", "मराठी"},
            {"ur", "Urdu", "اردو"},
    };

    private static final String PREFS = "agricare_prefs";
    private static final String KEY = "language";

    private LocaleManager() {
    }

    /** Applies the saved language. Call from Application.onCreate. */
    public static void applySaved(Context context) {
        String tag = saved(context);
        if (tag != null) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
        }
    }

    public static void set(Context context, String tag) {
        prefs(context).edit().putString(KEY, tag).apply();
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
    }

    public static String saved(Context context) {
        return prefs(context).getString(KEY, null);
    }

    /** The tag in force now, falling back to the system locale. */
    public static String currentTag(Context context) {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (!locales.isEmpty()) {
            return locales.get(0).getLanguage();
        }
        String stored = saved(context);
        return stored != null ? stored : java.util.Locale.getDefault().getLanguage();
    }

    /** English name of the current language, for the AgriBot system prompt. */
    public static String currentEnglishName(Context context) {
        String tag = currentTag(context);
        for (String[] row : LANGUAGES) {
            if (row[0].equals(tag)) {
                return row[1];
            }
        }
        return "English";
    }

    public static int currentIndex(Context context) {
        String tag = currentTag(context);
        for (int i = 0; i < LANGUAGES.length; i++) {
            if (LANGUAGES[i][0].equals(tag)) {
                return i;
            }
        }
        return 0;
    }

    /** Names as the speakers write them, for the picker. */
    /**
     * Applies the Nastaliq font when the app is in Urdu.
     *
     * Call before setContentView. applyStyle merges into the theme already in
     * force, so the colour scheme and night mode survive untouched.
     */
    public static void applyFont(android.app.Activity activity) {
        if ("ur".equals(currentTag(activity))) {
            activity.getTheme().applyStyle(R.style.ThemeOverlay_AgriCare_Urdu, true);
        }
    }

    public static CharSequence[] nativeNames() {
        CharSequence[] names = new CharSequence[LANGUAGES.length];
        for (int i = 0; i < LANGUAGES.length; i++) {
            names[i] = LANGUAGES[i][2];
        }
        return names;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
