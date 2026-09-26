package com.skt.visionpulse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class DetectionOverlayView extends View {
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<ObjectTrackingStore.TrackedDetection> detections = new ArrayList<>();
    private int sourceWidth = 1;
    private int sourceHeight = 1;
    private double fps;
    private double inferenceMs;
    private int inputSize = 416;

    DetectionOverlayView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);

        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(dp(2.2f));

        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(dp(12.5f));
        labelPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        labelBackgroundPaint.setColor(0xE6151A22);

        hudPaint.setColor(0xFFF4F7FA);
        hudPaint.setTextSize(dp(11.5f));
        hudPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        hudBackgroundPaint.setColor(0xD90B0F14);
    }

    void updateTrackedDetections(
            List<ObjectTrackingStore.TrackedDetection> newDetections,
            int frameWidth,
            int frameHeight,
            double currentFps,
            double currentInferenceMs,
            int currentInputSize
    ) {
        detections = new ArrayList<>(newDetections);
        sourceWidth = Math.max(1, frameWidth);
        sourceHeight = Math.max(1, frameHeight);
        fps = currentFps;
        inferenceMs = currentInferenceMs;
        inputSize = currentInputSize;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float sx = getWidth() / (float) sourceWidth;
        float sy = getHeight() / (float) sourceHeight;

        for (ObjectTrackingStore.TrackedDetection tracked : detections) {
            YoloDetector.Detection detection = tracked.detection;
            int color = Color.HSVToColor(
                    new float[]{(detection.classId * 47f) % 360f, 0.72f, 1.0f}
            );
            boxPaint.setColor(color);

            RectF rect = new RectF(
                    detection.x1 * sx,
                    detection.y1 * sy,
                    detection.x2 * sx,
                    detection.y2 * sy
            );
            canvas.drawRoundRect(rect, dp(7f), dp(7f), boxPaint);

            String label = CocoLabels.NAMES[detection.classId]
                    + " #" + tracked.trackId
                    + String.format(Locale.US, "  %.2f", detection.confidence);

            float horizontalPad = dp(7f);
            float verticalPad = dp(4f);
            float textWidth = labelPaint.measureText(label);
            Paint.FontMetrics metrics = labelPaint.getFontMetrics();
            float textHeight = metrics.descent - metrics.ascent;

            float labelTop = Math.max(
                    dp(4f),
                    rect.top - textHeight - verticalPad * 2f - dp(4f)
            );
            RectF labelBounds = new RectF(
                    rect.left,
                    labelTop,
                    Math.min(getWidth() - dp(4f), rect.left + textWidth + horizontalPad * 2f),
                    labelTop + textHeight + verticalPad * 2f
            );

            labelBackgroundPaint.setColor(0xE6151A22);
            canvas.drawRoundRect(labelBounds, dp(7f), dp(7f), labelBackgroundPaint);
            canvas.drawText(
                    label,
                    labelBounds.left + horizontalPad,
                    labelBounds.bottom - verticalPad - metrics.descent,
                    labelPaint
            );
        }

        String hud = String.format(
                Locale.US,
                "VP  %d  ·  %.1f FPS  ·  %.1f ms  ·  %d tracked",
                inputSize,
                fps,
                inferenceMs,
                detections.size()
        );

        float padX = dp(9f);
        float padY = dp(6f);
        Paint.FontMetrics hudMetrics = hudPaint.getFontMetrics();
        float hudHeight = hudMetrics.descent - hudMetrics.ascent;
        float hudWidth = hudPaint.measureText(hud);

        RectF hudBounds = new RectF(
                dp(10f),
                dp(10f),
                dp(10f) + hudWidth + padX * 2f,
                dp(10f) + hudHeight + padY * 2f
        );

        canvas.drawRoundRect(hudBounds, dp(10f), dp(10f), hudBackgroundPaint);
        canvas.drawText(
                hud,
                hudBounds.left + padX,
                hudBounds.bottom - padY - hudMetrics.descent,
                hudPaint
        );
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
