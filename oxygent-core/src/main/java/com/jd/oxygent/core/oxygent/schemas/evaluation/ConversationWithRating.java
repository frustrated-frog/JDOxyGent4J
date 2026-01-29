package com.jd.oxygent.core.oxygent.schemas.evaluation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConversationWithRating {
    private String traceId;
    private String input;
    private String callee;
    private String output;
    private String createTime;
    private String fromTraceId;
    private RatingStats ratingStats;
    private List<ConversationRating> ratingHistory;

    public ConversationWithRating() {
    }

    public ConversationWithRating(String traceId, String input, String callee, String output,
                                  String createTime, String fromTraceId, RatingStats ratingStats,
                                  List<ConversationRating> ratingHistory) {
        this.traceId = traceId;
        this.input = input;
        this.callee = callee;
        this.output = output;
        this.createTime = createTime;
        this.fromTraceId = fromTraceId;
        this.ratingStats = ratingStats;
        this.ratingHistory = ratingHistory != null ? ratingHistory : new ArrayList<>();
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getCallee() {
        return callee;
    }

    public void setCallee(String callee) {
        this.callee = callee;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getFromTraceId() {
        return fromTraceId;
    }

    public void setFromTraceId(String fromTraceId) {
        this.fromTraceId = fromTraceId;
    }

    public RatingStats getRatingStats() {
        return ratingStats;
    }

    public void setRatingStats(RatingStats ratingStats) {
        this.ratingStats = ratingStats;
    }

    public List<ConversationRating> getRatingHistory() {
        return ratingHistory;
    }

    public void setRatingHistory(List<ConversationRating> ratingHistory) {
        this.ratingHistory = ratingHistory;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("trace_id", traceId);
        map.put("input", input);
        map.put("callee", callee);
        map.put("output", output);
        map.put("create_time", createTime);
        map.put("from_trace_id", fromTraceId);

        if (ratingStats != null) {
            map.put("rating_stats", ratingStats.toMap());
        }

        if (ratingHistory != null && !ratingHistory.isEmpty()) {
            List<Map<String, Object>> ratingHistoryMaps = new ArrayList<>();
            for (ConversationRating rating : ratingHistory) {
                ratingHistoryMaps.add(rating.toMap());
            }
            map.put("rating_history", ratingHistoryMaps);
        }

        return map;
    }

    @SuppressWarnings("unchecked")
    public static ConversationWithRating fromMap(Map<String, Object> map) {
        ConversationWithRating conversation = new ConversationWithRating();

        conversation.setTraceId((String) map.getOrDefault("trace_id", null));
        conversation.setInput((String) map.getOrDefault("input", null));
        conversation.setCallee((String) map.getOrDefault("callee", null));
        conversation.setOutput((String) map.getOrDefault("output", null));
        conversation.setCreateTime((String) map.getOrDefault("create_time", null));
        conversation.setFromTraceId((String) map.getOrDefault("from_trace_id", null));

        Map<String, Object> ratingStatsMap = (Map<String, Object>) map.getOrDefault("rating_stats", null);
        if (ratingStatsMap != null) {
            conversation.setRatingStats(RatingStats.fromMap(ratingStatsMap));
        }

        List<Map<String, Object>> ratingHistoryMaps = (List<Map<String, Object>>) map.getOrDefault("rating_history", null);
        if (ratingHistoryMaps != null && !ratingHistoryMaps.isEmpty()) {
            List<ConversationRating> ratingHistory = new ArrayList<>();
            for (Map<String, Object> ratingMap : ratingHistoryMaps) {
                ratingHistory.add(ConversationRating.fromMap(ratingMap));
            }
            conversation.setRatingHistory(ratingHistory);
        }

        return conversation;
    }
}