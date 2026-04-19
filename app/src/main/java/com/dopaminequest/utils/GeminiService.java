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

    private static final String TAG     = "DQ_Gemini";
    private static final String MODEL   = "gemini-1.5-flash";
    private static final String API_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/"
        + MODEL + ":generateContent?key=";

    public interface Callback<T> {
        void onResult(T result);
        void onError(String error);
    }

    // ── Validate API key ──────────────────────────────────────────────────────
    public static void validateKey(Context ctx, String key, Callback<Boolean> cb) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                JSONArray contents = new JSONArray();
                JSONObject content = new JSONObject();
                JSONArray parts = new JSONArray();
                parts.put(new JSONObject().put("text", "Reply with the single word: valid"));
                content.put("parts", parts);
                contents.put(content);
                body.put("contents", contents);

                String response = post(key, body.toString());
                boolean ok = response != null && response.contains("valid");
                cb.onResult(ok);
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }

    // ── Generate 10 quiz questions from 6 page images ─────────────────────────
    public static void generateQuiz(Context ctx, List<Bitmap> pages, Callback<JSONArray> cb) {
        new Thread(() -> {
            try {
                String key = AppState.getGeminiKey(ctx);

                JSONObject body     = new JSONObject();
                JSONArray  contents = new JSONArray();
                JSONObject content  = new JSONObject();
                JSONArray  parts    = new JSONArray();

                // Add each page as base64 image
                for (Bitmap bmp : pages) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.JPEG, 75, baos);
                    String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                    JSONObject imgPart = new JSONObject();
                    JSONObject inlineData = new JSONObject();
                    inlineData.put("mimeType", "image/jpeg");
                    inlineData.put("data", b64);
                    imgPart.put("inlineData", inlineData);
                    parts.put(imgPart);
                }

                // Prompt
                String prompt =
                    "These are photos of study material. " +
                    "Generate exactly 10 multiple-choice questions that test understanding of the content. " +
                    "Questions can be directly from the text OR require reasoning about it. " +
                    "Return ONLY a valid JSON array with no markdown, no explanation. " +
                    "Each element must have: " +
                    "{\"q\": \"question text\", \"options\": [\"A\",\"B\",\"C\",\"D\"], \"answer\": 0} " +
                    "where answer is the 0-based index of the correct option.";

                parts.put(new JSONObject().put("text", prompt));
                content.put("parts", parts);
                contents.put(content);
                body.put("contents", contents);

                // Generation config — keep output tight
                JSONObject genConfig = new JSONObject();
                genConfig.put("temperature", 0.3);
                genConfig.put("maxOutputTokens", 2048);
                body.put("generationConfig", genConfig);

                String response = post(key, body.toString());
                if (response == null) { cb.onError("No response from Gemini"); return; }

                // Extract text from response
                JSONObject resp    = new JSONObject(response);
                String     text    = resp.getJSONArray("candidates")
                                        .getJSONObject(0)
                                        .getJSONObject("content")
                                        .getJSONArray("parts")
                                        .getJSONObject(0)
                                        .getString("text")
                                        .trim();

                // Strip markdown fences if present
                if (text.startsWith("```")) {
                    text = text.replaceAll("```json", "").replaceAll("```", "").trim();
                }

                JSONArray questions = new JSONArray(text);
                cb.onResult(questions);

            } catch (Exception e) {
                Log.e(TAG, "generateQuiz error: " + e.getMessage());
                cb.onError(e.getMessage());
            }
        }).start();
    }

    // ── HTTP POST ─────────────────────────────────────────────────────────────
    private static String post(String key, String jsonBody) {
        try {
            URL url = new URL(API_URL + key);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            OutputStream os = conn.getOutputStream();
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            os.flush();

            int code = conn.getResponseCode();
            java.io.InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
            byte[] bytes = is.readAllBytes();
            String result = new String(bytes, StandardCharsets.UTF_8);

            if (code != 200) {
                Log.e(TAG, "HTTP " + code + ": " + result);
                return null;
            }
            return result;

        } catch (Exception e) {
            Log.e(TAG, "POST error: " + e.getMessage());
            return null;
        }
    }
}
