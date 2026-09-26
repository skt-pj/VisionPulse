package com.skt.visionpulse;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

public final class MainActivity extends AppCompatActivity {
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private TextView statusText;
    private TextView performanceText;
    private TextView thresholdText;
    private TextView labelsText;
    private ImageView previewImage;
    private Button startStopButton;

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            renderSnapshot();
            uiHandler.postDelayed(this, 400);
        }
    };

    private ActivityResultLauncher<Intent> captureLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        captureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                        statusText.setText("画面共有が許可されませんでした");
                        return;
                    }
                    Intent serviceIntent = new Intent(this, CaptureService.class)
                            .setAction(CaptureService.ACTION_START)
                            .putExtra(CaptureService.EXTRA_RESULT_CODE, result.getResultCode())
                            .putExtra(CaptureService.EXTRA_DATA, result.getData());
                    ContextCompat.startForegroundService(this, serviceIntent);
                }
        );

        setContentView(buildUi());
        requestNotificationPermissionIfNeeded();
    }

    private View buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(36));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("VisionPulse");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView description = new TextView(this);
        description.setText("スマホ画面をライブ取得し、YOLO26nで検出性能を確認します。");
        description.setTextSize(15);
        description.setPadding(0, dp(4), 0, dp(16));
        root.addView(description);

        statusText = sectionValue(root, "状態", "停止中");

        sectionLabel(root, "YOLO入力解像度");
        Spinner resolutionSpinner = new Spinner(this);
        String[] resolutions = {"320 × 320", "416 × 416", "512 × 512", "640 × 640"};
        ArrayAdapter<String> resolutionAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                resolutions
        );
        resolutionSpinner.setAdapter(resolutionAdapter);
        resolutionSpinner.setSelection(1);
        resolutionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int[] values = {320, 416, 512, 640};
                DetectionStore.setInputSize(values[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        root.addView(resolutionSpinner, matchWrap());

        thresholdText = sectionLabel(root, "信頼度しきい値: 0.25");
        SeekBar thresholdSeek = new SeekBar(this);
        thresholdSeek.setMax(80);
        thresholdSeek.setProgress(15);
        thresholdSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = (progress + 10) / 100f;
                DetectionStore.setConfidenceThreshold(value);
                thresholdText.setText(String.format(Locale.US, "信頼度しきい値: %.2f", value));
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(thresholdSeek, matchWrap());

        startStopButton = new Button(this);
        startStopButton.setText("ライブ検出を開始");
        startStopButton.setAllCaps(false);
        startStopButton.setOnClickListener(v -> {
            if (DetectionStore.isRunning()) {
                stopService(new Intent(this, CaptureService.class));
            } else {
                MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                captureLauncher.launch(manager.createScreenCaptureIntent());
            }
        });
        LinearLayout.LayoutParams buttonParams = matchWrap();
        buttonParams.topMargin = dp(14);
        root.addView(startStopButton, buttonParams);

        performanceText = sectionValue(root, "パフォーマンス", "未実行");

        sectionLabel(root, "検出プレビュー");
        previewImage = new ImageView(this);
        previewImage.setAdjustViewBounds(true);
        previewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewImage.setBackgroundColor(0xFF111111);
        root.addView(previewImage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        labelsText = sectionValue(root, "ラベル統計", "まだ検出されていません");
        labelsText.setTypeface(Typeface.MONOSPACE);
        labelsText.setTextSize(12);

        TextView note = new TextView(this);
        note.setText("精度確認はプレビューの検出枠と信頼度を見て手動評価します。厳密なmAP等には正解ラベル付きデータが必要です。");
        note.setTextSize(12);
        note.setPadding(0, dp(12), 0, 0);
        root.addView(note);
        return scrollView;
    }

    private TextView sectionLabel(LinearLayout root, String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(15);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setPadding(0, dp(18), 0, dp(6));
        root.addView(label);
        return label;
    }

    private TextView sectionValue(LinearLayout root, String label, String value) {
        sectionLabel(root, label);
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(14);
        text.setGravity(Gravity.START);
        root.addView(text, matchWrap());
        return text;
    }

    private void renderSnapshot() {
        DetectionStore.DetectionSnapshot snapshot = DetectionStore.getSnapshot();
        statusText.setText(snapshot.status);
        startStopButton.setText(DetectionStore.isRunning() ? "停止" : "ライブ検出を開始");
        performanceText.setText(String.format(
                Locale.US,
                "入力: %d×%d\n処理FPS: %.1f\n推論: %.1f ms\n前処理: %.1f ms\n後処理: %.1f ms\n合計: %.1f ms\n処理フレーム: %d\n現在の検出数: %d",
                snapshot.inputSize,
                snapshot.inputSize,
                snapshot.fps,
                snapshot.inferenceMs,
                snapshot.preprocessMs,
                snapshot.postprocessMs,
                snapshot.pipelineMs,
                snapshot.frameCount,
                snapshot.currentDetections
        ));

        if (snapshot.preview != null && !snapshot.preview.isRecycled()) {
            previewImage.setImageBitmap(snapshot.preview);
        }

        if (snapshot.metrics.isEmpty()) {
            labelsText.setText("まだ検出されていません");
        } else {
            StringBuilder builder = new StringBuilder();
            builder.append(String.format("%-16s %5s %7s %7s %7s\n", "label", "now", "total", "avg", "max"));
            int limit = Math.min(20, snapshot.metrics.size());
            for (int i = 0; i < limit; i++) {
                DetectionStore.LabelMetric metric = snapshot.metrics.get(i);
                builder.append(String.format(
                        Locale.US,
                        "%-16s %5d %7d %7.2f %7.2f\n",
                        metric.label,
                        metric.currentCount,
                        metric.totalCount,
                        metric.averageConfidence,
                        metric.maxConfidence
                ));
            }
            labelsText.setText(builder.toString());
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 41);
        }
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onStart() {
        super.onStart();
        uiHandler.post(refreshTask);
    }

    @Override
    protected void onStop() {
        uiHandler.removeCallbacks(refreshTask);
        super.onStop();
    }
}
