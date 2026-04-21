package com.dopaminequest.activities;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.dopaminequest.R;
import com.dopaminequest.utils.AppState;
import com.dopaminequest.utils.CoachEngine;
import com.dopaminequest.utils.GeminiService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CoachChatActivity extends AppCompatActivity {

    private LinearLayout chatContainer;
    private ScrollView   scrollView;
    private EditText     etInput;
    private Button       btnSend;
    private TextView     tvAppName, tvStatus;

    private String   blockedPkg;
    private String   appLabel;
    private JSONArray history   = new JSONArray();
    private boolean  granted   = false;
    private int      turnCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_coach_chat);

        chatContainer = findViewById(R.id.chat_container);
        scrollView    = findViewById(R.id.scroll_view);
        etInput       = findViewById(R.id.et_input);
        btnSend       = findViewById(R.id.btn_send);
        tvAppName     = findViewById(R.id.tv_app_name);
        tvStatus      = findViewById(R.id.tv_status);

        blockedPkg = getIntent().getStringExtra("blocked_pkg");
        appLabel   = getAppLabel(blockedPkg);
        tvAppName.setText(appLabel);
        tvStatus.setText("Tell the coach why you need " + appLabel + ".");

        addBubble("coach",
            "You're trying to open " + appLabel + ". " +
            "Give me a real reason and I'll consider it.");

        btnSend.setOnClickListener(v -> sendMessage());
        findViewById(R.id.btn_back).setOnClickListener(v -> saveAndFinish());
    }

    private void sendMessage() {
        String text = etInput.getText().toString().trim();
        if (text.isEmpty() || granted) return;

        etInput.setText("");
        btnSend.setEnabled(false);
        addBubble("user", text);
        turnCount++;

        // Try local engine first — no network call needed
        CoachEngine.Decision local =
            CoachEngine.evaluate(text, appLabel, turnCount);

        if (!local.needsGemini) {
            // Handle locally
            if (local.granted) {
                granted = true;
                AppState.grantTempAppAccess(this, blockedPkg, local.minutes);
                scheduleExpiry();
                String display = local.message
                    .replaceAll("\\[GRANT:\\d+\\]", "").trim();
                addBubble("coach", display);
                showGranted(local.minutes);
                btnSend.setEnabled(true);
            } else {
                addBubble("coach", local.message);
                btnSend.setEnabled(true);
            }
            scrollToBottom();
            return;
        }

        // Escalate to Gemini for ambiguous cases
        if (!AppState.hasGeminiKey(this)) {
            // No key — be fair, grant after sustained effort
            addBubble("coach",
                "I'll take your word for it. You've got 10 minutes. " +
                "Make it count. [GRANT:10]");
            granted = true;
            AppState.grantTempAppAccess(this, blockedPkg, 10);
            scheduleExpiry();
            showGranted(10);
            btnSend.setEnabled(true);
            scrollToBottom();
            return;
        }

        tvStatus.setText("Thinking…");

        // Record turn in history for Gemini context
        try {
            JSONObject userTurn = new JSONObject();
            userTurn.put("role", "user");
            userTurn.put("text", text);
            history.put(userTurn);
        } catch (Exception ignored) {}

        GeminiService.coachChat(this, blockedPkg, appLabel, history, text,
            new GeminiService.Callback<String>() {
                @Override public void onResult(String reply) {
                    runOnUiThread(() -> {
                        btnSend.setEnabled(true);
                        tvStatus.setText("");

                        try {
                            JSONObject modelTurn = new JSONObject();
                            modelTurn.put("role", "model");
                            modelTurn.put("text", reply);
                            history.put(modelTurn);
                        } catch (Exception ignored) {}

                        int minutes = parseGrant(reply);
                        String display = reply.replaceAll(
                            "\\[GRANT:\\d+\\]", "").trim();
                        addBubble("coach", display);

                        if (minutes > 0) {
                            granted = true;
                            AppState.grantTempAppAccess(
                                CoachChatActivity.this, blockedPkg, minutes);
                            scheduleExpiry();
                            showGranted(minutes);
                        }
                        scrollToBottom();
                    });
                }
                @Override public void onError(String error) {
                    runOnUiThread(() -> {
                        btnSend.setEnabled(true);
                        tvStatus.setText("");
                        // Gemini failed — fall back to generous local grant
                        // after user has already argued their case
                        addBubble("coach",
                            "Network issue — I'll trust you this time. " +
                            "15 minutes. Don't waste it.");
                        granted = true;
                        AppState.grantTempAppAccess(
                            CoachChatActivity.this, blockedPkg, 15);
                        scheduleExpiry();
                        showGranted(15);
                        scrollToBottom();
                    });
                }
            });

        scrollToBottom();
    }

    private int parseGrant(String text) {
        Matcher m = Pattern.compile("\\[GRANT:(\\d+)\\]").matcher(text);
        if (m.find()) {
            try { return Math.min(30, Integer.parseInt(m.group(1))); }
            catch (Exception ignored) {}
        }
        return 0;
    }

    private void showGranted(int minutes) {
        tvStatus.setText("✓ " + minutes + " min access granted.");
        btnSend.setEnabled(false);
        etInput.setEnabled(false);
        tvStatus.postDelayed(this::saveAndFinish, 1500);
    }

    private void scheduleExpiry() {
        android.app.AlarmManager am =
            (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
        if (am == null) return;
        android.content.Intent i = new android.content.Intent(this,
            com.dopaminequest.receivers.AccessWindowReceiver.class);
        i.setAction("com.dopaminequest.ACTION_ACCESS_EXPIRED");
        android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
            this, 9002, i,
            android.app.PendingIntent.FLAG_IMMUTABLE |
            android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        long at = AppState.getTempAppUntil(this);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                && am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP, at, pi);
        } else {
            am.setAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    private void addBubble(String role, String text) {
        View bubble = LayoutInflater.from(this)
            .inflate(R.layout.item_chat_bubble, chatContainer, false);

        TextView tvRole = bubble.findViewById(R.id.tv_role);
        TextView tvText = bubble.findViewById(R.id.tv_text);
        View     bg     = bubble.findViewById(R.id.bubble_bg);

        tvRole.setText(role.equals("coach") ? "COACH" : "YOU");
        tvText.setText(text);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin    = 8;
        lp.bottomMargin = 8;
        lp.maxWidth     = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);

        if (role.equals("user")) {
            lp.gravity = Gravity.END;
            lp.leftMargin = 48;
            bg.setBackgroundColor(
                getResources().getColor(R.color.ink, getTheme()));
            tvText.setTextColor(
                getResources().getColor(R.color.paper, getTheme()));
            tvRole.setTextColor(
                getResources().getColor(R.color.ink3, getTheme()));
        } else {
            lp.gravity = Gravity.START;
            lp.rightMargin = 48;
            bg.setBackgroundColor(
                getResources().getColor(R.color.rule, getTheme()));
            tvText.setTextColor(
                getResources().getColor(R.color.ink, getTheme()));
            tvRole.setTextColor(
                getResources().getColor(R.color.accent, getTheme()));
        }
        bubble.setLayoutParams(lp);
        chatContainer.addView(bubble);
    }

    private void scrollToBottom() {
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void saveAndFinish() {
        try {
            AppState.logConversation(this, blockedPkg, history.toString());
        } catch (Exception ignored) {}
        finish();
    }

    private String getAppLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (Exception e) { return pkg != null ? pkg : "that app"; }
    }

    @Override
    public void onBackPressed() { saveAndFinish(); }
}
