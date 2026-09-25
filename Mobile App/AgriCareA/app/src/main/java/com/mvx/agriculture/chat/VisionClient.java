package com.mvx.agriculture.chat;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.mvx.agriculture.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Sends a photo to NVIDIA's vision model and returns a written diagnosis.
 *
 * The bundled TFLite model only knows a handful of tomato and potato classes; this
 * path handles any crop, pest, weed or deficiency at the cost of needing a network.
 */
public class VisionClient {

    private static final String TAG = "VisionClient";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /** NVIDIA rejects inline images past ~180 KB of base64, so aim below that. */
    private static final int MAX_BASE64_BYTES = 170_000;
    private static final int MAX_EDGE_PX = 1024;

    public interface Listener {
        void onResult(String diagnosis);

        void onError(String message);
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build();
    private final Handler main = new Handler(Looper.getMainLooper());

    public static boolean hasApiKey() {
        return !TextUtils.isEmpty(BuildConfig.NVIDIA_API_KEY);
    }

    /**
     * @param photo    the captured frame
     * @param mode     what to look for
     * @param language the language to answer in, e.g. "Hindi"
     * @param context  optional extra detail from the farmer, may be null
     */
    public void analyse(Bitmap photo, ScanMode mode, String language, String context,
                        Listener listener) {
        if (!hasApiKey()) {
            main.post(() -> listener.onError("No API key configured."));
            return;
        }

        String encoded = encode(photo);
        if (encoded == null) {
            main.post(() -> listener.onError("Could not prepare the photo."));
            return;
        }

        StringBuilder prompt = new StringBuilder(mode.instruction);
        if (!TextUtils.isEmpty(context)) {
            prompt.append(" The farmer adds: ").append(context).append('.');
        }
        prompt.append(" Keep it under 200 words, in plain language a farmer can act on. ")
                .append("If the photo is too blurry or does not show a plant, say so instead of guessing. ")
                .append("Answer entirely in ").append(language).append('.');
        // NVIDIA takes the image as an inline data URI inside the message text.
        prompt.append(" <img src=\"data:image/jpeg;base64,").append(encoded).append("\" />");

        final Request request;
        try {
            JSONObject body = new JSONObject()
                    .put("model", BuildConfig.NVIDIA_VISION_MODEL)
                    .put("messages", new JSONArray().put(
                            new JSONObject().put("role", "user").put("content", prompt.toString())))
                    .put("max_tokens", 600)
                    .put("temperature", 0.2)
                    .put("stream", false);
            request = new Request.Builder()
                    .url(BuildConfig.NVIDIA_BASE_URL)
                    .header("Authorization", "Bearer " + BuildConfig.NVIDIA_API_KEY)
                    .header("Accept", "application/json")
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();
        } catch (JSONException e) {
            Log.e(TAG, "Could not build the request", e);
            main.post(() -> listener.onError("Could not build the request."));
            return;
        }

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Vision request failed", e);
                main.post(() -> listener.onError("Could not reach the AI. Check your connection."));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (ResponseBody responseBody = response.body()) {
                    String raw = responseBody == null ? "" : responseBody.string();
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "HTTP " + response.code() + ": " + raw);
                        main.post(() -> listener.onError(
                                response.code() == 413
                                        ? "That photo was too large. Try again."
                                        : "The AI could not read that photo (error "
                                                + response.code() + ")."));
                        return;
                    }
                    JSONArray choices = new JSONObject(raw).optJSONArray("choices");
                    String text = null;
                    if (choices != null && choices.length() > 0) {
                        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
                        if (message != null) {
                            text = message.optString("content", "");
                            if (TextUtils.isEmpty(text.trim())) {
                                text = message.optString("reasoning_content", "");
                            }
                        }
                    }
                    final String diagnosis = text == null ? "" : text.trim();
                    if (diagnosis.isEmpty()) {
                        main.post(() -> listener.onError("The AI returned an empty answer."));
                    } else {
                        main.post(() -> listener.onResult(diagnosis));
                    }
                } catch (IOException | JSONException e) {
                    Log.e(TAG, "Could not read the response", e);
                    main.post(() -> listener.onError("Could not read the answer."));
                }
            }
        });
    }

    /** Scales and compresses until the base64 fits the inline-image budget. */
    private static String encode(Bitmap source) {
        Bitmap scaled = scaleDown(source);
        for (int quality = 80; quality >= 35; quality -= 15) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out);
            String encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
            if (encoded.length() <= MAX_BASE64_BYTES) {
                return encoded;
            }
        }
        return null;
    }

    private static Bitmap scaleDown(Bitmap source) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        if (longest <= MAX_EDGE_PX) {
            return source;
        }
        float ratio = MAX_EDGE_PX / (float) longest;
        return Bitmap.createScaledBitmap(source,
                Math.round(source.getWidth() * ratio),
                Math.round(source.getHeight() * ratio), true);
    }
}
