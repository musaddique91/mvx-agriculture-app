package com.mvx.agriculture.voice;

import android.content.Context;
import android.content.SharedPreferences;

/** The farmer's voice choices: how fast to talk, and whether AgriBot answers aloud. */
public final class VoicePrefs {

    /** Slow suits a first-time listener; normal is the engine's own pace. */
    public static final float[] RATES = {0.7f, 0.9f, 1.15f};

    private static final String PREFS = "agricare_prefs";
    private static final String KEY_RATE = "voice_rate_index";
    private static final String KEY_AUTO_READ = "voice_auto_read_replies";

    private VoicePrefs() {
    }

    public static int rateIndex(Context context) {
        int index = prefs(context).getInt(KEY_RATE, 1);
        return index >= 0 && index < RATES.length ? index : 1;
    }

    public static float rate(Context context) {
        return RATES[rateIndex(context)];
    }

    public static void setRateIndex(Context context, int index) {
        prefs(context).edit().putInt(KEY_RATE, index).apply();
    }

    /** On by default: a farmer who can't read shouldn't have to find a setting first. */
    public static boolean autoReadReplies(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_READ, true);
    }

    public static void setAutoReadReplies(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_AUTO_READ, on).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
