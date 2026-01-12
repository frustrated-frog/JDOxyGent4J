package com.jd.oxygent.core.oxygent.schemas.evaluation;

import java.util.HashMap;
import java.util.Map;

public class RatingStats {
    private String traceId;
    private int likeCount;
    private int dislikeCount;
    private int totalRatings;
    private double satisfactionRate;
    private String lastUpdated;

    public RatingStats() {
    }

    public RatingStats(String traceId, int likeCount, int dislikeCount, int totalRatings,
                       double satisfactionRate, String lastUpdated) {
        this.traceId = traceId;
        this.likeCount = likeCount;
        this.dislikeCount = dislikeCount;
        this.totalRatings = totalRatings;
        this.satisfactionRate = satisfactionRate;
        this.lastUpdated = lastUpdated;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(int likeCount) {
        this.likeCount = likeCount;
    }

    public int getDislikeCount() {
        return dislikeCount;
    }

    public void setDislikeCount(int dislikeCount) {
        this.dislikeCount = dislikeCount;
    }

    public int getTotalRatings() {
        return totalRatings;
    }

    public void setTotalRatings(int totalRatings) {
        this.totalRatings = totalRatings;
    }

    public double getSatisfactionRate() {
        return satisfactionRate;
    }

    public void setSatisfactionRate(double satisfactionRate) {
        this.satisfactionRate = satisfactionRate;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("trace_id", traceId);
        map.put("like_count", likeCount);
        map.put("dislike_count", dislikeCount);
        map.put("total_ratings", totalRatings);
        map.put("satisfaction_rate", satisfactionRate);
        map.put("last_updated", lastUpdated);
        return map;
    }

    public static RatingStats fromMap(Map<String, Object> map) {
        RatingStats stats = new RatingStats();
        stats.setTraceId((String) map.getOrDefault("trace_id", null));
        stats.setLikeCount((Integer) map.getOrDefault("like_count", 0));
        stats.setDislikeCount((Integer) map.getOrDefault("dislike_count", 0));
        stats.setTotalRatings((Integer) map.getOrDefault("total_ratings", 0));
        stats.setSatisfactionRate((Double) map.getOrDefault("satisfaction_rate", 0.0));
        stats.setLastUpdated((String) map.getOrDefault("last_updated", null));
        return stats;
    }
}