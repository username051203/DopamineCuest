package com.dopaminequest.services;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.dopaminequest.DopamineApp;
import com.dopaminequest.R;
import com.dopaminequest.activities.MainActivity;
import com.dopaminequest.receivers.WatchdogAlarmReceiver;

public class PersistenceService extends Service {

    private static final String TAG        = "DQ_Persistence";
    private static final int    NOTIF_ID   = 1001;
    private static final long   WATCHDOG_INTERVAL = 10 * 60 * 1000L; // 10 min

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification());
        scheduleWatchdog();
        Log.d(TAG, "PersistenceService started");
        return START_STICKY; // system restarts if killed
    }

    private Notification buildNotification() {
        Intent tapIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, tapIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, DopamineApp.CHANNEL_SHIELD)
                .setContentTitle("Shield is active")
                .setContentText("DopamineQuest is protecting your focus")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pi)
                .build();
    }

    /**
     * Schedule a repeating AlarmManager alarm that checks service health.
     * setExactAndAllowWhileIdle fires even in Doze mode.
     */
    private void scheduleWatchdog() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (am == null) return;

        Intent i = new Intent(this, WatchdogAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        long trigger = SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // App swiped from recents — restart immediately
        Intent restart = new Intent(this, PersistenceService.class);
        startService(restart);
    }
}
