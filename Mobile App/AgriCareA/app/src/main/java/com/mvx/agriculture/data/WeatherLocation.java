package com.mvx.agriculture.data;

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
