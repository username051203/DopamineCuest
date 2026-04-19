package com.dopaminequest.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.dopaminequest.activities.QuizPopupActivity;
import com.dopaminequest.utils.AppState;

public class QuizPopupReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!AppState.hasWrongQuestions(ctx)) return;
        Intent i = new Intent(ctx, QuizPopupActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        ctx.startActivity(i);
    }
}
