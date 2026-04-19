package com.dopaminequest.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StudyActivity extends AppCompatActivity {

    private static final int    PAGES_REQUIRED   = AppState.STUDY_PAGES_REQUIRED;
    private static final int    PERM_REQUEST     = 201;

    private PreviewView     previewView;
    private ImageCapture    imageCapture;
    private ExecutorService cameraExecutor;
    private Button          btnCapture, btnRetake;
    private TextView        tvStatus, tvCounter;
    private View            layoutPreview, layoutConfirm;

    private final List<Bitmap> pages       = new ArrayList<>();
    private       Bitmap       pendingPage = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study);

        previewView   = findViewById(R.id.preview_view);
        tvStatus      = findViewById(R.id.tv_status);
        tvCounter     = findViewById(R.id.tv_counter);
        btnCapture    = findViewById(R.id.btn_capture);
        btnRetake     = findViewById(R.id.btn_retake);
        layoutPreview = findViewById(R.id.layout_preview);
        layoutConfirm = findViewById(R.id.layout_confirm);

        cameraExecutor = Executors.newSingleThreadExecutor();

        Button btnConfirm = findViewById(R.id.btn_confirm);
        btnConfirm.setOnClickListener(v -> confirmPage());
        btnRetake.setOnClickListener(v -> retakePage());
        btnCapture.setOnClickListener(v -> capturePage());

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
                ProcessCameraProvider provider =
                    ProcessCameraProvider.getInstance(this).get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build();

                provider.unbindAll();
                provider.bindToLifecycle(this,
                    CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);

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
                @Override
                public void onCaptureSuccess(@NonNull ImageProxy proxy) {
                    // Convert to Bitmap
                    android.graphics.ImageDecoder.Source src =
                        android.graphics.ImageDecoder.createSource(
                            getContentResolver(),
                            android.net.Uri.EMPTY);
                    // Simple approach — use proxy planes
                    pendingPage = proxyToBitmap(proxy);
                    proxy.close();
                    runOnUiThread(() -> showConfirmUI());
                }
                @Override
                public void onError(@NonNull ImageCaptureException e) {
                    runOnUiThread(() -> {
                        tvStatus.setText("Capture failed. Try again.");
                        btnCapture.setEnabled(true);
                    });
                }
            });
    }

    @SuppressWarnings("UnsafeOptInUsageError")
    private Bitmap proxyToBitmap(ImageProxy proxy) {
        android.media.Image img = proxy.getImage();
        if (img == null) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        android.media.Image.Plane[] planes = img.getPlanes();
        java.nio.ByteBuffer buffer = planes[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
        opts.inSampleSize = 2; // downsample to save memory
        Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        // Rotate if needed
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postRotate(proxy.getImageInfo().getRotationDegrees());
        return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
    }

    private void confirmPage() {
        if (pendingPage != null) {
            pages.add(pendingPage);
            pendingPage = null;
        }
        updateCounter();
        if (pages.size() >= PAGES_REQUIRED) {
            startAnalysis();
        } else {
            showScanUI();
        }
    }

    private void retakePage() {
        pendingPage = null;
        showScanUI();
    }

    private void showScanUI() {
        layoutPreview.setVisibility(View.VISIBLE);
        layoutConfirm.setVisibility(View.GONE);
        btnCapture.setEnabled(true);
        tvStatus.setText("Position page " + (pages.size() + 1) + " clearly and capture.");
    }

    private void showConfirmUI() {
        layoutPreview.setVisibility(View.GONE);
        layoutConfirm.setVisibility(View.VISIBLE);
        tvStatus.setText("Page " + (pages.size() + 1) + " captured. Keep it?");
    }

    private void updateCounter() {
        tvCounter.setText(pages.size() + " / " + PAGES_REQUIRED + " pages");
    }

    private void startAnalysis() {
        layoutPreview.setVisibility(View.GONE);
        layoutConfirm.setVisibility(View.GONE);
        tvStatus.setText("Analyzing your study material…\nThis may take 20–30 seconds.");
        tvCounter.setText("Sending to Gemini");

        GeminiService.generateQuiz(this, pages, new GeminiService.Callback<JSONArray>() {
            @Override
            public void onResult(JSONArray questions) {
                runOnUiThread(() -> {
                    Intent i = new Intent(StudyActivity.this, QuizActivity.class);
                    i.putExtra("questions_json", questions.toString());
                    i.putExtra("from_overlay",
                        getIntent().getBooleanExtra("from_overlay", false));
                    startActivity(i);
                    finish();
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    tvStatus.setText("Analysis failed: " + error + "\n\nTap to retry.");
                    tvCounter.setText("");
                    tvStatus.setOnClickListener(v -> startAnalysis());
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
