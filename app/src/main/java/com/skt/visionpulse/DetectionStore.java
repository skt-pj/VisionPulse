package com.skt.visionpulse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class DetectionStore {
    private static volatile int inputSize = 416;
    private static volatile float confidenceThreshold = 0.25f;
    private static volatile boolean running = false;

    private static final AtomicReference<DetectionSnapshot> SNAPSHOT = new AtomicReference<>(
            new DetectionSnapshot("停止中", 0, inputSize, 0, 0, 0, 0, 0, 0, AnalyticsSnapshot.empty(inputSize, confidenceThreshold))
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
        public final AnalyticsSnapshot analytics;

        public DetectionSnapshot(String status, double fps, int inputSize, long frameCount,
                int currentDetections, double preprocessMs, double inferenceMs, double postprocessMs,
                double pipelineMs, AnalyticsSnapshot analytics) {
            this.status = status;
            this.fps = fps;
            this.inputSize = inputSize;
            this.frameCount = frameCount;
            this.currentDetections = currentDetections;
            this.preprocessMs = preprocessMs;
            this.inferenceMs = inferenceMs;
            this.postprocessMs = postprocessMs;
            this.pipelineMs = pipelineMs;
            this.analytics = analytics;
        }
    }

    public static final class AnalyticsSnapshot {
        public final long sessionStartWallMs;
        public final long durationMs;
        public final int inputSize;
        public final float threshold;
        public final int stableEpisodes;
        public final int unstableEpisodes;
        public final double averageEpisodeMs;
        public final int labelSwitches;
        public final int lostAndReacquired;
        public final double averageFps;
        public final double averagePipelineMs;
        public final double p50PipelineMs;
        public final double p95PipelineMs;
        public final long processedFrames;
        public final long droppedFrames;
        public final double droppedFrameRate;
        public final double averageConfidence;
        public final List<LabelBehavior> labels;
        public final List<EpisodeView> episodes;
        public final List<RuntimeSample> samples;

        public AnalyticsSnapshot(long sessionStartWallMs, long durationMs, int inputSize, float threshold,
                int stableEpisodes, int unstableEpisodes, double averageEpisodeMs, int labelSwitches,
                int lostAndReacquired, double averageFps, double averagePipelineMs, double p50PipelineMs,
                double p95PipelineMs, long processedFrames, long droppedFrames, double droppedFrameRate,
                double averageConfidence, List<LabelBehavior> labels, List<EpisodeView> episodes,
                List<RuntimeSample> samples) {
            this.sessionStartWallMs = sessionStartWallMs;
            this.durationMs = durationMs;
            this.inputSize = inputSize;
            this.threshold = threshold;
            this.stableEpisodes = stableEpisodes;
            this.unstableEpisodes = unstableEpisodes;
            this.averageEpisodeMs = averageEpisodeMs;
            this.labelSwitches = labelSwitches;
            this.lostAndReacquired = lostAndReacquired;
            this.averageFps = averageFps;
            this.averagePipelineMs = averagePipelineMs;
            this.p50PipelineMs = p50PipelineMs;
            this.p95PipelineMs = p95PipelineMs;
            this.processedFrames = processedFrames;
            this.droppedFrames = droppedFrames;
            this.droppedFrameRate = droppedFrameRate;
            this.averageConfidence = averageConfidence;
            this.labels = Collections.unmodifiableList(new ArrayList<>(labels));
            this.episodes = Collections.unmodifiableList(new ArrayList<>(episodes));
            this.samples = Collections.unmodifiableList(new ArrayList<>(samples));
        }

        public static AnalyticsSnapshot empty(int inputSize, float threshold) {
            return new AnalyticsSnapshot(0, 0, inputSize, threshold, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }
    }

    public static final class LabelBehavior {
        public final String label;
        public final int episodes;
        public final long visibleMs;
        public final double averageConfidence;
        public final double minConfidence;
        public final double maxConfidence;
        public final double stability;
        public final int activeNow;

        public LabelBehavior(String label, int episodes, long visibleMs, double averageConfidence,
                double minConfidence, double maxConfidence, double stability, int activeNow) {
            this.label = label;
            this.episodes = episodes;
            this.visibleMs = visibleMs;
            this.averageConfidence = averageConfidence;
            this.minConfidence = minConfidence;
            this.maxConfidence = maxConfidence;
            this.stability = stability;
            this.activeNow = activeNow;
        }
    }

    public static final class EpisodeView {
        public final String label;
        public final long startMs;
        public final long endMs;
        public final double averageConfidence;
        public final double stability;
        public final boolean active;

        public EpisodeView(String label, long startMs, long endMs, double averageConfidence,
                double stability, boolean active) {
            this.label = label;
            this.startMs = startMs;
            this.endMs = endMs;
            this.averageConfidence = averageConfidence;
            this.stability = stability;
            this.active = active;
        }
    }

    public static final class RuntimeSample {
        public final long elapsedMs;
        public final double fps;
        public final double pipelineMs;

        public RuntimeSample(long elapsedMs, double fps, double pipelineMs) {
            this.elapsedMs = elapsedMs;
            this.fps = fps;
            this.pipelineMs = pipelineMs;
        }
    }
}
