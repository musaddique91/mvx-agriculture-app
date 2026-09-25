package com.mvx.agriculture.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SamMaskTest {

    private static final int W = 400;
    private static final int H = 400;

    /** Logits for {@code count} masks laid end to end; positive inside each rectangle. */
    private static float[] masks(int[][] rects) {
        float[] out = new float[rects.length * W * H];
        for (int m = 0; m < rects.length; m++) {
            int[] r = rects[m];
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    boolean in = x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
                    out[m * W * H + y * W + x] = in ? 5f : -5f;
                }
            }
        }
        return out;
    }

    @Test
    public void picksTheMostConfidentFieldSizedMaskUnderTheTap() {
        float[] logits = masks(new int[][]{
                {195, 195, 205, 205},   // a speck: 0.06% of the picture
                {100, 100, 300, 250},   // the plot
                {0, 0, 400, 400},       // everything
                {300, 300, 380, 380}}); // a plot, but not the tapped one
        float[] iou = {0.99f, 0.90f, 0.95f, 0.97f};
        assertEquals(1, SamMaskChooser.choose(logits, 4, W, H, iou, 200, 200));
    }

    @Test
    public void noUsableMaskMeansNoChoice() {
        float[] logits = masks(new int[][]{{0, 0, 400, 400}, {198, 198, 202, 202}});
        assertEquals(-1, SamMaskChooser.choose(logits, 2, W, H, new float[]{0.9f, 0.9f}, 200, 200));
    }

    @Test
    public void aRectangularMaskBecomesFourCornersOnIt() {
        boolean[] mask = new boolean[W * H];
        for (int y = 120; y < 280; y++) {
            for (int x = 80; x < 330; x++) {
                mask[y * W + x] = true;
            }
        }
        int[][] outline = FieldBoundaryDetector.outlineFromMask(mask, W, H, 200, 200);
        assertNotNull(outline);
        assertEquals(4, outline.length);
        for (int[] c : outline) {
            boolean nearX = Math.abs(c[0] - 80) <= 3 || Math.abs(c[0] - 330) <= 3;
            boolean nearY = Math.abs(c[1] - 120) <= 3 || Math.abs(c[1] - 280) <= 3;
            assertTrue("corner " + c[0] + "," + c[1], nearX && nearY);
        }
    }

    @Test
    public void anEmptyMaskHasNoOutline() {
        assertNull(FieldBoundaryDetector.outlineFromMask(new boolean[W * H], W, H, 200, 200));
    }
}
