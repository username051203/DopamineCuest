package com.dopaminequest.utils;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class GeminiService {

    private static final String TAG   = "DQ_Gemini";
    private static final String MODEL = "gemini-1.5-flash-latest";
    private static final String BASE  =
        "https://generativelanguage.googleapis.com/v1beta/models/"\;

    public interface Callback<T> {
        void onResult(T result);
        void onError(String error);
    }

    // ── Validate API key ──────────────────────────────────────────────────────
    public static void validateKey(Context ctx, String key, Callback<Boolean> cb) {
        new Thread(() -> {
            try {
                Log.d(TAG, "validateKey key.length=" + key.length());
                JSONObject body = new JSONObject()
                    .put("contents", new JSONArray().put(
                        new JSONObject().put("parts", new JSONArray().put(
                            new JSONObject().put("text", "Say: valid")))));

                String[] r = postRaw(BASE + MODEL + ":generateContent?key=" + key.trim(),
                    body.toString());
                int code = Integer.parseInt(r[0]);
                Log.d(TAG, "validateKey HTTP " + code + " | " +
                    r[1].substring(0, Math.min(200, r[1].length())));

                if (code == 200 || code == 429) { cb.onResult(true); }
                else if (code == 400) cb.onError("Invalid API key (400).\nGet one free at aistudio.google.com");
                else if (code == 403) cb.onError("Key not authorized (403).\nCheck aistudio.google.com");
                else cb.onError("HTTP " + code + ": " + r[1].substring(0, Math.min(120, r[1].length())));

            } catch (java.net.UnknownHostException e) {
                cb.onError("Cannot reach Google.\nCheck your internet connection.");
            } catch (java.net.SocketTimeoutException e) {
                cb.onError("Connection timed out.");
            } catch (Exception e) {
                Log.e(TAG, "validateKey: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                cb.onError(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }).start();
    }

    // ── Generate quiz from OCR text (no images sent — avoids vision quota) ───
    // ocrText is the combined text extracted from all 6 pages via ML Kit
    public static void generateQuizFromText(Context ctx, String ocrText,
                                            Callback<JSONArray> cb) {
        new Thread(() -> {
            try {
                String key = AppState.getGeminiKey(ctx);
                if (key.isEmpty()) { cb.onError("No API key set."); return; }

                String prompt =
                    "The following is text extracted from a student's study material " +
                    "(6 pages of notes or textbook content):\n\n" +
                    ocrText + "\n\n" +
                    "Generate exactly 10 multiple-choice questions that test understanding. " +
                    "Mix questions directly from the text with ones requiring reasoning. " +
                    "Return ONLY a valid JSON array. No markdown, no explanation, nothing else. " +
                    "Format: [{\"q\":\"question\",\"options\":[\"A\",\"B\",\"C\",\"D\"],\"answer\":0}] " +
                    "where answer is the 0-based index of the correct option.";

                JSONObject body = new JSONObject()
                    .put("contents", new JSONArray().put(
                        new JSONObject().put("parts", new JSONArray().put(
                            new JSONObject().put("text", prompt)))))
                    .put("generationConfig", new JSONObject()
                        .put("temperature", 0.4)
                        .put("maxOutputTokens", 2048));

                String[] r = postRaw(BASE + MODEL + ":generateContent?key=" + key.trim(),
                    body.toString());
                int code = Integer.parseInt(r[0]);
                Log.d(TAG, "generateQuiz HTTP " + code);

                if (code == 429) {
                    cb.onError("Rate limit hit.\nWait 60 seconds and try again.");
                    return;
                }
                if (code != 200) {
                    cb.onError("Gemini HTTP " + code + ":\n" +
                        r[1].substring(0, Math.min(150, r[1].length())));
                    return;
                }

                String text = new JSONObject(r[1])
                    .getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                    .getJSONObject(0).getString("text")
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

    // ── Coach chat ────────────────────────────────────────────────────────────
    public static void coachChat(Context ctx, String blockedPkg, String appLabel,
                                 JSONArray history, String userMessage,
                                 Callback<String> cb) {
        new Thread(() -> {
            try {
                String key = AppState.getGeminiKey(ctx);
                JSONArray contents = new JSONArray();

                for (int i = 0; i < history.length(); i++) {
                    JSONObject t = history.getJSONObject(i);
                    contents.put(new JSONObject()
                        .put("role", t.getString("role"))
                        .put("parts", new JSONArray().put(
                            new JSONObject().put("text", t.getString("text")))));
                }

                String msg = history.length() == 0
                    ? "You are a strict but fair focus coach in DopamineQuest. " +
                      "The user wants access to '" + appLabel + "' which is blocked. " +
                      "Grant only for genuinely necessary reasons. " +
                      "When granting include [GRANT:N] where N = minutes (max 30). " +
                      "Keep replies to 2-4 sentences. Deny vague requests. " +
                      "User says: " + userMessage
                    : userMessage;

                contents.put(new JSONObject()
                    .put("role", "user")
                    .put("parts", new JSONArray().put(
                        new JSONObject().put("text", msg))));

                JSONObject body = new JSONObject()
                    .put("contents", contents)
                    .put("generationConfig", new JSONObject()
                        .put("temperature", 0.7)
                        .put("maxOutputTokens", 300));

                String[] r = postRaw(BASE + MODEL + ":generateContent?key=" + key.trim(),
                    body.toString());
                int code = Integer.parseInt(r[0]);

                if (code == 429) { cb.onError("Rate limited — wait a moment."); return; }
                if (code != 200) { cb.onError("HTTP " + code); return; }

                cb.onResult(new JSONObject(r[1])
                    .getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                    .getJSONObject(0).getString("text").trim());

            } catch (Exception e) {
                Log.e(TAG, "coachChat: " + e.getMessage());
                cb.onError(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }).start();
    }

    // ── Raw POST ──────────────────────────────────────────────────────────────
    private static String[] postRaw(String urlStr, String body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        c.setDoOutput(true);
        c.setConnectTimeout(30000);
        c.setReadTimeout(90000);
        try (OutputStream os = c.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
        int code = c.getResponseCode();
        java.io.InputStream is = code == 200 ? c.getInputStream() : c.getErrorStream();
        if (is == null) return new String[]{ String.valueOf(code), "" };
        return new String[]{ String.valueOf(code),
            new String(is.readAllBytes(), StandardCharsets.UTF_8) };
    }
}
