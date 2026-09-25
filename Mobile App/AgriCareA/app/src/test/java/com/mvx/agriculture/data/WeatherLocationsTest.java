package com.mvx.agriculture.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.mvx.agriculture.User;

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

        assertFalse("expected the mapped field to survive", all.isEmpty());
        boolean hasMappedField = false;
        for (WeatherLocation loc : all) {
            assertFalse("field:3".equals(loc.id));
            if ("field:4".equals(loc.id)) {
                hasMappedField = true;
            }
        }
        assertTrue("field:4 should be present since it has a boundary", hasMappedField);
    }

    @Test
    public void cityIsIncludedWhenCoordinatesAreKnownAndOmittedOtherwise() {
        List<WeatherLocation> withCity = WeatherLocations.build(
                Collections.emptyList(), user(), BELAGAVI, null, Collections.emptyList());
        assertEquals("city", withCity.get(0).id);
        assertEquals("Belagavi", withCity.get(0).name);

        List<WeatherLocation> withoutCity = WeatherLocations.build(
                Collections.emptyList(), user(), null, null, Collections.emptyList());
        assertFalse("expected the GPS entry to still be built", withoutCity.isEmpty());
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

    @Test
    public void deletedRememberedCustomFallsBackToNewestField() {
        List<WeatherLocation> all = WeatherLocations.build(
                Arrays.asList(
                        field(1, "Old plot", "maize", 2.0, 100L, 15.90, 74.50),
                        field(2, "New plot", "tomato", 1.2, 900L, 15.95, 74.55)),
                user(), BELAGAVI, null, Collections.emptyList());

        // "custom:gone" was remembered but its saved point no longer exists.
        assertEquals("field:2", WeatherLocations.resolveSelected(all, "custom:gone").id);
    }

    @Test
    public void rememberedGpsWithFieldsPresentAndGpsUnavailableFallsBackToNewestField() {
        List<WeatherLocation> all = WeatherLocations.build(
                Arrays.asList(
                        field(1, "Old plot", "maize", 2.0, 100L, 15.90, 74.50),
                        field(2, "New plot", "tomato", 1.2, 900L, 15.95, 74.55)),
                user(), BELAGAVI, null, Collections.emptyList());

        // GPS was remembered but has no fix, so it is unavailable.
        assertEquals("field:2", WeatherLocations.resolveSelected(all, WeatherLocations.ID_GPS).id);
    }

    @Test
    public void everythingUnavailableExceptGpsResolvesToGps() {
        List<WeatherLocation> all = WeatherLocations.build(
                Collections.emptyList(), null, null,
                new double[]{16.10, 74.70}, Collections.emptyList());

        WeatherLocation selected = WeatherLocations.resolveSelected(all, null);
        assertEquals(WeatherLocations.ID_GPS, selected.id);
        assertTrue(selected.available);
    }
}
