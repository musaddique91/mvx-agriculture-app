package com.mvx.agriculture.chat;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.mvx.agriculture.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
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
 * Talks to NVIDIA NIM's OpenAI-compatible chat completions endpoint.
 *
 * Replies are delivered on the main thread, so callers can touch views directly.
 */
public class NvidiaChatClient {

    private static final String TAG = "NvidiaChatClient";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int MAX_HISTORY = 12;   // keeps the request small and the cost down

    private static final String SYSTEM_PROMPT =
            "You are AgriBot, an assistant for farmers using the AgriCare app. "
                    + "Answer questions about crop diseases, pests, treatment and prevention. "
                    + "Be practical and concise: a short paragraph or a few short bullets. "
                    + "Prefer widely available treatments and name active ingredients where useful. "
                    + "If a question is not about farming, say so briefly and offer to help with crops.";

    public interface ResponseListener {
        void onReply(String reply);

        void onError(String message, boolean isNetworkFailure);
    }

    private final OkHttpClient client;
    private final Handler main = new Handler(Looper.getMainLooper());

    public NvidiaChatClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)   // generation can outlast a short read timeout
                .build();
    }

    /** True when the build carries a key; without one every call would 401. */
    public static boolean hasApiKey() {
        return !TextUtils.isEmpty(BuildConfig.NVIDIA_API_KEY);
    }

    /**
     * Sends the conversation and returns the assistant's next message.
     *
     * @param history  full transcript, oldest first, ending with the user's new message
     * @param language the language to answer in, e.g. "Hindi"
     */
    public void send(List<ChatMessage> history, String language, ResponseListener listener) {
        if (!hasApiKey()) {
            main.post(() -> listener.onError("No API key configured.", false));
            return;
        }

        final Request request;
        try {
            request = new Request.Builder()
                    .url(BuildConfig.NVIDIA_BASE_URL)
                    .header("Authorization", "Bearer " + BuildConfig.NVIDIA_API_KEY)
                    .header("Accept", "application/json")
                    .post(RequestBody.create(buildBody(history, language).toString(), JSON))
                    .build();
        } catch (JSONException e) {
            Log.e(TAG, "Could not build request", e);
            main.post(() -> listener.onError("Could not build the request.", false));
            return;
        }

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Request failed", e);
                main.post(() -> listener.onError("Could not reach AgriBot.", true));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (ResponseBody body = response.body()) {
                    String raw = body == null ? "" : body.string();
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "HTTP " + response.code() + ": " + raw);
                        final String message = describeHttpError(response.code());
                        main.post(() -> listener.onError(message, false));
                        return;
                    }
                    final String reply = parseReply(raw);
                    if (TextUtils.isEmpty(reply)) {
                        main.post(() -> listener.onError("AgriBot returned an empty answer.", false));
                    } else {
                        main.post(() -> listener.onReply(reply));
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Could not read response", e);
                    main.post(() -> listener.onError("Could not read the answer.", true));
                } catch (JSONException e) {
                    Log.e(TAG, "Could not parse response", e);
                    main.post(() -> listener.onError("AgriBot sent something unexpected.", false));
                }
            }
        });
    }

    private JSONObject buildBody(List<ChatMessage> history, String language) throws JSONException {
        JSONArray messages = new JSONArray();
        String system = SYSTEM_PROMPT + " Answer entirely in " + language
                + ", using the script that language is normally written in.";
        messages.put(new JSONObject().put("role", "system").put("content", system));

        int from = Math.max(0, history.size() - MAX_HISTORY);
        for (int i = from; i < history.size(); i++) {
            ChatMessage message = history.get(i);
            // JSONObject escapes the text, so quotes and newlines in a question are safe.
            messages.put(new JSONObject()
                    .put("role", message.apiRole())
                    .put("content", message.text()));
        }

        return new JSONObject()
                .put("model", BuildConfig.NVIDIA_MODEL)
                .put("messages", messages)
                .put("temperature", 0.4)
                .put("top_p", 0.9)
                .put("max_tokens", 700)
                .put("stream", false);
    }

    private String parseReply(String raw) throws JSONException {
        JSONArray choices = new JSONObject(raw).optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            return null;
        }
        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        if (message == null) {
            return null;
        }
        String content = message.optString("content", "");
        if (TextUtils.isEmpty(content.trim())) {
            // Some NIM models put everything in reasoning_content and leave content blank.
            content = message.optString("reasoning_content", "");
        }
        return content.trim();
    }

    private static String describeHttpError(int code) {
        switch (code) {
            case 401:
            case 403:
                return "AgriBot rejected the API key.";
            case 404:
                return "That model is not available for this account.";
            case 429:
                return "Too many requests just now. Try again in a moment.";
            default:
                return code >= 500
                        ? "AgriBot's service is having trouble. Try again shortly."
                        : "AgriBot could not answer (error " + code + ").";
        }
    }
}
