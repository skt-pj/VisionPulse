package com.skt.visionpulse;

import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ObjectTrackingStore {
    private static final float MATCH_IOU = 0.18f;
    private static final float MATCH_CENTER_DISTANCE = 0.42f;
    private static final long REACQUIRE_GRACE_MS = 1800L;
    private static final int LOST_AFTER_MISSES = 2;
    private static final int MAX_SAMPLES = 180;
    private static final int MAX_TRACKS_FOR_UI = 120;

    private static long startElapsedMs;
    private static long startWallMs;
    private static int nextTrackId = 1;
    private static long processedFrames;
    private static long droppedFrames;
    private static double confidenceSum;
    private static long confidenceCount;

    private static final List<Track> tracks = new ArrayList<>();
    private static final List<RuntimeSample> runtimeSamples = new ArrayList<>();

    private ObjectTrackingStore() {}

    static synchronized void reset() {
        startElapsedMs = SystemClock.elapsedRealtime();
        startWallMs = System.currentTimeMillis();
        nextTrackId = 1;
        processedFrames = 0;
        droppedFrames = 0;
        confidenceSum = 0;
        confidenceCount = 0;
        tracks.clear();
        runtimeSamples.clear();
    }

    static synchronized void recordDroppedFrame() {
        droppedFrames++;
    }

    static synchronized List<TrackedDetection> update(
            List<YoloDetector.Detection> detections,
            double fps,
            double pipelineMs
    ) {
        if (startElapsedMs == 0) reset();

        long now = SystemClock.elapsedRealtime();
        processedFrames++;

        for (Track track : tracks) {
            if (!track.ended) track.matchedThisFrame = false;
        }

        List<TrackedDetection> assigned = new ArrayList<>();

        for (YoloDetector.Detection detection : detections) {
            Track best = findBestTrack(detection, now);

            if (best == null) {
                best = new Track(nextTrackId++, detection, now);
                tracks.add(best);
            } else {
                best.match(detection, now);
            }

            confidenceSum += detection.confidence;
            confidenceCount++;
            assigned.add(new TrackedDetection(best.id, detection));
        }

        for (Track track : tracks) {
            if (track.ended || track.matchedThisFrame) continue;

            track.missedFrames++;
            if (track.visible && track.missedFrames >= LOST_AFTER_MISSES) {
                track.markLost();
            }

            if (now - track.lastSeenMs > REACQUIRE_GRACE_MS) {
                track.finish(now);
            }
        }

        runtimeSamples.add(new RuntimeSample(now - startElapsedMs, fps, pipelineMs));
        while (runtimeSamples.size() > MAX_SAMPLES) runtimeSamples.remove(0);

        return assigned;
    }

    static synchronized Snapshot snapshot(int inputSize, float threshold) {
        return buildSnapshot(inputSize, threshold, false);
    }

    static synchronized Snapshot finish(int inputSize, float threshold) {
        return buildSnapshot(inputSize, threshold, true);
    }

    private static Snapshot buildSnapshot(int inputSize, float threshold, boolean finish) {
        if (startElapsedMs == 0) return Snapshot.empty(inputSize, threshold);

        long now = SystemClock.elapsedRealtime();
        if (finish) {
            for (Track track : tracks) {
                if (!track.ended) track.finish(now);
            }
        }

        List<Track> included = tracks;
        if (tracks.size() > MAX_TRACKS_FOR_UI) {
            included = tracks.subList(tracks.size() - MAX_TRACKS_FOR_UI, tracks.size());
        }

        int visibleNow = 0;
        int lostNow = 0;
        int lostEvents = 0;
        int reacquired = 0;
        double continuitySum = 0;

        List<ObjectView> objects = new ArrayList<>();
        Map<Integer, LabelAccumulator> labelMap = new HashMap<>();

        for (Track track : included) {
            if (!track.ended && track.visible) visibleNow++;
            if (!track.ended && !track.visible) lostNow++;

            long endReference = track.ended ? track.endMs : now;
            long elapsedMs = Math.max(1L, endReference - track.startMs);
            long visibleMs = track.visibleMs(now);
            double continuity = clamp01(visibleMs / (double) elapsedMs);

            lostEvents += track.lostCount;
            reacquired += track.reacquiredCount;
            continuitySum += continuity;

            double avgConfidence = track.confidenceCount == 0
                    ? 0
                    : track.confidenceSum / track.confidenceCount;

            List<VisibilitySegment> segments = new ArrayList<>();
            for (Segment segment : track.closedSegments) {
                segments.add(new VisibilitySegment(
                        Math.max(0, segment.startMs - startElapsedMs),
                        Math.max(0, segment.endMs - startElapsedMs)
                ));
            }
            if (track.visible && !track.ended) {
                segments.add(new VisibilitySegment(
                        Math.max(0, track.openVisibleStartMs - startElapsedMs),
                        Math.max(0, now - startElapsedMs)
                ));
            }

            String state = track.ended ? "ENDED" : (track.visible ? "TRACKING" : "LOST");
            objects.add(new ObjectView(
                    track.id,
                    CocoLabels.NAMES[track.classId],
                    state,
                    Math.max(0, track.startMs - startElapsedMs),
                    Math.max(0, track.lastSeenMs - startElapsedMs),
                    elapsedMs,
                    visibleMs,
                    continuity,
                    track.lostCount,
                    track.reacquiredCount,
                    track.longestLostMs(now),
                    avgConfidence,
                    track.minConfidence,
                    track.maxConfidence,
                    segments
            ));

            LabelAccumulator label = labelMap.computeIfAbsent(
                    track.classId,
                    ignored -> new LabelAccumulator()
            );
            label.objectCount++;
            label.visibleMs += visibleMs;
            label.lostCount += track.lostCount;
            label.reacquiredCount += track.reacquiredCount;
            label.continuitySum += continuity;
            label.confidenceSum += avgConfidence;
        }

        objects.sort(Comparator
                .comparingInt((ObjectView item) -> statePriority(item.state))
                .thenComparingInt(item -> item.trackId));

        List<LabelSummary> labels = new ArrayList<>();
        for (Map.Entry<Integer, LabelAccumulator> entry : labelMap.entrySet()) {
            LabelAccumulator value = entry.getValue();
            labels.add(new LabelSummary(
                    CocoLabels.NAMES[entry.getKey()],
                    value.objectCount,
                    value.visibleMs,
                    value.lostCount,
                    value.reacquiredCount,
                    value.objectCount == 0 ? 0 : value.continuitySum / value.objectCount,
                    value.objectCount == 0 ? 0 : value.confidenceSum / value.objectCount
            ));
        }
        labels.sort(Comparator
                .comparingInt((LabelSummary item) -> item.objectCount).reversed()
                .thenComparingDouble(item -> -item.averageContinuity));

        List<Double> latencies = new ArrayList<>();
        double fpsSum = 0;
        int fpsCount = 0;
        for (RuntimeSample sample : runtimeSamples) {
            latencies.add(sample.pipelineMs);
            if (sample.fps > 0) {
                fpsSum += sample.fps;
                fpsCount++;
            }
        }
        Collections.sort(latencies);

        double dropRate = processedFrames + droppedFrames == 0
                ? 0
                : droppedFrames / (double) (processedFrames + droppedFrames);

        return new Snapshot(
                startWallMs,
                Math.max(0, now - startElapsedMs),
                inputSize,
                threshold,
                tracks.size(),
                visibleNow,
                lostNow,
                lostEvents,
                reacquired,
                objects.isEmpty() ? 0 : continuitySum / objects.size(),
                fpsCount == 0 ? 0 : fpsSum / fpsCount,
                percentile(latencies, 0.50),
                percentile(latencies, 0.95),
                processedFrames,
                droppedFrames,
                dropRate,
                confidenceCount == 0 ? 0 : confidenceSum / confidenceCount,
                objects,
                labels,
                new ArrayList<>(runtimeSamples)
        );
    }

    private static Track findBestTrack(YoloDetector.Detection detection, long now) {
        Track best = null;
        double bestScore = -1;

        for (Track track : tracks) {
            if (track.ended || track.matchedThisFrame || track.classId != detection.classId) continue;
            if (now - track.lastSeenMs > REACQUIRE_GRACE_MS) continue;

            float overlap = iou(track, detection);
            float centerDistance = normalizedCenterDistance(track, detection);
            if (overlap < MATCH_IOU && centerDistance > MATCH_CENTER_DISTANCE) continue;

            double score = overlap * 2.0 + (1.0 - Math.min(1.0, centerDistance));
            if (track.visible) score += 0.2;

            if (score > bestScore) {
                bestScore = score;
                best = track;
            }
        }
        return best;
    }

    private static int statePriority(String state) {
        if ("TRACKING".equals(state)) return 0;
        if ("LOST".equals(state)) return 1;
        return 2;
    }

    private static double percentile(List<Double> sortedValues, double p) {
        if (sortedValues.isEmpty()) return 0;
        int index = (int) Math.ceil(p * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(sortedValues.size() - 1, index)));
    }

    private static float iou(Track track, YoloDetector.Detection detection) {
        float left = Math.max(track.x1, detection.x1);
        float top = Math.max(track.y1, detection.y1);
        float right = Math.min(track.x2, detection.x2);
        float bottom = Math.min(track.y2, detection.y2);

        float width = Math.max(0, right - left);
        float height = Math.max(0, bottom - top);
        float intersection = width * height;

        float trackArea = Math.max(0, track.x2 - track.x1)
                * Math.max(0, track.y2 - track.y1);
        float detectionArea = Math.max(0, detection.x2 - detection.x1)
                * Math.max(0, detection.y2 - detection.y1);

        float union = trackArea + detectionArea - intersection;
        return union <= 0 ? 0 : intersection / union;
    }

    private static float normalizedCenterDistance(Track track, YoloDetector.Detection detection) {
        float trackCx = (track.x1 + track.x2) * 0.5f;
        float trackCy = (track.y1 + track.y2) * 0.5f;
        float detectionCx = (detection.x1 + detection.x2) * 0.5f;
        float detectionCy = (detection.y1 + detection.y2) * 0.5f;

        float dx = trackCx - detectionCx;
        float dy = trackCy - detectionCy;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        float trackDiagonal = (float) Math.sqrt(
                Math.pow(track.x2 - track.x1, 2) + Math.pow(track.y2 - track.y1, 2));
        float detectionDiagonal = (float) Math.sqrt(
                Math.pow(detection.x2 - detection.x1, 2)
                        + Math.pow(detection.y2 - detection.y1, 2));

        return distance / Math.max(1f, Math.max(trackDiagonal, detectionDiagonal));
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    static final class TrackedDetection {
        final int trackId;
        final YoloDetector.Detection detection;

        TrackedDetection(int trackId, YoloDetector.Detection detection) {
            this.trackId = trackId;
            this.detection = detection;
        }
    }

    static final class Snapshot {
        final long sessionStartWallMs;
        final long durationMs;
        final int inputSize;
        final float threshold;
        final int trackedObjects;
        final int visibleObjects;
        final int lostObjects;
        final int lostEvents;
        final int reacquiredEvents;
        final double averageContinuity;
        final double averageFps;
        final double p50PipelineMs;
        final double p95PipelineMs;
        final long processedFrames;
        final long droppedFrames;
        final double droppedFrameRate;
        final double averageConfidence;
        final List<ObjectView> objects;
        final List<LabelSummary> labels;
        final List<RuntimeSample> samples;

        Snapshot(long sessionStartWallMs, long durationMs, int inputSize, float threshold,
                int trackedObjects, int visibleObjects, int lostObjects, int lostEvents,
                int reacquiredEvents, double averageContinuity, double averageFps,
                double p50PipelineMs, double p95PipelineMs, long processedFrames,
                long droppedFrames, double droppedFrameRate, double averageConfidence,
                List<ObjectView> objects, List<LabelSummary> labels, List<RuntimeSample> samples) {
            this.sessionStartWallMs = sessionStartWallMs;
            this.durationMs = durationMs;
            this.inputSize = inputSize;
            this.threshold = threshold;
            this.trackedObjects = trackedObjects;
            this.visibleObjects = visibleObjects;
            this.lostObjects = lostObjects;
            this.lostEvents = lostEvents;
            this.reacquiredEvents = reacquiredEvents;
            this.averageContinuity = averageContinuity;
            this.averageFps = averageFps;
            this.p50PipelineMs = p50PipelineMs;
            this.p95PipelineMs = p95PipelineMs;
            this.processedFrames = processedFrames;
            this.droppedFrames = droppedFrames;
            this.droppedFrameRate = droppedFrameRate;
            this.averageConfidence = averageConfidence;
            this.objects = Collections.unmodifiableList(new ArrayList<>(objects));
            this.labels = Collections.unmodifiableList(new ArrayList<>(labels));
            this.samples = Collections.unmodifiableList(new ArrayList<>(samples));
        }

        static Snapshot empty(int inputSize, float threshold) {
            return new Snapshot(0, 0, inputSize, threshold, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }
    }

    static final class ObjectView {
        final int trackId;
        final String label;
        final String state;
        final long firstSeenMs;
        final long lastSeenMs;
        final long elapsedMs;
        final long visibleMs;
        final double continuity;
        final int lostCount;
        final int reacquiredCount;
        final long longestLostMs;
        final double averageConfidence;
        final double minConfidence;
        final double maxConfidence;
        final List<VisibilitySegment> segments;

        ObjectView(int trackId, String label, String state, long firstSeenMs, long lastSeenMs,
                long elapsedMs, long visibleMs, double continuity, int lostCount,
                int reacquiredCount, long longestLostMs, double averageConfidence,
                double minConfidence, double maxConfidence, List<VisibilitySegment> segments) {
            this.trackId = trackId;
            this.label = label;
            this.state = state;
            this.firstSeenMs = firstSeenMs;
            this.lastSeenMs = lastSeenMs;
            this.elapsedMs = elapsedMs;
            this.visibleMs = visibleMs;
            this.continuity = continuity;
            this.lostCount = lostCount;
            this.reacquiredCount = reacquiredCount;
            this.longestLostMs = longestLostMs;
            this.averageConfidence = averageConfidence;
            this.minConfidence = minConfidence;
            this.maxConfidence = maxConfidence;
            this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
        }
    }

    static final class VisibilitySegment {
        final long startMs;
        final long endMs;

        VisibilitySegment(long startMs, long endMs) {
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }

    static final class LabelSummary {
        final String label;
        final int objectCount;
        final long visibleMs;
        final int lostCount;
        final int reacquiredCount;
        final double averageContinuity;
        final double averageConfidence;

        LabelSummary(String label, int objectCount, long visibleMs, int lostCount,
                int reacquiredCount, double averageContinuity, double averageConfidence) {
            this.label = label;
            this.objectCount = objectCount;
            this.visibleMs = visibleMs;
            this.lostCount = lostCount;
            this.reacquiredCount = reacquiredCount;
            this.averageContinuity = averageContinuity;
            this.averageConfidence = averageConfidence;
        }
    }

    static final class RuntimeSample {
        final long elapsedMs;
        final double fps;
        final double pipelineMs;

        RuntimeSample(long elapsedMs, double fps, double pipelineMs) {
            this.elapsedMs = elapsedMs;
            this.fps = fps;
            this.pipelineMs = pipelineMs;
        }
    }

    private static final class Track {
        final int id;
        final int classId;
        final long startMs;

        long lastSeenMs;
        long endMs;
        float x1;
        float y1;
        float x2;
        float y2;

        boolean visible = true;
        boolean ended;
        boolean matchedThisFrame = true;
        int missedFrames;

        long openVisibleStartMs;
        final List<Segment> closedSegments = new ArrayList<>();

        int lostCount;
        int reacquiredCount;
        long lostStartedMs;
        long longestLostMs;

        double confidenceSum;
        long confidenceCount = 1;
        float minConfidence;
        float maxConfidence;

        Track(int id, YoloDetector.Detection detection, long now) {
            this.id = id;
            this.classId = detection.classId;
            this.startMs = now;
            this.lastSeenMs = now;
            this.openVisibleStartMs = now;
            this.x1 = detection.x1;
            this.y1 = detection.y1;
            this.x2 = detection.x2;
            this.y2 = detection.y2;
            this.confidenceSum = detection.confidence;
            this.minConfidence = detection.confidence;
            this.maxConfidence = detection.confidence;
        }

        void match(YoloDetector.Detection detection, long now) {
            matchedThisFrame = true;
            missedFrames = 0;

            if (!visible) {
                long lostDuration = Math.max(0, now - lostStartedMs);
                longestLostMs = Math.max(longestLostMs, lostDuration);
                reacquiredCount++;
                visible = true;
                openVisibleStartMs = now;
            }

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

        void markLost() {
            if (!visible || ended) return;
            visible = false;
            lostCount++;
            lostStartedMs = lastSeenMs;
            closedSegments.add(new Segment(openVisibleStartMs, lastSeenMs));
        }

        void finish(long now) {
            if (ended) return;
            if (visible) {
                closedSegments.add(new Segment(openVisibleStartMs, lastSeenMs));
                visible = false;
            } else if (lostStartedMs > 0) {
                longestLostMs = Math.max(longestLostMs, Math.max(0, now - lostStartedMs));
            }
            endMs = Math.max(lastSeenMs, now);
            ended = true;
        }

        long visibleMs(long now) {
            long total = 0;
            for (Segment segment : closedSegments) {
                total += Math.max(0, segment.endMs - segment.startMs);
            }
            if (visible && !ended) {
                total += Math.max(0, now - openVisibleStartMs);
            }
            return total;
        }

        long longestLostMs(long now) {
            long value = longestLostMs;
            if (!visible && !ended && lostStartedMs > 0) {
                value = Math.max(value, Math.max(0, now - lostStartedMs));
            }
            return value;
        }
    }

    private static final class Segment {
        final long startMs;
        final long endMs;

        Segment(long startMs, long endMs) {
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }

    private static final class LabelAccumulator {
        int objectCount;
        long visibleMs;
        int lostCount;
        int reacquiredCount;
        double continuitySum;
        double confidenceSum;
    }
}
