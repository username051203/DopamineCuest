package com.dopaminequest.utils;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * AI service — uses Groq API (free tier: 14,400 req/day, 30 req/min)
 * Model: llama-3.1-8b-instant (fast, free, no billing required)
 * Get a free key at: console.groq.com
 */
public class GeminiService {

    private static final String TAG   = "DQ_AI";
    private static final String MODEL = "llama-3.1-8b-instant";
    private static final String URL_CHAT =
        "https://api.groq.com/openai/v1/chat/completions";

    public interface Callback<T> {
        void onResult(T result);
        void onError(String error);
    }

    // ── Validate API key ──────────────────────────────────────────────────────
    public static void validateKey(Context ctx, String key, Callback<Boolean> cb) {
        new Thread(() -> {
            try {
                Log.d(TAG, "validateKey length=" + key.length());

                JSONObject body = new JSONObject()
                    .put("model", MODEL)
                    .put("max_tokens", 5)
                    .put("messages", new JSONArray()
                        .put(new JSONObject()
                            .put("role", "user")
                            .put("content", "Say: valid")));

                String[] r = postRawPublic(URL_CHAT, key.trim(), body.toString());
                int code = Integer.parseInt(r[0]);
                Log.d(TAG, "validateKey HTTP " + code);

                if (code == 200 || code == 429) {
                    cb.onResult(true);
                } else if (code == 401) {
                    cb.onError("Invalid API key (401).\nGet a free key at console.groq.com");
                } else if (code == 403) {
                    cb.onError("Key not authorized (403).\nCheck console.groq.com");
                } else {
                    cb.onError("HTTP " + code + ": " +
                        r[1].substring(0, Math.min(120, r[1].length())));
                }
            } catch (java.net.UnknownHostException e) {
                cb.onError("Cannot reach Groq API.\nCheck your internet connection.");
            } catch (java.net.SocketTimeoutException e) {
                cb.onError("Connection timed out.");
            } catch (Exception e) {
                Log.e(TAG, "validateKey: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
                cb.onError(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }).start();
    }

    // ── Generate quiz from OCR text ───────────────────────────────────────────
    public static void generateQuizFromText(Context ctx, String ocrText,
                                            Callback<JSONArray> cb) {
        new Thread(() -> {
            try {
                String key = AppState.getGeminiKey(ctx);
                if (key.isEmpty()) { cb.onError("No API key set."); return; }

                String prompt =
                    "The following is text extracted from a student's study material:\n\n"
                    + ocrText.substring(0, Math.min(ocrText.length(), 6000))
                    + "\n\nGenerate exactly 10 multiple-choice questions testing understanding. "
                    + "Mix direct recall with reasoning questions. "
                    + "Return ONLY a valid JSON array, no markdown, no explanation. "
                    + "Format: [{\"q\":\"question\",\"options\":[\"A\",\"B\",\"C\",\"D\"],\"answer\":0}] "
                    + "where answer is the 0-based index of the correct option.";

                JSONObject body = new JSONObject()
                    .put("model", MODEL)
                    .put("max_tokens", 2048)
                    .put("temperature", 0.4)
                    .put("messages", new JSONArray()
                        .put(new JSONObject()
                            .put("role", "system")
                            .put("content",
                                "You are a quiz generator. Output ONLY valid JSON arrays. "
                                + "No markdown fences, no explanation, just the JSON array."))
                        .put(new JSONObject()
                            .put("role", "user")
                            .put("content", prompt)));

                String[] r = postRawPublic(URL_CHAT, key.trim(), body.toString());
                int code = Integer.parseInt(r[0]);
                Log.d(TAG, "generateQuiz HTTP " + code);

                if (code == 429) {
                    cb.onError("Rate limit hit. Wait 60 seconds and try again.\n"
                        + "(Groq free: 30 req/min, 14400/day)");
                    return;
                }
                if (code != 200) {
                    cb.onError("API error HTTP " + code + ":\n" +
                        r[1].substring(0, Math.min(150, r[1].length())));
                    return;
                }

                String text = new JSONObject(r[1])
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

                cb.onResult(new JSONArray(text));

            } catch (Exception e) {
                Log.e(TAG, "generateQuiz: " + e.getMessage());
                cb.onError(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }).start();
    }

    // ── Coach multi-turn chat ─────────────────────────────────────────────────
    public static void coachChat(Context ctx, String blockedPkg, String appLabel,
                                 JSONArray history, String userMessage,
                                 Callback<String> cb) {
        new Thread(() -> {
            try {
                String key = AppState.getGeminiKey(ctx);

                JSONArray messages = new JSONArray();

                // System prompt
                messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content",
                        "You are a strict but fair focus coach in DopamineQuest. "
                        + "The user wants access to '" + appLabel + "' which is blocked. "
                        + "Grant access only for genuinely necessary reasons. "
                        + "When granting, include [GRANT:N] in your reply where N = minutes (max 30). "
                        + "Keep replies to 2-4 sentences. Deny vague or boredom-based requests. "
                        + "Be direct but not harsh."));

                // History
                for (int i = 0; i < history.length(); i++) {
                    JSONObject t = history.getJSONObject(i);
                    String role = t.getString("role")
                        .replace("model", "assistant"); // Gemini→OpenAI naming
                    messages.put(new JSONObject()
                        .put("role", role)
                        .put("content", t.getString("text")));
                }

                // New message
                messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", userMessage));

                JSONObject body = new JSONObject()
                    .put("model", MODEL)
                    .put("max_tokens", 300)
                    .put("temperature", 0.7)
                    .put("messages", messages);

                String[] r = postRawPublic(URL_CHAT, key.trim(), body.toString());
                int code = Integer.parseInt(r[0]);
                Log.d(TAG, "coachChat HTTP " + code);

                if (code == 429) { cb.onError("Rate limited — wait a moment."); return; }
                if (code != 200) { cb.onError("HTTP " + code); return; }

                cb.onResult(new JSONObject(r[1])
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim());

            } catch (Exception e) {
                Log.e(TAG, "coachChat: " + e.getMessage());
                cb.onError(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }).start();
    }

    // ── Raw POST with Bearer auth (Groq/OpenAI format) ────────────────────────
    public static String[] postRawPublic(String urlStr, String apiKey,
                                    String body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + apiKey);
        c.setDoOutput(true);
        c.setConnectTimeout(30000);
        c.setReadTimeout(60000);

        try (OutputStream os = c.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        int code = c.getResponseCode();
        java.io.InputStream is = code == 200
            ? c.getInputStream() : c.getErrorStream();
        if (is == null) return new String[]{ String.valueOf(code), "" };
        return new String[]{ String.valueOf(code),
            new String(is.readAllBytes(), StandardCharsets.UTF_8) };
    }
}
