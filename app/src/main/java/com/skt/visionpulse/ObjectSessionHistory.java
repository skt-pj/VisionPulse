package com.skt.visionpulse;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ObjectSessionHistory {
    private static final String PREFS = "visionpulse_object_sessions";
    private static final String KEY = "history";
    private static final int LIMIT = 12;

    private ObjectSessionHistory() {}

    static void save(Context context, ObjectTrackingStore.Snapshot snapshot) {
        if (snapshot == null || snapshot.processedFrames == 0) return;

        List<SessionSummary> items = load(context);
        items.add(0, SessionSummary.from(snapshot));
        while (items.size() > LIMIT) items.remove(items.size() - 1);

        JSONArray array = new JSONArray();
        for (SessionSummary item : items) array.put(item.toJson());

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, array.toString())
                .apply();
    }

    static List<SessionSummary> load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY, "[]");

        try {
            JSONArray array = new JSONArray(raw);
            List<SessionSummary> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) result.add(SessionSummary.fromJson(object));
            }
            return result;
        } catch (JSONException ignored) {
            return Collections.emptyList();
        }
    }

    static final class SessionSummary {
        final long startedAt;
        final long durationMs;
        final int inputSize;
        final double threshold;
        final int trackedObjects;
        final int lostEvents;
        final int reacquiredEvents;
        final double averageContinuity;
        final double averageFps;
        final double p95LatencyMs;
        final double dropRate;

        SessionSummary(
                long startedAt,
                long durationMs,
                int inputSize,
                double threshold,
                int trackedObjects,
                int lostEvents,
                int reacquiredEvents,
                double averageContinuity,
                double averageFps,
                double p95LatencyMs,
                double dropRate
        ) {
            this.startedAt = startedAt;
            this.durationMs = durationMs;
            this.inputSize = inputSize;
            this.threshold = threshold;
            this.trackedObjects = trackedObjects;
            this.lostEvents = lostEvents;
            this.reacquiredEvents = reacquiredEvents;
            this.averageContinuity = averageContinuity;
            this.averageFps = averageFps;
            this.p95LatencyMs = p95LatencyMs;
            this.dropRate = dropRate;
        }

        static SessionSummary from(ObjectTrackingStore.Snapshot snapshot) {
            return new SessionSummary(
                    snapshot.sessionStartWallMs,
                    snapshot.durationMs,
                    snapshot.inputSize,
                    snapshot.threshold,
                    snapshot.trackedObjects,
                    snapshot.lostEvents,
                    snapshot.reacquiredEvents,
                    snapshot.averageContinuity,
                    snapshot.averageFps,
                    snapshot.p95PipelineMs,
                    snapshot.droppedFrameRate
            );
        }

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("startedAt", startedAt);
                object.put("durationMs", durationMs);
                object.put("inputSize", inputSize);
                object.put("threshold", threshold);
                object.put("trackedObjects", trackedObjects);
                object.put("lostEvents", lostEvents);
                object.put("reacquiredEvents", reacquiredEvents);
                object.put("averageContinuity", averageContinuity);
                object.put("averageFps", averageFps);
                object.put("p95LatencyMs", p95LatencyMs);
                object.put("dropRate", dropRate);
            } catch (JSONException ignored) {}
            return object;
        }

        static SessionSummary fromJson(JSONObject object) {
            return new SessionSummary(
                    object.optLong("startedAt"),
                    object.optLong("durationMs"),
                    object.optInt("inputSize"),
                    object.optDouble("threshold"),
                    object.optInt("trackedObjects"),
                    object.optInt("lostEvents"),
                    object.optInt("reacquiredEvents"),
                    object.optDouble("averageContinuity"),
                    object.optDouble("averageFps"),
                    object.optDouble("p95LatencyMs"),
                    object.optDouble("dropRate")
            );
        }
    }
}
