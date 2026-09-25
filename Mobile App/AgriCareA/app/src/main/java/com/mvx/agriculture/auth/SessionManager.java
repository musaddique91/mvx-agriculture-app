package com.mvx.agriculture.auth;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * Holds the signed-in user as a JWT in encrypted preferences.
 *
 * The signing secret is generated once per install and kept in the same encrypted
 * store, so a token copied to another device or hand-edited fails verification.
 */
public class SessionManager {

    private static final String TAG = "SessionManager";
    private static final String FILE = "agricare_session";
    private static final String KEY_TOKEN = "jwt";
    private static final String KEY_SECRET = "secret";
    private static final long TTL = TimeUnit.DAYS.toMillis(7);

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        this.prefs = openPrefs(context.getApplicationContext());
    }

    /** Signs a fresh token for the user and stores it. */
    public void logIn(String username, String city, String region) {
        JSONObject claims = new JSONObject();
        try {
            claims.put("city", city == null ? "" : city);
            claims.put("region", region == null ? "" : region);
        } catch (JSONException e) {
            Log.w(TAG, "Could not attach profile claims", e);
        }
        String token = JwtUtils.sign(username, TTL, claims, secret());
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    /** True when a stored token is present, correctly signed and unexpired. */
    public boolean isLoggedIn() {
        return username() != null;
    }

    /** Username from a valid token, or null when there is no usable session. */
    public String username() {
        String token = prefs.getString(KEY_TOKEN, null);
        if (token == null) {
            return null;
        }
        try {
            return JwtUtils.verify(token, secret()).optString("sub", null);
        } catch (JwtUtils.InvalidTokenException e) {
            Log.i(TAG, "Session rejected: " + e.getMessage());
            clear();   // never keep a token we would not accept
            return null;
        }
    }

    public void clear() {
        prefs.edit().remove(KEY_TOKEN).apply();
    }

    /**
     * Clears the session and returns to the welcome screen, wiping the back stack
     * so Back cannot land on a signed-in screen.
     */
    public void logOutTo(Context context, Class<?> welcomeActivity) {
        clear();
        Intent intent = new Intent(context, welcomeActivity);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }

    /** Per-install HMAC secret, created on first use. */
    private byte[] secret() {
        String stored = prefs.getString(KEY_SECRET, null);
        if (stored == null) {
            byte[] fresh = new byte[32];
            new SecureRandom().nextBytes(fresh);
            stored = Base64.encodeToString(fresh, Base64.NO_WRAP);
            prefs.edit().putString(KEY_SECRET, stored).apply();
        }
        return Base64.decode(stored, Base64.NO_WRAP);
    }

    private static SharedPreferences openPrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | java.io.IOException e) {
            // Keystore can be unavailable on some devices; a working session beats
            // a crash loop, and the token is still signature-checked either way.
            Log.e(TAG, "Encrypted preferences unavailable, falling back to plain", e);
            return context.getSharedPreferences(FILE + "_plain", Context.MODE_PRIVATE);
        }
    }
}
