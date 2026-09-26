package com.skt.visionpulse;

import android.os.SystemClock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SessionAnalytics {
    private static final float MATCH_IOU = 0.30f;
    private static final float SWITCH_IOU = 0.55f;
    private static final int MAX_MISSED_FRAMES = 2;
    private static final long STABLE_DURATION_MS = 1000;
    private static final int STABLE_HITS = 3;
    private static final int MAX_EPISODES_FOR_UI = 80;
    private static final int MAX_SAMPLES = 180;

    private final long startElapsedMs = SystemClock.elapsedRealtime();
    private final long startWallMs = System.currentTimeMillis();
    private final List<Track> active = new ArrayList<>();
    private final List<Track> completed = new ArrayList<>();
    private final List<DetectionStore.RuntimeSample> samples = new ArrayList<>();
    private long processedFrames;
    private long droppedFrames;
    private int nextTrackId = 1;
    private int labelSwitches;
    private int lostAndReacquired;
    private double confidenceSum;
    private long confidenceCount;

    void recordDroppedFrame() { droppedFrames++; }

    void update(List<YoloDetector.Detection> detections, double fps, double pipelineMs) {
        long now = SystemClock.elapsedRealtime();
        processedFrames++;

        boolean[] used = new boolean[detections.size()];
        for (Track track : active) track.matchedThisFrame = false;

        for (Track track : new ArrayList<>(active)) {
            int best = -1;
            float bestIou = 0f;
            for (int i = 0; i < detections.size(); i++) {
                if (used[i]) continue;
                YoloDetector.Detection detection = detections.get(i);
                if (detection.classId != track.classId) continue;
                float candidate = iou(track, detection);
                if (candidate > bestIou) {
                    bestIou = candidate;
                    best = i;
                }
            }
            if (best >= 0 && bestIou >= MATCH_IOU) {
                YoloDetector.Detection detection = detections.get(best);
                used[best] = true;
                if (track.missedFrames > 0) lostAndReacquired++;
                track.update(detection, now);
                addConfidence(detection.confidence);
            }
        }

        for (Track track : new ArrayList<>(active)) {
            if (track.matchedThisFrame) continue;
            int switched = -1;
            float bestIou = 0f;
            for (int i = 0; i < detections.size(); i++) {
                if (used[i]) continue;
                YoloDetector.Detection detection = detections.get(i);
                if (detection.classId == track.classId) continue;
                float candidate = iou(track, detection);
                if (candidate > bestIou) {
                    bestIou = candidate;
                    switched = i;
                }
            }
            if (switched >= 0 && bestIou >= SWITCH_IOU) {
                labelSwitches++;
                finishTrack(track, now);
                YoloDetector.Detection detection = detections.get(switched);
                used[switched] = true;
                startTrack(detection, now);
                addConfidence(detection.confidence);
            }
        }

        for (Track track : new ArrayList<>(active)) {
            if (track.matchedThisFrame) continue;
            track.missedFrames++;
            track.transitionOpportunities++;
            if (track.missedFrames > MAX_MISSED_FRAMES) finishTrack(track, now);
        }

        for (int i = 0; i < detections.size(); i++) {
            if (used[i]) continue;
            YoloDetector.Detection detection = detections.get(i);
            startTrack(detection, now);
            addConfidence(detection.confidence);
        }

        samples.add(new DetectionStore.RuntimeSample(now - startElapsedMs, fps, pipelineMs));
        while (samples.size() > MAX_SAMPLES) samples.remove(0);
    }

    DetectionStore.AnalyticsSnapshot snapshot(int inputSize, float threshold, boolean finish) {
        long now = SystemClock.elapsedRealtime();
        if (finish) {
            for (Track track : new ArrayList<>(active)) finishTrack(track, now);
        }

        List<Track> all = new ArrayList<>(completed);
        all.addAll(active);
        int stable = 0;
        int unstable = 0;
        long durationSum = 0;
        Map<Integer, LabelAccumulator> labelMap = new HashMap<>();

        for (Track track : all) {
            long duration = Math.max(0, track.lastSeenMs - track.startMs);
            durationSum += duration;
            if (isStable(track, duration)) stable++;
            else unstable++;

            LabelAccumulator acc = labelMap.computeIfAbsent(track.classId, key -> new LabelAccumulator());
            acc.episodes++;
            acc.visibleMs += duration;
            acc.confidenceSum += track.confidenceSum;
            acc.confidenceCount += track.confidenceCount;
            acc.minConfidence = Math.min(acc.minConfidence, track.minConfidence);
            acc.maxConfidence = Math.max(acc.maxConfidence, track.maxConfidence);
            acc.continuityHits += track.continuityHits;
            acc.transitionOpportunities += track.transitionOpportunities;
            if (active.contains(track)) acc.activeNow++;
        }

        List<DetectionStore.LabelBehavior> labels = new ArrayList<>();
        for (Map.Entry<Integer, LabelAccumulator> entry : labelMap.entrySet()) {
            LabelAccumulator acc = entry.getValue();
            double avg = acc.confidenceCount == 0 ? 0 : acc.confidenceSum / acc.confidenceCount;
            double stability = acc.transitionOpportunities == 0
                    ? (acc.episodes > 0 ? 1.0 : 0.0)
                    : acc.continuityHits / (double) acc.transitionOpportunities;
            double min = acc.minConfidence == Float.MAX_VALUE ? 0 : acc.minConfidence;
            labels.add(new DetectionStore.LabelBehavior(
                    CocoLabels.NAMES[entry.getKey()],
                    acc.episodes,
                    acc.visibleMs,
                    avg,
                    min,
                    acc.maxConfidence,
                    clamp01(stability),
                    acc.activeNow
            ));
        }
        labels.sort(Comparator
                .comparingDouble((DetectionStore.LabelBehavior item) -> item.stability).reversed()
                .thenComparingLong(item -> -item.visibleMs));

        List<DetectionStore.EpisodeView> episodes = new ArrayList<>();
        int from = Math.max(0, all.size() - MAX_EPISODES_FOR_UI);
        for (int i = from; i < all.size(); i++) {
            Track track = all.get(i);
            double avg = track.confidenceCount == 0 ? 0 : track.confidenceSum / track.confidenceCount;
            double stability = track.transitionOpportunities == 0
                    ? 1.0
                    : track.continuityHits / (double) track.transitionOpportunities;
            episodes.add(new DetectionStore.EpisodeView(
                    CocoLabels.NAMES[track.classId],
                    track.startMs - startElapsedMs,
                    track.lastSeenMs - startElapsedMs,
                    avg,
                    clamp01(stability),
                    active.contains(track)
            ));
        }
        episodes.sort(Comparator.comparingLong(item -> item.startMs));

        List<Double> latencies = new ArrayList<>();
        double fpsSum = 0;
        int fpsCount = 0;
        double pipelineSum = 0;
        for (DetectionStore.RuntimeSample sample : samples) {
            latencies.add(sample.pipelineMs);
            pipelineSum += sample.pipelineMs;
            if (sample.fps > 0) {
                fpsSum += sample.fps;
                fpsCount++;
            }
        }
        Collections.sort(latencies);
        double p50 = percentile(latencies, 0.50);
        double p95 = percentile(latencies, 0.95);
        double avgPipeline = samples.isEmpty() ? 0 : pipelineSum / samples.size();
        double avgFps = fpsCount == 0 ? 0 : fpsSum / fpsCount;
        double dropRate = (processedFrames + droppedFrames) == 0
                ? 0
                : droppedFrames / (double) (processedFrames + droppedFrames);
        double avgConfidence = confidenceCount == 0 ? 0 : confidenceSum / confidenceCount;
        long sessionDuration = Math.max(0, now - startElapsedMs);

        return new DetectionStore.AnalyticsSnapshot(
                startWallMs, sessionDuration, inputSize, threshold, stable, unstable,
                all.isEmpty() ? 0 : durationSum / (double) all.size(),
                labelSwitches, lostAndReacquired, avgFps, avgPipeline, p50, p95,
                processedFrames, droppedFrames, dropRate, avgConfidence,
                labels, episodes, new ArrayList<>(samples)
        );
    }

    private void startTrack(YoloDetector.Detection detection, long now) {
        active.add(new Track(nextTrackId++, detection, now));
    }

    private void finishTrack(Track track, long now) {
        if (!active.remove(track)) return;
        track.lastSeenMs = Math.max(track.lastSeenMs, Math.min(now, track.lastSeenMs + 500));
        completed.add(track);
    }

    private void addConfidence(float confidence) {
        confidenceSum += confidence;
        confidenceCount++;
    }

    private static boolean isStable(Track track, long duration) {
        return duration >= STABLE_DURATION_MS && track.hits >= STABLE_HITS && track.stability() >= 0.60;
    }

    private static double percentile(List<Double> values, double p) {
        if (values.isEmpty()) return 0;
        int index = (int) Math.ceil(p * values.size()) - 1;
        return values.get(Math.max(0, Math.min(values.size() - 1, index)));
    }

    private static float iou(Track track, YoloDetector.Detection detection) {
        float left = Math.max(track.x1, detection.x1);
        float top = Math.max(track.y1, detection.y1);
        float right = Math.min(track.x2, detection.x2);
        float bottom = Math.min(track.y2, detection.y2);
        float w = Math.max(0, right - left);
        float h = Math.max(0, bottom - top);
        float intersection = w * h;
        float a = Math.max(0, track.x2 - track.x1) * Math.max(0, track.y2 - track.y1);
        float b = Math.max(0, detection.x2 - detection.x1) * Math.max(0, detection.y2 - detection.y1);
        float union = a + b - intersection;
        return union <= 0 ? 0 : intersection / union;
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static final class Track {
        final int id;
        final int classId;
        final long startMs;
        long lastSeenMs;
        float x1;
        float y1;
        float x2;
        float y2;
        int hits = 1;
        int missedFrames;
        long continuityHits;
        long transitionOpportunities;
        double confidenceSum;
        long confidenceCount = 1;
        float minConfidence;
        float maxConfidence;
        boolean matchedThisFrame = true;

        Track(int id, YoloDetector.Detection detection, long now) {
            this.id = id;
            this.classId = detection.classId;
            this.startMs = now;
            this.lastSeenMs = now;
            this.x1 = detection.x1;
            this.y1 = detection.y1;
            this.x2 = detection.x2;
            this.y2 = detection.y2;
            this.confidenceSum = detection.confidence;
            this.minConfidence = detection.confidence;
            this.maxConfidence = detection.confidence;
        }

        void update(YoloDetector.Detection detection, long now) {
            matchedThisFrame = true;
            hits++;
            transitionOpportunities++;
            continuityHits++;
            missedFrames = 0;
            lastSeenMs = now;
            x1 = detection.x1;
            y1 = detection.y1;
            x2 = detection.x2;
            y2 = detection.y2;
            confidenceSum += detection.confidence;
            confidenceCount++;
            minConfidence = Math.min(minConfidence, detection.confidence);
            maxConfidence = Math.max(maxConfidence, detection.confidence);
        }

        double stability() {
            return transitionOpportunities == 0 ? 1.0 : continuityHits / (double) transitionOpportunities;
        }
    }

    private static final class LabelAccumulator {
        int episodes;
        long visibleMs;
        double confidenceSum;
        long confidenceCount;
        double minConfidence = Float.MAX_VALUE;
        double maxConfidence;
        long continuityHits;
        long transitionOpportunities;
        int activeNow;
    }
}
