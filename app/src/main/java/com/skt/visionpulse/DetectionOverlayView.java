package com.skt.visionpulse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
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

    private List<YoloDetector.Detection> detections = new ArrayList<>();
    private int sourceWidth = 1;
    private int sourceHeight = 1;
    private double fps;
    private double inferenceMs;
    private int inputSize = 416;

    DetectionOverlayView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(dp(2.5f));
        labelPaint.setTextSize(dp(13f));
        labelPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        labelBackgroundPaint.setStyle(Paint.Style.FILL);
        hudPaint.setColor(Color.WHITE);
        hudPaint.setTextSize(dp(12f));
        hudPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        hudBackgroundPaint.setColor(0xB0000000);
    }

    void updateDetections(
            List<YoloDetector.Detection> newDetections,
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

    void clearDetections() {
        detections = new ArrayList<>();
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float sx = getWidth() / (float) sourceWidth;
        float sy = getHeight() / (float) sourceHeight;

        for (YoloDetector.Detection detection : detections) {
            int color = Color.HSVToColor(new float[]{(detection.classId * 47f) % 360f, 0.9f, 1.0f});
            boxPaint.setColor(color);
            labelPaint.setColor(Color.WHITE);
            labelBackgroundPaint.setColor(0xD9000000);

            RectF rect = new RectF(
                    detection.x1 * sx,
                    detection.y1 * sy,
                    detection.x2 * sx,
                    detection.y2 * sy
            );
            canvas.drawRect(rect, boxPaint);

            String label = CocoLabels.NAMES[detection.classId] + String.format(Locale.US, " %.2f", detection.confidence);
            float pad = dp(4f);
            float textWidth = labelPaint.measureText(label);
            Paint.FontMetrics fm = labelPaint.getFontMetrics();
            float textHeight = fm.descent - fm.ascent;
            float labelTop = Math.max(0f, rect.top - textHeight - (pad * 2f));
            RectF labelBg = new RectF(
                    rect.left,
                    labelTop,
                    Math.min(getWidth(), rect.left + textWidth + (pad * 2f)),
                    labelTop + textHeight + (pad * 2f)
            );
            canvas.drawRect(labelBg, labelBackgroundPaint);
            canvas.drawText(label, labelBg.left + pad, labelBg.bottom - pad - fm.descent, labelPaint);
        }

        String hud = String.format(
                Locale.US,
                "YOLO26n  %d  |  %.1f FPS  |  %.1f ms  |  %d objects",
                inputSize,
                fps,
                inferenceMs,
                detections.size()
        );
        float pad = dp(6f);
        Paint.FontMetrics hudFm = hudPaint.getFontMetrics();
        float hudHeight = hudFm.descent - hudFm.ascent;
        float hudWidth = hudPaint.measureText(hud);
        RectF hudBg = new RectF(dp(8f), dp(8f), dp(8f) + hudWidth + pad * 2f, dp(8f) + hudHeight + pad * 2f);
        canvas.drawRoundRect(hudBg, dp(5f), dp(5f), hudBackgroundPaint);
        canvas.drawText(hud, hudBg.left + pad, hudBg.bottom - pad - hudFm.descent, hudPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
