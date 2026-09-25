package com.mvx.agriculture;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mvx.agriculture.data.CropJourneyPlan;
import com.mvx.agriculture.data.EpochDays;
import com.mvx.agriculture.data.Field;
import com.mvx.agriculture.data.FieldRepository;
import com.mvx.agriculture.data.JourneyTemplate;
import com.mvx.agriculture.data.JourneyTemplates;
import com.mvx.agriculture.data.Season;
import com.mvx.agriculture.data.SeasonRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Once a day, tells the farmer about crop tasks that have just come due.
 *
 * Runs entirely on the phone: the schedule is in the app and the dates are the
 * farmer's own, so reminders work without a signal or a server. Each task is
 * announced once; ticking it off or skipping it in the app stops it for good.
 */
public class CropTaskReminderWorker extends Worker {

    private static final String UNIQUE_NAME = "crop-task-reminders";
    private static final String CHANNEL_ID = "crop_tasks";
    private static final String PREFS = "journey_reminders";
    private static final String KEY_NOTIFIED = "notified";

    public CropTaskReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Safe to call on every launch: an existing daily schedule is kept, not restarted. */
    public static void schedule(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                CropTaskReminderWorker.class, 1, TimeUnit.DAYS).build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    /** Checks straight away, e.g. just after a season starts with a task already due. */
    public static void runNow(Context context) {
        WorkManager.getInstance(context.getApplicationContext())
                .enqueue(new OneTimeWorkRequest.Builder(CropTaskReminderWorker.class).build());
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        NotificationManagerCompat notifications = NotificationManagerCompat.from(context);
        if (!notifications.areNotificationsEnabled()) {
            // Not marked as told: once notifications are allowed, these still arrive.
            return Result.success();
        }
        createChannel(context);

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> notified = new HashSet<>(prefs.getStringSet(KEY_NOTIFIED, new HashSet<>()));
        long today = EpochDays.today();
        SeasonRepository seasons = new SeasonRepository(context);
        FieldRepository fields = new FieldRepository(context);

        for (Season season : seasons.allActive()) {
            JourneyTemplate template = JourneyTemplates.forCrop(context, season.crop);
            Field field = fields.byId(season.fieldId);
            if (template == null || field == null) {
                continue;
            }
            List<CropJourneyPlan.Item> fresh = new ArrayList<>();
            for (CropJourneyPlan.Item item : CropJourneyPlan.plan(template, season.method, season.seedlingAge,
                    season.plantedDay, season.trackingStartDay, today, seasons.records(season.id))) {
                boolean due = item.status == CropJourneyPlan.Status.DUE || item.status == CropJourneyPlan.Status.OVERDUE;
                if (due && !notified.contains(key(season, item))) {
                    fresh.add(item);
                }
            }
            if (fresh.isEmpty()) {
                continue;
            }
            String text = fresh.size() == 1
                    ? fresh.get(0).task.title
                    : context.getString(R.string.journey_notify_many, fresh.size(), fresh.get(0).task.title);
            notify(context, notifications, season, field, text);
            for (CropJourneyPlan.Item item : fresh) {
                notified.add(key(season, item));
            }
        }
        prefs.edit().putStringSet(KEY_NOTIFIED, notified).apply();
        return Result.success();
    }

    private static String key(Season season, CropJourneyPlan.Item item) {
        return season.id + ":" + item.task.id;
    }

    @SuppressWarnings("MissingPermission")   // checked via areNotificationsEnabled() in doWork
    private static void notify(Context context, NotificationManagerCompat notifications, Season season,
                               Field field, String text) {
        Intent open = new Intent(context, MainShellActivity.class)
                .putExtra(MainShellActivity.EXTRA_JOURNEY_FIELD_ID, field.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(context, (int) season.id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        notifications.notify((int) season.id, new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_crop)
                .setContentTitle(field.name + " · " + season.crop)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build());
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                    context.getString(R.string.journey_notify_channel), NotificationManager.IMPORTANCE_DEFAULT));
        }
    }
}
