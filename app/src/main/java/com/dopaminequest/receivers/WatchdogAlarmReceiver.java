package com.dopaminequest.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.dopaminequest.services.AppBlockerAccessibilityService;
import com.dopaminequest.services.PersistenceService;

public class WatchdogAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "DQ_Watchdog";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        Log.d(TAG, "Watchdog tick");

        // Restart persistence service (idempotent — START_STICKY keeps it alive anyway)
        Intent svc = new Intent(ctx, PersistenceService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            ctx.startForegroundService(svc);
        } else {
            ctx.startService(svc);
        }

        // Check if accessibility service is alive
        if (!AppBlockerAccessibilityService.isRunning()) {
            Log.w(TAG, "Accessibility service not running — user must re-enable");
            // We can't programmatically re-enable it (Android security restriction)
            // But we can notify the user
            // NotificationHelper.showAccessibilityDeadNotification(ctx);
        }
    }
}
