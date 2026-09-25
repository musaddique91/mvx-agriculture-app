package com.mvx.agriculture.data;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.mvx.agriculture.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Daily mandi rates from the Agmarknet dataset on data.gov.in.
 *
 * The key is the farmer's own free one from data.gov.in; the shared demo key is
 * rate-limited to the point of being useless.
 */
public class MandiRepository {

    private static final String TAG = "MandiRepository";
    private static final String RESOURCE = "9ef84268-d588-465a-a308-a864a43d0070";
    /**
     * States as Agmarknet spells them ("Chattisgarh", "Uttrakhand"), which is
     * what the state filter must send. Only states that report rates are listed.
     */
    public static final List<String> STATES = Collections.unmodifiableList(Arrays.asList(
            "Andaman and Nicobar", "Andhra Pradesh", "Assam", "Bihar", "Chandigarh", "Chattisgarh",
            "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir", "Jharkhand",
            "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya",
            "Nagaland", "NCT of Delhi", "Odisha", "Pondicherry", "Punjab", "Rajasthan", "Tamil Nadu",
            "Telangana", "Tripura", "Uttar Pradesh", "Uttrakhand", "West Bengal"));

    /** data.gov.in's result window; anything larger is rejected outright. */
    private static final int MAX_ROWS = 10000;

    public interface Listener {
        void onPrices(List<MandiPrice> prices);

        void onError(boolean missingKey);
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    private final Handler main = new Handler(Looper.getMainLooper());

    public static boolean hasApiKey() {
        return !TextUtils.isEmpty(BuildConfig.DATA_GOV_API_KEY);
    }

    /**
     * Every rate reported today for one state.
     *
     * The screen filters by district, mandi, commodity and variety on the phone
     * (see {@link MandiFilter}), so one request serves every filter change.
     * data.gov.in refuses windows above 10,000 rows; the busiest state reports
     * a few thousand a day.
     *
     * @param state an Agmarknet state name, e.g. "Karnataka"
     */
    public void loadState(String state, Listener listener) {
        if (!hasApiKey()) {
            main.post(() -> listener.onError(true));
            return;
        }

        StringBuilder url = new StringBuilder("https://api.data.gov.in/resource/")
                .append(RESOURCE)
                .append("?api-key=").append(BuildConfig.DATA_GOV_API_KEY)
                .append("&format=json&limit=").append(MAX_ROWS);
        append(url, "state", state);

        client.newCall(new Request.Builder().url(url.toString()).build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Mandi request failed", e);
                main.post(() -> listener.onError(false));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        main.post(() -> listener.onError(false));
                        return;
                    }
                    List<MandiPrice> prices = parse(new JSONObject(body.string()));
                    main.post(() -> listener.onPrices(prices));
                } catch (IOException | JSONException e) {
                    Log.e(TAG, "Could not read mandi prices", e);
                    main.post(() -> listener.onError(false));
                }
            }
        });
    }

    private static void append(StringBuilder url, String field, String value) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        try {
            url.append("&filters[").append(field).append("]=")
                    .append(URLEncoder.encode(value, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 is always present; nothing sensible to do here
        }
    }

    private List<MandiPrice> parse(JSONObject root) {
        List<MandiPrice> prices = new ArrayList<>();
        JSONArray records = root.optJSONArray("records");
        if (records == null) {
            return prices;
        }
        for (int i = 0; i < records.length(); i++) {
            JSONObject row = records.optJSONObject(i);
            if (row == null) {
                continue;
            }
            MandiPrice price = new MandiPrice();
            price.commodity = row.optString("commodity").trim();
            price.variety = row.optString("variety").trim();
            price.market = row.optString("market").trim();
            price.district = row.optString("district").trim();
            price.date = row.optString("arrival_date");
            price.minPrice = asInt(row.optString("min_price"));
            price.maxPrice = asInt(row.optString("max_price"));
            price.modalPrice = asInt(row.optString("modal_price"));
            prices.add(price);
        }
        return prices;
    }

    private static int asInt(String raw) {
        try {
            return (int) Double.parseDouble(raw.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return 0;
        }
    }
}
