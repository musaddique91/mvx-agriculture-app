# Weather Location Selector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a farmer choose which location the weather and its advisories describe — any mapped field, their registered city, their current GPS position, or a point picked on the map — defaulting to their own farm rather than the district town.

**Architecture:** `WeatherRepository.load(lat, lon, listener)` is already coordinate-based and does not change. The city assumption lives only in its two callers. We insert a pure, JVM-testable resolver between them: raw data in (fields, user, coordinates, GPS fix), an ordered list of `WeatherLocation` out, plus a deterministic fallback chain for choosing one. Android-specific concerns — SharedPreferences, GPS, string resources — stay in thin wrappers around that pure core, so the logic that matters is tested without a device.

**Tech Stack:** Java 17, Android minSdk 24, JUnit 4 (`testImplementation(libs.junit)`), Material 3 `AutoCompleteTextView` exposed dropdown, `LocationManager` (permissions already declared), existing `osmdroid`-based `LocationPickerDialog`.

## Global Constraints

- Package root: `com.example.agricarea` (namespace in `app/build.gradle.kts:19`).
- Android project root for all paths below: `Mobile App/AgriCareA/`.
- minSdk 24 — no APIs above that level without a guard.
- `data/WeatherRepository.java` MUST NOT be modified. It is already correct.
- The resolver (`WeatherLocations`) MUST NOT import anything from `android.*`. This is what keeps it JVM-testable.
- All farmer-facing text goes in `app/src/main/res/values/strings.xml`. No hardcoded strings in Java.
- Existing behaviour for a farmer with no mapped fields MUST be unchanged: they still see their registered city's weather.
- Permissions `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` are already declared in `AndroidManifest.xml:11-12`. Do not re-add them.
- Build/test commands run from `Mobile App/AgriCareA/`. Device serial for verification: `2A191FDH300GMF`.

---

### Task 1: The `WeatherLocation` value type and the pure resolver

This is the heart of the feature and the only part with real logic. It is pure Java so it runs under `./gradlew test` in seconds with no emulator.

**Files:**
- Create: `Mobile App/AgriCareA/app/src/main/java/com/example/agricarea/data/WeatherLocation.java`
- Create: `Mobile App/AgriCareA/app/src/main/java/com/example/agricarea/data/WeatherLocations.java`
- Test: `Mobile App/AgriCareA/app/src/test/java/com/example/agricarea/data/WeatherLocationsTest.java`

**Interfaces:**
- Consumes: `data/Field.java` (fields `id`, `name`, `crop`, `areaAcres`, `createdAt`, `boundary`; method `centroid()` returning `Field.Point` or **null**), `User.java` (`getCity()`, `getRegion()`).
- Produces:
  - `WeatherLocation(Kind kind, String id, String name, String crop, double areaAcres, double lat, double lon, boolean available)`
  - `WeatherLocation.Kind` — `FIELD`, `CITY`, `GPS`, `CUSTOM`
  - `static List<WeatherLocation> WeatherLocations.build(List<Field> fields, User user, double[] cityCoords, double[] gpsFix, List<WeatherLocation> customs)`
  - `static WeatherLocation WeatherLocations.resolveSelected(List<WeatherLocation> all, String rememberedId)`
  - Id scheme: `"field:<id>"`, `"city"`, `"gps"`, `"custom:<uuid>"`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/example/agricarea/data/WeatherLocationsTest.java`:

```java
package com.example.agricarea.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.agricarea.User;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WeatherLocationsTest {

    private static final double[] BELAGAVI = {15.8497, 74.4977};

    private static User user() {
        return new User("musa", "pwd", "Belagavi", "1991-01-01", "Karnataka");
    }

    private static Field field(int id, String name, String crop, double acres,
                               long createdAt, double lat, double lon) {
        Field f = new Field();
        f.id = id;
        f.name = name;
        f.crop = crop;
        f.areaAcres = acres;
        f.createdAt = createdAt;
        f.boundary.add(new Field.Point(lat, lon));
        f.boundary.add(new Field.Point(lat + 0.01, lon));
        f.boundary.add(new Field.Point(lat, lon + 0.01));
        return f;
    }

    /** A field saved before its boundary was drawn: centroid() returns null. */
    private static Field unmappedField(int id, String name) {
        Field f = new Field();
        f.id = id;
        f.name = name;
        f.createdAt = 1L;
        return f;
    }

    @Test
    public void fieldsComeFirstNewestMappedFirst() {
        List<Field> fields = Arrays.asList(
                field(1, "Old plot", "maize", 2.0, 100L, 15.90, 74.50),
                field(2, "New plot", "tomato", 1.2, 900L, 15.95, 74.55));

        List<WeatherLocation> all = WeatherLocations.build(
                fields, user(), BELAGAVI, null, Collections.emptyList());

        assertEquals("field:2", all.get(0).id);
        assertEquals("New plot", all.get(0).name);
        assertEquals("field:1", all.get(1).id);
    }

    @Test
    public void fieldCarriesCentroidCropAndArea() {
        List<Field> fields = Collections.singletonList(
                field(7, "Tomato plot", "tomato", 1.2, 500L, 15.90, 74.50));

        WeatherLocation loc = WeatherLocations.build(
                fields, user(), BELAGAVI, null, Collections.emptyList()).get(0);

        assertEquals(WeatherLocation.Kind.FIELD, loc.kind);
        assertEquals("tomato", loc.crop);
        assertEquals(1.2, loc.areaAcres, 0.0001);
        // centroid of (15.90,74.50) (15.91,74.50) (15.90,74.51)
        assertEquals(15.9033, loc.lat, 0.001);
        assertEquals(74.5033, loc.lon, 0.001);
        assertTrue(loc.available);
    }

    @Test
    public void fieldWithoutBoundaryIsSkipped() {
        List<Field> fields = Arrays.asList(
                unmappedField(3, "Not drawn yet"),
                field(4, "Drawn", "rice", 1.0, 200L, 15.90, 74.50));

        List<WeatherLocation> all = WeatherLocations.build(
                fields, user(), BELAGAVI, null, Collections.emptyList());

        for (WeatherLocation loc : all) {
            assertFalse("field:3".equals(loc.id));
        }
    }

    @Test
    public void cityIsIncludedWhenCoordinatesAreKnownAndOmittedOtherwise() {
        List<WeatherLocation> withCity = WeatherLocations.build(
                Collections.emptyList(), user(), BELAGAVI, null, Collections.emptyList());
        assertEquals("city", withCity.get(0).id);
        assertEquals("Belagavi", withCity.get(0).name);

        List<WeatherLocation> withoutCity = WeatherLocations.build(
                Collections.emptyList(), user(), null, null, Collections.emptyList());
        for (WeatherLocation loc : withoutCity) {
            assertFalse("city".equals(loc.id));
        }
    }

    @Test
    public void gpsEntryIsUnavailableWithoutAFix() {
        List<WeatherLocation> all = WeatherLocations.build(
                Collections.emptyList(), user(), BELAGAVI, null, Collections.emptyList());

        WeatherLocation gps = null;
        for (WeatherLocation loc : all) {
            if ("gps".equals(loc.id)) {
                gps = loc;
            }
        }
        assertFalse(gps == null);
        assertFalse(gps.available);
    }

    @Test
    public void gpsEntryCarriesTheFixWhenPresent() {
        List<WeatherLocation> all = WeatherLocations.build(
                Collections.emptyList(), user(), BELAGAVI,
                new double[]{16.10, 74.70}, Collections.emptyList());

        WeatherLocation gps = null;
        for (WeatherLocation loc : all) {
            if ("gps".equals(loc.id)) {
                gps = loc;
            }
        }
        assertTrue(gps.available);
        assertEquals(16.10, gps.lat, 0.0001);
    }

    @Test
    public void customPointsAreAppended() {
        WeatherLocation custom = new WeatherLocation(
                WeatherLocation.Kind.CUSTOM, "custom:abc", "Uncle's field",
                null, 0, 16.2, 74.8, true);

        List<WeatherLocation> all = WeatherLocations.build(
                Collections.emptyList(), user(), BELAGAVI, null,
                Collections.singletonList(custom));

        assertEquals("custom:abc", all.get(all.size() - 1).id);
    }

    @Test
    public void rememberedChoiceWins() {
        List<WeatherLocation> all = WeatherLocations.build(
                Arrays.asList(
                        field(1, "Old plot", "maize", 2.0, 100L, 15.90, 74.50),
                        field(2, "New plot", "tomato", 1.2, 900L, 15.95, 74.55)),
                user(), BELAGAVI, null, Collections.emptyList());

        assertEquals("field:1", WeatherLocations.resolveSelected(all, "field:1").id);
    }

    @Test
    public void deletedRememberedFieldFallsBackToNewestField() {
        List<WeatherLocation> all = WeatherLocations.build(
                Arrays.asList(
                        field(1, "Old plot", "maize", 2.0, 100L, 15.90, 74.50),
                        field(2, "New plot", "tomato", 1.2, 900L, 15.95, 74.55)),
                user(), BELAGAVI, null, Collections.emptyList());

        // field:99 was deleted since it was remembered
        assertEquals("field:2", WeatherLocations.resolveSelected(all, "field:99").id);
    }

    @Test
    public void withNoFieldsFallsBackToCity() {
        List<WeatherLocation> all = WeatherLocations.build(
                Collections.emptyList(), user(), BELAGAVI, null, Collections.emptyList());

        assertEquals("city", WeatherLocations.resolveSelected(all, null).id);
    }

    @Test
    public void unavailableGpsIsNeverSelectedAsAFallback() {
        List<WeatherLocation> all = WeatherLocations.build(
                Collections.emptyList(), user(), null, null, Collections.emptyList());

        // only the unavailable GPS entry exists
        assertNull(WeatherLocations.resolveSelected(all, "gps"));
    }

    @Test
    public void emptyEverythingResolvesToNull() {
        List<WeatherLocation> all = new ArrayList<>();
        assertNull(WeatherLocations.resolveSelected(all, "field:1"));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd "Mobile App/AgriCareA" && ./gradlew test --tests '*WeatherLocationsTest*'
```

Expected: FAIL — compilation error, `WeatherLocation` and `WeatherLocations` do not exist.

- [ ] **Step 3: Write `WeatherLocation`**

Create `app/src/main/java/com/example/agricarea/data/WeatherLocation.java`:

```java
package com.example.agricarea.data;

/**
 * One place the farmer can ask about the weather: a mapped plot, their
 * registered town, wherever they are standing, or a point they picked.
 *
 * Deliberately dumb and free of Android types, so the resolver that builds
 * these stays testable on the JVM. Display text is the view layer's job — this
 * carries the raw crop and area so a fragment can format them with string
 * resources in the farmer's own language.
 */
public final class WeatherLocation {

    public enum Kind { FIELD, CITY, GPS, CUSTOM }

    public final Kind kind;
    /** Stable across launches: "field:3", "city", "gps", "custom:<uuid>". */
    public final String id;
    public final String name;
    /** The plot's crop, or null for anything that is not a field. */
    public final String crop;
    /** The plot's area, or 0 for anything that is not a field. */
    public final double areaAcres;
    public final double lat;
    public final double lon;
    /** False when we know of the place but have no usable fix for it — an
     *  unlocated GPS entry still appears in the list, greyed, so the farmer can
     *  see the option exists rather than wondering where it went. */
    public final boolean available;

    public WeatherLocation(Kind kind, String id, String name, String crop,
                           double areaAcres, double lat, double lon, boolean available) {
        this.kind = kind;
        this.id = id;
        this.name = name;
        this.crop = crop;
        this.areaAcres = areaAcres;
        this.lat = lat;
        this.lon = lon;
        this.available = available;
    }
}
```

- [ ] **Step 4: Write `WeatherLocations`**

Create `app/src/main/java/com/example/agricarea/data/WeatherLocations.java`:

```java
package com.example.agricarea.data;

import com.example.agricarea.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the weather location list and decides which one is showing.
 *
 * Pure: no Context, no SharedPreferences, no Android types at all. Everything
 * it needs arrives as an argument, which is what lets the fallback chain — the
 * only part with real behaviour — be covered by fast JVM tests.
 */
public final class WeatherLocations {

    public static final String ID_CITY = "city";
    public static final String ID_GPS = "gps";

    private WeatherLocations() {
    }

    /**
     * The dropdown, in display order: mapped plots newest first, the registered
     * town, the current position, then saved points.
     *
     * @param cityCoords the registered town's fix, or null when the CSV has none
     * @param gpsFix     the latest position, or null when there is none yet
     */
    public static List<WeatherLocation> build(List<Field> fields, User user,
                                              double[] cityCoords, double[] gpsFix,
                                              List<WeatherLocation> customs) {
        List<WeatherLocation> out = new ArrayList<>();

        List<Field> sorted = new ArrayList<>(fields);
        // Newest first, so "my latest plot" is what a farmer lands on.
        Collections.sort(sorted, new Comparator<Field>() {
            @Override
            public int compare(Field a, Field b) {
                return Long.compare(b.createdAt, a.createdAt);
            }
        });

        for (Field field : sorted) {
            Field.Point centre = field.centroid();
            if (centre == null) {
                continue;   // saved but never drawn — we have no fix to ask about
            }
            out.add(new WeatherLocation(WeatherLocation.Kind.FIELD, "field:" + field.id,
                    field.name, field.crop, field.areaAcres, centre.lat, centre.lon, true));
        }

        if (cityCoords != null && user != null && user.getCity() != null) {
            out.add(new WeatherLocation(WeatherLocation.Kind.CITY, ID_CITY,
                    user.getCity(), null, 0, cityCoords[0], cityCoords[1], true));
        }

        boolean hasFix = gpsFix != null;
        out.add(new WeatherLocation(WeatherLocation.Kind.GPS, ID_GPS, null, null, 0,
                hasFix ? gpsFix[0] : 0, hasFix ? gpsFix[1] : 0, hasFix));

        out.addAll(customs);
        return out;
    }

    /**
     * The location to show: what they last chose, else their newest plot, else
     * their town, else anything usable. Null only when nothing is usable at all.
     *
     * Anything unavailable is skipped, so a remembered GPS entry with no fix
     * quietly yields to a real plot instead of showing an empty screen.
     */
    public static WeatherLocation resolveSelected(List<WeatherLocation> all, String rememberedId) {
        if (rememberedId != null) {
            for (WeatherLocation loc : all) {
                if (rememberedId.equals(loc.id) && loc.available) {
                    return loc;
                }
            }
        }
        WeatherLocation firstAvailable = null;
        for (WeatherLocation loc : all) {
            if (!loc.available) {
                continue;
            }
            if (loc.kind == WeatherLocation.Kind.FIELD) {
                return loc;   // list is already newest-first
            }
            if (firstAvailable == null) {
                firstAvailable = loc;
            }
        }
        return firstAvailable;
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
cd "Mobile App/AgriCareA" && ./gradlew test --tests '*WeatherLocationsTest*'
```

Expected: PASS, 12 tests.

- [ ] **Step 6: Commit**

```bash
git add "Mobile App/AgriCareA/app/src/main/java/com/example/agricarea/data/WeatherLocation.java" \
        "Mobile App/AgriCareA/app/src/main/java/com/example/agricarea/data/WeatherLocations.java" \
        "Mobile App/AgriCareA/app/src/test/java/com/example/agricarea/data/WeatherLocationsTest.java"
git commit -m "Add weather location model and pure resolver

Mapped plots newest first, then the registered town, current position and
saved points, with a fallback chain that survives a deleted field or a
missing GPS fix. No Android types, so it is covered by JVM tests."
```

---

### Task 2: Remembering the choice

**Files:**
- Create: `Mobile App/AgriCareA/app/src/main/java/com/example/agricarea/data/WeatherLocationStore.java`

**Interfaces:**
- Consumes: `WeatherLocation` from Task 1.
- Produces:
  - `WeatherLocationStore(Context context)`
  - `String selectedId()` — null when nothing chosen yet
  - `void select(String id)`
  - `List<WeatherLocation> customs()`
  - `void addCustom(String name, double lat, double lon)` — returns nothing; generates the `custom:<uuid>` id itself
  - `void removeCustom(String id)`

- [ ] **Step 1: Write the store**

Create `app/src/main/java/com/example/agricarea/data/WeatherLocationStore.java`:

```java
package com.example.agricarea.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Remembers which location the farmer is watching, and any points they saved
 * off the map. Small enough for SharedPreferences; there is no reason to put a
 * handful of coordinates in SQLite.
 */
public class WeatherLocationStore {

    private static final String TAG = "WeatherLocationStore";
    private static final String PREFS = "weather_locations";
    private static final String KEY_SELECTED = "selected_id";
    private static final String KEY_CUSTOMS = "customs";

    private final SharedPreferences prefs;

    public WeatherLocationStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The last choice, or null on a first run. */
    public String selectedId() {
        return prefs.getString(KEY_SELECTED, null);
    }

    public void select(String id) {
        prefs.edit().putString(KEY_SELECTED, id).apply();
    }

    public List<WeatherLocation> customs() {
        List<WeatherLocation> out = new ArrayList<>();
        String raw = prefs.getString(KEY_CUSTOMS, null);
        if (raw == null) {
            return out;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                out.add(new WeatherLocation(WeatherLocation.Kind.CUSTOM,
                        o.getString("id"), o.getString("name"), null, 0,
                        o.getDouble("lat"), o.getDouble("lon"), true));
            }
        } catch (JSONException e) {
            // Corrupt preference should cost the saved points, never the screen.
            Log.w(TAG, "Dropping unreadable saved locations", e);
            return new ArrayList<>();
        }
        return out;
    }

    public void addCustom(String name, double lat, double lon) {
        List<WeatherLocation> existing = customs();
        existing.add(new WeatherLocation(WeatherLocation.Kind.CUSTOM,
                "custom:" + UUID.randomUUID(), name, null, 0, lat, lon, true));
        write(existing);
    }

    public void removeCustom(String id) {
        List<WeatherLocation> kept = new ArrayList<>();
        for (WeatherLocation loc : customs()) {
            if (!loc.id.equals(id)) {
                kept.add(loc);
            }
        }
        write(kept);
    }

    private void write(List<WeatherLocation> locations) {
        JSONArray array = new JSONArray();
        try {
            for (WeatherLocation loc : locations) {
                JSONObject o = new JSONObject();
                o.put("id", loc.id);
                o.put("name", loc.name);
                o.put("lat", loc.lat);
                o.put("lon", loc.lon);
                array.put(o);
            }
        } catch (JSONException e) {
            Log.w(TAG, "Could not save locations", e);
            return;
        }
        prefs.edit().putString(KEY_CUSTOMS, array.toString()).apply();
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
cd "Mobile App/AgriCareA" && ./gradlew compileDebugJavaWithJavac
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add "Mobile App/AgriCareA/app/src/main/java/com/example/agricarea/data/WeatherLocationStore.java"
git commit -m "Remember the chosen weather location and saved map points

SharedPreferences, since this is a handful of coordinates. A corrupt
preference drops the saved points rather than taking down the screen."
```

---

### Task 3: The Android-side provider

Glue: gathers the four inputs the pure resolver needs and hands them over. Keeping this separate is what stops `WeatherLocations` growing a `Context`.

**Files:**
- Create: `Mobile App/AgriCareA/app/src/main/java/com/example/agricarea/data/WeatherLocationProvider.java`

**Interfaces:**
- Consumes: `WeatherLocations.build/resolveSelected` (Task 1), `WeatherLocationStore` (Task 2), `FieldRepository.all()` returning `List<Field>`, `CityRepository.coordinatesOf(String region, String city)` returning `double[]` or null, `User.getCity()/getRegion()`.
- Produces:
  - `WeatherLocationProvider(Context context)`
  - `List<WeatherLocation> list(User user)`
  - `WeatherLocation selected(User user)`
  - `void select(String id)`

- [ ] **Step 1: Write the provider**

Create `app/src/main/java/com/example/agricarea/data/WeatherLocationProvider.java`:

```java
package com.example.agricarea.data;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.agricarea.CityRepository;
import com.example.agricarea.User;

import java.util.List;

/**
 * Assembles what {@link WeatherLocations} needs from Android, so the resolver
 * itself can stay free of Context and remain unit-testable.
 */
public class WeatherLocationProvider {

    private static final String TAG = "WeatherLocationProvider";

    private final Context context;
    private final WeatherLocationStore store;

    public WeatherLocationProvider(Context context) {
        this.context = context.getApplicationContext();
        this.store = new WeatherLocationStore(this.context);
    }

    public List<WeatherLocation> list(User user) {
        double[] cityCoords = null;
        if (user != null) {
            cityCoords = new CityRepository(context)
                    .coordinatesOf(user.getRegion(), user.getCity());
        }
        return WeatherLocations.build(
                new FieldRepository(context).all(), user, cityCoords, lastKnownFix(),
                store.customs());
    }

    public WeatherLocation selected(User user) {
        return WeatherLocations.resolveSelected(list(user), store.selectedId());
    }

    public void select(String id) {
        store.select(id);
    }

    /**
     * The last position Android already knows, which is instant. We never block
     * the weather screen waiting for a fresh fix — a farmer opening the forecast
     * wants a number now, and a stale-by-minutes position picks the same
     * forecast grid cell as a fresh one.
     */
    private double[] lastKnownFix() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        LocationManager manager =
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            return null;
        }
        try {
            for (String provider : manager.getProviders(true)) {
                Location fix = manager.getLastKnownLocation(provider);
                if (fix != null) {
                    return new double[]{fix.getLatitude(), fix.getLongitude()};
                }
            }
        } catch (SecurityException e) {
            Log.i(TAG, "Location permission withdrawn while reading the last fix");
        }
        return null;
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
cd "Mobile App/AgriCareA" && ./gradlew compileDebugJavaWithJavac
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add "Mobile App/AgriCareA/app/src/main/java/com/example/agricarea/data/WeatherLocationProvider.java"
git commit -m "Gather weather location inputs from Android

Reads fields, the registered town's fix and the last known position, then
defers to the pure resolver. Never blocks the screen on a fresh GPS fix."
```

---

### Task 4: The dropdown on the Weather screen

**Files:**
- Modify: `Mobile App/AgriCareA/app/src/main/res/layout/fragment_weather.xml` (add the dropdown above the existing content)
- Modify: `Mobile App/AgriCareA/app/src/main/res/values/strings.xml` (add strings)
- Modify: `Mobile App/AgriCareA/app/src/main/java/com/example/agricarea/ui/WeatherFragment.java:57-62` and its `load()` method
- Create: `Mobile App/AgriCareA/app/src/main/java/com/example/agricarea/ui/WeatherLocationLabels.java`

**Interfaces:**
- Consumes: `WeatherLocationProvider` (Task 3), `WeatherLocation` (Task 1).
- Produces: `static String WeatherLocationLabels.title(Context c, WeatherLocation loc)` and `static String WeatherLocationLabels.subtitle(Context c, WeatherLocation loc)` — used again by Task 5, so the Home card and the dropdown never disagree about what a place is called.

- [ ] **Step 1: Add the strings**

In `app/src/main/res/values/strings.xml`, before `</resources>`:

```xml
    <string name="weather_location_label">Weather for</string>
    <string name="weather_location_gps">My current location</string>
    <string name="weather_location_gps_unavailable">My current location (unavailable)</string>
    <string name="weather_location_city_sub">Registered town</string>
    <string name="weather_location_field_sub">%1$s · %2$.1f acres</string>
    <string name="weather_location_pick_on_map">Choose on map…</string>
    <string name="weather_location_saved_point">Saved point</string>
```

- [ ] **Step 2: Write the shared label helper**

Create `app/src/main/java/com/example/agricarea/ui/WeatherLocationLabels.java`:

```java
package com.example.agricarea.ui;

import android.content.Context;

import com.example.agricarea.R;
import com.example.agricarea.data.WeatherLocation;

/**
 * Turns a {@link WeatherLocation} into farmer-facing text.
 *
 * Lives apart from both screens on purpose: the Home card and the Weather
 * dropdown must never disagree about what a place is called.
 */
public final class WeatherLocationLabels {

    private WeatherLocationLabels() {
    }

    public static String title(Context context, WeatherLocation loc) {
        if (loc.kind == WeatherLocation.Kind.GPS) {
            return context.getString(loc.available
                    ? R.string.weather_location_gps
                    : R.string.weather_location_gps_unavailable);
        }
        return loc.name;
    }

    public static String subtitle(Context context, WeatherLocation loc) {
        switch (loc.kind) {
            case FIELD:
                String crop = loc.crop == null ? "" : loc.crop;
                return context.getString(R.string.weather_location_field_sub, crop, loc.areaAcres);
            case CITY:
                return context.getString(R.string.weather_location_city_sub);
            case CUSTOM:
                return context.getString(R.string.weather_location_saved_point);
            default:
                return "";
        }
    }
}
```

- [ ] **Step 3: Add the dropdown to the layout**

In `app/src/main/res/layout/fragment_weather.xml`, insert immediately inside the top of the scrolling content container, above the element currently holding `@id/placeLabel`:

```xml
    <com.google.android.material.textfield.TextInputLayout
        android:id="@+id/locationPickerLayout"
        style="@style/Widget.Material3.TextInputLayout.OutlinedBox.ExposedDropdownMenu"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginHorizontal="16dp"
        android:layout_marginTop="8dp"
        android:hint="@string/weather_location_label">

        <com.google.android.material.textfield.MaterialAutoCompleteTextView
            android:id="@+id/locationPicker"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="none"
            android:focusable="false" />
    </com.google.android.material.textfield.TextInputLayout>
```

- [ ] **Step 4: Wire the fragment**

In `ui/WeatherFragment.java`, add these imports:

```java
import android.widget.ArrayAdapter;
import com.example.agricarea.data.WeatherLocation;
import com.example.agricarea.data.WeatherLocationProvider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import java.util.ArrayList;
import java.util.List;
```

Add fields to the class:

```java
    private WeatherLocationProvider locations;
    private WeatherLocation current;
    private User user;
```

Replace the block at `ui/WeatherFragment.java:57-62`:

```java
        User user = ((MainShellActivity) requireActivity()).currentUser();
        TextView place = view.findViewById(R.id.placeLabel);
        if (user != null) {
            place.setText(user.getLocation());
            coordinates = new CityRepository(requireContext())
                    .coordinatesOf(user.getRegion(), user.getCity());
        }
```

with:

```java
        user = ((MainShellActivity) requireActivity()).currentUser();
        locations = new WeatherLocationProvider(requireContext());
        TextView place = view.findViewById(R.id.placeLabel);
        place.setVisibility(View.GONE);   // the dropdown now names the place
        setUpLocationPicker(view);
```

Add the picker method:

```java
    private void setUpLocationPicker(View view) {
        MaterialAutoCompleteTextView picker = view.findViewById(R.id.locationPicker);
        final List<WeatherLocation> all = locations.list(user);

        List<String> labels = new ArrayList<>();
        for (WeatherLocation loc : all) {
            String subtitle = WeatherLocationLabels.subtitle(requireContext(), loc);
            labels.add(subtitle.isEmpty()
                    ? WeatherLocationLabels.title(requireContext(), loc)
                    : WeatherLocationLabels.title(requireContext(), loc) + " — " + subtitle);
        }
        picker.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, labels));

        current = locations.selected(user);
        if (current != null) {
            picker.setText(WeatherLocationLabels.title(requireContext(), current), false);
        }

        picker.setOnItemClickListener((parent, v, position, id) -> {
            WeatherLocation picked = all.get(position);
            if (!picked.available) {
                // Shown so the farmer knows the option exists; not selectable.
                picker.setText(current == null
                        ? "" : WeatherLocationLabels.title(requireContext(), current), false);
                return;
            }
            current = picked;
            locations.select(picked.id);
            picker.setText(WeatherLocationLabels.title(requireContext(), picked), false);
            load();
        });
    }
```

Replace the guard and the call at the top of `load()`:

```java
    private void load() {
        if (current == null) {
            swipeRefresh.setRefreshing(false);
            errorLabel.setVisibility(View.VISIBLE);
            return;
        }
        swipeRefresh.setRefreshing(true);
        new WeatherRepository().load(current.lat, current.lon,
```

Delete the now-unused `coordinates` field and the `CityRepository` import if nothing else in the file uses them.

- [ ] **Step 5: Build and install on the device**

```bash
cd "Mobile App/AgriCareA" && ./run.sh --serial 2A191FDH300GMF
```

Expected: BUILD SUCCESSFUL, then Success, then the app launches.

- [ ] **Step 6: Verify on the device**

Open the Weather tab. Expected: a "Weather for" dropdown at the top. With no fields mapped it reads the registered town and the forecast is unchanged from before. Opening the dropdown lists the town and a greyed "My current location" when there is no fix.

```bash
adb -s 2A191FDH300GMF exec-out screencap -p > /tmp/weather.png
```

- [ ] **Step 7: Commit**

```bash
git add "Mobile App/AgriCareA/app/src/main/res/layout/fragment_weather.xml" \
        "Mobile App/AgriCareA/app/src/main/res/values/strings.xml" \
        "Mobile App/AgriCareA/app/src/main/java/com/example/agricarea/ui/WeatherFragment.java" \
        "Mobile App/AgriCareA/app/src/main/java/com/example/agricarea/ui/WeatherLocationLabels.java"
git commit -m "Choose which location the weather describes

A dropdown of mapped plots, the registered town and the current position
replaces the fixed district centroid, so a plot outside the town gets its
own forecast rather than the town's."
```

---

### Task 5: Home card follows the same selection

**Files:**
- Modify: `Mobile App/AgriCareA/app/src/main/java/com/example/agricarea/ui/HomeFragment.java:52-56` (the location label) and `:153-163` (`loadWeather`)
- Modify: `Mobile App/AgriCareA/app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `WeatherLocationProvider.selected(User)` (Task 3), `WeatherLocationLabels.title(Context, WeatherLocation)` (Task 4), `MainShellActivity.openDestination(int menuId)` (exists at `MainShellActivity.java:272`), menu id `R.id.nav_weather` (exists in `res/menu/bottom_nav.xml:6`).
- Produces: nothing new.

- [ ] **Step 1: Add the string**

`R.string.home_today` is "Today in %1$s" and stays for the town case. Add the
place-agnostic wording to `app/src/main/res/values/strings.xml`:

```xml
    <string name="home_today_at">Today at %1$s</string>
```

- [ ] **Step 2: Name the selected place on the card**

In `ui/HomeFragment.java`, add imports:

```java
import com.example.agricarea.data.WeatherLocation;
import com.example.agricarea.data.WeatherLocationProvider;
```

Add a field to the class:

```java
    private WeatherLocation weatherPlace;
```

Replace `ui/HomeFragment.java:52-56`:

```java
        TextView subGreeting = view.findViewById(R.id.subGreeting);
        String city = user == null ? null : user.getCity();
        subGreeting.setText(city == null || city.isEmpty()
                ? getString(R.string.app_tagline)
                : getString(R.string.home_today, city));
```

with:

```java
        TextView subGreeting = view.findViewById(R.id.subGreeting);
        weatherPlace = new WeatherLocationProvider(requireContext()).selected(user);
        if (weatherPlace == null) {
            subGreeting.setText(R.string.app_tagline);
        } else {
            // "Today at Tomato plot" once a reading can differ from the town's,
            // so a number on this card is never unattributed.
            subGreeting.setText(getString(
                    weatherPlace.kind == WeatherLocation.Kind.CITY
                            ? R.string.home_today
                            : R.string.home_today_at,
                    WeatherLocationLabels.title(requireContext(), weatherPlace)));
            subGreeting.setOnClickListener(v -> shell.openDestination(R.id.nav_weather));
        }
```

- [ ] **Step 3: Read the weather from the same selection**

Replace `ui/HomeFragment.java:157-158`:

```java
        double[] coordinates = new CityRepository(requireContext())
                .coordinatesOf(user.getRegion(), user.getCity());
        if (coordinates == null) {
```

with:

```java
        double[] coordinates = weatherPlace == null
                ? null
                : new double[]{weatherPlace.lat, weatherPlace.lon};
        if (coordinates == null) {
```

Remove the `CityRepository` import if nothing else in the file uses it.

Note `loadWeather` is called after `onViewCreated` has set `weatherPlace`. If any
call path reaches `loadWeather` before that, resolve it there instead — the null
guard above already keeps it safe either way.

- [ ] **Step 4: Rebuild and check both screens agree**

```bash
cd "Mobile App/AgriCareA" && ./run.sh --serial 2A191FDH300GMF
```

Expected: Home reads "Today at <plot>" when a field is selected and "Today in
<town>" when the town is. Its temperature matches the Weather tab's for the same
selection. Change the selection on Weather, return to Home, confirm Home
followed. Tapping the subtitle opens the Weather tab.

- [ ] **Step 5: Commit**

```bash
git add "Mobile App/AgriCareA/app/src/main/java/com/example/agricarea/ui/HomeFragment.java" \
        "Mobile App/AgriCareA/app/src/main/res/values/strings.xml"
git commit -m "Show the selected location on the home weather card

Home and the Weather tab now read one selection, and the card names the
place so a reading is never unattributed. Tapping it opens Weather."
```

---

### Task 6: Pick any point on the map

**Files:**
- Modify: `Mobile App/AgriCareA/app/src/main/java/com/example/agricarea/ui/WeatherFragment.java` (the picker built in Task 4)

**Interfaces:**
- Consumes: `LocationPickerDialog(Activity activity, OnPicked callback)` with `void onPicked(String region, String city, double lat, double lon)`; `WeatherLocationStore.addCustom(String name, double lat, double lon)` (Task 2).
- Produces: nothing new.

- [ ] **Step 1: Append the map entry to the dropdown**

In `setUpLocationPicker`, after the loop that builds `labels`:

```java
        labels.add(getString(R.string.weather_location_pick_on_map));
```

- [ ] **Step 2: Handle it in the click listener**

At the top of the `setOnItemClickListener` body, before `all.get(position)`:

```java
            if (position == all.size()) {   // the trailing "Choose on map…" entry
                picker.setText(current == null
                        ? "" : WeatherLocationLabels.title(requireContext(), current), false);
                new LocationPickerDialog(requireActivity(), (region, city, lat, lon) -> {
                    new WeatherLocationStore(requireContext()).addCustom(city, lat, lon);
                    setUpLocationPicker(requireView());   // rebuild, so the new point appears
                    load();
                }).show();
                return;
            }
```

Add imports:

```java
import com.example.agricarea.data.WeatherLocationStore;
```

The dialog already snaps a pick to the nearest district it knows, so `city` is a sensible name for the saved point without asking the farmer to type one.

- [ ] **Step 3: Rebuild and verify**

```bash
cd "Mobile App/AgriCareA" && ./run.sh --serial 2A191FDH300GMF
```

Expected: "Choose on map…" is the last dropdown entry; picking a point saves it, selects it, and the forecast reloads for it. Reopening the dropdown shows it under "Saved point". Force-stop and reopen the app: the saved point is still listed and still selected.

- [ ] **Step 4: Commit**

```bash
git add "Mobile App/AgriCareA/app/src/main/java/com/example/agricarea/ui/WeatherFragment.java"
git commit -m "Watch the weather at any point picked on the map

Reuses the existing satellite picker, so a farmer can follow a plot they
have not mapped yet."
```

---

### Task 7: Full regression pass on the device

**Files:** none — verification only.

- [ ] **Step 1: Run the whole unit suite**

```bash
cd "Mobile App/AgriCareA" && ./gradlew test
```

Expected: PASS, including the 12 tests from Task 1.

- [ ] **Step 2: Run the instrumented suite**

```bash
cd "Mobile App/AgriCareA" && ./gradlew connectedAndroidTest
```

Expected: PASS. This is the existing auth/session/city suite — it proves the city path still works.

- [ ] **Step 3: Walk the cases that the fallback chain exists for**

On the device, confirm each:

1. Fresh install, no fields mapped → Weather shows the registered town. Unchanged from before this work.
2. Map a field in My fields → return to Weather → the new plot is first in the dropdown and is auto-selected.
3. Select the town, force-stop, reopen → still the town.
4. Delete the selected field in My fields → return to Weather → falls back to the newest remaining plot, or the town, with no empty screen.
5. Deny location permission → "My current location" is greyed and tapping it does not change the selection.
6. Home's temperature matches Weather's for the same selection.

- [ ] **Step 4: Commit any fixes, then stop**

Sub-project 1 is done. Sub-project 2 (the agent backend) is a separate plan.
