package com.mvx.agriculture.data;

import android.content.Context;
import android.text.TextUtils;

import com.mvx.agriculture.LocaleManager;
import com.mvx.agriculture.chat.ChatMessage;
import com.mvx.agriculture.chat.NvidiaChatClient;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Asks the model what to grow on one specific field.
 *
 * The value is in the briefing: size, location, season, what the weather is
 * actually doing there this week, what was grown last, and what the scout found.
 * Without that the answer is a generic crop list.
 */
public class FieldAdvisor {

    public interface Listener {
        void onAdvice(String advice);

        void onError(String message);
    }

    private final Context context;

    public FieldAdvisor(Context context) {
        this.context = context.getApplicationContext();
    }

    public void recommend(Field field, String region, Weather weather,
                          List<ScoutingNote> notes, Listener listener) {
        if (!NvidiaChatClient.hasApiKey()) {
            listener.onError(context.getString(com.mvx.agriculture.R.string.chat_no_key));
            return;
        }

        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(new ChatMessage(ChatMessage.Role.USER, brief(field, region, weather, notes)));

        new NvidiaChatClient().send(conversation,
                LocaleManager.currentEnglishName(context),
                new NvidiaChatClient.ResponseListener() {
                    @Override
                    public void onReply(String reply) {
                        listener.onAdvice(reply);
                    }

                    @Override
                    public void onError(String message, boolean isNetworkFailure) {
                        listener.onError(message);
                    }
                });
    }

    private String brief(Field field, String region, Weather weather, List<ScoutingNote> notes) {
        StringBuilder brief = new StringBuilder();
        brief.append("Recommend what to grow on this specific field. Details:\n");
        brief.append("- Area: ").append(String.format(Locale.US, "%.2f", field.areaAcres))
                .append(" acres\n");
        if (!TextUtils.isEmpty(region)) {
            brief.append("- Region: ").append(region).append(", India\n");
        }
        Field.Point centre = field.centroid();
        if (centre != null) {
            brief.append(String.format(Locale.US,
                    "- Location: %.4f, %.4f\n", centre.lat, centre.lon));
        }
        brief.append("- Month: ").append(monthName()).append('\n');
        if (!TextUtils.isEmpty(field.crop)) {
            brief.append("- Currently or last grown: ").append(field.crop)
                    .append(". Suggest a rotation that does not repeat the same family.\n");
        }
        if (weather != null) {
            brief.append(String.format(Locale.US,
                    "- Weather now: %.0f C, humidity %d%%, wind %.0f km/h. "
                            + "Rain expected this week: %.0f mm. "
                            + "Topsoil moisture: %.2f m3/m3. Soil temperature: %.0f C.\n",
                    weather.nowC, weather.humidity, weather.windKph,
                    weather.rainWeekMm, weather.soilMoisture, weather.soilTempC));
        }
        if (notes != null && !notes.isEmpty()) {
            brief.append("- Recent scouting observations on this field:\n");
            int limit = Math.min(notes.size(), 6);
            for (int i = 0; i < limit; i++) {
                ScoutingNote note = notes.get(i);
                brief.append("  * ").append(note.category.name().toLowerCase(Locale.US));
                if (!TextUtils.isEmpty(note.text)) {
                    brief.append(": ").append(note.text);
                }
                brief.append('\n');
            }
        }

        brief.append("\nAnswer in this shape:\n")
                .append("1. Two or three crops worth sowing now, best first, one line each on why "
                        + "this field and this season suit them.\n")
                .append("2. For the top pick: seed rate and fertiliser dose for the area above, "
                        + "in kilograms for the whole field, not per hectare.\n")
                .append("3. One risk to watch here, based on the weather and any scouting notes.\n")
                .append("Keep the whole answer under 220 words and practical. "
                        + "Use Indian crop names and rupees.");
        return brief.toString();
    }

    private String monthName() {
        return new java.text.SimpleDateFormat("MMMM", Locale.US)
                .format(Calendar.getInstance().getTime());
    }
}
