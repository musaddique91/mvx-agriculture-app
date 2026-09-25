package com.mvx.agriculture.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds the outline of the field around a tapped point in a satellite image.
 *
 * OpenStreetMap has almost no farm parcels mapped in rural India (0 in 16 km²
 * around Ramdurga when checked), so the boundary has to come from the imagery.
 *
 * Neighbouring plots of the same crop are often the same colour; what separates
 * them is a thin line - a bund, a furrow, a path. So this finds edges first and
 * fills outward from the tap without crossing one. It tries the strongest edges
 * first and admits weaker ones only while the fill still spills past a plot-sized
 * area. The region is then turned into a few draggable corners: a rectangle when
 * the plot is rectangular, as most are, otherwise a simplified outline that is
 * never allowed to cross itself.
 *
 * Classical rather than neural: no key, no backend, well under a second on a
 * phone. Where edges are too faint it gives up, and the farmer draws by hand.
 *
 * Pure Java on an ARGB pixel array, so it is covered by JVM tests.
 */
public final class FieldBoundaryDetector {

    /** Longest side of the working copy. High enough that a 3 px bund survives downscaling. */
    private static final int WORK_MAX = 600;
    /** Light smoothing only: enough to quiet crop-row speckle, not enough to erase a bund. */
    private static final int BLUR_RADIUS = 1;
    /** Edge strengths tried, as percentiles of all gradients: strongest lines first. */
    private static final double[] EDGE_PERCENTILES = {97, 94, 90, 86, 82, 78};
    /** However faint the edges, a plot is not wildly different in colour from where it was tapped. */
    private static final double MAX_COLOUR_DISTANCE = 70;
    /** A fill bigger than this share of the image has leaked past the plot. */
    private static final double MAX_FRACTION = 0.35;
    /** A fill smaller than this share of the image is a bush, a shadow or texture, not a plot. */
    private static final double MIN_FRACTION = 0.004;
    /** Region area over its best-fit rectangle's area above which the plot is drawn as a rectangle. */
    private static final double RECTANGULAR = 0.80;
    private static final int MAX_CORNERS = 12;

    // Moore neighbourhood, clockwise on screen (y grows downward): W, NW, N, NE, E, SE, S, SW.
    private static final int[] DX = {-1, -1, 0, 1, 1, 1, 0, -1};
    private static final int[] DY = {0, -1, -1, -1, 0, 1, 1, 1};

    private FieldBoundaryDetector() {
    }

    /**
     * @param argb  pixels, row by row
     * @param seedX tapped pixel, inside the field
     * @return the outline as {x, y} pixel corners in the original image, or null
     *         when no field-sized region with a clear edge surrounds the seed
     */
    public static int[][] detect(int[] argb, int width, int height, int seedX, int seedY) {
        if (argb == null || width <= 0 || height <= 0 || argb.length < width * height
                || seedX < 0 || seedY < 0 || seedX >= width || seedY >= height) {
            return null;
        }

        int scale = Math.max(1, (int) Math.ceil(Math.max(width, height) / (double) WORK_MAX));
        int w = Math.max(1, width / scale);
        int h = Math.max(1, height / scale);
        float[][] rgb = downscale(argb, width, height, scale, w, h);
        for (float[] channel : rgb) {
            blur(channel, w, h);
        }
        float[] gradient = gradient(rgb, w, h);
        int sx = Math.min(seedX / scale, w - 1);
        int sy = Math.min(seedY / scale, h - 1);
        float[] seedColour = meanAround(rgb, w, h, sx, sy);

        boolean[] mask = fillWithinEdges(rgb, gradient, w, h, sx, sy, seedColour);
        if (mask == null) {
            return null;
        }
        return outline(mask, w, h, sx, sy, scale);
    }

    /**
     * The outline of an already segmented field, such as one from the on-device AI model.
     *
     * @param mask  true inside the field, row by row
     * @return {x, y} pixel corners, or null when the mask holds nothing field-sized
     */
    public static int[][] outlineFromMask(boolean[] mask, int width, int height, int seedX, int seedY) {
        if (mask == null || width <= 0 || height <= 0 || mask.length < width * height
                || seedX < 0 || seedY < 0 || seedX >= width || seedY >= height) {
            return null;
        }
        int scale = Math.max(1, (int) Math.ceil(Math.max(width, height) / (double) WORK_MAX));
        int w = Math.max(1, width / scale);
        int h = Math.max(1, height / scale);
        boolean[] small = new boolean[w * h];
        int half = scale * scale / 2;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int inside = 0;
                for (int dy = 0; dy < scale; dy++) {
                    int row = (y * scale + dy) * width;
                    for (int dx = 0; dx < scale; dx++) {
                        if (mask[row + x * scale + dx]) {
                            inside++;
                        }
                    }
                }
                small[y * w + x] = inside > half || (scale == 1 && inside == 1);
            }
        }
        return outline(small, w, h, Math.min(seedX / scale, w - 1), Math.min(seedY / scale, h - 1), scale);
    }

    /** Cleans a working-size mask and turns it into draggable corners in original pixels. */
    private static int[][] outline(boolean[] mask, int w, int h, int sx, int sy, int scale) {
        // Opening trims spurs that slipped through a gap in an edge.
        mask = dilate(erode(mask, w, h), w, h);
        mask = componentAt(mask, w, h, sx, sy);
        if (mask == null) {
            return null;
        }
        fillHoles(mask, w, h);
        int area = count(mask);
        if (area < MIN_FRACTION * w * h) {
            return null;
        }

        List<int[]> contour = trace(mask, w, h);
        if (contour.size() < 3) {
            return null;
        }
        List<int[]> corners = corners(contour, area);
        if (corners == null || corners.size() < 3) {
            return null;
        }
        int[][] out = new int[corners.size()][];
        for (int i = 0; i < corners.size(); i++) {
            int[] c = corners.get(i);
            out[i] = new int[]{c[0] * scale + scale / 2, c[1] * scale + scale / 2};
        }
        return out;
    }

    /**
     * Fills from the seed without crossing an edge, admitting weaker edges until the
     * fill stops spilling past a plot-sized area. The first fill that fits is the
     * largest plot the strong edges enclose.
     */
    private static boolean[] fillWithinEdges(float[][] rgb, float[] gradient, int w, int h,
                                             int sx, int sy, float[] seed) {
        float[] sorted = gradient.clone();
        java.util.Arrays.sort(sorted);
        int total = w * h;
        for (double percentile : EDGE_PERCENTILES) {
            float threshold = sorted[Math.min(sorted.length - 1, (int) (sorted.length * percentile / 100.0))];
            int start = nearestNonEdge(gradient, w, h, sx, sy, threshold);
            if (start < 0) {
                continue;
            }
            boolean[] region = flood(rgb, gradient, w, h, start, seed, threshold);
            int area = count(region);
            if (area > MAX_FRACTION * total) {
                continue;   // leaked: let weaker edges in
            }
            return area < MIN_FRACTION * total ? null : region;
        }
        return null;
    }

    /** The seed pixel, or the nearest pixel within 4 px that is not itself an edge. */
    private static int nearestNonEdge(float[] gradient, int w, int h, int sx, int sy, float threshold) {
        for (int r = 0; r <= 4; r++) {
            for (int y = Math.max(0, sy - r); y <= Math.min(h - 1, sy + r); y++) {
                for (int x = Math.max(0, sx - r); x <= Math.min(w - 1, sx + r); x++) {
                    if (gradient[y * w + x] < threshold) {
                        return y * w + x;
                    }
                }
            }
        }
        return -1;
    }

    /** 4-connected fill, so a one-pixel diagonal edge line still seals the plot. */
    private static boolean[] flood(float[][] rgb, float[] gradient, int w, int h, int start,
                                   float[] seed, float threshold) {
        boolean[] region = new boolean[w * h];
        double colourLimit = MAX_COLOUR_DISTANCE * MAX_COLOUR_DISTANCE;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        region[start] = true;
        queue.add(start);
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int x = i % w;
            int y = i / w;
            for (int d = 0; d < 8; d += 2) {
                int nx = x + DX[d];
                int ny = y + DY[d];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                    continue;
                }
                int n = ny * w + nx;
                if (!region[n] && gradient[n] < threshold && distanceSquared(rgb, n, seed) <= colourLimit) {
                    region[n] = true;
                    queue.add(n);
                }
            }
        }
        return region;
    }

    /** Sobel magnitude, taking the strongest of the three colour channels at each pixel. */
    private static float[] gradient(float[][] rgb, int w, int h) {
        float[] out = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float best = 0;
                for (float[] c : rgb) {
                    float gx = at(c, w, h, x + 1, y - 1) + 2 * at(c, w, h, x + 1, y) + at(c, w, h, x + 1, y + 1)
                            - at(c, w, h, x - 1, y - 1) - 2 * at(c, w, h, x - 1, y) - at(c, w, h, x - 1, y + 1);
                    float gy = at(c, w, h, x - 1, y + 1) + 2 * at(c, w, h, x, y + 1) + at(c, w, h, x + 1, y + 1)
                            - at(c, w, h, x - 1, y - 1) - 2 * at(c, w, h, x, y - 1) - at(c, w, h, x + 1, y - 1);
                    best = Math.max(best, gx * gx + gy * gy);
                }
                out[y * w + x] = (float) Math.sqrt(best);
            }
        }
        return out;
    }

    private static float at(float[] channel, int w, int h, int x, int y) {
        return channel[clampIndex(y, h) * w + clampIndex(x, w)];
    }

    /**
     * A rectangle when the plot is nearly one (most are), otherwise a simplified
     * outline; and never a shape whose sides cross, which the farmer could not fix
     * by dragging.
     */
    private static List<int[]> corners(List<int[]> contour, int area) {
        List<int[]> hull = convexHull(contour);
        if (hull.size() < 3) {
            return null;
        }
        List<int[]> rectangle = minimumAreaRectangle(hull);
        if (area / Math.max(1.0, polygonArea(rectangle)) >= RECTANGULAR) {
            return rectangle;
        }
        List<int[]> outline = simplify(contour);
        if (outline.size() >= 3 && !selfIntersects(outline)) {
            return outline;
        }
        return simplify(hull);
    }

    private static double distanceSquared(float[][] rgb, int i, float[] seed) {
        double dr = rgb[0][i] - seed[0];
        double dg = rgb[1][i] - seed[1];
        double db = rgb[2][i] - seed[2];
        return dr * dr + dg * dg + db * db;
    }

    private static float[][] downscale(int[] argb, int width, int height, int scale, int w, int h) {
        float[][] out = new float[3][w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                long r = 0, g = 0, b = 0;
                int n = 0;
                for (int yy = y * scale; yy < Math.min(height, (y + 1) * scale); yy++) {
                    for (int xx = x * scale; xx < Math.min(width, (x + 1) * scale); xx++) {
                        int p = argb[yy * width + xx];
                        r += (p >> 16) & 0xFF;
                        g += (p >> 8) & 0xFF;
                        b += p & 0xFF;
                        n++;
                    }
                }
                int i = y * w + x;
                out[0][i] = r / (float) n;
                out[1][i] = g / (float) n;
                out[2][i] = b / (float) n;
            }
        }
        return out;
    }

    /** Separable box blur with clamped edges, in place. */
    private static void blur(float[] channel, int w, int h) {
        float[] tmp = new float[channel.length];
        int size = 2 * BLUR_RADIUS + 1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float sum = 0;
                for (int k = -BLUR_RADIUS; k <= BLUR_RADIUS; k++) {
                    sum += channel[y * w + clampIndex(x + k, w)];
                }
                tmp[y * w + x] = sum / size;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float sum = 0;
                for (int k = -BLUR_RADIUS; k <= BLUR_RADIUS; k++) {
                    sum += tmp[clampIndex(y + k, h) * w + x];
                }
                channel[y * w + x] = sum / size;
            }
        }
    }

    private static int clampIndex(int v, int size) {
        return v < 0 ? 0 : (v >= size ? size - 1 : v);
    }

    private static float[] meanAround(float[][] rgb, int w, int h, int sx, int sy) {
        float[] mean = new float[3];
        int n = 0;
        for (int y = Math.max(0, sy - 2); y <= Math.min(h - 1, sy + 2); y++) {
            for (int x = Math.max(0, sx - 2); x <= Math.min(w - 1, sx + 2); x++) {
                for (int c = 0; c < 3; c++) {
                    mean[c] += rgb[c][y * w + x];
                }
                n++;
            }
        }
        for (int c = 0; c < 3; c++) {
            mean[c] /= n;
        }
        return mean;
    }

    /** Marks as region every background pixel the image border cannot reach. */
    private static void fillHoles(boolean[] mask, int w, int h) {
        boolean[] outside = new boolean[w * h];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            seedOutside(mask, outside, queue, x);
            seedOutside(mask, outside, queue, (h - 1) * w + x);
        }
        for (int y = 0; y < h; y++) {
            seedOutside(mask, outside, queue, y * w);
            seedOutside(mask, outside, queue, y * w + w - 1);
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int x = i % w;
            int y = i / w;
            for (int d = 0; d < 8; d += 2) {
                int nx = x + DX[d];
                int ny = y + DY[d];
                if (nx >= 0 && ny >= 0 && nx < w && ny < h) {
                    seedOutside(mask, outside, queue, ny * w + nx);
                }
            }
        }
        for (int i = 0; i < mask.length; i++) {
            if (!outside[i]) {
                mask[i] = true;
            }
        }
    }

    private static void seedOutside(boolean[] mask, boolean[] outside, ArrayDeque<Integer> queue, int i) {
        if (!mask[i] && !outside[i]) {
            outside[i] = true;
            queue.add(i);
        }
    }

    private static boolean[] erode(boolean[] mask, int w, int h) {
        boolean[] out = new boolean[mask.length];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                out[i] = mask[i] && mask[i - 1] && mask[i + 1] && mask[i - w] && mask[i + w];
            }
        }
        return out;
    }

    private static boolean[] dilate(boolean[] mask, int w, int h) {
        boolean[] out = new boolean[mask.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                out[i] = mask[i]
                        || (x > 0 && mask[i - 1]) || (x < w - 1 && mask[i + 1])
                        || (y > 0 && mask[i - w]) || (y < h - 1 && mask[i + w]);
            }
        }
        return out;
    }

    /** The connected piece holding the seed, or the largest piece if opening removed the seed pixel itself. */
    private static boolean[] componentAt(boolean[] mask, int w, int h, int sx, int sy) {
        int start = sy * w + sx;
        if (!mask[start]) {
            start = -1;
            int bestSize = 0;
            boolean[] seen = new boolean[mask.length];
            for (int i = 0; i < mask.length; i++) {
                if (mask[i] && !seen[i]) {
                    int size = flood(mask, seen, w, h, i).size();
                    if (size > bestSize) {
                        bestSize = size;
                        start = i;
                    }
                }
            }
            if (start < 0) {
                return null;
            }
        }
        boolean[] out = new boolean[mask.length];
        for (int i : flood(mask, new boolean[mask.length], w, h, start)) {
            out[i] = true;
        }
        return out;
    }

    private static List<Integer> flood(boolean[] mask, boolean[] seen, int w, int h, int start) {
        List<Integer> pixels = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        seen[start] = true;
        queue.add(start);
        while (!queue.isEmpty()) {
            int i = queue.poll();
            pixels.add(i);
            int x = i % w;
            int y = i / w;
            for (int d = 0; d < 8; d += 2) {
                int nx = x + DX[d];
                int ny = y + DY[d];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                    continue;
                }
                int n = ny * w + nx;
                if (mask[n] && !seen[n]) {
                    seen[n] = true;
                    queue.add(n);
                }
            }
        }
        return pixels;
    }

    private static int count(boolean[] mask) {
        int n = 0;
        for (boolean b : mask) {
            if (b) {
                n++;
            }
        }
        return n;
    }

    private static boolean set(boolean[] mask, int w, int h, int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && mask[y * w + x];
    }

    /** Moore-neighbour trace of the region's outer edge, starting at its top-left pixel. */
    private static List<int[]> trace(boolean[] mask, int w, int h) {
        List<int[]> contour = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < mask.length; i++) {
            if (mask[i]) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return contour;
        }
        int sx = start % w;
        int sy = start / w;
        int cx = sx;
        int cy = sy;
        int back = 0;   // the pixel to the west of the top-left pixel is always background
        contour.add(new int[]{cx, cy});
        int limit = 4 * mask.length;
        while (limit-- > 0) {
            int nextX = -1;
            int nextY = -1;
            for (int k = 1; k <= 8; k++) {
                int d = (back + k) % 8;
                int nx = cx + DX[d];
                int ny = cy + DY[d];
                if (set(mask, w, h, nx, ny)) {
                    int pd = (back + k - 1) % 8;
                    int bx = cx + DX[pd];
                    int by = cy + DY[pd];
                    nextX = nx;
                    nextY = ny;
                    back = directionOf(bx - nx, by - ny);
                    break;
                }
            }
            if (nextX < 0 || (nextX == sx && nextY == sy)) {
                break;   // a lone pixel, or back where the trace began
            }
            cx = nextX;
            cy = nextY;
            contour.add(new int[]{cx, cy});
        }
        return contour;
    }

    private static int directionOf(int dx, int dy) {
        for (int d = 0; d < 8; d++) {
            if (DX[d] == dx && DY[d] == dy) {
                return d;
            }
        }
        return 0;
    }

    /** Douglas-Peucker on the closed outline, loosening until there are few enough corners to drag. */
    private static List<int[]> simplify(List<int[]> contour) {
        int far = 0;
        double farDistance = -1;
        int[] first = contour.get(0);
        for (int i = 1; i < contour.size(); i++) {
            double d = Math.hypot(contour.get(i)[0] - first[0], contour.get(i)[1] - first[1]);
            if (d > farDistance) {
                farDistance = d;
                far = i;
            }
        }
        List<int[]> result = contour;
        for (double epsilon = 1.0; epsilon < 200; epsilon *= 1.4) {
            List<int[]> out = new ArrayList<>();
            douglasPeucker(contour, 0, far, epsilon, out);
            List<int[]> secondHalf = new ArrayList<>(contour.subList(far, contour.size()));
            secondHalf.add(first);
            List<int[]> tail = new ArrayList<>();
            douglasPeucker(secondHalf, 0, secondHalf.size() - 1, epsilon, tail);
            out.addAll(tail);   // douglasPeucker already leaves out the closing point, which repeats the first
            result = out;
            if (out.size() <= MAX_CORNERS) {
                break;
            }
        }
        return result;
    }

    /** Appends the kept points from {@code from} up to but excluding {@code to}. */
    private static void douglasPeucker(List<int[]> points, int from, int to, double epsilon, List<int[]> out) {
        if (to <= from) {
            return;
        }
        int[] a = points.get(from);
        int[] b = points.get(to);
        double length = Math.hypot(b[0] - a[0], b[1] - a[1]);
        int index = -1;
        double maxDistance = -1;
        for (int i = from + 1; i < to; i++) {
            int[] p = points.get(i);
            double d = length == 0
                    ? Math.hypot(p[0] - a[0], p[1] - a[1])
                    : Math.abs((b[0] - a[0]) * (a[1] - p[1]) - (a[0] - p[0]) * (b[1] - a[1])) / length;
            if (d > maxDistance) {
                maxDistance = d;
                index = i;
            }
        }
        if (index >= 0 && maxDistance > epsilon) {
            douglasPeucker(points, from, index, epsilon, out);
            douglasPeucker(points, index, to, epsilon, out);
        } else {
            out.add(a);
        }
    }
    /** Andrew's monotone chain. */
    private static List<int[]> convexHull(List<int[]> points) {
        List<int[]> sorted = new ArrayList<>(points);
        sorted.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        List<int[]> hull = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            int base = hull.size();
            for (int[] p : sorted) {
                while (hull.size() >= base + 2
                        && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), p) <= 0) {
                    hull.remove(hull.size() - 1);
                }
                hull.add(p);
            }
            hull.remove(hull.size() - 1);
            java.util.Collections.reverse(sorted);
        }
        return hull;
    }

    private static long cross(int[] o, int[] a, int[] b) {
        return (long) (a[0] - o[0]) * (b[1] - o[1]) - (long) (a[1] - o[1]) * (b[0] - o[0]);
    }

    /** Rotating calipers: the smallest rectangle, at any angle, around a convex hull. */
    private static List<int[]> minimumAreaRectangle(List<int[]> hull) {
        double bestArea = Double.MAX_VALUE;
        double[][] best = null;
        for (int i = 0; i < hull.size(); i++) {
            int[] a = hull.get(i);
            int[] b = hull.get((i + 1) % hull.size());
            double ex = b[0] - a[0];
            double ey = b[1] - a[1];
            double length = Math.hypot(ex, ey);
            if (length == 0) {
                continue;
            }
            ex /= length;
            ey /= length;
            double minU = Double.MAX_VALUE, maxU = -Double.MAX_VALUE, minV = Double.MAX_VALUE, maxV = -Double.MAX_VALUE;
            for (int[] p : hull) {
                double u = p[0] * ex + p[1] * ey;
                double v = -p[0] * ey + p[1] * ex;
                minU = Math.min(minU, u);
                maxU = Math.max(maxU, u);
                minV = Math.min(minV, v);
                maxV = Math.max(maxV, v);
            }
            double rectArea = (maxU - minU) * (maxV - minV);
            if (rectArea < bestArea) {
                bestArea = rectArea;
                best = new double[][]{{minU, minV}, {maxU, minV}, {maxU, maxV}, {minU, maxV}, {ex, ey}};
            }
        }
        List<int[]> out = new ArrayList<>();
        if (best == null) {
            return out;
        }
        double ex = best[4][0];
        double ey = best[4][1];
        for (int k = 0; k < 4; k++) {
            double u = best[k][0];
            double v = best[k][1];
            out.add(new int[]{(int) Math.round(u * ex - v * ey), (int) Math.round(u * ey + v * ex)});
        }
        return out;
    }

    private static double polygonArea(List<int[]> polygon) {
        double sum = 0;
        for (int i = 0; i < polygon.size(); i++) {
            int[] a = polygon.get(i);
            int[] b = polygon.get((i + 1) % polygon.size());
            sum += (double) a[0] * b[1] - (double) b[0] * a[1];
        }
        return Math.abs(sum) / 2.0;
    }

    private static boolean selfIntersects(List<int[]> polygon) {
        int n = polygon.size();
        for (int i = 0; i < n; i++) {
            int[] a = polygon.get(i);
            int[] b = polygon.get((i + 1) % n);
            for (int j = i + 2; j < n; j++) {
                if (i == 0 && j == n - 1) {
                    continue;   // adjacent through the closing side
                }
                int[] c = polygon.get(j);
                int[] d = polygon.get((j + 1) % n);
                if (Long.signum(cross(a, b, c)) * Long.signum(cross(a, b, d)) < 0
                        && Long.signum(cross(c, d, a)) * Long.signum(cross(c, d, b)) < 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
