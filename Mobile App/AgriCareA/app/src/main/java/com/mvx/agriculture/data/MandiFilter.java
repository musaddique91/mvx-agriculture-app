package com.mvx.agriculture.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Narrows and orders one state's Agmarknet rows on the phone.
 *
 * The Mandi screen fetches a whole state once (about 33 KB compressed) and
 * filters here, so every dropdown only ever offers names that really have rates
 * today, and switching a filter needs no network. Pure Java with no Android
 * types, which keeps it covered by JVM tests.
 */
public final class MandiFilter {

    public enum Sort { NAME, PRICE_HIGH, PRICE_LOW }

    /** What the farmer has picked. A null field means "all". */
    public static final class Selection {
        public String district;
        public String market;
        public String commodity;
        public String variety;
        public Sort sort = Sort.NAME;
    }

    /**
     * Signup city names that Agmarknet files under a different district.
     *
     * Some are spellings (Ballari / Bellary), some are renamed districts
     * (Aurangabad / Chattrapati Sambhajinagar), and some are towns inside a
     * differently named district (Mangaluru / Dakshina Kannada). Without this a
     * third of Karnataka farmers never saw their own district. Checked against
     * the feed on 2026-09-16; a city missing here passes through unchanged.
     */
    private static final Map<String, String> DISTRICT_ALIASES = new HashMap<>();

    static {
        // Karnataka
        DISTRICT_ALIASES.put("Ballari", "Bellary");
        DISTRICT_ALIASES.put("Bhadravati", "Shivamogga");
        DISTRICT_ALIASES.put("Davanagere", "Davangere");
        DISTRICT_ALIASES.put("Hospet", "Vijayanagara");
        DISTRICT_ALIASES.put("Hubballi", "Dharwad");
        DISTRICT_ALIASES.put("Karwar", "Uttara Kannada");
        DISTRICT_ALIASES.put("Madikeri", "Kodagu");
        DISTRICT_ALIASES.put("Mangaluru", "Dakshina Kannada");
        DISTRICT_ALIASES.put("Ramanagara", "Bengaluru South");
        DISTRICT_ALIASES.put("Sirsi", "Uttara Kannada");
        // Maharashtra
        DISTRICT_ALIASES.put("Ahmednagar", "Ahilyanagar");
        DISTRICT_ALIASES.put("Amravati", "Amarawati");
        DISTRICT_ALIASES.put("Aurangabad", "Chattrapati Sambhajinagar");
        DISTRICT_ALIASES.put("Gondia", "Gondiya");
        DISTRICT_ALIASES.put("Navi Mumbai", "Mumbai");   // APMC Vashi is filed under Mumbai
        DISTRICT_ALIASES.put("Osmanabad", "Dharashiv");
    }

    private interface Key {
        String of(MandiPrice price);
    }

    private MandiFilter() {
    }

    /** The Agmarknet district for a city picked at signup, or null for null. */
    public static String agmarknetDistrict(String city) {
        if (city == null) {
            return null;
        }
        String trimmed = city.trim();
        String alias = DISTRICT_ALIASES.get(trimmed);
        return alias != null ? alias : trimmed;
    }

    public static List<String> districts(List<MandiPrice> all) {
        return distinct(all, p -> p.district);
    }

    public static List<String> markets(List<MandiPrice> all, String district) {
        return distinct(match(all, district, null, null, null), p -> p.market);
    }

    public static List<String> commodities(List<MandiPrice> all, String district, String market) {
        return distinct(match(all, district, market, null, null), p -> p.commodity);
    }

    /** Varieties only mean something within one commodity, so none are offered before one is chosen. */
    public static List<String> varieties(List<MandiPrice> all, String district, String market,
                                         String commodity) {
        if (commodity == null) {
            return Collections.emptyList();
        }
        return distinct(match(all, district, market, commodity, null), p -> p.variety);
    }

    public static List<MandiPrice> apply(List<MandiPrice> all, Selection selection) {
        List<MandiPrice> out = match(all, selection.district, selection.market,
                selection.commodity, selection.variety);
        Collections.sort(out, comparator(selection.sort));
        return out;
    }

    /** A pick that is still on offer, in the list's own spelling; otherwise null, meaning "all". */
    public static String keepIfOffered(String pick, List<String> offered) {
        if (pick == null) {
            return null;
        }
        for (String name : offered) {
            if (name.equalsIgnoreCase(pick)) {
                return name;
            }
        }
        return null;
    }

    /**
     * The variety worth printing beside a commodity, or null when it adds
     * nothing: blank, the catch-all "Other", or a repeat of the commodity, which
     * the feed writes both ways ("Beans" / "Beans (Whole)",
     * "Sunflower/Sunflower Seed" / "Sunflower").
     */
    public static String shownVariety(MandiPrice price) {
        String variety = price.variety == null ? "" : price.variety.trim();
        String commodity = price.commodity == null ? "" : price.commodity.trim();
        String v = variety.toLowerCase(Locale.ROOT);
        String c = commodity.toLowerCase(Locale.ROOT);
        if (v.isEmpty() || v.equals("other")
                || (!c.isEmpty() && (v.startsWith(c) || c.contains(v)))) {
            return null;
        }
        return variety;
    }

    private static List<MandiPrice> match(List<MandiPrice> all, String district, String market,
                                          String commodity, String variety) {
        List<MandiPrice> out = new ArrayList<>();
        for (MandiPrice p : all) {
            if (same(district, p.district) && same(market, p.market)
                    && same(commodity, p.commodity) && same(variety, p.variety)) {
                out.add(p);
            }
        }
        return out;
    }

    /** A null pick matches everything; names are compared without case because the feed is inconsistent. */
    private static boolean same(String pick, String value) {
        return pick == null || pick.equalsIgnoreCase(value);
    }

    private static List<String> distinct(List<MandiPrice> rows, Key key) {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (MandiPrice p : rows) {
            String value = key.of(p);
            if (value != null && !value.isEmpty()) {
                names.add(value);
            }
        }
        return new ArrayList<>(names);
    }

    private static Comparator<MandiPrice> comparator(Sort sort) {
        Comparator<MandiPrice> byName = (a, b) -> {
            int c = String.CASE_INSENSITIVE_ORDER.compare(nullToEmpty(a.commodity), nullToEmpty(b.commodity));
            return c != 0 ? c : String.CASE_INSENSITIVE_ORDER.compare(nullToEmpty(a.market), nullToEmpty(b.market));
        };
        switch (sort) {
            case PRICE_HIGH:
                return (a, b) -> {
                    int c = Integer.compare(b.modalPrice, a.modalPrice);
                    return c != 0 ? c : byName.compare(a, b);
                };
            case PRICE_LOW:
                return (a, b) -> {
                    int c = Integer.compare(a.modalPrice, b.modalPrice);
                    return c != 0 ? c : byName.compare(a, b);
                };
            default:
                return byName;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
