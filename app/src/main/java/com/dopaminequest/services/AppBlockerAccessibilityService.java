package com.dopaminequest.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import com.dopaminequest.activities.BlockOverlayActivity;
import com.dopaminequest.utils.AppState;

public class AppBlockerAccessibilityService extends AccessibilityService {

    private static final String TAG = "DQ_Blocker";
    private static AppBlockerAccessibilityService sInstance;

    private long lastVolUpTime   = 0;
    private long lastVolDownTime = 0;
    private static final long COMBO_WINDOW_MS = 400;

    public static boolean isRunning() { return sInstance != null; }

    @Override
    public void onServiceConnected() {
        sInstance = this;
        Log.d(TAG, "Accessibility service connected");

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                        | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                   | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 50;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (!AppState.isShieldEnabled(this)) return;
        if (AppState.hasActiveAccessWindow(this)) return;

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
         && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;

        CharSequence pkgSeq = event.getPackageName();
        if (pkgSeq == null) return;
        String pkg = pkgSeq.toString();

        if (!AppState.isAllowed(this, pkg)) {
            Log.d(TAG, "Blocked: " + pkg);
            launchOverlay(pkg);
        }
    }

    private void launchOverlay(String blockedPkg) {
        Intent i = new Intent(this, BlockOverlayActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                 | Intent.FLAG_ACTIVITY_CLEAR_TOP
                 | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra("blocked_pkg", blockedPkg);
        startActivity(i);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (!AppState.isShieldEnabled(this)) return false;
        if (AppState.hasActiveAccessWindow(this)) return false;

        int code = event.getKeyCode();
        long now = System.currentTimeMillis();

        if (code == KeyEvent.KEYCODE_VOLUME_UP) {
            lastVolUpTime = now;
        } else if (code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            lastVolDownTime = now;
        }

        if (Math.abs(lastVolUpTime - lastVolDownTime) < COMBO_WINDOW_MS
                && lastVolUpTime > 0 && lastVolDownTime > 0) {
            lastVolUpTime = 0;
            lastVolDownTime = 0;
            Log.d(TAG, "Volume combo detected — locking screen");
            turnScreenOff();
            return true;
        }

        return false;
    }

    private void turnScreenOff() {
        try {
            DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            ComponentName admin =
                com.dopaminequest.receivers.DQDeviceAdminReceiver.getComponentName(this);
            if (dpm != null && dpm.isAdminActive(admin)) {
                dpm.lockNow();
            }
        } catch (Exception e) {
            Log.e(TAG, "lockNow failed: " + e.getMessage());
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted");
        sInstance = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sInstance = null;
        Intent i = new Intent(this, PersistenceService.class);
        startService(i);
    }
}
