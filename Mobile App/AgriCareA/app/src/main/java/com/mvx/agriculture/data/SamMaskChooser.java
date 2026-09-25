package com.mvx.agriculture.data;

/**
 * Picks which of the AI model's candidate masks is the tapped field. The model
 * offers several guesses from the same tap (a crop row, the plot, the whole
 * farm); the plot is the most confident one that covers the tap and is
 * field-sized. Pure Java; covered by JVM tests.
 */
public final class SamMaskChooser {

    /** Smaller than this share of the picture is a bush or a shadow. */
    static final double MIN_FRACTION = 0.002;
    /** Larger than this share is the whole farm or the landscape, not one plot. */
    static final double MAX_FRACTION = 0.25;

    private SamMaskChooser() {
    }

    /**
     * @param logits masks laid end to end, {@code count} of them, each {@code w * h}; positive means inside
     * @param iou    the model's own confidence in each mask
     * @return the index of the chosen mask, or -1 if none is a plausible field under the tap
     */
    public static int choose(float[] logits, int count, int w, int h, float[] iou, int seedX, int seedY) {
        int size = w * h;
        int best = -1;
        for (int m = 0; m < count; m++) {
            int offset = m * size;
            if (logits[offset + seedY * w + seedX] <= 0) {
                continue;
            }
            int inside = 0;
            for (int i = 0; i < size; i++) {
                if (logits[offset + i] > 0) {
                    inside++;
                }
            }
            double fraction = inside / (double) size;
            if (fraction < MIN_FRACTION || fraction > MAX_FRACTION) {
                continue;
            }
            if (best < 0 || iou[m] > iou[best]) {
                best = m;
            }
        }
        return best;
    }
}
