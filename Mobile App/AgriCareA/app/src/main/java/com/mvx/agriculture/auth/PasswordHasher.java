package com.mvx.agriculture.auth;

import android.util.Base64;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Salted PBKDF2 password hashing.
 *
 * Stored form is {@code pbkdf2$<iterations>$<salt>$<hash>}. Anything that does not
 * parse is treated as a legacy plaintext password so accounts created before this
 * change can still log in once, and are re-hashed on the way through.
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA1";  // available since API 19
    private static final String PREFIX = "pbkdf2";
    private static final int ITERATIONS = 20_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int B64 = Base64.NO_WRAP;

    private PasswordHasher() {
    }

    public static String hash(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] derived = derive(password, salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS
                + "$" + Base64.encodeToString(salt, B64)
                + "$" + Base64.encodeToString(derived, B64);
    }

    /** True when {@code password} matches {@code stored}, in either format. */
    public static boolean verify(String password, String stored) {
        // PBEKeySpec rejects an empty password outright, so never let one reach it.
        if (password == null || password.isEmpty() || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            // Legacy row: the column still holds the password itself.
            return password.equals(stored);
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.decode(parts[2], B64);
            byte[] expected = Base64.decode(parts[3], B64);
            return MessageDigest.isEqual(expected, derive(password, salt, iterations));
        } catch (IllegalArgumentException e) {   // covers NumberFormatException too
            return false;
        }
    }

    /** True when the stored value predates hashing and should be upgraded. */
    public static boolean needsUpgrade(String stored) {
        return stored == null || !stored.startsWith(PREFIX + "$");
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        }
    }
}
