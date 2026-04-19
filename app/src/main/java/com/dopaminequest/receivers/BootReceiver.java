package com.dopaminequest.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.dopaminequest.services.PersistenceService;
import com.dopaminequest.utils.AppState;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "DQ_Boot";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Boot received: " + action);

        // Detect safe mode — if in safe mode, Accessibility Service won't run
        // Log it and kill streak as penalty
        boolean safeMode = intent.getBooleanExtra("android.intent.extra.SAFE_BOOT", false);
        if (safeMode) {
            Log.w(TAG, "Safe mode detected — penalizing streak");
            AppState.setStreak(ctx, 0); // reset streak as penalty
            // Access window is meaningless in safe mode — revoke it
            AppState.revokeAccessWindow(ctx);
        }

        // Start persistence service — this also schedules the watchdog alarm
        Intent service = new Intent(ctx, PersistenceService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            ctx.startForegroundService(service);
        } else {
            ctx.startService(service);
        }
    }
}
