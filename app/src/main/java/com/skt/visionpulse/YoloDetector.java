package com.skt.visionpulse;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

final class YoloDetector implements AutoCloseable {
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final String inputName;

    YoloDetector(Context context) throws IOException, OrtException {
        environment = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        session = environment.createSession(readAsset(context, "yolo26n.onnx"), options);
        inputName = session.getInputNames().iterator().next();
    }

    Result detect(Bitmap source, int inputSize, float threshold) throws OrtException {
        long pipelineStart = System.nanoTime();
        long preprocessStart = pipelineStart;

        int srcWidth = source.getWidth();
        int srcHeight = source.getHeight();
        float scale = Math.min((float) inputSize / srcWidth, (float) inputSize / srcHeight);
        int resizedWidth = Math.max(1, Math.round(srcWidth * scale));
        int resizedHeight = Math.max(1, Math.round(srcHeight * scale));
        float padX = (inputSize - resizedWidth) / 2.0f;
        float padY = (inputSize - resizedHeight) / 2.0f;

        Bitmap input = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(input);
        canvas.drawColor(Color.rgb(114, 114, 114));
        Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        RectF dst = new RectF(padX, padY, padX + resizedWidth, padY + resizedHeight);
        canvas.drawBitmap(source, null, dst, imagePaint);

        int[] pixels = new int[inputSize * inputSize];
        input.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize);
        float[] nchw = new float[3 * pixels.length];
        int plane = pixels.length;
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i];
            nchw[i] = Color.red(c) / 255.0f;
            nchw[plane + i] = Color.green(c) / 255.0f;
            nchw[(2 * plane) + i] = Color.blue(c) / 255.0f;
        }
        input.recycle();
        double preprocessMs = elapsedMs(preprocessStart);

        long inferenceStart = System.nanoTime();
        List<Detection> detections = new ArrayList<>();
        try (OnnxTensor tensor = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(nchw),
                new long[]{1, 3, inputSize, inputSize}
        )) {
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put(inputName, tensor);
            try (OrtSession.Result result = session.run(inputs)) {
                double inferenceMs = elapsedMs(inferenceStart);
                long postStart = System.nanoTime();
                Object value = result.get(0).getValue();
                if (value instanceof float[][][]) {
                    float[][] rows = ((float[][][]) value)[0];
                    for (float[] row : rows) {
                        if (row.length < 6) continue;
                        float confidence = row[4];
                        if (confidence < threshold) continue;
                        int classId = Math.round(row[5]);
                        if (classId < 0 || classId >= CocoLabels.NAMES.length) continue;

                        float x1 = clamp((row[0] - padX) / scale, 0, srcWidth - 1);
                        float y1 = clamp((row[1] - padY) / scale, 0, srcHeight - 1);
                        float x2 = clamp((row[2] - padX) / scale, 0, srcWidth - 1);
                        float y2 = clamp((row[3] - padY) / scale, 0, srcHeight - 1);
                        if (x2 <= x1 || y2 <= y1) continue;
                        detections.add(new Detection(classId, confidence, x1, y1, x2, y2));
                    }
                }
                double postprocessMs = elapsedMs(postStart);
                Bitmap preview = createPreview(source, detections);
                double pipelineMs = elapsedMs(pipelineStart);
                return new Result(detections, preview, preprocessMs, inferenceMs, postprocessMs, pipelineMs);
            }
        }
    }

    private static Bitmap createPreview(Bitmap source, List<Detection> detections) {
        int previewWidth = Math.min(720, source.getWidth());
        float scale = (float) previewWidth / source.getWidth();
        int previewHeight = Math.max(1, Math.round(source.getHeight() * scale));
        Bitmap preview = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(preview);
        Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source, null, new RectF(0, 0, previewWidth, previewHeight), imagePaint);

        Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
        box.setStyle(Paint.Style.STROKE);
        box.setStrokeWidth(Math.max(2f, previewWidth / 240f));
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTextSize(Math.max(18f, previewWidth / 26f));
        text.setStyle(Paint.Style.FILL);

        for (Detection detection : detections) {
            int color = Color.HSVToColor(new float[]{(detection.classId * 47f) % 360f, 0.85f, 1.0f});
            box.setColor(color);
            text.setColor(color);
            RectF rect = new RectF(
                    detection.x1 * scale,
                    detection.y1 * scale,
                    detection.x2 * scale,
                    detection.y2 * scale
            );
            canvas.drawRect(rect, box);
            String label = CocoLabels.NAMES[detection.classId] + String.format(" %.2f", detection.confidence);
            float textY = Math.max(text.getTextSize(), rect.top - 4f);
            canvas.drawText(label, rect.left, textY, text);
        }
        return preview;
    }

    private static byte[] readAsset(Context context, String name) throws IOException {
        try (InputStream input = context.getAssets().open(name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static double elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000.0;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void close() throws OrtException {
        session.close();
    }

    static final class Detection {
        final int classId;
        final float confidence;
        final float x1;
        final float y1;
        final float x2;
        final float y2;

        Detection(int classId, float confidence, float x1, float y1, float x2, float y2) {
            this.classId = classId;
            this.confidence = confidence;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }

    static final class Result {
        final List<Detection> detections;
        final Bitmap preview;
        final double preprocessMs;
        final double inferenceMs;
        final double postprocessMs;
        final double pipelineMs;

        Result(List<Detection> detections, Bitmap preview, double preprocessMs, double inferenceMs, double postprocessMs, double pipelineMs) {
            this.detections = detections;
            this.preview = preview;
            this.preprocessMs = preprocessMs;
            this.inferenceMs = inferenceMs;
            this.postprocessMs = postprocessMs;
            this.pipelineMs = pipelineMs;
        }
    }
}
