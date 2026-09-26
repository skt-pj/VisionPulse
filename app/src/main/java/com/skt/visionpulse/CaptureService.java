package com.skt.visionpulse;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CaptureService extends Service {
    public static final String ACTION_START = "com.skt.visionpulse.START_CAPTURE";
    public static final String ACTION_STOP = "com.skt.visionpulse.STOP_CAPTURE";
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_DATA = "data";

    private static final String CHANNEL_ID = "visionpulse_capture";
    private static final int NOTIFICATION_ID = 2601;

    private final AtomicBoolean processing = new AtomicBoolean(false);
    private final ExecutorService inferenceExecutor = Executors.newSingleThreadExecutor();
    private final Map<Integer, LabelStats> cumulativeStats = new HashMap<>();
    private final Handler mainHandler = new Handler(android.os.Looper.getMainLooper());

    private HandlerThread captureThread;
    private Handler captureHandler;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private YoloDetector detector;
    private WindowManager windowManager;
    private DetectionOverlayView overlayView;
    private int captureWidth;
    private int captureHeight;
    private long frameCount;
    private long fpsWindowStartNs;
    private int fpsWindowFrames;
    private double lastFps;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        captureThread = new HandlerThread("VisionPulseCapture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());
        fpsWindowStartNs = System.nanoTime();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action)) return START_NOT_STICKY;

        if (!Settings.canDrawOverlays(this)) {
            publishError("オーバーレイ権限がありません");
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, createNotification("YOLO26nを準備中"));
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent data;
        if (Build.VERSION.SDK_INT >= 33) {
            data = intent.getParcelableExtra(EXTRA_DATA, Intent.class);
        } else {
            //noinspection deprecation
            data = intent.getParcelableExtra(EXTRA_DATA);
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            publishError("画面共有の許可情報がありません");
            stopSelf();
            return START_NOT_STICKY;
        }

        final Intent projectionData = new Intent(data);
        inferenceExecutor.execute(() -> {
            try {
                detector = new YoloDetector(getApplicationContext());
                captureHandler.post(() -> startProjection(resultCode, projectionData));
            } catch (Exception e) {
                publishError("モデル初期化失敗: " + e.getClass().getSimpleName());
                stopSelf();
            }
        });
        return START_NOT_STICKY;
    }

    private void startProjection(int resultCode, Intent data) {
        try {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, data);
            projection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    stopSelf();
                }
            }, captureHandler);

            DisplayMetrics metrics = getResources().getDisplayMetrics();
            captureWidth = metrics.widthPixels;
            captureHeight = metrics.heightPixels;
            int density = metrics.densityDpi;

            imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2);
            imageReader.setOnImageAvailableListener(this::onImageAvailable, captureHandler);
            virtualDisplay = projection.createVirtualDisplay(
                    "VisionPulseScreen",
                    captureWidth,
                    captureHeight,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null,
                    captureHandler
            );

            mainHandler.post(this::showOverlay);
            DetectionStore.setRunning(true);
            DetectionStore.publish(new DetectionStore.DetectionSnapshot(
                    "ライブ検出中", 0, DetectionStore.getInputSize(), 0, 0,
                    0, 0, 0, 0, null, new ArrayList<>()
            ));
            updateNotification("ライブ検出中");
        } catch (Exception e) {
            publishError("画面キャプチャ開始失敗: " + e.getClass().getSimpleName());
            stopSelf();
        }
    }

    private void showOverlay() {
        if (overlayView != null) return;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = new DetectionOverlayView(this);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(overlayView, params);
    }

    private void onImageAvailable(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        if (image == null) return;
        if (!processing.compareAndSet(false, true)) {
            image.close();
            return;
        }

        Bitmap frame;
        try {
            frame = imageToBitmap(image);
        } catch (Throwable t) {
            image.close();
            processing.set(false);
            publishError("フレーム変換失敗: " + t.getClass().getSimpleName());
            return;
        }
        image.close();

        inferenceExecutor.execute(() -> {
            try {
                int inputSize = DetectionStore.getInputSize();
                float threshold = DetectionStore.getConfidenceThreshold();
                YoloDetector.Result result = detector.detect(frame, inputSize, threshold);
                updateStats(inputSize, result);
                List<YoloDetector.Detection> overlayDetections = new ArrayList<>(result.detections);
                mainHandler.post(() -> {
                    if (overlayView != null) {
                        overlayView.updateDetections(
                                overlayDetections,
                                captureWidth,
                                captureHeight,
                                lastFps,
                                result.inferenceMs,
                                inputSize
                        );
                    }
                });
            } catch (Throwable t) {
                publishError("推論失敗: " + t.getClass().getSimpleName());
            } finally {
                frame.recycle();
                processing.set(false);
            }
        });
    }

    private void updateStats(int inputSize, YoloDetector.Result result) {
        frameCount++;
        fpsWindowFrames++;
        long now = System.nanoTime();
        double elapsedSec = (now - fpsWindowStartNs) / 1_000_000_000.0;
        if (elapsedSec >= 1.0) {
            lastFps = fpsWindowFrames / elapsedSec;
            fpsWindowFrames = 0;
            fpsWindowStartNs = now;
        }

        int[] currentCounts = new int[CocoLabels.NAMES.length];
        for (YoloDetector.Detection detection : result.detections) {
            currentCounts[detection.classId]++;
            LabelStats stats = cumulativeStats.computeIfAbsent(detection.classId, key -> new LabelStats());
            stats.totalCount++;
            stats.confidenceSum += detection.confidence;
            stats.maxConfidence = Math.max(stats.maxConfidence, detection.confidence);
        }

        List<DetectionStore.LabelMetric> metrics = new ArrayList<>();
        for (int classId = 0; classId < CocoLabels.NAMES.length; classId++) {
            LabelStats stats = cumulativeStats.get(classId);
            if (stats == null && currentCounts[classId] == 0) continue;
            long total = stats == null ? 0 : stats.totalCount;
            double average = stats == null || total == 0 ? 0 : stats.confidenceSum / total;
            double max = stats == null ? 0 : stats.maxConfidence;
            metrics.add(new DetectionStore.LabelMetric(
                    CocoLabels.NAMES[classId], currentCounts[classId], total, average, max
            ));
        }
        metrics.sort(Comparator.comparingLong((DetectionStore.LabelMetric m) -> m.totalCount).reversed());

        DetectionStore.publish(new DetectionStore.DetectionSnapshot(
                "ライブ検出中",
                lastFps,
                inputSize,
                frameCount,
                result.detections.size(),
                result.preprocessMs,
                result.inferenceMs,
                result.postprocessMs,
                result.pipelineMs,
                result.preview,
                metrics
        ));
    }

    private static Bitmap imageToBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int width = image.getWidth();
        int height = image.getHeight();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        int paddedWidth = width + Math.max(0, rowPadding / Math.max(1, pixelStride));

        Bitmap padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888);
        buffer.rewind();
        padded.copyPixelsFromBuffer(buffer);
        if (paddedWidth == width) return padded;
        Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, width, height);
        padded.recycle();
        return cropped;
    }

    private void publishError(String message) {
        DetectionStore.publish(new DetectionStore.DetectionSnapshot(
                message, 0, DetectionStore.getInputSize(), frameCount, 0,
                0, 0, 0, 0, null, new ArrayList<>()
        ));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "VisionPulse live detection",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("画面をリアルタイム解析している間に表示されます");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setContentTitle("VisionPulse")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, createNotification(text));
    }

    @Override
    public void onDestroy() {
        DetectionStore.setRunning(false);
        mainHandler.post(() -> {
            if (overlayView != null && windowManager != null) {
                try {
                    windowManager.removeView(overlayView);
                } catch (Exception ignored) {
                }
            }
            overlayView = null;
            windowManager = null;
        });
        if (imageReader != null) {
            imageReader.setOnImageAvailableListener(null, null);
            imageReader.close();
            imageReader = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (projection != null) {
            projection.stop();
            projection = null;
        }
        inferenceExecutor.shutdownNow();
        if (detector != null) {
            try {
                detector.close();
            } catch (Exception ignored) {
            }
            detector = null;
        }
        if (captureThread != null) {
            captureThread.quitSafely();
            captureThread = null;
        }
        DetectionStore.DetectionSnapshot current = DetectionStore.getSnapshot();
        DetectionStore.publish(new DetectionStore.DetectionSnapshot(
                "停止中", 0, DetectionStore.getInputSize(), frameCount, 0,
                0, 0, 0, 0, current.preview, current.metrics
        ));
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static final class LabelStats {
        long totalCount;
        double confidenceSum;
        double maxConfidence;
    }
}
