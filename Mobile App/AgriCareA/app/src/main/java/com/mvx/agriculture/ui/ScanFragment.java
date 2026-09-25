package com.mvx.agriculture.ui;

import static com.mvx.agriculture.Constants.LABELS_PATH;
import static com.mvx.agriculture.Constants.MODEL_PATH;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.mvx.agriculture.BoundingBox;
import com.mvx.agriculture.Detector;
import com.mvx.agriculture.LocaleManager;
import com.mvx.agriculture.MainShellActivity;
import com.mvx.agriculture.OverlayView;
import com.mvx.agriculture.R;
import com.mvx.agriculture.chat.ScanMode;
import com.mvx.agriculture.chat.VisionClient;
import com.mvx.agriculture.voice.Speaker;
import com.mvx.agriculture.voice.VoicePrefs;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Two ways to look at a plant.
 *
 * The live pane runs the bundled TFLite model, which is fast, offline and limited
 * to the tomato and potato classes it was trained on. The photo pane sends one
 * frame to the vision model, which handles any crop, pest, weed or deficiency but
 * needs a network.
 */
public class ScanFragment extends Fragment implements Detector.DetectorListener {

    private static final String TAG = "ScanFragment";

    private PreviewView viewFinder;
    private OverlayView overlay;
    private TextView inferenceTime, detectionChip, resultText;
    private MaterialButton askButton, captureButton, askAboutResult, listenResult;
    private final Speaker.Listener listenIcon = this::showListening;
    private View livePane, photoPane, resultCard;
    private ImageView photoPreview;
    private LinearProgressIndicator scanProgress;
    private ChipGroup modeChips;
    private TextInputEditText scanContext;
    private View scopeCard, scopeBody, switchToAiButton;
    private TabLayout tabs;
    /** Below this the label is a guess rather than a reading. */
    private static final float TRUST_CONFIDENCE = 0.70f;

    private Detector detector;
    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalyzer;
    private ExecutorService cameraExecutor;
    private String detectedClassname;

    private Bitmap latestFrame;        // kept for the photo pane's capture
    private Bitmap capturedPhoto;
    private String lastDiagnosis;
    private ScanMode selectedMode = ScanMode.DISEASE;

    private final ActivityResultLauncher<String> cameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    Toast.makeText(requireContext(), R.string.camera_permission_needed,
                            Toast.LENGTH_LONG).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewFinder = view.findViewById(R.id.view_finder);
        overlay = view.findViewById(R.id.overlay);
        inferenceTime = view.findViewById(R.id.inferenceTime);
        detectionChip = view.findViewById(R.id.detectionChip);
        askButton = view.findViewById(R.id.askButton);
        livePane = view.findViewById(R.id.livePane);
        photoPane = view.findViewById(R.id.photoPane);
        photoPreview = view.findViewById(R.id.photoPreview);
        captureButton = view.findViewById(R.id.captureButton);
        scanProgress = view.findViewById(R.id.scanProgress);
        resultCard = view.findViewById(R.id.resultCard);
        resultText = view.findViewById(R.id.resultText);
        askAboutResult = view.findViewById(R.id.askAboutResult);
        listenResult = view.findViewById(R.id.listenResult);
        listenResult.setOnClickListener(v ->
                Speaker.get(requireContext()).toggle(requireContext(), resultText.getText()));
        Speaker.get(requireContext()).addListener(listenIcon);
        modeChips = view.findViewById(R.id.modeChips);
        scanContext = view.findViewById(R.id.scanContext);

        scopeCard = view.findViewById(R.id.scopeCard);
        scopeBody = view.findViewById(R.id.scopeBody);
        switchToAiButton = view.findViewById(R.id.switchToAiButton);

        tabs = view.findViewById(R.id.scanTabs);
        tabs.addTab(tabs.newTab().setText(R.string.scan_live_tab));
        tabs.addTab(tabs.newTab().setText(R.string.scan_photo_tab));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                boolean live = tab.getPosition() == 0;
                livePane.setVisibility(live ? View.VISIBLE : View.GONE);
                photoPane.setVisibility(live ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        // The scope note stays collapsed until tapped, then explains the limits and
        // offers the tool that does not have them.
        scopeCard.setOnClickListener(v -> {
            boolean open = scopeBody.getVisibility() == View.VISIBLE;
            scopeBody.setVisibility(open ? View.GONE : View.VISIBLE);
            switchToAiButton.setVisibility(open ? View.GONE : View.VISIBLE);
        });
        switchToAiButton.setOnClickListener(v -> {
            TabLayout.Tab photoTab = tabs.getTabAt(1);
            if (photoTab != null) {
                photoTab.select();
            }
        });

        buildModeChips();

        askButton.setEnabled(false);
        askButton.setOnClickListener(v -> {
            if (detectedClassname != null) {
                ((MainShellActivity) requireActivity()).openChatAbout(detectedClassname);
            }
        });

        captureButton.setOnClickListener(v -> capture());
        askAboutResult.setOnClickListener(v -> {
            if (lastDiagnosis != null) {
                ((MainShellActivity) requireActivity()).openChatWithPrefill(
                        getString(R.string.scan_followup, lastDiagnosis));
            }
        });

        detector = new Detector(requireContext().getApplicationContext(),
                MODEL_PATH, LABELS_PATH, this);
        detector.setup();
        cameraExecutor = Executors.newSingleThreadExecutor();

        if (hasCameraPermission()) {
            startCamera();
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void buildModeChips() {
        for (ScanMode mode : ScanMode.values()) {
            Chip chip = new Chip(requireContext());
            chip.setText(mode.labelRes);
            chip.setCheckable(true);
            chip.setChipIconResource(mode.iconRes);
            chip.setChecked(mode == ScanMode.DISEASE);
            chip.setOnClickListener(v -> selectedMode = mode);
            modeChips.addView(chip);
        }
    }

    // ------------------------------------------------------------------ camera

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        ProcessCameraProvider.getInstance(requireContext()).addListener(() -> {
            try {
                cameraProvider = ProcessCameraProvider.getInstance(requireContext()).get();
                bindUseCases();
            } catch (Exception e) {
                Log.e(TAG, "Camera initialisation failed", e);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindUseCases() {
        if (cameraProvider == null || !isAdded()) {
            return;
        }
        int rotation = viewFinder.getDisplay() == null
                ? 0 : viewFinder.getDisplay().getRotation();

        CameraSelector selector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(rotation)
                .build();

        imageAnalyzer = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(rotation)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build();

        imageAnalyzer.setAnalyzer(cameraExecutor, this::analyse);

        cameraProvider.unbindAll();
        try {
            cameraProvider.bindToLifecycle(getViewLifecycleOwner(), selector, preview, imageAnalyzer);
            preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
        }
    }

    private void analyse(ImageProxy imageProxy) {
        try {
            Bitmap buffer = Bitmap.createBitmap(imageProxy.getWidth(), imageProxy.getHeight(),
                    Bitmap.Config.ARGB_8888);
            buffer.copyPixelsFromBuffer(imageProxy.getPlanes()[0].getBuffer());

            Matrix matrix = new Matrix();
            matrix.postRotate(imageProxy.getImageInfo().getRotationDegrees());
            Bitmap rotated = Bitmap.createBitmap(buffer, 0, 0,
                    buffer.getWidth(), buffer.getHeight(), matrix, true);

            latestFrame = rotated;      // the photo pane grabs whatever is on screen
            detector.detect(rotated);
        } catch (Exception e) {
            Log.e(TAG, "Frame analysis failed", e);
        } finally {
            imageProxy.close();
        }
    }

    // -------------------------------------------------------------- AI capture

    private void capture() {
        Bitmap frame = latestFrame;
        if (frame == null) {
            Toast.makeText(requireContext(), R.string.scan_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        capturedPhoto = frame.copy(Bitmap.Config.ARGB_8888, false);
        photoPreview.setImageBitmap(capturedPhoto);
        photoPreview.setVisibility(View.VISIBLE);

        scanProgress.setVisibility(View.VISIBLE);
        resultCard.setVisibility(View.GONE);
        captureButton.setEnabled(false);
        captureButton.setText(R.string.scan_analysing);

        String context = scanContext.getText() == null
                ? null : scanContext.getText().toString().trim();

        new VisionClient().analyse(capturedPhoto, selectedMode,
                LocaleManager.currentEnglishName(requireContext()), context,
                new VisionClient.Listener() {
                    @Override
                    public void onResult(String diagnosis) {
                        if (!isAdded()) {
                            return;
                        }
                        finishCapture();
                        lastDiagnosis = diagnosis;
                        resultText.setText(diagnosis);
                        resultCard.setVisibility(View.VISIBLE);
                        // Say the diagnosis straight away: the farmer is looking at the plant, not the phone.
                        if (VoicePrefs.autoReadReplies(requireContext())) {
                            Speaker.get(requireContext()).speak(requireContext(), diagnosis);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) {
                            return;
                        }
                        finishCapture();
                        lastDiagnosis = null;
                        resultText.setText(message);
                        resultCard.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void finishCapture() {
        scanProgress.setVisibility(View.GONE);
        captureButton.setEnabled(true);
        captureButton.setText(R.string.scan_retake);
    }

    // ------------------------------------------------------------- detector

    @Override
    public void onEmptyDetect() {
        if (isAdded()) {
            overlay.invalidate();
        }
    }

    @Override
    public void onDetect(List<BoundingBox> boundingBoxes, long inferenceMs) {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(() -> {
            if (!isAdded()) {
                return;
            }
            inferenceTime.setText(String.format(java.util.Locale.getDefault(), "%d ms", inferenceMs));
            overlay.setResults(boundingBoxes);
            overlay.invalidate();

            if (boundingBoxes.isEmpty()) {
                return;
            }

            // Lead with the strongest box and show how sure it is, so a weak guess
            // does not read like a diagnosis.
            BoundingBox best = boundingBoxes.get(0);
            for (BoundingBox box : boundingBoxes) {
                if (box.getCnf() > best.getCnf()) {
                    best = box;
                }
            }

            if (best.getCnf() < TRUST_CONFIDENCE) {
                detectionChip.setText(R.string.scan_low_confidence);
                askButton.setEnabled(false);
                detectedClassname = null;
                return;
            }

            Set<String> unique = new HashSet<>();
            for (BoundingBox box : boundingBoxes) {
                if (box.getCnf() >= TRUST_CONFIDENCE) {
                    unique.add(box.getClsName());
                }
            }
            detectedClassname = String.join(", ", unique);
            detectionChip.setText(getString(R.string.scan_detected_conf,
                    detectedClassname, Math.round(best.getCnf() * 100)));
            askButton.setEnabled(true);
        });
    }

    private void showListening(boolean speaking) {
        if (listenResult != null) {
            listenResult.setIconResource(speaking ? R.drawable.ic_stop : R.drawable.ic_volume_up);
            listenResult.setText(speaking ? R.string.voice_stop : R.string.voice_listen);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Speaker.get(requireContext()).removeListener(listenIcon);
        if (imageAnalyzer != null) {
            imageAnalyzer.clearAnalyzer();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        // Drain the analyser before freeing the native interpreter, otherwise a
        // frame already in flight calls into closed TFLite memory.
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            try {
                cameraExecutor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (detector != null) {
            detector.clear();
        }
    }
}
