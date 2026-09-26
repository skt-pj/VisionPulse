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
import java.util.List;
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
    private final Handler mainHandler = new Handler(android.os.Looper.getMainLooper());

    private HandlerThread captureThread;
    private Handler captureHandler;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private YoloDetector detector;
    private WindowManager windowManager;
    private DetectionOverlayView overlayView;
    private SessionAnalytics analytics;
    private int captureWidth;
    private int captureHeight;
    private long frameCount;
    private long fpsWindowStartNs;
    private int fpsWindowFrames;
    private double lastFps;
    private boolean sessionStarted;
    private boolean finalSnapshotSaved;

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
        if (ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction())) return START_NOT_STICKY;

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
            data = intent.getParcelableExtra(EXTRA_DATA);
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            publishError("画面共有の許可情報がありません");
            stopSelf();
            return START_NOT_STICKY;
        }

        Intent projectionData = new Intent(data);
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
            imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2);
            imageReader.setOnImageAvailableListener(this::onImageAvailable, captureHandler);
            virtualDisplay = projection.createVirtualDisplay(
                    "VisionPulseScreen", captureWidth, captureHeight, metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, captureHandler);

            analytics = new SessionAnalytics();
            ObjectTrackingStore.reset();
            sessionStarted = true;
            DetectionStore.setRunning(true);
            mainHandler.post(this::showOverlay);
            publishSnapshot("ライブ検出中", null);
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
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(overlayView, params);
    }

    private void onImageAvailable(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        if (image == null) return;
        if (!processing.compareAndSet(false, true)) {
            image.close();
            if (analytics != null) analytics.recordDroppedFrame();
            ObjectTrackingStore.recordDroppedFrame();
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
                updateRuntime(result);
                List<ObjectTrackingStore.TrackedDetection> trackedDetections =
                        ObjectTrackingStore.update(result.detections, lastFps, result.pipelineMs);

                mainHandler.post(() -> {
                    if (overlayView != null) {
                        overlayView.updateTrackedDetections(
                                trackedDetections, captureWidth, captureHeight,
                                lastFps, result.inferenceMs, inputSize);
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

    private void updateRuntime(YoloDetector.Result result) {
        frameCount++;
        fpsWindowFrames++;
        long now = System.nanoTime();
        double elapsedSec = (now - fpsWindowStartNs) / 1_000_000_000.0;
        if (elapsedSec >= 1.0) {
            lastFps = fpsWindowFrames / elapsedSec;
            fpsWindowFrames = 0;
            fpsWindowStartNs = now;
        }

        if (analytics != null) analytics.update(result.detections, lastFps, result.pipelineMs);
        DetectionStore.AnalyticsSnapshot analyticsSnapshot = analytics == null
                ? DetectionStore.AnalyticsSnapshot.empty(
                        DetectionStore.getInputSize(), DetectionStore.getConfidenceThreshold())
                : analytics.snapshot(
                        DetectionStore.getInputSize(), DetectionStore.getConfidenceThreshold(), false);

        DetectionStore.publish(new DetectionStore.DetectionSnapshot(
                "ライブ検出中", lastFps, DetectionStore.getInputSize(), frameCount,
                result.detections.size(), result.preprocessMs, result.inferenceMs,
                result.postprocessMs, result.pipelineMs, analyticsSnapshot));
    }

    private void publishSnapshot(String status, DetectionStore.AnalyticsSnapshot forcedAnalytics) {
        DetectionStore.AnalyticsSnapshot value = forcedAnalytics != null
                ? forcedAnalytics
                : analytics == null
                    ? DetectionStore.AnalyticsSnapshot.empty(
                            DetectionStore.getInputSize(), DetectionStore.getConfidenceThreshold())
                    : analytics.snapshot(
                            DetectionStore.getInputSize(), DetectionStore.getConfidenceThreshold(), false);
        DetectionStore.publish(new DetectionStore.DetectionSnapshot(
                status, lastFps, DetectionStore.getInputSize(), frameCount, 0,
                0, 0, 0, 0, value));
    }

    private void publishError(String message) {
        publishSnapshot(message, null);
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

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "VisionPulse live detection", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("画面をリアルタイム解析している間に表示されます");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder.setContentTitle("VisionPulse")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(NOTIFICATION_ID, createNotification(text));
    }

    @Override
    public void onDestroy() {
        DetectionStore.setRunning(false);

        DetectionStore.AnalyticsSnapshot finalAnalytics = null;
        if (sessionStarted && analytics != null && !finalSnapshotSaved) {
            finalAnalytics = analytics.snapshot(
                    DetectionStore.getInputSize(), DetectionStore.getConfidenceThreshold(), true);
            SessionHistory.save(getApplicationContext(), finalAnalytics);

            ObjectTrackingStore.Snapshot trackingSnapshot = ObjectTrackingStore.finish(
                    DetectionStore.getInputSize(), DetectionStore.getConfidenceThreshold());
            ObjectSessionHistory.save(getApplicationContext(), trackingSnapshot);
            finalSnapshotSaved = true;
        }

        mainHandler.post(() -> {
            if (overlayView != null && windowManager != null) {
                try {
                    windowManager.removeView(overlayView);
                } catch (Exception ignored) {}
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
            MediaProjection local = projection;
            projection = null;
            local.stop();
        }
        inferenceExecutor.shutdownNow();
        if (detector != null) {
            try { detector.close(); } catch (Exception ignored) {}
            detector = null;
        }
        if (captureThread != null) {
            captureThread.quitSafely();
            captureThread = null;
        }

        publishSnapshot("停止中", finalAnalytics);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
