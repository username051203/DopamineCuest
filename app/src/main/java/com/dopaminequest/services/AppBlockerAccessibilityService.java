package com.dopaminequest.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.PowerManager;
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

    /**
     * Intercept volume key combinations.
     * Vol UP + Vol DOWN within 400ms → turn screen off.
     * This discourages using hardware combos to bypass the overlay.
     */
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

        // Both volume keys within combo window → screen off
        if (Math.abs(lastVolUpTime - lastVolDownTime) < COMBO_WINDOW_MS
                && lastVolUpTime > 0 && lastVolDownTime > 0) {
            lastVolUpTime = 0;
            lastVolDownTime = 0;
            Log.d(TAG, "Volume combo detected — turning screen off");
            turnScreenOff();
            return true; // consume the event
        }

        return false;
    }

    private void turnScreenOff() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                // goToSleep requires DEVICE_POWER — granted via Device Admin
                pm.goToSleep(System.currentTimeMillis());
            }
        } catch (Exception e) {
            Log.e(TAG, "goToSleep failed: " + e.getMessage());
            // Fallback: lock via KeyguardManager
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            // No public lock API without admin — Device Admin handles this
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
        // Restart self via PersistenceService
        Intent i = new Intent(this, PersistenceService.class);
        startService(i);
    }
}
