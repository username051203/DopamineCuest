package com.dopaminequest.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.Image;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dopaminequest.R;
import com.dopaminequest.utils.AppState;
import com.dopaminequest.utils.GeminiService;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class StudyActivity extends AppCompatActivity {

    private static final int PAGES_REQUIRED = AppState.STUDY_PAGES_REQUIRED;
    private static final int PERM_REQUEST   = 201;

    private PreviewView     previewView;
    private ImageCapture    imageCapture;
    private ExecutorService cameraExecutor;

    private TextView tvStatus, tvCounter;
    private Button   btnCapture, btnRetake, btnConfirm;
    private View     layoutPreview, layoutConfirm, layoutConfirmBtns;

    private final List<Bitmap> pages       = new ArrayList<>();
    private       Bitmap       pendingPage = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study);

        previewView       = findViewById(R.id.preview_view);
        tvStatus          = findViewById(R.id.tv_status);
        tvCounter         = findViewById(R.id.tv_counter);
        btnCapture        = findViewById(R.id.btn_capture);
        btnRetake         = findViewById(R.id.btn_retake);
        btnConfirm        = findViewById(R.id.btn_confirm);
        layoutPreview     = findViewById(R.id.layout_preview);
        layoutConfirm     = findViewById(R.id.layout_confirm);
        layoutConfirmBtns = findViewById(R.id.layout_confirm_buttons);

        cameraExecutor = Executors.newSingleThreadExecutor();

        btnCapture.setOnClickListener(v -> capturePage());
        btnConfirm.setOnClickListener(v -> confirmPage());
        btnRetake.setOnClickListener(v  -> retakePage());

        updateCounter();
        requestCameraPermission();
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                new String[]{ Manifest.permission.CAMERA }, PERM_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms,
                                           @NonNull int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == PERM_REQUEST && grants.length > 0
                && grants[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            tvStatus.setText("Camera permission required.");
            btnCapture.setEnabled(false);
        }
    }

    private void startCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider p = ProcessCameraProvider.getInstance(this).get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build();
                p.unbindAll();
                p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, imageCapture);
                showScanUI();
            } catch (Exception e) {
                tvStatus.setText("Camera error: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void capturePage() {
        if (imageCapture == null) return;
        btnCapture.setEnabled(false);
        tvStatus.setText("Capturing…");
        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
            new ImageCapture.OnImageCapturedCallback() {
                @Override public void onCaptureSuccess(@NonNull ImageProxy proxy) {
                    pendingPage = proxyToBitmap(proxy);
                    proxy.close();
                    runOnUiThread(() -> showConfirmUI());
                }
                @Override public void onError(@NonNull ImageCaptureException e) {
                    runOnUiThread(() -> {
                        tvStatus.setText("Capture failed. Try again.");
                        btnCapture.setEnabled(true);
                    });
                }
            });
    }

    private Bitmap proxyToBitmap(ImageProxy proxy) {
        try {
            Image img = proxy.getImage();
            if (img == null) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            ByteBuffer buf = img.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2;
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
            if (bmp == null) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            Matrix m = new Matrix();
            m.postRotate(proxy.getImageInfo().getRotationDegrees());
            return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        } catch (Exception e) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }
    }

    private void confirmPage() {
        if (pendingPage != null) { pages.add(pendingPage); pendingPage = null; }
        updateCounter();
        if (pages.size() >= PAGES_REQUIRED) startOcr();
        else showScanUI();
    }

    private void retakePage() { pendingPage = null; showScanUI(); }

    private void showScanUI() {
        layoutPreview.setVisibility(View.VISIBLE);
        layoutConfirm.setVisibility(View.GONE);
        layoutConfirmBtns.setVisibility(View.GONE);
        btnCapture.setVisibility(View.VISIBLE);
        btnCapture.setEnabled(true);
        tvStatus.setText("Position page " + (pages.size() + 1)
            + " of " + PAGES_REQUIRED + " and capture.");
    }

    private void showConfirmUI() {
        layoutPreview.setVisibility(View.GONE);
        layoutConfirm.setVisibility(View.VISIBLE);
        layoutConfirmBtns.setVisibility(View.VISIBLE);
        btnCapture.setVisibility(View.GONE);
        tvStatus.setText("Page " + (pages.size() + 1) + " captured. Keep it?");
    }

    private void updateCounter() {
        tvCounter.setText(pages.size() + " / " + PAGES_REQUIRED + " pages");
    }

    // ── OCR all pages locally with ML Kit, then send text to Gemini ──────────
    private void startOcr() {
        layoutPreview.setVisibility(View.GONE);
        layoutConfirm.setVisibility(View.GONE);
        layoutConfirmBtns.setVisibility(View.GONE);
        btnCapture.setVisibility(View.GONE);
        tvStatus.setText("Reading your pages… (1/" + pages.size() + ")");
        tvCounter.setText("OCR in progress");

        TextRecognizer recognizer = TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS);

        StringBuilder allText  = new StringBuilder();
        AtomicInteger done     = new AtomicInteger(0);
        int           total    = pages.size();

        for (int i = 0; i < total; i++) {
            final int idx = i;
            InputImage inputImage = InputImage.fromBitmap(pages.get(idx), 0);
            recognizer.process(inputImage)
                .addOnSuccessListener(visionText -> {
                    synchronized (allText) {
                        allText.append("--- Page ").append(idx + 1).append(" ---\n");
                        allText.append(visionText.getText()).append("\n\n");
                    }
                    int finished = done.incrementAndGet();
                    runOnUiThread(() ->
                        tvStatus.setText("Reading pages… (" + finished + "/" + total + ")"));
                    if (finished == total) sendToGemini(allText.toString());
                })
                .addOnFailureListener(e -> {
                    // OCR failed for this page — add placeholder and continue
                    synchronized (allText) {
                        allText.append("--- Page ").append(idx + 1)
                               .append(" --- [unreadable]\n\n");
                    }
                    int finished = done.incrementAndGet();
                    if (finished == total) sendToGemini(allText.toString());
                });
        }
    }

    private void sendToGemini(String ocrText) {
        runOnUiThread(() -> {
            tvStatus.setText("Generating quiz questions…\nThis takes a few seconds.");
            tvCounter.setText("Asking Gemini");
        });

        if (ocrText.trim().isEmpty() || ocrText.replace("[unreadable]", "").trim().length() < 50) {
            runOnUiThread(() -> {
                tvStatus.setText("Couldn't read enough text from the pages.\n" +
                    "Make sure pages are well-lit and in focus.\nTap to retry.");
                tvStatus.setOnClickListener(v -> {
                    pages.clear();
                    updateCounter();
                    showScanUI();
                });
            });
            return;
        }

        GeminiService.generateQuizFromText(this, ocrText,
            new GeminiService.Callback<JSONArray>() {
                @Override public void onResult(JSONArray questions) {
                    runOnUiThread(() -> {
                        Intent i = new Intent(StudyActivity.this, QuizActivity.class);
                        i.putExtra("questions_json", questions.toString());
                        i.putExtra("from_overlay",
                            getIntent().getBooleanExtra("from_overlay", false));
                        startActivity(i);
                        finish();
                    });
                }
                @Override public void onError(String error) {
                    runOnUiThread(() -> {
                        tvStatus.setText("Failed: " + error + "\n\nTap to retry.");
                        tvCounter.setText("");
                        tvStatus.setOnClickListener(v -> sendToGemini(ocrText));
                    });
                }
            });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
