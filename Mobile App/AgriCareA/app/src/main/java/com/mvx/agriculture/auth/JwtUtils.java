package com.mvx.agriculture.auth;

import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * A minimal HS256 JSON Web Token implementation.
 *
 * AgriCare has no backend, so tokens are signed and verified on the device with
 * a per-install secret. That gives the session a real expiry and a tamper-evident
 * payload; it is not a substitute for server-side auth, which is what a JWT buys
 * you once there is a server to trust.
 */
public final class JwtUtils {

    private static final String HMAC = "HmacSHA256";
    private static final int FLAGS = Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP;

    private JwtUtils() {
    }

    /** Thrown for any token that cannot be trusted: malformed, mis-signed or expired. */
    public static class InvalidTokenException extends Exception {
        InvalidTokenException(String message) {
            super(message);
        }
    }

    /**
     * Signs a token for {@code subject} valid for {@code ttlMillis} from now.
     *
     * @param claims extra claims to embed, may be null
     */
    public static String sign(String subject, long ttlMillis, JSONObject claims, byte[] secret) {
        long nowSeconds = System.currentTimeMillis() / 1000L;
        try {
            JSONObject header = new JSONObject();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            JSONObject payload = claims == null ? new JSONObject() : new JSONObject(claims.toString());
            payload.put("sub", subject);
            payload.put("iat", nowSeconds);
            payload.put("exp", nowSeconds + ttlMillis / 1000L);

            String signingInput = encode(header.toString()) + "." + encode(payload.toString());
            return signingInput + "." + base64(hmac(signingInput, secret));
        } catch (JSONException e) {
            // Only thrown for null keys, which cannot happen with these literals.
            throw new IllegalStateException("Could not build JWT", e);
        }
    }

    /**
     * Verifies signature and expiry, returning the payload claims.
     *
     * @throws InvalidTokenException when the token is malformed, forged or expired
     */
    public static JSONObject verify(String token, byte[] secret) throws InvalidTokenException {
        if (token == null) {
            throw new InvalidTokenException("No token");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new InvalidTokenException("Malformed token");
        }

        String signingInput = parts[0] + "." + parts[1];
        byte[] expected = hmac(signingInput, secret);
        byte[] actual = Base64.decode(parts[2], FLAGS);
        if (!MessageDigest.isEqual(expected, actual)) {   // constant-time compare
            throw new InvalidTokenException("Bad signature");
        }

        JSONObject payload;
        try {
            payload = new JSONObject(new String(Base64.decode(parts[1], FLAGS), StandardCharsets.UTF_8));
        } catch (JSONException | IllegalArgumentException e) {
            throw new InvalidTokenException("Unreadable payload");
        }

        long exp = payload.optLong("exp", 0L);
        if (exp <= System.currentTimeMillis() / 1000L) {
            throw new InvalidTokenException("Token expired");
        }
        return payload;
    }

    private static String encode(String json) {
        return base64(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64(byte[] bytes) {
        return Base64.encodeToString(bytes, FLAGS);
    }

    private static byte[] hmac(String data, byte[] secret) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
