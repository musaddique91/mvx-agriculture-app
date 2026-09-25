package com.mvx.agriculture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.mvx.agriculture.auth.JwtUtils;
import com.mvx.agriculture.auth.PasswordHasher;
import com.mvx.agriculture.auth.SessionManager;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * These exercise android.util.Base64 and the Keystore, so they run on a device
 * rather than as plain JVM unit tests.
 */
@RunWith(AndroidJUnit4.class)
public class AuthInstrumentedTest {

    private static final byte[] SECRET = "a-test-signing-secret-32-bytes!!".getBytes(StandardCharsets.UTF_8);

    private Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    // ---------------------------------------------------------------- JWT

    @Test
    public void signedTokenVerifiesAndCarriesItsClaims() throws Exception {
        JSONObject claims = new JSONObject().put("city", "Belagavi").put("region", "Karnataka");
        String token = JwtUtils.sign("ramesh", 60_000L, claims, SECRET);

        JSONObject payload = JwtUtils.verify(token, SECRET);
        assertEquals("ramesh", payload.getString("sub"));
        assertEquals("Belagavi", payload.getString("city"));
        assertEquals("Karnataka", payload.getString("region"));
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    public void tokenSignedWithAnotherSecretIsRejected() {
        String token = JwtUtils.sign("ramesh", 60_000L, null, SECRET);
        byte[] otherSecret = "a-different-secret-of-32-bytes!!".getBytes(StandardCharsets.UTF_8);
        try {
            JwtUtils.verify(token, otherSecret);
            fail("a token signed with a different secret must not verify");
        } catch (JwtUtils.InvalidTokenException expected) {
            // exactly what we want
        }
    }

    @Test
    public void tamperedPayloadIsRejected() {
        String token = JwtUtils.sign("ramesh", 60_000L, null, SECRET);
        String[] parts = token.split("\\.");
        // flip a character in the payload, keeping the original signature
        char[] payload = parts[1].toCharArray();
        payload[0] = payload[0] == 'a' ? 'b' : 'a';
        String forged = parts[0] + "." + new String(payload) + "." + parts[2];
        try {
            JwtUtils.verify(forged, SECRET);
            fail("an edited payload must not verify");
        } catch (JwtUtils.InvalidTokenException expected) {
        }
    }

    @Test
    public void expiredTokenIsRejected() {
        String token = JwtUtils.sign("ramesh", -1_000L, null, SECRET);   // already past
        try {
            JwtUtils.verify(token, SECRET);
            fail("an expired token must not verify");
        } catch (JwtUtils.InvalidTokenException expected) {
        }
    }

    @Test
    public void malformedTokenIsRejected() {
        try {
            JwtUtils.verify("not-a-jwt", SECRET);
            fail("a malformed token must not verify");
        } catch (JwtUtils.InvalidTokenException expected) {
        }
    }

    // ----------------------------------------------------------- Passwords

    @Test
    public void hashVerifiesAgainstItsOwnPassword() {
        String hash = PasswordHasher.hash("farm2026");
        assertTrue(hash.startsWith("pbkdf2$"));
        assertTrue(PasswordHasher.verify("farm2026", hash));
        assertFalse(PasswordHasher.verify("farm2027", hash));
        assertFalse(PasswordHasher.verify("", hash));
    }

    @Test
    public void samespasswordHashesDifferentlyEachTime() {
        // different salts, so two hashes of one password must not be identical
        assertNotEquals(PasswordHasher.hash("farm2026"), PasswordHasher.hash("farm2026"));
    }

    @Test
    public void legacyPlaintextStillVerifiesAndIsFlaggedForUpgrade() {
        assertTrue(PasswordHasher.verify("farm2026", "farm2026"));
        assertFalse(PasswordHasher.verify("wrong", "farm2026"));
        assertTrue(PasswordHasher.needsUpgrade("farm2026"));
        assertFalse(PasswordHasher.needsUpgrade(PasswordHasher.hash("farm2026")));
    }

    // ------------------------------------------------------------ Session

    @Test
    public void sessionRoundTripsThenClears() {
        SessionManager session = new SessionManager(context());
        session.clear();
        assertFalse(session.isLoggedIn());
        assertNull(session.username());

        session.logIn("ramesh", "Belagavi", "Karnataka");
        assertTrue(session.isLoggedIn());
        assertEquals("ramesh", session.username());

        session.clear();
        assertFalse(session.isLoggedIn());
    }

    // --------------------------------------------------------------- Cities

    @Test
    public void cityListCoversAllThreeRegions() {
        CityRepository repository = new CityRepository(context());
        List<String> regions = repository.getRegions();

        assertTrue(regions.contains("Tunisia"));
        assertTrue(regions.contains("Karnataka"));
        assertTrue(regions.contains("Maharashtra"));

        assertTrue(repository.getCities("Karnataka").contains("Bengaluru"));
        assertTrue(repository.getCities("Maharashtra").contains("Pune"));
        assertTrue(repository.getCities("Tunisia").contains("Tunis"));

        // regions must not bleed into one another
        assertFalse(repository.getCities("Tunisia").contains("Bengaluru"));
        assertTrue(repository.getCities("Nowhere").isEmpty());

        assertEquals("Karnataka", repository.findRegionOf("Bengaluru"));
        assertNull(repository.findRegionOf("Atlantis"));
    }
}
