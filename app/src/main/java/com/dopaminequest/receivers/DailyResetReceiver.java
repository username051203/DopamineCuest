package com.dopaminequest.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.dopaminequest.utils.AppState;

import java.util.Calendar;

public class DailyResetReceiver extends BroadcastReceiver {

    private static final String TAG = "DQ_DailyReset";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        Log.d(TAG, "Daily reset fired");
        AppState.performDailyResetIfNeeded(ctx);
        scheduleNext(ctx); // reschedule for tomorrow
    }

    public static void scheduleNext(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        // Fire at next midnight
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 5);
        midnight.set(Calendar.MILLISECOND, 0);
        midnight.add(Calendar.DAY_OF_YEAR, 1); // always tomorrow

        Intent i = new Intent(ctx, DailyResetReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 8888, i,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, midnight.getTimeInMillis(), pi);
        } else {
            am.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, midnight.getTimeInMillis(), pi);
        }
        Log.d(TAG, "Next reset scheduled for midnight");
    }
}
