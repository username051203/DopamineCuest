package com.dopaminequest.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.dopaminequest.receivers.QuizPopupReceiver;

import java.util.Calendar;
import java.util.Random;

public class PopupScheduler {

    // Fires one popup at a random time between 8am and 10pm today
    public static void scheduleNext(Context ctx) {
        if (!AppState.hasWrongQuestions(ctx)) return;

        Calendar now  = Calendar.getInstance();
        Calendar from = (Calendar) now.clone();
        Calendar to   = (Calendar) now.clone();

        from.set(Calendar.HOUR_OF_DAY, 8);
        from.set(Calendar.MINUTE, 0);
        to.set(Calendar.HOUR_OF_DAY, 22);
        to.set(Calendar.MINUTE, 0);

        // Pick random time between now+15min and 10pm
        long earliest = Math.max(now.getTimeInMillis() + 15 * 60 * 1000L,
                                 from.getTimeInMillis());
        long latest   = to.getTimeInMillis();

        if (earliest >= latest) return; // too late in the day — skip to tomorrow
        // Schedule for tomorrow 8am–10pm instead
        if (earliest >= latest) {
            from.add(Calendar.DAY_OF_YEAR, 1);
            to.add(Calendar.DAY_OF_YEAR, 1);
            earliest = from.getTimeInMillis();
            latest   = to.getTimeInMillis();
        }

        long triggerAt = earliest + (long)(new Random().nextDouble() * (latest - earliest));

        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent i  = new Intent(ctx, QuizPopupReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 9001, i,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }
}
