package com.mvx.agriculture.data;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Calendar days as plain numbers (days since 1970-01-01), so "30 days after
 * planting" is simple addition with no daylight-saving or time-zone surprises.
 */
public final class EpochDays {

    private static final long MILLIS_PER_DAY = 86_400_000L;

    private EpochDays() {
    }

    /** Today in the phone's own time zone. */
    public static long today() {
        Calendar local = Calendar.getInstance();
        return of(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
    }

    /** @param month zero-based, as in {@link Calendar} */
    public static long of(int year, int month, int day) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(year, month, day);
        return Math.floorDiv(utc.getTimeInMillis(), MILLIS_PER_DAY);
    }

    /** Midnight UTC of that day, as a material date picker expects and returns. */
    public static long toUtcMillis(long epochDay) {
        return epochDay * MILLIS_PER_DAY;
    }

    public static long fromUtcMillis(long utcMillis) {
        return Math.floorDiv(utcMillis, MILLIS_PER_DAY);
    }

    /** e.g. "12 Oct 2026", in the phone's language. */
    public static String format(long epochDay) {
        DateFormat format = DateFormat.getDateInstance(DateFormat.MEDIUM);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(toUtcMillis(epochDay)));
    }
}
