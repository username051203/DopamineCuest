package com.dopaminequest.receivers;

import android.app.admin.DeviceAdminReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.dopaminequest.activities.UninstallGateActivity;
import com.dopaminequest.utils.AppState;

public class DQDeviceAdminReceiver extends DeviceAdminReceiver {

    private static final String TAG = "DQ_DeviceAdmin";

    public static ComponentName getComponentName(Context ctx) {
        return new ComponentName(ctx, DQDeviceAdminReceiver.class);
    }

    /**
     * Called when user tries to deactivate Device Admin (first step to uninstall).
     * We return a warning message. Android shows it in the deactivation dialog.
     * Then we activate the gate so overlay intercepts immediately after.
     */
    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        Log.w(TAG, "Device admin disable requested — activating gate");
        AppState.setGateActive(context, true);

        // Launch gate activity over the system deactivation screen
        Intent gateIntent = new Intent(context, UninstallGateActivity.class);
        gateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(gateIntent);

        return "Complete 3 tasks to remove DopamineQuest protection.";
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        // Admin was actually disabled — if gate not passed, re-request
        Log.w(TAG, "Device admin disabled");
        if (AppState.isGateActive(context)) {
            Toast.makeText(context, "Complete your tasks first.", Toast.LENGTH_LONG).show();
            // Re-request admin activation
            Intent reRequest = new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            reRequest.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            reRequest.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    getComponentName(context));
            reRequest.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "DopamineQuest needs admin access to protect your focus.");
            context.startActivity(reRequest);
        }
    }

    @Override
    public void onEnabled(Context context, Intent intent) {
        Log.d(TAG, "Device admin enabled");
    }
}
