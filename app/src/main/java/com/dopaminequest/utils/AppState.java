package com.dopaminequest.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class AppState {

    private static final String PREFS = "dq_prefs";
    public static final int GATE_TASKS_REQUIRED = 3;
    public static final int STUDY_PAGES_REQUIRED = 6;
    public static final int QUIZ_QUESTIONS = 10;
    public static final int QUIZ_MAX_ATTEMPTS = 3;

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ── Onboarding ────────────────────────────────────────────────────────────
    public static boolean isOnboarded(Context ctx) { return prefs(ctx).getBoolean("onboarded", false); }
    public static void setOnboarded(Context ctx, boolean v) { prefs(ctx).edit().putBoolean("onboarded", v).apply(); }

    // ── Gemini API key ────────────────────────────────────────────────────────
    public static String getGeminiKey(Context ctx) { return prefs(ctx).getString("gemini_key", ""); }
    public static void setGeminiKey(Context ctx, String key) { prefs(ctx).edit().putString("gemini_key", key).apply(); }
    public static boolean hasGeminiKey(Context ctx) { return !getGeminiKey(ctx).isEmpty(); }

    // ── Allowlist ─────────────────────────────────────────────────────────────
    public static Set<String> getAllowlist(Context ctx) {
        Set<String> saved = prefs(ctx).getStringSet("allowlist", null);
        Set<String> result = new HashSet<>(defaultSystemApps());
        if (saved != null) result.addAll(saved);
        return result;
    }
    public static void setAllowlist(Context ctx, Set<String> packages) {
        Set<String> merged = new HashSet<>(packages);
        merged.addAll(defaultSystemApps());
        prefs(ctx).edit().putStringSet("allowlist", merged).apply();
    }
    public static boolean isAllowed(Context ctx, String pkg) {
        if (pkg == null) return true;
        if (pkg.equals("com.dopaminequest")) return true;
        if (pkg.startsWith("com.android.systemui")) return true;
        return getAllowlist(ctx).contains(pkg);
    }
    private static Set<String> defaultSystemApps() {
        Set<String> s = new HashSet<>();
        s.add("com.dopaminequest");
        s.add("com.android.phone");
        s.add("com.android.dialer");
        s.add("com.google.android.dialer");
        s.add("com.android.contacts");
        s.add("com.android.mms");
        s.add("com.google.android.apps.messaging");
        s.add("com.android.settings");
        s.add("com.android.camera2");
        s.add("com.google.android.apps.camera");
        s.add("com.android.deskclock");
        s.add("com.google.android.deskclock");
        s.add("com.android.systemui");
        s.add("com.android.launcher3");
        s.add("com.google.android.apps.nexuslauncher");
        s.add("com.miui.home");
        s.add("com.sec.android.app.launcher");
        s.add("com.huawei.android.launcher");
        s.add("com.oppo.launcher");
        s.add("net.oneplus.launcher");
        s.add("com.android.emergency");
        s.add("com.google.android.gms");
        s.add("com.android.vending");
        return s;
    }

    // ── XP + Level ────────────────────────────────────────────────────────────
    public static int getXp(Context ctx) { return prefs(ctx).getInt("xp", 0); }
    public static void addXp(Context ctx, int amount) { prefs(ctx).edit().putInt("xp", getXp(ctx) + amount).apply(); }
    public static int getLevel(Context ctx) { return Math.max(1, (int) Math.floor(Math.sqrt(getXp(ctx) / 100.0)) + 1); }

    // ── Streak ────────────────────────────────────────────────────────────────
    public static int getStreak(Context ctx) { return prefs(ctx).getInt("streak", 0); }
    public static void setStreak(Context ctx, int v) { prefs(ctx).edit().putInt("streak", v).apply(); }

    // ── Tasks today ───────────────────────────────────────────────────────────
    public static int getTasksDoneToday(Context ctx) { return prefs(ctx).getInt("tasks_done_today", 0); }
    public static void incrementTasksDoneToday(Context ctx) { prefs(ctx).edit().putInt("tasks_done_today", getTasksDoneToday(ctx) + 1).apply(); }
    public static void resetTasksDoneToday(Context ctx) { prefs(ctx).edit().putInt("tasks_done_today", 0).apply(); }

    // ── Completed task IDs ────────────────────────────────────────────────────
    public static Set<String> getCompletedIds(Context ctx) {
        return new HashSet<>(prefs(ctx).getStringSet("completed_ids", new HashSet<>()));
    }
    public static void markTaskCompleted(Context ctx, int taskId) {
        Set<String> ids = getCompletedIds(ctx);
        ids.add(String.valueOf(taskId));
        prefs(ctx).edit().putStringSet("completed_ids", ids).apply();
    }
    public static boolean isTaskCompleted(Context ctx, int taskId) {
        return getCompletedIds(ctx).contains(String.valueOf(taskId));
    }
    public static void resetCompletedIds(Context ctx) {
        prefs(ctx).edit().putStringSet("completed_ids", new HashSet<>()).apply();
    }

    // ── Uninstall gate ────────────────────────────────────────────────────────
    public static boolean isGateActive(Context ctx) { return prefs(ctx).getBoolean("gate_active", false); }
    public static void setGateActive(Context ctx, boolean v) {
        SharedPreferences.Editor ed = prefs(ctx).edit().putBoolean("gate_active", v);
        if (v) ed.putInt("gate_tasks_done", 0);
        ed.apply();
    }
    public static int getGateTasksDone(Context ctx) { return prefs(ctx).getInt("gate_tasks_done", 0); }
    public static boolean incrementGateTask(Context ctx) {
        int done = getGateTasksDone(ctx) + 1;
        prefs(ctx).edit().putInt("gate_tasks_done", done).apply();
        if (done >= GATE_TASKS_REQUIRED) { prefs(ctx).edit().putBoolean("gate_active", false).apply(); return true; }
        return false;
    }

    // ── Access window ─────────────────────────────────────────────────────────
    public static boolean hasActiveAccessWindow(Context ctx) { return System.currentTimeMillis() < prefs(ctx).getLong("access_until", 0); }
    public static long getAccessWindowEnd(Context ctx) { return prefs(ctx).getLong("access_until", 0); }
    public static void grantAccessWindow(Context ctx, int minutes) { prefs(ctx).edit().putLong("access_until", System.currentTimeMillis() + (long) minutes * 60000).apply(); }
    public static void revokeAccessWindow(Context ctx) { prefs(ctx).edit().putLong("access_until", 0).apply(); }

    // ── Shield ────────────────────────────────────────────────────────────────
    public static boolean isShieldEnabled(Context ctx) { return prefs(ctx).getBoolean("shield_enabled", true); }
    public static void setShieldEnabled(Context ctx, boolean v) { prefs(ctx).edit().putBoolean("shield_enabled", v).apply(); }

    // ── Wrong answer queue (stored as pipe-separated JSON strings) ────────────
    public static void enqueueWrongQuestion(Context ctx, String questionJson) {
        String existing = prefs(ctx).getString("wrong_queue", "");
        String updated  = existing.isEmpty() ? questionJson : existing + "|||" + questionJson;
        prefs(ctx).edit().putString("wrong_queue", updated).apply();
    }
    public static String dequeueWrongQuestion(Context ctx) {
        String existing = prefs(ctx).getString("wrong_queue", "");
        if (existing.isEmpty()) return null;
        int sep = existing.indexOf("|||");
        if (sep == -1) {
            prefs(ctx).edit().putString("wrong_queue", "").apply();
            return existing;
        }
        String first = existing.substring(0, sep);
        String rest  = existing.substring(sep + 3);
        prefs(ctx).edit().putString("wrong_queue", rest).apply();
        return first;
    }
    public static boolean hasWrongQuestions(Context ctx) {
        return !prefs(ctx).getString("wrong_queue", "").isEmpty();
    }
    public static int wrongQueueSize(Context ctx) {
        String q = prefs(ctx).getString("wrong_queue", "");
        if (q.isEmpty()) return 0;
        return q.split("\\|\\|\\|").length;
    }
}
