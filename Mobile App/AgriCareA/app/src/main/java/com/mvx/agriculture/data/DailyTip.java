package com.mvx.agriculture.data;

import android.content.Context;
import android.text.TextUtils;

import com.mvx.agriculture.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * The tip shown on Home, rebuilt every day from what is actually true today.
 *
 * The old version cycled a fixed list of crop tips, so it said the same thing
 * every year on the same date regardless of the weather. This one reads the
 * forecast, the season, the farmer's own fields and their scouting notes, and
 * rotates through subjects so consecutive days do not repeat a theme.
 */
public class DailyTip {

    /** Subjects rotate by day so the card does not harp on one thing. */
    public enum Topic {
        WEATHER(R.string.tip_topic_weather, R.drawable.ic_weather, R.color.accent_weather),
        SPRAY(R.string.tip_topic_spray, R.drawable.ic_spray, R.color.accent_market),
        SOIL(R.string.tip_topic_soil, R.drawable.ic_soil, R.color.accent_scan),
        CROP(R.string.tip_topic_crop, R.drawable.ic_crop, R.color.accent_calendar),
        DISEASE(R.string.tip_topic_disease, R.drawable.ic_disease, R.color.md_error),
        MARKET(R.string.tip_topic_market, R.drawable.ic_rupee, R.color.accent_market),
        SCHEME(R.string.tip_topic_scheme, R.drawable.ic_scheme, R.color.accent_scheme);

        public final int labelRes;
        public final int iconRes;
        public final int colorRes;

        Topic(int labelRes, int iconRes, int colorRes) {
            this.labelRes = labelRes;
            this.iconRes = iconRes;
            this.colorRes = colorRes;
        }
    }

    public final Topic topic;
    public final String text;

    private DailyTip(Topic topic, String text) {
        this.topic = topic;
        this.text = text;
    }

    /**
     * Builds today's tip.
     *
     * @param weather this morning's forecast, or null when it could not be loaded
     * @param fields  the farmer's saved plots, may be empty
     */
    public static DailyTip forToday(Context context, Weather weather, List<Field> fields) {
        Calendar today = Calendar.getInstance();
        int dayOfYear = today.get(Calendar.DAY_OF_YEAR);

        // Collect every tip that genuinely applies right now, then pick by day so
        // the choice is stable within a day and moves on tomorrow.
        List<DailyTip> candidates = new ArrayList<>();

        if (weather != null) {
            addWeatherTips(context, weather, candidates);
        }
        addCropTips(context, fields, candidates, dayOfYear);
        addMarketTip(context, fields, candidates);
        addSchemeTip(context, candidates, dayOfYear);

        if (candidates.isEmpty()) {
            return fallback(context, dayOfYear);
        }

        // Urgent weather warnings jump the queue; otherwise rotate.
        for (DailyTip candidate : candidates) {
            if (candidate.topic == Topic.SPRAY || candidate.topic == Topic.DISEASE) {
                if (isUrgent(weather, candidate.topic)) {
                    return candidate;
                }
            }
        }
        return candidates.get(dayOfYear % candidates.size());
    }

    private static boolean isUrgent(Weather weather, Topic topic) {
        if (weather == null) {
            return false;
        }
        if (topic == Topic.SPRAY) {
            return weather.rainComing();          // spraying now would be wasted
        }
        return weather.humidity >= 85 && weather.nowC >= 18 && weather.nowC <= 30;
    }

    private static void addWeatherTips(Context context, Weather weather, List<DailyTip> out) {
        if (weather.rainComing()) {
            out.add(new DailyTip(Topic.SPRAY, context.getString(R.string.tip_rain_soon)));
        } else if (weather.tooWindyToSpray()) {
            out.add(new DailyTip(Topic.SPRAY,
                    context.getString(R.string.tip_windy, weather.windKph)));
        } else {
            out.add(new DailyTip(Topic.SPRAY, context.getString(R.string.tip_spray_now)));
        }

        if (weather.needsIrrigation()) {
            out.add(new DailyTip(Topic.SOIL,
                    context.getString(R.string.tip_dry_soil, weather.soilMoisture * 100)));
        } else if (weather.rainWeekMm >= 25) {
            out.add(new DailyTip(Topic.SOIL,
                    context.getString(R.string.tip_wet_week, weather.rainWeekMm)));
        }

        if (weather.heatStress()) {
            out.add(new DailyTip(Topic.WEATHER, context.getString(R.string.tip_heat)));
        }

        // Warm and humid is what fungal disease waits for; this is the single most
        // useful thing to say on such a day.
        if (weather.humidity >= 85 && weather.nowC >= 18 && weather.nowC <= 30) {
            out.add(new DailyTip(Topic.DISEASE,
                    context.getString(R.string.tip_fungal_risk, weather.humidity)));
        }

        if (weather.soilTempC > 0 && weather.soilTempC < 15) {
            out.add(new DailyTip(Topic.SOIL,
                    context.getString(R.string.tip_cold_soil, weather.soilTempC)));
        }
    }

    private static void addCropTips(Context context, List<Field> fields,
                                    List<DailyTip> out, int dayOfYear) {
        CropData cropData = new CropData(context);

        // Anything the farmer actually grows comes first.
        for (Field field : fields) {
            CropData.Crop crop = cropData.byName(field.crop);
            if (crop != null) {
                out.add(new DailyTip(Topic.CROP, context.getString(
                        R.string.tip_your_crop, field.name, crop.name, crop.tip)));
            }
        }

        // Then whatever is in season, so the card still teaches something new.
        List<CropData.Crop> inSeason = new ArrayList<>();
        for (CropData.Crop crop : cropData.all()) {
            if (crop.inSeasonNow()) {
                inSeason.add(crop);
            }
        }
        if (!inSeason.isEmpty()) {
            CropData.Crop crop = inSeason.get(dayOfYear % inSeason.size());
            out.add(new DailyTip(Topic.CROP, context.getString(
                    R.string.tip_in_season, crop.name, crop.sow, crop.tip)));
        }
    }

    private static void addMarketTip(Context context, List<Field> fields, List<DailyTip> out) {
        for (Field field : fields) {
            if (!TextUtils.isEmpty(field.crop)) {
                out.add(new DailyTip(Topic.MARKET,
                        context.getString(R.string.tip_market_crop, field.crop)));
                return;
            }
        }
        out.add(new DailyTip(Topic.MARKET, context.getString(R.string.tip_market_general)));
    }

    private static void addSchemeTip(Context context, List<DailyTip> out, int dayOfYear) {
        List<SchemeData.Scheme> schemes = new SchemeData(context).all();
        if (schemes.isEmpty()) {
            return;
        }
        SchemeData.Scheme scheme = schemes.get(dayOfYear % schemes.size());
        out.add(new DailyTip(Topic.SCHEME, context.getString(
                R.string.tip_scheme, scheme.name, scheme.summary)));
    }

    /** No forecast and no fields yet: still say something useful. */
    private static DailyTip fallback(Context context, int dayOfYear) {
        List<CropData.Crop> crops = new CropData(context).all();
        if (crops.isEmpty()) {
            return new DailyTip(Topic.CROP, context.getString(R.string.app_tagline));
        }
        CropData.Crop crop = crops.get(dayOfYear % crops.size());
        return new DailyTip(Topic.CROP,
                String.format(Locale.getDefault(), "%s: %s", crop.name, crop.tip));
    }
}
