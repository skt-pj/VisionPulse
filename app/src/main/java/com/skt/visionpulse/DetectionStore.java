package com.skt.visionpulse;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class DetectionStore {
    private static volatile int inputSize = 416;
    private static volatile float confidenceThreshold = 0.25f;
    private static volatile boolean running = false;
    private static final AtomicReference<DetectionSnapshot> SNAPSHOT = new AtomicReference<>(
            new DetectionSnapshot("停止中", 0, inputSize, 0, 0, 0, 0, 0, 0, null, Collections.emptyList())
    );

    private DetectionStore() {}

    public static int getInputSize() { return inputSize; }
    public static void setInputSize(int value) { inputSize = value; }

    public static float getConfidenceThreshold() { return confidenceThreshold; }
    public static void setConfidenceThreshold(float value) { confidenceThreshold = value; }

    public static boolean isRunning() { return running; }
    public static void setRunning(boolean value) { running = value; }

    public static DetectionSnapshot getSnapshot() { return SNAPSHOT.get(); }
    public static void publish(DetectionSnapshot snapshot) { SNAPSHOT.set(snapshot); }

    public static final class DetectionSnapshot {
        public final String status;
        public final double fps;
        public final int inputSize;
        public final long frameCount;
        public final int currentDetections;
        public final double preprocessMs;
        public final double inferenceMs;
        public final double postprocessMs;
        public final double pipelineMs;
        public final Bitmap preview;
        public final List<LabelMetric> metrics;

        public DetectionSnapshot(
                String status,
                double fps,
                int inputSize,
                long frameCount,
                int currentDetections,
                double preprocessMs,
                double inferenceMs,
                double postprocessMs,
                double pipelineMs,
                Bitmap preview,
                List<LabelMetric> metrics
        ) {
            this.status = status;
            this.fps = fps;
            this.inputSize = inputSize;
            this.frameCount = frameCount;
            this.currentDetections = currentDetections;
            this.preprocessMs = preprocessMs;
            this.inferenceMs = inferenceMs;
            this.postprocessMs = postprocessMs;
            this.pipelineMs = pipelineMs;
            this.preview = preview;
            this.metrics = Collections.unmodifiableList(new ArrayList<>(metrics));
        }
    }

    public static final class LabelMetric {
        public final String label;
        public final int currentCount;
        public final long totalCount;
        public final double averageConfidence;
        public final double maxConfidence;

        public LabelMetric(String label, int currentCount, long totalCount, double averageConfidence, double maxConfidence) {
            this.label = label;
            this.currentCount = currentCount;
            this.totalCount = totalCount;
            this.averageConfidence = averageConfidence;
            this.maxConfidence = maxConfidence;
        }
    }
}
