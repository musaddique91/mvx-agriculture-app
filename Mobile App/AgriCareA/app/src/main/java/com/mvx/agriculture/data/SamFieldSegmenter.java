package com.mvx.agriculture.data;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/**
 * Finds the field under a tap with MobileSAM, entirely on the phone. The image
 * encoder reads the satellite picture once; the small decoder then turns the tap
 * into candidate masks, and the most confident field-sized one becomes corners.
 *
 * Not thread-safe: call from one background thread.
 */
public final class SamFieldSegmenter implements AutoCloseable {

    /** The encoder was exported for pictures whose longest side is this. */
    private static final int MODEL_SIDE = 1024;

    private final OrtEnvironment env;
    private final OrtSession encoder;
    private final OrtSession decoder;

    private SamFieldSegmenter(OrtEnvironment env, OrtSession encoder, OrtSession decoder) {
        this.env = env;
        this.encoder = encoder;
        this.decoder = decoder;
    }

    /** Loads both models; a few hundred milliseconds, so do it off the main thread. */
    public static SamFieldSegmenter load(Context context) throws IOException, OrtException {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())));
        OrtSession encoder = env.createSession(asset(context, "sam/mobile_sam_image_encoder.onnx"), options);
        OrtSession decoder = env.createSession(asset(context, "sam/sam_mask_decoder_multi.onnx"), options);
        return new SamFieldSegmenter(env, encoder, decoder);
    }

    /**
     * @param image the map as the farmer sees it
     * @return corners in {@code image} pixels, or null when no field-sized region was found
     */
    public int[][] segment(Bitmap image, int seedX, int seedY) throws OrtException {
        // A square around the tap keeps the field large in the model's 1024 px view.
        int side = Math.min(image.getWidth(), image.getHeight());
        int left = clamp(seedX - side / 2, 0, image.getWidth() - side);
        int top = clamp(seedY - side / 2, 0, image.getHeight() - side);
        Bitmap crop = Bitmap.createBitmap(image, left, top, side, side);
        Bitmap input = Bitmap.createScaledBitmap(crop, MODEL_SIDE, MODEL_SIDE, true);
        int[] pixels = new int[MODEL_SIDE * MODEL_SIDE];
        input.getPixels(pixels, 0, MODEL_SIDE, 0, 0, MODEL_SIDE, MODEL_SIDE);
        if (input != crop) {
            input.recycle();
        }
        if (crop != image) {
            crop.recycle();
        }

        FloatBuffer rgb = FloatBuffer.allocate(pixels.length * 3);
        for (int p : pixels) {
            rgb.put((p >> 16) & 0xFF).put((p >> 8) & 0xFF).put(p & 0xFF);
        }
        rgb.rewind();

        float scale = MODEL_SIDE / (float) side;
        int localX = seedX - left;
        int localY = seedY - top;

        try (OnnxTensor imageTensor = OnnxTensor.createTensor(env, rgb, new long[]{MODEL_SIDE, MODEL_SIDE, 3});
             OrtSession.Result encoded = encoder.run(single("input_image", imageTensor))) {
            OnnxTensor embeddings = (OnnxTensor) encoded.get(0);

            Map<String, OnnxTensor> inputs = new HashMap<>();
            try {
                inputs.put("image_embeddings", embeddings);
                // The tap, plus the padding point SAM expects when no box is given.
                inputs.put("point_coords", OnnxTensor.createTensor(env,
                        FloatBuffer.wrap(new float[]{localX * scale, localY * scale, 0f, 0f}), new long[]{1, 2, 2}));
                inputs.put("point_labels", OnnxTensor.createTensor(env,
                        FloatBuffer.wrap(new float[]{1f, -1f}), new long[]{1, 2}));
                inputs.put("mask_input", OnnxTensor.createTensor(env,
                        FloatBuffer.allocate(256 * 256), new long[]{1, 1, 256, 256}));
                inputs.put("has_mask_input", OnnxTensor.createTensor(env,
                        FloatBuffer.wrap(new float[]{0f}), new long[]{1}));
                // Masks come back at the crop's own size, so corners need no rescaling.
                inputs.put("orig_im_size", OnnxTensor.createTensor(env,
                        FloatBuffer.wrap(new float[]{side, side}), new long[]{2}));

                try (OrtSession.Result decoded = decoder.run(inputs)) {
                    OnnxTensor masksTensor = (OnnxTensor) decoded.get("masks").get();
                    long[] shape = masksTensor.getInfo().getShape();   // 1, count, h, w
                    int count = (int) shape[1];
                    int h = (int) shape[2];
                    int w = (int) shape[3];
                    FloatBuffer maskBuffer = masksTensor.getFloatBuffer();
                    float[] logits = new float[maskBuffer.remaining()];
                    maskBuffer.get(logits);
                    float[] iou = ((float[][]) decoded.get("iou_predictions").get().getValue())[0];

                    int sx = clamp(localX * w / side, 0, w - 1);
                    int sy = clamp(localY * h / side, 0, h - 1);
                    int chosen = SamMaskChooser.choose(logits, count, w, h, iou, sx, sy);
                    if (chosen < 0) {
                        return null;
                    }
                    boolean[] mask = new boolean[w * h];
                    int offset = chosen * w * h;
                    for (int i = 0; i < mask.length; i++) {
                        mask[i] = logits[offset + i] > 0;
                    }
                    int[][] corners = FieldBoundaryDetector.outlineFromMask(mask, w, h, sx, sy);
                    if (corners == null) {
                        return null;
                    }
                    for (int[] c : corners) {
                        c[0] = left + c[0] * side / w;
                        c[1] = top + c[1] * side / h;
                    }
                    return corners;
                }
            } finally {
                for (Map.Entry<String, OnnxTensor> e : inputs.entrySet()) {
                    if (!"image_embeddings".equals(e.getKey())) {
                        e.getValue().close();
                    }
                }
            }
        }
    }

    @Override
    public void close() {
        try {
            encoder.close();
            decoder.close();
        } catch (OrtException ignored) {
            // Nothing more to release.
        }
    }

    private static Map<String, OnnxTensor> single(String name, OnnxTensor tensor) {
        Map<String, OnnxTensor> map = new HashMap<>();
        map.put(name, tensor);
        return map;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static byte[] asset(Context context, String name) throws IOException {
        try (InputStream in = context.getAssets().open(name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1 << 16];
            for (int n; (n = in.read(buffer)) > 0; ) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }
}
