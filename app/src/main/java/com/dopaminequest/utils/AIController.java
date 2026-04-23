package com.dopaminequest.utils;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * AIController — single brain for all access decisions.
 *
 * Replaces CoachEngine + ad-hoc grant logic.
 * One structured Groq call with full context → JSON decision.
 *
 * Decision format returned by Groq:
 * {
 *   "action":  "GRANT" | "DENY" | "TASK_FIRST",
 *   "minutes": 0-30,
 *   "message": "shown to user"
 * }
 */
public class AIController {

    private static final String TAG = "DQ_AI";

    public static class Decision {
        public final String action;   // "GRANT", "DENY", "TASK_FIRST"
        public final int    minutes;
        public final String message;

        public Decision(String action, int minutes, String message) {
            this.action  = action;
            this.minutes = minutes;
            this.message = message;
        }

        public boolean isGrant()     { return "GRANT".equals(action); }
        public boolean isDeny()      { return "DENY".equals(action); }
        public boolean isTaskFirst() { return "TASK_FIRST".equals(action); }
    }

    public interface Callback {
        void onDecision(Decision decision);
        void onError(String error);
    }

    // ── Main entry point ──────────────────────────────────────────────────────
    // Called by CoachChatActivity when user submits a reason.

    public static void decide(Context ctx,
                               String blockedPkg,
                               String appLabel,
                               String userReason,
                               Callback cb) {
        new Thread(() -> {
            try {
                String prompt = buildPrompt(ctx, appLabel, userReason);
                String key    = AppState.getGeminiKey(ctx);

                if (key.isEmpty()) {
                    // No key — fallback to local rules
                    cb.onDecision(localFallback(userReason, appLabel));
                    return;
                }

                JSONObject body = new JSONObject()
                    .put("model", "llama-3.1-8b-instant")
                    .put("max_tokens", 150)
                    .put("temperature", 0.3)
                    .put("messages", new JSONArray()
                        .put(new JSONObject()
                            .put("role", "system")
                            .put("content",
                                "You are a strict focus coach. " +
                                "Respond ONLY with a JSON object. No markdown, no explanation. " +
                                "Format: {\"action\":\"GRANT\"|\"DENY\"|\"TASK_FIRST\",\"minutes\":0,\"message\":\"...\"}. " +
                                "GRANT only for genuine necessity (work, study, emergency, navigation). " +
                                "DENY for boredom, vague reasons, social media cravings. " +
                                "TASK_FIRST if user hasn't done enough tasks today. " +
                                "minutes = 0 for DENY/TASK_FIRST, 5-30 for GRANT."))
                        .put(new JSONObject()
                            .put("role", "user")
                            .put("content", prompt)));

                String[] r = GeminiService.postRawPublic(
                    "https://api.groq.com/openai/v1/chat/completions",
                    key.trim(), body.toString());

                int code = Integer.parseInt(r[0]);
                if (code == 429) {
                    cb.onDecision(localFallback(userReason, appLabel));
                    return;
                }
                if (code != 200) {
                    cb.onError("HTTP " + code);
                    return;
                }

                String raw = new JSONObject(r[1])
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

                JSONObject resp = new JSONObject(raw);
                cb.onDecision(new Decision(
                    resp.getString("action"),
                    resp.optInt("minutes", 0),
                    resp.getString("message")));

            } catch (Exception e) {
                Log.e(TAG, "decide: " + e.getMessage());
                cb.onDecision(localFallback(userReason, appLabel));
            }
        }).start();
    }

    // ── Build rich context prompt ─────────────────────────────────────────────

    private static String buildPrompt(Context ctx, String appLabel, String userReason) {
        int tasksDone  = AppState.getTasksDoneToday(ctx);
        int streak     = AppState.getStreak(ctx);
        int xp         = AppState.getXp(ctx);
        String timeCtx = getTimeContext();
        String usage   = getUsageSummary(ctx);

        return "Context:\n" +
               "- Time: " + timeCtx + "\n" +
               "- Tasks completed today: " + tasksDone + "\n" +
               "- Current streak: " + streak + " days\n" +
               "- XP: " + xp + "\n" +
               (usage.isEmpty() ? "" : "- Recent phone usage: " + usage + "\n") +
               "\nUser wants access to: " + appLabel + "\n" +
               "User's reason: \"" + userReason + "\"\n\n" +
               "Should they get access? Reply with JSON decision only.";
    }

    // ── Usage stats summary (last 2 hours) ────────────────────────────────────

    private static String getUsageSummary(Context ctx) {
        try {
            UsageStatsManager usm = (UsageStatsManager)
                ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return "";

            long now   = System.currentTimeMillis();
            long start = now - 2 * 60 * 60 * 1000; // last 2 hours
            Map<String, UsageStats> statsMap =
                usm.queryAndAggregateUsageStats(start, now);

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, UsageStats> e : statsMap.entrySet()) {
                long ms = e.getValue().getTotalTimeInForeground();
                if (ms < 60_000) continue; // skip < 1 min
                String pkg = e.getKey();
                if (pkg.startsWith("com.android") || pkg.startsWith("com.dopaminequest")) continue;
                long mins = ms / 60_000;
                // Use last segment of package name as readable label
                String label = pkg.substring(pkg.lastIndexOf('.') + 1);
                sb.append(label).append(":").append(mins).append("m ");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    // ── Time context ──────────────────────────────────────────────────────────

    private static String getTimeContext() {
        Calendar c    = Calendar.getInstance();
        int hour      = c.get(Calendar.HOUR_OF_DAY);
        int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
        String day    = new String[]{"","Sun","Mon","Tue","Wed","Thu","Fri","Sat"}[dayOfWeek];
        String period = hour < 6  ? "late night" :
                        hour < 12 ? "morning"    :
                        hour < 17 ? "afternoon"  :
                        hour < 21 ? "evening"    : "night";
        return day + " " + period + " (" + hour + ":00)";
    }

    // ── Local fallback when no key or rate limited ────────────────────────────

    private static Decision localFallback(String reason, String appLabel) {
        String lower = reason.toLowerCase();

        String[] denyWords = {"bored","scroll","chill","just check","nothing","random","want to","feel like"};
        for (String w : denyWords) {
            if (lower.contains(w))
                return new Decision("DENY", 0,
                    "That's not a good enough reason. Do a task and come back.");
        }

        String[] grantWords = {"work","study","class","lecture","research","emergency",
                               "doctor","payment","bank","navigate","map","deadline","boss"};
        for (String w : grantWords) {
            if (lower.contains(w))
                return new Decision("GRANT", 15,
                    "That sounds legitimate. 15 minutes — use it well.");
        }

        if (lower.length() < 10)
            return new Decision("DENY", 0,
                "Give me a real reason, not just '" + reason + "'.");

        return new Decision("TASK_FIRST", 0,
            "Do one quick task first, then come back with your reason.");
    }
}
