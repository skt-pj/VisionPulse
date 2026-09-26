package com.skt.visionpulse;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class SessionHistory {
    private static final String PREFS = "visionpulse_sessions";
    private static final String KEY = "history";
    private static final int LIMIT = 12;

    private SessionHistory() {}

    static void save(Context context, DetectionStore.AnalyticsSnapshot snapshot) {
        if (snapshot == null || snapshot.processedFrames == 0) return;
        List<SessionSummary> items = load(context);
        items.add(0, SessionSummary.from(snapshot));
        while (items.size() > LIMIT) items.remove(items.size() - 1);

        JSONArray array = new JSONArray();
        for (SessionSummary item : items) array.put(item.toJson());
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, array.toString()).apply();
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
        final double averageFps;
        final double p95LatencyMs;
        final double dropRate;
        final int stableEpisodes;
        final int unstableEpisodes;
        final double averageConfidence;

        SessionSummary(long startedAt, long durationMs, int inputSize, double threshold,
                double averageFps, double p95LatencyMs, double dropRate,
                int stableEpisodes, int unstableEpisodes, double averageConfidence) {
            this.startedAt = startedAt;
            this.durationMs = durationMs;
            this.inputSize = inputSize;
            this.threshold = threshold;
            this.averageFps = averageFps;
            this.p95LatencyMs = p95LatencyMs;
            this.dropRate = dropRate;
            this.stableEpisodes = stableEpisodes;
            this.unstableEpisodes = unstableEpisodes;
            this.averageConfidence = averageConfidence;
        }

        static SessionSummary from(DetectionStore.AnalyticsSnapshot snapshot) {
            return new SessionSummary(
                    snapshot.sessionStartWallMs, snapshot.durationMs, snapshot.inputSize, snapshot.threshold,
                    snapshot.averageFps, snapshot.p95PipelineMs, snapshot.droppedFrameRate,
                    snapshot.stableEpisodes, snapshot.unstableEpisodes, snapshot.averageConfidence);
        }

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("startedAt", startedAt);
                object.put("durationMs", durationMs);
                object.put("inputSize", inputSize);
                object.put("threshold", threshold);
                object.put("averageFps", averageFps);
                object.put("p95LatencyMs", p95LatencyMs);
                object.put("dropRate", dropRate);
                object.put("stableEpisodes", stableEpisodes);
                object.put("unstableEpisodes", unstableEpisodes);
                object.put("averageConfidence", averageConfidence);
            } catch (JSONException ignored) {}
            return object;
        }

        static SessionSummary fromJson(JSONObject object) {
            return new SessionSummary(
                    object.optLong("startedAt"), object.optLong("durationMs"),
                    object.optInt("inputSize"), object.optDouble("threshold"),
                    object.optDouble("averageFps"), object.optDouble("p95LatencyMs"),
                    object.optDouble("dropRate"), object.optInt("stableEpisodes"),
                    object.optInt("unstableEpisodes"), object.optDouble("averageConfidence"));
        }
    }
}
