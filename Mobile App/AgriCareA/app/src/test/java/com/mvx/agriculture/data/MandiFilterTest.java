package com.mvx.agriculture.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MandiFilterTest {

    private static MandiPrice row(String district, String market, String commodity,
                                  String variety, int modal) {
        MandiPrice p = new MandiPrice();
        p.district = district;
        p.market = market;
        p.commodity = commodity;
        p.variety = variety;
        p.modalPrice = modal;
        return p;
    }

    /** A slice of a real Karnataka day, trimmed to what the tests need. */
    private static List<MandiPrice> karnataka() {
        return Arrays.asList(
                row("Belagavi", "Ramdurga APMC", "Maize", "Yellow", 2555),
                row("Belagavi", "Ramdurga APMC", "Maize", "Hybrid/Local", 2400),
                row("Belagavi", "Belgaum APMC", "Tomato", "Local", 800),
                row("Belagavi", "Bailahongal APMC", "Cotton", "Other", 9523),
                row("Kalaburagi", "Kalaburagi APMC", "Rice", "Fine", 3700),
                row("Kalaburagi", "Kalaburagi APMC", "Maize", "Yellow", 2600));
    }

    private static MandiFilter.Selection select(String district, String market,
                                                String commodity, String variety) {
        MandiFilter.Selection s = new MandiFilter.Selection();
        s.district = district;
        s.market = market;
        s.commodity = commodity;
        s.variety = variety;
        return s;
    }

    private static List<String> commoditiesOf(List<MandiPrice> rows) {
        List<String> out = new ArrayList<>();
        for (MandiPrice p : rows) {
            out.add(p.commodity + "@" + p.market);
        }
        return out;
    }

    @Test
    public void appCityNamesMapToAgmarknetDistricts() {
        assertEquals("Dakshina Kannada", MandiFilter.agmarknetDistrict("Mangaluru"));
        assertEquals("Bellary", MandiFilter.agmarknetDistrict("Ballari"));
        assertEquals("Uttara Kannada", MandiFilter.agmarknetDistrict("Sirsi"));
        assertEquals("Chattrapati Sambhajinagar", MandiFilter.agmarknetDistrict("Aurangabad"));
        assertEquals("Mumbai", MandiFilter.agmarknetDistrict("Navi Mumbai"));
    }

    @Test
    public void cityThatAlreadyMatchesPassesThroughAndNullStaysNull() {
        assertEquals("Belagavi", MandiFilter.agmarknetDistrict("Belagavi"));
        assertEquals("Belagavi", MandiFilter.agmarknetDistrict("  Belagavi "));
        assertNull(MandiFilter.agmarknetDistrict(null));
    }

    @Test
    public void districtsAreDistinctAndSorted() {
        assertEquals(Arrays.asList("Belagavi", "Kalaburagi"), MandiFilter.districts(karnataka()));
    }

    @Test
    public void marketsNarrowToTheChosenDistrict() {
        assertEquals(Arrays.asList("Bailahongal APMC", "Belgaum APMC", "Ramdurga APMC"),
                MandiFilter.markets(karnataka(), "Belagavi"));
        assertEquals(4, MandiFilter.markets(karnataka(), null).size());
    }

    @Test
    public void commoditiesNarrowToDistrictAndMarket() {
        assertEquals(Arrays.asList("Cotton", "Maize", "Tomato"),
                MandiFilter.commodities(karnataka(), "Belagavi", null));
        assertEquals(Collections.singletonList("Maize"),
                MandiFilter.commodities(karnataka(), "Belagavi", "Ramdurga APMC"));
    }

    @Test
    public void varietiesAreOfferedOnlyOnceACommodityIsChosen() {
        assertTrue(MandiFilter.varieties(karnataka(), "Belagavi", null, null).isEmpty());
        assertEquals(Arrays.asList("Hybrid/Local", "Yellow"),
                MandiFilter.varieties(karnataka(), "Belagavi", null, "Maize"));
    }

    @Test
    public void applyCombinesEveryFilter() {
        List<MandiPrice> maizeInBelagavi =
                MandiFilter.apply(karnataka(), select("Belagavi", null, "Maize", null));
        assertEquals(2, maizeInBelagavi.size());

        List<MandiPrice> yellowOnly =
                MandiFilter.apply(karnataka(), select("Belagavi", null, "Maize", "Yellow"));
        assertEquals(1, yellowOnly.size());
        assertEquals(2555, yellowOnly.get(0).modalPrice);

        assertEquals(6, MandiFilter.apply(karnataka(), select(null, null, null, null)).size());
    }

    @Test
    public void matchingIgnoresCaseSoOddlyCasedFeedNamesStillMatch() {
        List<MandiPrice> rows = new ArrayList<>(karnataka());
        rows.add(row("belagavi", "Kudchi APMC", "maize", "Local", 2300));
        assertEquals(3, MandiFilter.apply(rows, select("Belagavi", null, "Maize", null)).size());
    }

    @Test
    public void sortByPriceHighestFirst() {
        MandiFilter.Selection s = select(null, null, "Maize", null);
        s.sort = MandiFilter.Sort.PRICE_HIGH;
        List<MandiPrice> out = MandiFilter.apply(karnataka(), s);
        assertEquals(2600, out.get(0).modalPrice);
        assertEquals(2400, out.get(out.size() - 1).modalPrice);
    }

    @Test
    public void sortByPriceLowestFirst() {
        MandiFilter.Selection s = select(null, null, "Maize", null);
        s.sort = MandiFilter.Sort.PRICE_LOW;
        assertEquals(2400, MandiFilter.apply(karnataka(), s).get(0).modalPrice);
    }

    @Test
    public void sortByNameOrdersCommodityThenMarket() {
        MandiFilter.Selection s = select(null, null, null, null);
        s.sort = MandiFilter.Sort.NAME;
        assertEquals(Arrays.asList(
                        "Cotton@Bailahongal APMC", "Maize@Kalaburagi APMC", "Maize@Ramdurga APMC",
                        "Maize@Ramdurga APMC", "Rice@Kalaburagi APMC", "Tomato@Belgaum APMC"),
                commoditiesOf(MandiFilter.apply(karnataka(), s)));
    }

    @Test
    public void keepIfOfferedDropsAPickThatIsNoLongerAvailable() {
        List<String> offered = Arrays.asList("Belagavi", "Kalaburagi");
        assertEquals("Belagavi", MandiFilter.keepIfOffered("Belagavi", offered));
        assertEquals("Belagavi", MandiFilter.keepIfOffered("BELAGAVI", offered));
        assertNull(MandiFilter.keepIfOffered("Mysuru", offered));
        assertNull(MandiFilter.keepIfOffered(null, offered));
    }

    @Test
    public void shownVarietyHidesUninformativeValues() {
        assertEquals("Yellow", MandiFilter.shownVariety(row("B", "M", "Maize", "Yellow", 1)));
        assertNull(MandiFilter.shownVariety(row("B", "M", "Cotton", "Other", 1)));
        assertNull(MandiFilter.shownVariety(row("B", "M", "Tomato", "Tomato", 1)));
        assertNull(MandiFilter.shownVariety(row("B", "M", "Beans", "", 1)));
        // The feed often repeats the commodity inside the variety.
        assertNull(MandiFilter.shownVariety(row("B", "M", "Beans", "Beans (Whole)", 1)));
        assertEquals("Green (Whole)", MandiFilter.shownVariety(row("B", "M", "Peas", "Green (Whole)", 1)));
        // ...or names a variety already spelled out in the commodity.
        assertNull(MandiFilter.shownVariety(row("B", "M", "Sunflower/Sunflower Seed", "Sunflower", 1)));
        assertEquals("Local", MandiFilter.shownVariety(row("B", "M", "Maize", "Local", 1)));
    }
}
