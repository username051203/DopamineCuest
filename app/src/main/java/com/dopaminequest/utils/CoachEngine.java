package com.dopaminequest.utils;

import android.content.Context;

import org.json.JSONArray;

/**
 * Local rule-based coach — runs entirely offline.
 * Handles the majority of access requests without touching Gemini.
 * Only escalates to Gemini for genuinely ambiguous cases.
 */
public class CoachEngine {

    public static class Decision {
        public final boolean granted;
        public final int     minutes;   // 0 if denied
        public final String  message;
        public final boolean needsGemini; // escalate to AI?

        Decision(boolean granted, int minutes, String message, boolean needsGemini) {
            this.granted     = granted;
            this.minutes     = minutes;
            this.message     = message;
            this.needsGemini = needsGemini;
        }
    }

    // Phrases that are clearly legitimate — grant immediately
    private static final String[] GRANT_SIGNALS = {
        "tutorial", "homework", "assignment", "class", "lecture", "study",
        "research", "project", "work", "job", "email", "boss", "meeting",
        "deadline", "urgent", "emergency", "doctor", "hospital", "family",
        "call", "message", "payment", "bank", "navigate", "map", "direction",
        "news", "weather", "alarm", "reminder", "important", "need for"
    };

    // Phrases that are clearly time-wasting — deny immediately
    private static final String[] DENY_SIGNALS = {
        "bored", "boring", "just a minute", "just for a sec", "just quickly",
        "want to", "feel like", "want to check", "nothing important",
        "just checking", "habit", "waste time", "kill time", "chill",
        "relax", "procrastinat", "scroll", "browse", "random"
    };

    /**
     * Make a local decision based on the user's message.
     * Returns a Decision — if needsGemini is true, caller should escalate.
     */
    public static Decision evaluate(String userMessage, String appLabel,
                                     int turnCount) {
        String lower = userMessage.toLowerCase().trim();

        // Very short messages with no substance — deny
        if (lower.length() < 8) {
            return new Decision(false, 0,
                "Tell me specifically why you need " + appLabel + ". " +
                "A real reason, not just 'please'.", false);
        }

        // Check deny signals first
        for (String deny : DENY_SIGNALS) {
            if (lower.contains(deny)) {
                return new Decision(false, 0,
                    "That's not a good enough reason. " +
                    "Do a quick task and come back with a clear head.", false);
            }
        }

        // Check grant signals
        for (String grant : GRANT_SIGNALS) {
            if (lower.contains(grant)) {
                int mins = estimateMinutes(lower, appLabel);
                return new Decision(true, mins,
                    "That sounds legitimate. You've got " + mins +
                    " minutes — use it well. [GRANT:" + mins + "]", false);
            }
        }

        // If user has been arguing for 3+ turns, grant a short window
        // to avoid being unnecessarily punitive
        if (turnCount >= 3) {
            return new Decision(false, 0, "", true); // escalate to Gemini
        }

        // Ambiguous — escalate to Gemini if available, otherwise ask for more detail
        if (AppState.hasGeminiKey(null)) {
            return new Decision(false, 0, "", true); // escalate
        }

        // No Gemini key — ask for more specifics
        return new Decision(false, 0,
            "I need more detail. What specifically do you need " +
            appLabel + " for right now?", false);
    }

    private static int estimateMinutes(String message, String appLabel) {
        // User specified time
        if (message.contains("5 min"))  return 5;
        if (message.contains("10 min")) return 10;
        if (message.contains("15 min")) return 15;
        if (message.contains("20 min")) return 20;
        if (message.contains("30 min")) return 30;
        if (message.contains("hour"))   return 30; // cap at 30

        // Estimate by app type
        String app = appLabel.toLowerCase();
        if (app.contains("youtube") || app.contains("video")) return 20;
        if (app.contains("maps") || app.contains("navigation")) return 15;
        if (app.contains("mail") || app.contains("gmail"))    return 10;
        if (app.contains("whatsapp") || app.contains("message")) return 10;
        if (app.contains("instagram") || app.contains("twitter")
         || app.contains("tiktok") || app.contains("snapchat")) return 10;

        return 15; // default
    }
}
