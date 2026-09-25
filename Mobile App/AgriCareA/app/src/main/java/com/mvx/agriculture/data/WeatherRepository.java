package com.mvx.agriculture.data;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.mvx.agriculture.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Open-Meteo forecasts. Free, keyless, and it carries the soil variables that make
 * the difference between a weather app and a farm advisory.
 */
public class WeatherRepository {

    private static final String TAG = "WeatherRepository";

    public interface Listener {
        void onWeather(Weather weather);

        void onError();
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    private final Handler main = new Handler(Looper.getMainLooper());

    public void load(double latitude, double longitude, Listener listener) {
        String url = String.format(Locale.US,
                "%s?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,relative_humidity_2m,wind_speed_10m,precipitation"
                        + "&hourly=precipitation,soil_temperature_0cm,soil_moisture_0_to_1cm"
                        + "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,"
                        + "precipitation_probability_max,wind_speed_10m_max"
                        + "&timezone=auto&forecast_days=7",
                BuildConfig.OPEN_METEO_URL, latitude, longitude);

        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Forecast request failed", e);
                main.post(listener::onError);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        main.post(listener::onError);
                        return;
                    }
                    Weather weather = parse(new JSONObject(body.string()));
                    main.post(() -> listener.onWeather(weather));
                } catch (IOException | JSONException e) {
                    Log.e(TAG, "Could not read the forecast", e);
                    main.post(listener::onError);
                }
            }
        });
    }

    private Weather parse(JSONObject root) throws JSONException {
        Weather weather = new Weather();

        JSONObject current = root.optJSONObject("current");
        if (current != null) {
            weather.nowC = current.optDouble("temperature_2m", Double.NaN);
            weather.humidity = current.optInt("relative_humidity_2m", 0);
            weather.windKph = current.optDouble("wind_speed_10m", 0);
        }

        JSONObject hourly = root.optJSONObject("hourly");
        if (hourly != null) {
            weather.soilTempC = firstFinite(hourly.optJSONArray("soil_temperature_0cm"));
            weather.soilMoisture = firstFinite(hourly.optJSONArray("soil_moisture_0_to_1cm"));
            // Open-Meteo returns the whole day from midnight, so sum from the current
            // hour rather than the start of the array.
            JSONArray rain = hourly.optJSONArray("precipitation");
            if (rain != null) {
                int startHour = java.util.Calendar.getInstance()
                        .get(java.util.Calendar.HOUR_OF_DAY);
                for (int i = startHour; i < Math.min(startHour + 6, rain.length()); i++) {
                    weather.rainNext6hMm += rain.optDouble(i, 0);
                }
            }
        }

        JSONObject daily = root.optJSONObject("daily");
        if (daily != null) {
            JSONArray dates = daily.optJSONArray("time");
            JSONArray max = daily.optJSONArray("temperature_2m_max");
            JSONArray min = daily.optJSONArray("temperature_2m_min");
            JSONArray rainSum = daily.optJSONArray("precipitation_sum");
            JSONArray chance = daily.optJSONArray("precipitation_probability_max");
            JSONArray wind = daily.optJSONArray("wind_speed_10m_max");

            int count = dates == null ? 0 : dates.length();
            for (int i = 0; i < count; i++) {
                Weather.Day day = new Weather.Day();
                day.date = dates.optString(i);
                day.maxC = max == null ? 0 : max.optDouble(i, 0);
                day.minC = min == null ? 0 : min.optDouble(i, 0);
                day.rainMm = rainSum == null ? 0 : rainSum.optDouble(i, 0);
                day.rainChance = chance == null ? 0 : chance.optInt(i, 0);
                day.windKph = wind == null ? 0 : wind.optDouble(i, 0);
                weather.days.add(day);
                weather.rainWeekMm += day.rainMm;
                if (i == 0) {
                    weather.rainTodayMm = day.rainMm;
                }
            }
        }
        return weather;
    }

    private static double firstFinite(JSONArray array) {
        if (array == null) {
            return 0;
        }
        for (int i = 0; i < array.length(); i++) {
            double value = array.optDouble(i, Double.NaN);
            if (!Double.isNaN(value)) {
                return value;
            }
        }
        return 0;
    }
}
