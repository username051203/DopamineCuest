package com.dopaminequest;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class DopamineApp extends Application {

    public static final String CHANNEL_SHIELD   = "dq_shield";
    public static final String CHANNEL_WATCHDOG = "dq_watchdog";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);

            NotificationChannel shield = new NotificationChannel(
                CHANNEL_SHIELD,
                "Shield Active",
                NotificationManager.IMPORTANCE_LOW
            );
            shield.setDescription("DopamineQuest is protecting your focus");
            shield.setShowBadge(false);
            nm.createNotificationChannel(shield);

            NotificationChannel watchdog = new NotificationChannel(
                CHANNEL_WATCHDOG,
                "Watchdog",
                NotificationManager.IMPORTANCE_MIN
            );
            watchdog.setShowBadge(false);
            nm.createNotificationChannel(watchdog);
        }
    }
}
