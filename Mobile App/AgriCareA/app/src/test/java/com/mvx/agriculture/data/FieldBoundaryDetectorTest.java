package com.mvx.agriculture.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

public class FieldBoundaryDetectorTest {

    private static final int W = 600;
    private static final int H = 900;

    private static int argb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** A satellite-like canvas: every pixel is the base colour plus per-pixel noise, like crop texture. */
    private static int[] canvas(int r, int g, int b, int noise, long seed) {
        Random random = new Random(seed);
        int[] px = new int[W * H];
        for (int i = 0; i < px.length; i++) {
            px[i] = argb(r + random.nextInt(2 * noise + 1) - noise,
                    g + random.nextInt(2 * noise + 1) - noise,
                    b + random.nextInt(2 * noise + 1) - noise);
        }
        return px;
    }

    private static void paintRect(int[] px, int x0, int y0, int x1, int y1, int r, int g, int b,
                                  int noise, long seed) {
        Random random = new Random(seed);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                px[y * W + x] = argb(r + random.nextInt(2 * noise + 1) - noise,
                        g + random.nextInt(2 * noise + 1) - noise,
                        b + random.nextInt(2 * noise + 1) - noise);
            }
        }
    }

    /** Shoelace area of a pixel polygon. */
    private static double area(int[][] polygon) {
        double sum = 0;
        for (int i = 0; i < polygon.length; i++) {
            int[] a = polygon[i];
            int[] b = polygon[(i + 1) % polygon.length];
            sum += (double) a[0] * b[1] - (double) b[0] * a[1];
        }
        return Math.abs(sum) / 2.0;
    }

    private static boolean inside(int[][] polygon, double x, double y) {
        boolean in = false;
        for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
            double xi = polygon[i][0], yi = polygon[i][1], xj = polygon[j][0], yj = polygon[j][1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                in = !in;
            }
        }
        return in;
    }

    @Test
    public void findsARectangularFieldOnADifferentBackground() {
        int[] px = canvas(150, 120, 80, 18, 1);                       // dry brown land
        paintRect(px, 150, 250, 450, 650, 60, 130, 50, 18, 2);        // green crop, 300 x 400

        int[][] polygon = FieldBoundaryDetector.detect(px, W, H, 300, 450);

        assertNotNull(polygon);
        assertTrue("a field should simplify to a handful of corners, got " + polygon.length,
                polygon.length >= 4 && polygon.length <= 12);
        double expected = 300.0 * 400.0;
        assertEquals("area within 12%", 1.0, area(polygon) / expected, 0.12);
        assertTrue(inside(polygon, 300, 450));
        assertTrue(!inside(polygon, 100, 100));
    }

    @Test
    public void stopsAtABundBetweenTwoFieldsOfTheSameCrop() {
        int[] px = canvas(150, 120, 80, 18, 3);
        paintRect(px, 100, 200, 500, 700, 60, 130, 50, 18, 4);        // one big green block...
        paintRect(px, 100, 445, 500, 455, 175, 150, 110, 6, 5);       // ...split by a pale 10 px bund

        int[][] polygon = FieldBoundaryDetector.detect(px, W, H, 300, 320);

        assertNotNull(polygon);
        double upperField = 400.0 * 245.0;
        assertEquals("only the upper field, area within 15%", 1.0, area(polygon) / upperField, 0.15);
        assertTrue(!inside(polygon, 300, 600));
    }

    @Test
    public void followsAnLShapedField() {
        int[] px = canvas(150, 120, 80, 18, 6);
        paintRect(px, 100, 200, 300, 700, 60, 130, 50, 18, 7);
        paintRect(px, 300, 500, 500, 700, 60, 130, 50, 18, 8);

        int[][] polygon = FieldBoundaryDetector.detect(px, W, H, 200, 400);

        assertNotNull(polygon);
        assertTrue("an L needs at least 5 corners, got " + polygon.length, polygon.length >= 5);
        double expected = 200.0 * 500.0 + 200.0 * 200.0;
        assertEquals("area within 15%", 1.0, area(polygon) / expected, 0.15);
        assertTrue(inside(polygon, 400, 600));
    }

    @Test
    public void givesUpWhenThereIsNoEdgeToFind() {
        int[] px = canvas(90, 120, 60, 18, 9);   // one uniform crop to the screen edges

        assertNull(FieldBoundaryDetector.detect(px, W, H, 300, 450));
    }

    @Test
    public void givesUpForASpeckTooSmallToBeAField() {
        int[] px = canvas(150, 120, 80, 18, 10);
        paintRect(px, 295, 445, 305, 455, 60, 130, 50, 4, 11);   // a 10 x 10 bush

        assertNull(FieldBoundaryDetector.detect(px, W, H, 300, 450));
    }

    @Test
    public void rejectsASeedOutsideTheImage() {
        int[] px = canvas(150, 120, 80, 18, 12);
        assertNull(FieldBoundaryDetector.detect(px, W, H, -5, 450));
        assertNull(FieldBoundaryDetector.detect(px, W, H, 300, H));
    }
}
