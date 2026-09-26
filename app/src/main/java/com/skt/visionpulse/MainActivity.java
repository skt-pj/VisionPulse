package com.skt.visionpulse;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends AppCompatActivity {
    private static final int BG = 0xFF090D12;
    private static final int SURFACE = 0xFF121820;
    private static final int SURFACE_ALT = 0xFF171F29;
    private static final int BORDER = 0xFF26313E;
    private static final int TEXT = 0xFFF4F7FA;
    private static final int MUTED = 0xFF94A3B8;
    private static final int ACCENT = 0xFF57D4C3;
    private static final int BLUE = 0xFF67A7FF;
    private static final int WARN = 0xFFFFB55A;
    private static final int DANGER = 0xFFFF6B7A;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private TextView statusText;
    private TextView statusPill;
    private TextView fpsValue;
    private TextView latencyValue;
    private TextView detectionsValue;
    private TextView framesValue;
    private TextView preprocessValue;
    private TextView inferenceValue;
    private TextView postprocessValue;
    private TextView thresholdValue;
    private TextView previewPlaceholder;
    private ImageView previewImage;
    private Button startStopButton;
    private PerformanceChartView performanceChart;
    private DetectionBarsView detectionBars;

    private ActivityResultLauncher<Intent> captureLauncher;
    private ActivityResultLauncher<Intent> overlayPermissionLauncher;

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            renderSnapshot();
            uiHandler.postDelayed(this, 400);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);

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
                    statusText.setText("画面キャプチャを開始しています。");
                }
        );

        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (Settings.canDrawOverlays(this)) {
                        requestScreenCapture();
                    } else {
                        statusText.setText("検出結果を重ねるにはオーバーレイ権限が必要です");
                    }
                }
        );

        setContentView(buildUi());
        requestNotificationPermissionIfNeeded();
    }

    private View buildUi() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(BG);

        screen.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(4), dp(16), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        addSectionHeader(content, "LIVE VIEW", "YOLO26n · COCO 80 classes");
        content.addView(buildPreviewCard(), fullWidth(dp(320)));

        addSectionHeader(content, "SESSION", "Live inference health");
        content.addView(buildKpiGrid(), fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT));

        addSectionHeader(content, "PERFORMANCE", "Rolling device-side runtime");
        content.addView(buildPerformanceCard(), fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT));

        addSectionHeader(content, "DETECTIONS", "Top classes by observed count");
        content.addView(buildDetectionCard(), fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT));

        addSectionHeader(content, "PIPELINE", "Latest frame breakdown");
        content.addView(buildPipelineCard(), fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT));

        addSectionHeader(content, "INFERENCE SETTINGS", "Changes apply to the live detector");
        content.addView(buildSettingsCard(), fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT));

        statusText = text("停止中", 13, MUTED, Typeface.NORMAL);
        statusText.setPadding(dp(2), dp(14), dp(2), 0);
        content.addView(statusText, matchWrap());

        screen.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        screen.addView(buildBottomAction(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return screen;
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), dp(18), dp(18), dp(12));

        LinearLayout titleGroup = new LinearLayout(this);
        titleGroup.setOrientation(LinearLayout.VERTICAL);

        TextView title = text("VisionPulse", 25, TEXT, Typeface.BOLD);
        TextView subtitle = text("ON-DEVICE VISION CONSOLE", 11, MUTED, Typeface.BOLD);
        subtitle.setLetterSpacing(0.08f);
        titleGroup.addView(title);
        titleGroup.addView(subtitle);

        bar.addView(titleGroup, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        statusPill = text("IDLE", 11, MUTED, Typeface.BOLD);
        statusPill.setGravity(Gravity.CENTER);
        statusPill.setPadding(dp(12), dp(7), dp(12), dp(7));
        statusPill.setBackground(roundRect(SURFACE_ALT, 999, BORDER, 1));
        bar.addView(statusPill);

        return bar;
    }

    private View buildPreviewCard() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(roundRect(0xFF05080C, 16, BORDER, 1));
        frame.setPadding(dp(1), dp(1), dp(1), dp(1));

        previewImage = new ImageView(this);
        previewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewImage.setBackgroundColor(0xFF05080C);
        previewImage.setContentDescription("Live detection preview");
        frame.addView(previewImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        previewPlaceholder = text("WAITING FOR CAPTURE", 12, MUTED, Typeface.BOLD);
        previewPlaceholder.setGravity(Gravity.CENTER);
        previewPlaceholder.setLetterSpacing(0.08f);
        frame.addView(previewPlaceholder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView liveBadge = text(" LIVE ", 10, TEXT, Typeface.BOLD);
        liveBadge.setGravity(Gravity.CENTER);
        liveBadge.setPadding(dp(7), dp(4), dp(7), dp(4));
        liveBadge.setBackground(roundRect(0xCC1C2631, 7, 0, 0));
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START
        );
        badgeParams.setMargins(dp(12), dp(12), 0, 0);
        frame.addView(liveBadge, badgeParams);
        return frame;
    }

    private View buildKpiGrid() {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        fpsValue = metricCard(row1, "FPS", "0.0", "frames / sec", ACCENT);
        latencyValue = metricCard(row1, "LATENCY", "0.0 ms", "pipeline", BLUE);
        group.addView(row1, fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        detectionsValue = metricCard(row2, "OBJECTS", "0", "current frame", WARN);
        framesValue = metricCard(row2, "FRAMES", "0", "processed", TEXT);
        LinearLayout.LayoutParams rowParams = fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(10);
        group.addView(row2, rowParams);

        return group;
    }

    private TextView metricCard(LinearLayout row, String label, String initial, String caption, int accent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(12));
        card.setBackground(roundRect(SURFACE, 14, BORDER, 1));

        TextView labelView = text(label, 10, MUTED, Typeface.BOLD);
        labelView.setLetterSpacing(0.08f);
        TextView valueView = text(initial, 24, accent, Typeface.BOLD);
        valueView.setPadding(0, dp(5), 0, dp(2));
        TextView captionView = text(caption, 11, MUTED, Typeface.NORMAL);

        card.addView(labelView);
        card.addView(valueView);
        card.addView(captionView);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (row.getChildCount() > 0) params.leftMargin = dp(10);
        row.addView(card, params);
        return valueView;
    }

    private View buildPerformanceCard() {
        LinearLayout card = card();
        TextView title = text("Runtime trend", 16, TEXT, Typeface.BOLD);
        TextView subtitle = text("FPS and end-to-end latency · latest 60 samples", 12, MUTED, Typeface.NORMAL);
        subtitle.setPadding(0, dp(3), 0, dp(10));
        card.addView(title);
        card.addView(subtitle);

        performanceChart = new PerformanceChartView(this);
        card.addView(performanceChart, fullWidth(dp(154)));

        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setPadding(0, dp(8), 0, 0);
        legend.addView(legendItem("FPS", ACCENT), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        legend.addView(legendItem("Latency", BLUE), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(legend);
        return card;
    }

    private View legendItem(String label, int color) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        dot.setBackground(roundRect(color, 999, 0, 0));
        item.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));

        TextView text = text(label, 11, MUTED, Typeface.BOLD);
        text.setPadding(dp(7), 0, 0, 0);
        item.addView(text);
        return item;
    }

    private View buildDetectionCard() {
        LinearLayout card = card();
        TextView title = text("Class activity", 16, TEXT, Typeface.BOLD);
        TextView subtitle = text("Current / cumulative count with average confidence", 12, MUTED, Typeface.NORMAL);
        subtitle.setPadding(0, dp(3), 0, dp(8));
        card.addView(title);
        card.addView(subtitle);

        detectionBars = new DetectionBarsView(this);
        card.addView(detectionBars, fullWidth(dp(230)));
        return card;
    }

    private View buildPipelineCard() {
        LinearLayout card = card();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        preprocessValue = compactMetric(row, "PRE", "0.0 ms");
        inferenceValue = compactMetric(row, "INFER", "0.0 ms");
        postprocessValue = compactMetric(row, "POST", "0.0 ms");

        card.addView(row, fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private TextView compactMetric(LinearLayout row, String label, String initial) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(12), dp(10), dp(12), dp(10));
        cell.setBackground(roundRect(SURFACE_ALT, 12, 0, 0));

        TextView labelView = text(label, 10, MUTED, Typeface.BOLD);
        TextView value = text(initial, 16, TEXT, Typeface.BOLD);
        value.setPadding(0, dp(4), 0, 0);
        cell.addView(labelView);
        cell.addView(value);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (row.getChildCount() > 0) params.leftMargin = dp(8);
        row.addView(cell, params);
        return value;
    }

    private View buildSettingsCard() {
        LinearLayout card = card();

        TextView inputLabel = text("Input resolution", 12, MUTED, Typeface.BOLD);
        card.addView(inputLabel);

        Spinner resolutionSpinner = new Spinner(this);
        String[] resolutions = {"320 × 320", "416 × 416", "512 × 512", "640 × 640"};
        ArrayAdapter<String> resolutionAdapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                resolutions
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(TEXT);
                view.setTextSize(15);
                return view;
            }
        };
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
        resolutionSpinner.setBackground(roundRect(SURFACE_ALT, 10, BORDER, 1));
        LinearLayout.LayoutParams spinnerParams = fullWidth(dp(48));
        spinnerParams.topMargin = dp(7);
        card.addView(resolutionSpinner, spinnerParams);

        LinearLayout thresholdHeader = new LinearLayout(this);
        thresholdHeader.setOrientation(LinearLayout.HORIZONTAL);
        thresholdHeader.setGravity(Gravity.CENTER_VERTICAL);
        thresholdHeader.setPadding(0, dp(16), 0, 0);

        TextView thresholdLabel = text("Confidence threshold", 12, MUTED, Typeface.BOLD);
        thresholdValue = text("0.25", 13, ACCENT, Typeface.BOLD);
        thresholdValue.setGravity(Gravity.END);

        thresholdHeader.addView(thresholdLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        thresholdHeader.addView(thresholdValue);
        card.addView(thresholdHeader);

        SeekBar thresholdSeek = new SeekBar(this);
        thresholdSeek.setMax(80);
        thresholdSeek.setProgress(15);
        thresholdSeek.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        thresholdSeek.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        thresholdSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = (progress + 10) / 100f;
                DetectionStore.setConfidenceThreshold(value);
                thresholdValue.setText(String.format(Locale.US, "%.2f", value));
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        card.addView(thresholdSeek, fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private View buildBottomAction() {
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.VERTICAL);
        dock.setPadding(dp(16), dp(10), dp(16), dp(14));
        dock.setBackgroundColor(BG);

        startStopButton = new Button(this);
        startStopButton.setText("START LIVE DETECTION");
        startStopButton.setTextColor(0xFF07100E);
        startStopButton.setTextSize(14);
        startStopButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        startStopButton.setAllCaps(false);
        startStopButton.setLetterSpacing(0.04f);
        startStopButton.setBackground(roundRect(ACCENT, 13, 0, 0));
        startStopButton.setOnClickListener(v -> {
            if (DetectionStore.isRunning()) {
                stopService(new Intent(this, CaptureService.class));
            } else {
                startLiveMode();
            }
        });
        dock.addView(startStopButton, fullWidth(dp(54)));
        return dock;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(15), dp(15), dp(15));
        card.setBackground(roundRect(SURFACE, 14, BORDER, 1));
        return card;
    }

    private void addSectionHeader(LinearLayout root, String title, String subtitle) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.BOTTOM);
        header.setPadding(dp(2), dp(18), dp(2), dp(8));

        TextView titleView = text(title, 11, TEXT, Typeface.BOLD);
        titleView.setLetterSpacing(0.10f);
        TextView subtitleView = text(subtitle, 11, MUTED, Typeface.NORMAL);
        subtitleView.setGravity(Gravity.END);

        header.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(subtitleView);
        root.addView(header);
    }

    private void startLiveMode() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            overlayPermissionLauncher.launch(intent);
            return;
        }
        requestScreenCapture();
    }

    private void requestScreenCapture() {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        captureLauncher.launch(manager.createScreenCaptureIntent());
    }

    private void renderSnapshot() {
        DetectionStore.DetectionSnapshot snapshot = DetectionStore.getSnapshot();
        boolean running = DetectionStore.isRunning();

        statusText.setText(snapshot.status);
        statusPill.setText(running ? "● LIVE" : "IDLE");
        statusPill.setTextColor(running ? ACCENT : MUTED);
        statusPill.setBackground(roundRect(running ? 0xFF12312C : SURFACE_ALT, 999, running ? 0xFF285E55 : BORDER, 1));

        startStopButton.setText(running ? "STOP LIVE DETECTION" : "START LIVE DETECTION");
        startStopButton.setTextColor(running ? TEXT : 0xFF07100E);
        startStopButton.setBackground(roundRect(running ? 0xFF3A2027 : ACCENT, 13, running ? 0xFF6B3340 : 0, running ? 1 : 0));

        fpsValue.setText(String.format(Locale.US, "%.1f", snapshot.fps));
        latencyValue.setText(String.format(Locale.US, "%.1f ms", snapshot.pipelineMs));
        detectionsValue.setText(String.valueOf(snapshot.currentDetections));
        framesValue.setText(String.format(Locale.US, "%,d", snapshot.frameCount));

        preprocessValue.setText(String.format(Locale.US, "%.1f ms", snapshot.preprocessMs));
        inferenceValue.setText(String.format(Locale.US, "%.1f ms", snapshot.inferenceMs));
        postprocessValue.setText(String.format(Locale.US, "%.1f ms", snapshot.postprocessMs));

        if (running && snapshot.frameCount > 0) {
            performanceChart.push((float) snapshot.fps, (float) snapshot.pipelineMs);
        }

        if (snapshot.preview != null && !snapshot.preview.isRecycled()) {
            previewImage.setImageBitmap(snapshot.preview);
            previewPlaceholder.setVisibility(View.GONE);
        } else {
            previewPlaceholder.setVisibility(View.VISIBLE);
        }

        detectionBars.setMetrics(snapshot.metrics);
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private GradientDrawable roundRect(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams fullWidth(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
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

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 41);
        }
    }

    private static final class PerformanceChartView extends View {
        private static final int MAX_SAMPLES = 60;
        private final List<Float> fps = new ArrayList<>();
        private final List<Float> latency = new ArrayList<>();
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fpsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint latencyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        PerformanceChartView(Context context) {
            super(context);
            gridPaint.setColor(BORDER);
            gridPaint.setStrokeWidth(1f);

            fpsPaint.setColor(ACCENT);
            fpsPaint.setStrokeWidth(3f);
            fpsPaint.setStyle(Paint.Style.STROKE);
            fpsPaint.setStrokeCap(Paint.Cap.ROUND);
            fpsPaint.setStrokeJoin(Paint.Join.ROUND);

            latencyPaint.setColor(BLUE);
            latencyPaint.setStrokeWidth(3f);
            latencyPaint.setStyle(Paint.Style.STROKE);
            latencyPaint.setStrokeCap(Paint.Cap.ROUND);
            latencyPaint.setStrokeJoin(Paint.Join.ROUND);

            labelPaint.setColor(MUTED);
            labelPaint.setTextSize(sp(context, 9));
        }

        void push(float fpsValue, float latencyValue) {
            fps.add(Math.max(0f, fpsValue));
            latency.add(Math.max(0f, latencyValue));
            while (fps.size() > MAX_SAMPLES) fps.remove(0);
            while (latency.size() > MAX_SAMPLES) latency.remove(0);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float left = dp(getContext(), 2);
            float top = dp(getContext(), 8);
            float right = getWidth() - dp(getContext(), 2);
            float bottom = getHeight() - dp(getContext(), 18);

            for (int i = 0; i <= 3; i++) {
                float y = top + (bottom - top) * i / 3f;
                canvas.drawLine(left, y, right, y, gridPaint);
            }

            canvas.drawText("60", left, top + labelPaint.getTextSize(), labelPaint);
            canvas.drawText("0", left, bottom + dp(getContext(), 13), labelPaint);

            if (fps.size() < 2) return;

            float maxFps = 60f;
            float maxLatency = 120f;
            for (float v : fps) maxFps = Math.max(maxFps, v * 1.15f);
            for (float v : latency) maxLatency = Math.max(maxLatency, v * 1.15f);

            drawSeries(canvas, fps, left, top, right, bottom, maxFps, fpsPaint);
            drawSeries(canvas, latency, left, top, right, bottom, maxLatency, latencyPaint);
        }

        private void drawSeries(Canvas canvas, List<Float> values, float left, float top, float right, float bottom, float max, Paint paint) {
            if (values.size() < 2) return;
            Path path = new Path();
            for (int i = 0; i < values.size(); i++) {
                float x = left + (right - left) * i / Math.max(1, MAX_SAMPLES - 1);
                float normalized = Math.min(1f, values.get(i) / Math.max(1f, max));
                float y = bottom - normalized * (bottom - top);
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            canvas.drawPath(path, paint);
        }
    }

    private static final class DetectionBarsView extends View {
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<DetectionStore.LabelMetric> metrics = new ArrayList<>();

        DetectionBarsView(Context context) {
            super(context);
            labelPaint.setColor(TEXT);
            labelPaint.setTextSize(sp(context, 11));
            labelPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

            valuePaint.setColor(MUTED);
            valuePaint.setTextSize(sp(context, 10));

            trackPaint.setColor(SURFACE_ALT);
            barPaint.setColor(ACCENT);
        }

        void setMetrics(List<DetectionStore.LabelMetric> values) {
            metrics.clear();
            if (values != null) metrics.addAll(values);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (metrics.isEmpty()) {
                Paint empty = new Paint(Paint.ANTI_ALIAS_FLAG);
                empty.setColor(MUTED);
                empty.setTextSize(sp(getContext(), 12));
                canvas.drawText("No detections yet", dp(getContext(), 2), dp(getContext(), 28), empty);
                return;
            }

            int count = Math.min(6, metrics.size());
            long maxTotal = 1;
            for (int i = 0; i < count; i++) {
                maxTotal = Math.max(maxTotal, metrics.get(i).totalCount);
            }

            float rowHeight = getHeight() / (float) count;
            float labelWidth = Math.min(dp(getContext(), 112), getWidth() * 0.34f);
            float right = getWidth() - dp(getContext(), 4);
            float barLeft = labelWidth;
            float barHeight = dp(getContext(), 9);

            for (int i = 0; i < count; i++) {
                DetectionStore.LabelMetric metric = metrics.get(i);
                float y = i * rowHeight;
                float baseline = y + dp(getContext(), 18);
                canvas.drawText(ellipsize(metric.label, 14), dp(getContext(), 2), baseline, labelPaint);

                String detail = String.format(
                        Locale.US,
                        "%d now · %,d total · %.0f%%",
                        metric.currentCount,
                        metric.totalCount,
                        metric.averageConfidence * 100.0
                );
                canvas.drawText(detail, dp(getContext(), 2), baseline + dp(getContext(), 15), valuePaint);

                float barTop = y + rowHeight - dp(getContext(), 16);
                RectF track = new RectF(barLeft, barTop, right, barTop + barHeight);
                canvas.drawRoundRect(track, barHeight / 2f, barHeight / 2f, trackPaint);

                float width = (right - barLeft) * metric.totalCount / (float) maxTotal;
                RectF value = new RectF(barLeft, barTop, barLeft + Math.max(dp(getContext(), 5), width), barTop + barHeight);
                canvas.drawRoundRect(value, barHeight / 2f, barHeight / 2f, barPaint);
            }
        }

        private String ellipsize(String value, int max) {
            if (value == null) return "unknown";
            if (value.length() <= max) return value;
            return value.substring(0, Math.max(1, max - 1)) + "…";
        }
    }

    private static float dp(Context context, int value) {
        return value * context.getResources().getDisplayMetrics().density;
    }

    private static float sp(Context context, int value) {
        return value * context.getResources().getDisplayMetrics().scaledDensity;
    }
}
