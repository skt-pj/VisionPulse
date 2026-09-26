package com.skt.visionpulse;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
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

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends AppCompatActivity {
    private static final int BG = 0xFF090D12;
    private static final int SURFACE = 0xFF121820;
    private static final int BORDER = 0xFF26313E;
    private static final int TEXT = 0xFFF4F7FA;
    private static final int MUTED = 0xFF94A3B8;
    private static final int ACCENT = 0xFF57D4C3;
    private static final int BLUE = 0xFF67A7FF;
    private static final int WARN = 0xFFFFB55A;
    private static final int DANGER = 0xFFFF6B7A;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private LinearLayout tabBar;
    private TextView status;
    private TextView thresholdValue;
    private Button primaryAction;
    private int selectedTab;

    private ActivityResultLauncher<Intent> captureLauncher;
    private ActivityResultLauncher<Intent> overlayPermissionLauncher;

    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            render();
            uiHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        captureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                        status.setText("画面共有が許可されませんでした");
                        return;
                    }
                    Intent serviceIntent = new Intent(this, CaptureService.class)
                            .setAction(CaptureService.ACTION_START)
                            .putExtra(CaptureService.EXTRA_RESULT_CODE, result.getResultCode())
                            .putExtra(CaptureService.EXTRA_DATA, result.getData());
                    ContextCompat.startForegroundService(this, serviceIntent);
                    status.setText("ライブ検出を開始しています");
                }
        );

        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (Settings.canDrawOverlays(this)) requestScreenCapture();
                    else status.setText("画面上へ検出結果を重ねるにはオーバーレイ権限が必要です");
                }
        );

        setContentView(buildScreen());
        requestNotificationPermissionIfNeeded();
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(18), dp(18), dp(12));
        header.addView(text("VisionPulse", 27, TEXT, Typeface.BOLD));
        TextView subtitle = text("YOLO26n live test console", 12, MUTED, Typeface.BOLD);
        subtitle.setPadding(0, dp(2), 0, dp(12));
        header.addView(subtitle);
        header.addView(buildSetupCard());
        root.addView(header);

        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(dp(12), dp(2), dp(12), dp(8));
        String[] tabs = {"Overview", "Timeline", "Labels", "Compare"};
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            Button button = new Button(this);
            button.setText(tabs[i]);
            button.setTextSize(11);
            button.setAllCaps(false);
            button.setOnClickListener(v -> {
                selectedTab = index;
                render();
            });
            tabBar.addView(button, new LinearLayout.LayoutParams(0, dp(42), 1f));
        }
        root.addView(tabBar);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(2), dp(16), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(16), dp(8), dp(16), dp(14));
        status = text("停止中", 12, MUTED, Typeface.NORMAL);
        status.setPadding(dp(2), 0, 0, dp(7));
        footer.addView(status);

        primaryAction = new Button(this);
        primaryAction.setText("ライブテスト開始");
        primaryAction.setAllCaps(false);
        primaryAction.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        primaryAction.setOnClickListener(v -> {
            if (DetectionStore.isRunning()) {
                stopService(new Intent(this, CaptureService.class));
            } else {
                startLiveMode();
            }
        });
        footer.addView(primaryAction, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        root.addView(footer);

        return root;
    }

    private View buildSetupCard() {
        LinearLayout card = card();
        card.addView(text("TEST SETUP", 11, MUTED, Typeface.BOLD));

        LinearLayout resolutionRow = new LinearLayout(this);
        resolutionRow.setOrientation(LinearLayout.HORIZONTAL);
        resolutionRow.setGravity(Gravity.CENTER_VERTICAL);
        resolutionRow.addView(text("Input resolution", 14, TEXT, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, dp(52), 1f));

        Spinner spinner = new Spinner(this);
        String[] resolutions = {"320", "416", "512", "640"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, resolutions);
        spinner.setAdapter(adapter);
        spinner.setSelection(1);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int[] values = {320, 416, 512, 640};
                DetectionStore.setInputSize(values[position]);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        resolutionRow.addView(spinner, new LinearLayout.LayoutParams(dp(110), dp(52)));
        card.addView(resolutionRow);

        LinearLayout thresholdHeader = new LinearLayout(this);
        thresholdHeader.setOrientation(LinearLayout.HORIZONTAL);
        thresholdHeader.setGravity(Gravity.CENTER_VERTICAL);
        thresholdHeader.addView(text("Confidence threshold", 14, TEXT, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, dp(36), 1f));
        thresholdValue = text("0.25", 14, ACCENT, Typeface.BOLD);
        thresholdHeader.addView(thresholdValue);
        card.addView(thresholdHeader);

        SeekBar threshold = new SeekBar(this);
        threshold.setMax(80);
        threshold.setProgress(15);
        threshold.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        threshold.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        threshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = (progress + 10) / 100f;
                DetectionStore.setConfidenceThreshold(value);
                thresholdValue.setText(String.format(Locale.US, "%.2f", value));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        card.addView(threshold);

        TextView hint = text(
                "開始後はテストしたいアプリへ移動してください。検出枠はその画面上に直接表示されます。",
                12, MUTED, Typeface.NORMAL);
        hint.setPadding(0, dp(7), 0, 0);
        card.addView(hint);
        return card;
    }

    private void render() {
        DetectionStore.DetectionSnapshot snapshot = DetectionStore.getSnapshot();
        DetectionStore.AnalyticsSnapshot analytics = snapshot.analytics;
        boolean running = DetectionStore.isRunning();

        status.setText(running
                ? "ライブ検出中 — 他のアプリへ移動して確認できます"
                : snapshot.status);
        primaryAction.setText(running ? "ライブテスト停止" : "ライブテスト開始");
        primaryAction.setTextColor(running ? TEXT : 0xFF07100E);
        primaryAction.setBackground(roundRect(running ? 0xFF542832 : ACCENT, 14));

        for (int i = 0; i < tabBar.getChildCount(); i++) {
            Button button = (Button) tabBar.getChildAt(i);
            boolean selected = i == selectedTab;
            button.setTextColor(selected ? ACCENT : MUTED);
            button.setBackground(roundRect(selected ? 0xFF15302D : BG, 12));
        }

        content.removeAllViews();
        if (selectedTab == 0) renderOverview(analytics, snapshot, running);
        else if (selectedTab == 1) renderTimeline(analytics);
        else if (selectedTab == 2) renderLabels(analytics);
        else renderCompare();
    }

    private void renderOverview(DetectionStore.AnalyticsSnapshot a,
            DetectionStore.DetectionSnapshot s, boolean running) {
        addSection("WHAT DID THE MODEL ACTUALLY DO?",
                "単純な検出回数ではなく、継続性・揺れ・処理性能で判断します。");

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        metric(row1, "Stable", String.valueOf(a.stableEpisodes), "1秒以上・継続検出", ACCENT);
        metric(row1, "Unstable", String.valueOf(a.unstableEpisodes), "短時間・途切れが多い", WARN);
        content.addView(row1, full());

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        metric(row2, "Avg episode", formatSeconds(a.averageEpisodeMs), "認識の継続時間", BLUE);
        metric(row2, "Label switches", String.valueOf(a.labelSwitches), "同位置でクラス変化", DANGER);
        LinearLayout.LayoutParams p2 = full();
        p2.topMargin = dp(9);
        content.addView(row2, p2);

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        metric(row3, "Reacquired", String.valueOf(a.lostAndReacquired), "消失後に再検出", WARN);
        metric(row3, "Avg confidence", percent(a.averageConfidence), "確信度。正解率ではない", TEXT);
        LinearLayout.LayoutParams p3 = full();
        p3.topMargin = dp(9);
        content.addView(row3, p3);

        addSection("PERFORMANCE", "同じ認識品質をどのコストで出せたか");
        LinearLayout perf = card();
        perf.addView(kv("Average FPS", String.format(Locale.US, "%.1f", a.averageFps)));
        perf.addView(kv("P50 pipeline", String.format(Locale.US, "%.1f ms", a.p50PipelineMs)));
        perf.addView(kv("P95 pipeline", String.format(Locale.US, "%.1f ms", a.p95PipelineMs)));
        perf.addView(kv("Dropped frames", percent(a.droppedFrameRate)));
        perf.addView(kv("Processed frames", String.format(Locale.US, "%,d", a.processedFrames)));
        perf.addView(kv("Current objects", String.valueOf(s.currentDetections)));
        content.addView(perf, full());

        if (!running && a.processedFrames > 0) {
            TextView conclusion = text(buildSummarySentence(a), 14, TEXT, Typeface.BOLD);
            conclusion.setPadding(dp(14), dp(14), dp(14), dp(14));
            conclusion.setBackground(roundRect(0xFF14211F, 14));
            LinearLayout.LayoutParams cp = full();
            cp.topMargin = dp(12);
            content.addView(conclusion, cp);
        }
    }

    private void renderTimeline(DetectionStore.AnalyticsSnapshot a) {
        addSection("DETECTION TIMELINE",
                "何がいつ安定して見え、どこで途切れたかを確認します。");
        if (a.episodes.isEmpty()) {
            empty("まだ検出エピソードがありません");
            return;
        }

        TimelineView timeline = new TimelineView(this);
        timeline.setData(a.episodes, Math.max(1000, a.durationMs));
        content.addView(timeline, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(dp(220), dp(42) * Math.min(12, a.episodes.size()))));

        TextView note = text(
                "緑=安定度70%以上 / 黄=それ未満。短い点滅が多いラベルは誤検出候補として確認してください。",
                12, MUTED, Typeface.NORMAL);
        note.setPadding(0, dp(10), 0, 0);
        content.addView(note);
    }

    private void renderLabels(DetectionStore.AnalyticsSnapshot a) {
        addSection("LABEL BEHAVIOR",
                "「何回出たか」ではなく、どれだけ長く・安定して認識したか");
        if (a.labels.isEmpty()) {
            empty("まだラベルデータがありません");
            return;
        }

        for (DetectionStore.LabelBehavior label : a.labels) {
            LinearLayout item = card();
            LinearLayout heading = new LinearLayout(this);
            heading.setOrientation(LinearLayout.HORIZONTAL);
            heading.addView(text(label.label, 17, TEXT, Typeface.BOLD),
                    new LinearLayout.LayoutParams(0, dp(30), 1f));
            TextView live = text(label.activeNow > 0 ? "LIVE" : "", 11, ACCENT, Typeface.BOLD);
            live.setGravity(Gravity.END);
            heading.addView(live);
            item.addView(heading);

            item.addView(kv("Episodes", String.valueOf(label.episodes)));
            item.addView(kv("Visible time", formatSeconds(label.visibleMs)));
            item.addView(kv("Stability", percent(label.stability)));
            item.addView(kv("Average confidence", percent(label.averageConfidence)));
            item.addView(kv("Confidence range",
                    String.format(Locale.US, "%.2f — %.2f",
                            label.minConfidence, label.maxConfidence)));

            TextView interpretation = text(
                    labelInterpretation(label), 12,
                    label.stability >= 0.70 ? ACCENT : WARN,
                    Typeface.BOLD);
            interpretation.setPadding(0, dp(8), 0, 0);
            item.addView(interpretation);

            LinearLayout.LayoutParams p = full();
            p.bottomMargin = dp(9);
            content.addView(item, p);
        }
    }

    private void renderCompare() {
        addSection("SESSION COMPARE",
                "解像度やthresholdを変えたテストを、速度と検出安定性で比較します。");

        List<SessionHistory.SessionSummary> sessions = SessionHistory.load(this);
        if (sessions.isEmpty()) {
            empty("終了したテストセッションがまだありません");
            return;
        }

        for (SessionHistory.SessionSummary s : sessions) {
            LinearLayout item = card();
            item.addView(text(
                    s.inputSize + " px  ·  conf " + String.format(Locale.US, "%.2f", s.threshold),
                    16, TEXT, Typeface.BOLD));
            TextView date = text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(new Date(s.startedAt)),
                    11, MUTED, Typeface.NORMAL);
            date.setPadding(0, dp(2), 0, dp(8));
            item.addView(date);
            item.addView(kv("Average FPS", String.format(Locale.US, "%.1f", s.averageFps)));
            item.addView(kv("P95 latency", String.format(Locale.US, "%.1f ms", s.p95LatencyMs)));
            item.addView(kv("Stable / unstable", s.stableEpisodes + " / " + s.unstableEpisodes));
            item.addView(kv("Dropped frames", percent(s.dropRate)));
            item.addView(kv("Avg confidence", percent(s.averageConfidence)));

            LinearLayout.LayoutParams p = full();
            p.bottomMargin = dp(9);
            content.addView(item, p);
        }
    }

    private String buildSummarySentence(DetectionStore.AnalyticsSnapshot a) {
        int total = a.stableEpisodes + a.unstableEpisodes;
        double stableRate = total == 0 ? 0 : a.stableEpisodes / (double) total;
        if (total == 0) return "このセッションでは有効な検出エピソードがありませんでした。";
        if (stableRate >= 0.75 && a.droppedFrameRate < 0.20) {
            return "検出は比較的継続しています。次は解像度を下げ、同じ安定性を維持できるか比較できます。";
        }
        if (a.droppedFrameRate >= 0.35) {
            return "処理落ちが大きいため、入力解像度を下げて再テストする価値があります。";
        }
        return "短時間・途切れの検出が目立ちます。TimelineとLabelsで不安定なクラスを確認してください。";
    }

    private String labelInterpretation(DetectionStore.LabelBehavior label) {
        if (label.stability >= 0.85 && label.visibleMs >= 1500) return "継続して認識できています";
        if (label.stability >= 0.65) return "概ね継続するが、途切れがあります";
        if (label.visibleMs < 700) return "短時間の点滅が多く、誤検出候補です";
        return "認識が不安定です";
    }

    private void addSection(String title, String subtitle) {
        TextView t = text(title, 12, TEXT, Typeface.BOLD);
        t.setPadding(dp(2), dp(16), 0, dp(2));
        content.addView(t);
        TextView s = text(subtitle, 12, MUTED, Typeface.NORMAL);
        s.setPadding(dp(2), 0, 0, dp(9));
        content.addView(s);
    }

    private void metric(LinearLayout row, String title, String value, String note, int valueColor) {
        LinearLayout box = card();
        box.addView(text(title, 11, MUTED, Typeface.BOLD));
        TextView v = text(value, 23, valueColor, Typeface.BOLD);
        v.setPadding(0, dp(5), 0, dp(2));
        box.addView(v);
        box.addView(text(note, 10, MUTED, Typeface.NORMAL));

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (row.getChildCount() > 0) p.leftMargin = dp(9);
        row.addView(box, p);
    }

    private View kv(String left, String right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(5), 0, dp(5));
        row.addView(text(left, 12, MUTED, Typeface.NORMAL),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = text(right, 13, TEXT, Typeface.BOLD);
        value.setGravity(Gravity.END);
        row.addView(value);
        return row;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(roundRect(SURFACE, 14));
        return card;
    }

    private void empty(String message) {
        TextView view = text(message, 14, MUTED, Typeface.NORMAL);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(20), dp(40), dp(20), dp(40));
        content.addView(view);
    }

    private void startLiveMode() {
        if (!Settings.canDrawOverlays(this)) {
            overlayPermissionLauncher.launch(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        requestScreenCapture();
    }

    private void requestScreenCapture() {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        captureLauncher.launch(manager.createScreenCaptureIntent());
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 41);
        }
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), BORDER);
        return drawable;
    }

    private LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private String formatSeconds(double ms) {
        return String.format(Locale.US, "%.1f s", ms / 1000.0);
    }

    private String percent(double value) {
        return String.format(Locale.US, "%.0f%%", value * 100.0);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onStart() {
        super.onStart();
        uiHandler.post(refreshTask);
    }

    @Override protected void onStop() {
        uiHandler.removeCallbacks(refreshTask);
        super.onStop();
    }

    private static final class TimelineView extends View {
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint railPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stablePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint unstablePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private List<DetectionStore.EpisodeView> episodes = java.util.Collections.emptyList();
        private long durationMs = 1000;

        TimelineView(Context context) {
            super(context);
            textPaint.setColor(TEXT);
            textPaint.setTextSize(sp(context, 11));
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            railPaint.setColor(BORDER);
            stablePaint.setColor(ACCENT);
            unstablePaint.setColor(WARN);
        }

        void setData(List<DetectionStore.EpisodeView> episodes, long durationMs) {
            this.episodes = episodes;
            this.durationMs = durationMs;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int count = Math.min(12, episodes.size());
            if (count == 0) return;

            float row = getHeight() / (float) count;
            float labelWidth = Math.min(dp(getContext(), 100), getWidth() * 0.30f);
            float right = getWidth() - dp(getContext(), 6);

            for (int i = 0; i < count; i++) {
                DetectionStore.EpisodeView e = episodes.get(episodes.size() - count + i);
                float y = i * row;
                canvas.drawText(trim(e.label, 13), dp(getContext(), 4),
                        y + row * 0.62f, textPaint);

                RectF rail = new RectF(
                        labelWidth, y + row * 0.35f, right, y + row * 0.65f);
                canvas.drawRoundRect(rail, row * 0.12f, row * 0.12f, railPaint);

                float start = labelWidth
                        + (right - labelWidth) * e.startMs / (float) durationMs;
                float end = labelWidth
                        + (right - labelWidth)
                        * Math.max(e.startMs + 50, e.endMs) / (float) durationMs;
                RectF bar = new RectF(
                        start, y + row * 0.35f, Math.min(right, end), y + row * 0.65f);
                canvas.drawRoundRect(
                        bar, row * 0.12f, row * 0.12f,
                        e.stability >= 0.70 ? stablePaint : unstablePaint);
            }
        }

        private String trim(String value, int max) {
            return value.length() <= max ? value : value.substring(0, max - 1) + "…";
        }

        private static float dp(Context context, int value) {
            return value * context.getResources().getDisplayMetrics().density;
        }

        private static float sp(Context context, int value) {
            return value * context.getResources().getDisplayMetrics().scaledDensity;
        }
    }
}
