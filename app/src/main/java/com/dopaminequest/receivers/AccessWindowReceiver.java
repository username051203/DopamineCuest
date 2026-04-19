package com.dopaminequest.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.dopaminequest.utils.AppState;

public class AccessWindowReceiver extends BroadcastReceiver {

    private static final String TAG = "DQ_AccessWindow";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        Log.d(TAG, "Access window expired");
        AppState.revokeAccessWindow(ctx);
    }
}
