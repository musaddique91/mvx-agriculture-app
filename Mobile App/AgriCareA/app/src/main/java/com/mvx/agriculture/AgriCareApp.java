package com.mvx.agriculture;

import android.app.Application;

/** Applies the saved language before any screen inflates. */
public class AgriCareApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        LocaleManager.applySaved(this);
        CropTaskReminderWorker.schedule(this);
    }
}
