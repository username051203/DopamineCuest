package com.dopaminequest.activities;

import android.os.Bundle;
import android.util.Log;
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
import androidx.core.content.ContextCompat;

import com.dopaminequest.R;
import com.dopaminequest.models.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraProofActivity extends AppCompatActivity {

    private static final String TAG = "DQ_Camera";

    private PreviewView    previewView;
    private ImageCapture   imageCapture;
    private ExecutorService cameraExecutor;
    private Button         btnCapture;
    private TextView       tvStatus;
    private Task           task;

    // ML Kit label keywords per task tag
    // These are loose — ML Kit returns many labels, we check if any match
    private static final List<String> MOVE_KEYWORDS  = Arrays.asList(
        "person","athlete","exercise","push up","sport","fitness","muscle","arm","hand","body"
    );
    private static final List<String> FOCUS_KEYWORDS = Arrays.asList(
        "desk","table","computer","laptop","keyboard","paper","book","pen","office","text","document","room"
    );
    private static final List<String> RESET_KEYWORDS = Arrays.asList(
        "water","drink","glass","cup","bottle","beverage","liquid","outdoor","sky","nature","plant","tree"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_proof);

        int taskId = getIntent().getIntExtra("task_id", -1);
        task = Task.findById(taskId);
        if (task == null) { setResult(RESULT_CANCELED); finish(); return; }

        previewView = findViewById(R.id.preview_view);
        btnCapture  = findViewById(R.id.btn_capture);
        tvStatus    = findViewById(R.id.tv_status);

        tvStatus.setText("Position your photo proof, then tap Capture.");
        // NO skip button — intentional

        cameraExecutor = Executors.newSingleThreadExecutor();
        startCamera();

        btnCapture.setOnClickListener(v -> capturePhoto());

        findViewById(R.id.btn_back).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                provider.unbindAll();
                provider.bindToLifecycle(this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, imageCapture);
            } catch (Exception e) {
                Log.e(TAG, "Camera bind failed: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void capturePhoto() {
        if (imageCapture == null) return;
        btnCapture.setEnabled(false);
        tvStatus.setText("Analyzing photo…");

        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy proxy) {
                        analyzeImage(proxy);
                    }
                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        Log.e(TAG, "Capture failed: " + e.getMessage());
                        tvStatus.setText("Capture failed. Try again.");
                        btnCapture.setEnabled(true);
                    }
                });
    }

    @SuppressWarnings("deprecation")
    private void analyzeImage(ImageProxy proxy) {
        InputImage image = InputImage.fromMediaImage(
                proxy.getImage(), proxy.getImageInfo().getRotationDegrees());

        ImageLabelerOptions options = new ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.55f)
                .build();
        ImageLabeler labeler = ImageLabeling.getClient(options);

        labeler.process(image)
            .addOnSuccessListener(labels -> {
                proxy.close();
                boolean verified = verifyLabels(labels);
                if (verified) {
                    tvStatus.setText("Verified! Great work.");
                    tvStatus.postDelayed(() -> {
                        setResult(RESULT_OK);
                        finish();
                    }, 900);
                } else {
                    tvStatus.setText("Couldn't verify. Make sure your photo clearly shows the task.");
                    btnCapture.setEnabled(true);
                }
            })
            .addOnFailureListener(e -> {
                proxy.close();
                Log.e(TAG, "ML Kit failed: " + e.getMessage());
                // If ML Kit fails, be lenient and accept (offline fallback)
                tvStatus.setText("Verified! (offline mode)");
                tvStatus.postDelayed(() -> {
                    setResult(RESULT_OK);
                    finish();
                }, 900);
            });
    }

    private boolean verifyLabels(List<ImageLabel> labels) {
        List<String> keywords = getKeywordsForTask();
        for (ImageLabel label : labels) {
            String text = label.getText().toLowerCase();
            Log.d(TAG, "Label: " + text + " (" + label.getConfidence() + ")");
            for (String kw : keywords) {
                if (text.contains(kw) || kw.contains(text)) return true;
            }
        }
        return false;
    }

    private List<String> getKeywordsForTask() {
        switch (task.tag) {
            case "Move":  return MOVE_KEYWORDS;
            case "Focus": return FOCUS_KEYWORDS;
            default:      return RESET_KEYWORDS;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
