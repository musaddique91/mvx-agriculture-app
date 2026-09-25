package com.mvx.agriculture.data;

/**
 * Divides one payment across several fields, in paise, so the shares always add
 * back up to exactly what was paid. Pure Java; covered by JVM tests.
 */
public final class MoneySplit {

    public enum Mode { AREA, EQUAL, CUSTOM }

    private MoneySplit() {
    }

    /** In proportion to each field's acres; equally if no field has an area. */
    public static long[] byArea(long totalPaise, double[] acres) {
        double sum = 0;
        for (double a : acres) {
            sum += Math.max(0, a);
        }
        if (sum <= 0) {
            return equally(totalPaise, acres.length);
        }
        double[] weights = new double[acres.length];
        for (int i = 0; i < acres.length; i++) {
            weights[i] = Math.max(0, acres[i]) / sum;
        }
        return byWeights(totalPaise, weights);
    }

    public static long[] equally(long totalPaise, int count) {
        double[] weights = new double[count];
        for (int i = 0; i < count; i++) {
            weights[i] = 1.0 / count;
        }
        return byWeights(totalPaise, weights);
    }

    /**
     * Largest-remainder rounding: everyone gets the floor of their share, then the
     * leftover paise go one each to the largest fractions, earliest field first on ties.
     */
    private static long[] byWeights(long totalPaise, double[] weights) {
        int n = weights.length;
        long[] out = new long[n];
        if (n == 0) {
            return out;
        }
        double[] fractions = new double[n];
        long given = 0;
        for (int i = 0; i < n; i++) {
            double exact = totalPaise * weights[i];
            out[i] = (long) Math.floor(exact);
            fractions[i] = exact - out[i];
            given += out[i];
        }
        for (long left = totalPaise - given; left > 0; left--) {
            int best = 0;
            for (int i = 1; i < n; i++) {
                if (fractions[i] > fractions[best] + 1e-9) {
                    best = i;
                }
            }
            out[best]++;
            fractions[best] = -1;
        }
        return out;
    }
}
