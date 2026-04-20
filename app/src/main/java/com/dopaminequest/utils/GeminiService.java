package com.dopaminequest.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class GeminiService {

    private static final String TAG  = "DQ_Gemini";
    private static final String MODEL = "gemini-1.5-flash";
    private static final String BASE  =
        "https://generativelanguage.googleapis.com/v1beta/models/";

    public interface Callback<T> {
        void onResult(T result);
        void onError(String error);
    }

    // ── Validate API key ──────────────────────────────────────────────────────
    public static void validateKey(Context ctx, String key, Callback<Boolean> cb) {
        new Thread(() -> {
            try {
                Log.d(TAG, "validateKey: starting, key length=" + key.length());

                JSONObject part    = new JSONObject().put("text", "Say: valid");
                JSONArray  parts   = new JSONArray().put(part);
                JSONObject content = new JSONObject().put("parts", parts);
                JSONArray  contents= new JSONArray().put(content);
                JSONObject body    = new JSONObject().put("contents", contents);

                String url = BASE + MODEL + ":generateContent?key=" + key.trim();
                Log.d(TAG, "validateKey: posting to URL (key hidden)");

                String[] result = postRaw(url, body.toString());
                int code = Integer.parseInt(result[0]);
                Log.d(TAG, "validateKey: HTTP " + code);
                Log.d(TAG, "validateKey: response=" + result[1].substring(0, Math.min(200, result[1].length())));

                if (code == 200) {
                    cb.onResult(true);
                } else if (code == 400) {
                    cb.onError("Invalid API key (400). Get a free key at aistudio.google.com");
                } else if (code == 403) {
                    cb.onError("API key not authorized (403). Check key permissions at aistudio.google.com");
                } else if (code == 429) {
                    // Rate limited but key is valid — treat as success
                    Log.d(TAG, "validateKey: 429 rate limited, key is valid");
                    cb.onResult(true);
                } else {
                    cb.onError("HTTP " + code + ": " + result[1].substring(0, Math.min(100, result[1].length())));
                }
            } catch (java.net.UnknownHostException e) {
                Log.e(TAG, "validateKey UnknownHostException: " + e.getMessage());
                cb.onError("DNS failed — cannot reach generativelanguage.googleapis.com\nCheck your internet connection.");
            } catch (java.net.SocketTimeoutException e) {
                Log.e(TAG, "validateKey timeout: " + e.getMessage());
                cb.onError("Connection timed out. Check your internet.");
            } catch (javax.net.ssl.SSLException e) {
                Log.e(TAG, "validateKey SSL: " + e.getMessage());
                cb.onError("SSL error: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "validateKey exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                cb.onError(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }).start();
    }

    // ── Generate quiz from page images ────────────────────────────────────────
    public static void generateQuiz(Context ctx, List<Bitmap> pages,
                                    Callback<JSONArray> cb) {
        new Thread(() -> {
            try {
                String key = AppState.getGeminiKey(ctx);
                JSONArray parts = new JSONArray();

                for (Bitmap bmp : pages) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.JPEG, 75, baos);
                    String b64 = Base64.encodeToString(
                        baos.toByteArray(), Base64.NO_WRAP);
                    JSONObject inlineData = new JSONObject()
                        .put("mimeType", "image/jpeg")
                        .put("data", b64);
                    parts.put(new JSONObject().put("inlineData", inlineData));
                }

                String prompt =
                    "These are photos of study material. " +
                    "Generate exactly 10 multiple-choice questions testing understanding. " +
                    "Questions may be directly from the text or require reasoning about it. " +
                    "Return ONLY a valid JSON array, no markdown, no explanation. " +
                    "Format: [{\"q\":\"question\",\"options\":[\"A\",\"B\",\"C\",\"D\"],\"answer\":0}] " +
                    "where answer is the 0-based index of the correct option.";
                parts.put(new JSONObject().put("text", prompt));

                JSONObject content  = new JSONObject().put("parts", parts);
                JSONArray  contents = new JSONArray().put(content);
                JSONObject genCfg   = new JSONObject()
                    .put("temperature", 0.3)
                    .put("maxOutputTokens", 2048);
                JSONObject body = new JSONObject()
                    .put("contents", contents)
                    .put("generationConfig", genCfg);

                String url = BASE + MODEL + ":generateContent?key=" + key.trim();
                String[] result = postRaw(url, body.toString());
                int code = Integer.parseInt(result[0]);

                if (code != 200) {
                    cb.onError("Gemini HTTP " + code + ": " +
                        result[1].substring(0, Math.min(120, result[1].length())));
                    return;
                }

                JSONObject resp = new JSONObject(result[1]);
                String text = resp.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
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
    public static void coachChat(Context ctx,
                                 String blockedPkg,
                                 String appLabel,
                                 JSONArray history,
                                 String userMessage,
                                 Callback<String> cb) {
        new Thread(() -> {
            try {
                String key = AppState.getGeminiKey(ctx);
                JSONArray contents = new JSONArray();

                for (int i = 0; i < history.length(); i++) {
                    JSONObject turn = history.getJSONObject(i);
                    JSONArray  p    = new JSONArray()
                        .put(new JSONObject().put("text", turn.getString("text")));
                    contents.put(new JSONObject()
                        .put("role",  turn.getString("role"))
                        .put("parts", p));
                }

                String msgText = history.length() == 0
                    ? "You are a strict but fair focus coach in DopamineQuest. " +
                      "The user wants access to '" + appLabel + "' which is blocked. " +
                      "Grant access only for genuinely necessary reasons. " +
                      "When granting include [GRANT:N] where N is minutes (max 30). " +
                      "Keep replies to 2-4 sentences. " +
                      "User says: " + userMessage
                    : userMessage;

                JSONArray newParts = new JSONArray()
                    .put(new JSONObject().put("text", msgText));
                contents.put(new JSONObject()
                    .put("role",  "user")
                    .put("parts", newParts));

                JSONObject genCfg = new JSONObject()
                    .put("temperature", 0.7)
                    .put("maxOutputTokens", 256);
                JSONObject body = new JSONObject()
                    .put("contents", contents)
                    .put("generationConfig", genCfg);

                String url = BASE + MODEL + ":generateContent?key=" + key.trim();
                String[] result = postRaw(url, body.toString());
                int code = Integer.parseInt(result[0]);

                if (code != 200) {
                    cb.onError("HTTP " + code);
                    return;
                }

                String text = new JSONObject(result[1])
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim();
                cb.onResult(text);

            } catch (Exception e) {
                Log.e(TAG, "coachChat: " + e.getMessage());
                cb.onError(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }).start();
    }

    // ── Raw POST — returns [statusCode, body] ─────────────────────────────────
    private static String[] postRaw(String urlStr, String jsonBody) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        int code = conn.getResponseCode();
        java.io.InputStream is = (code == 200)
            ? conn.getInputStream()
            : conn.getErrorStream();

        if (is == null) return new String[]{ String.valueOf(code), "" };

        byte[] bytes = is.readAllBytes();
        return new String[]{ String.valueOf(code),
                             new String(bytes, StandardCharsets.UTF_8) };
    }
}
