package com.jd.oxygent.core.oxygent.schemas.evaluation;

import java.util.HashMap;
import java.util.Map;

public class ConversationRating {
    private String ratingId;
    private String traceId;
    private RatingType ratingType;
    private String userId;
    private String userIp;
    private String comment;
    private String erp;
    private String createTime;
    private String updateTime;

    public ConversationRating() {
    }

    public ConversationRating(String ratingId, String traceId, RatingType ratingType, String userId,
                              String userIp, String comment, String erp, String createTime) {
        this.ratingId = ratingId;
        this.traceId = traceId;
        this.ratingType = ratingType;
        this.userId = userId;
        this.userIp = userIp;
        this.comment = comment;
        this.erp = erp;
        this.createTime = createTime;
    }

    public String getRatingId() {
        return ratingId;
    }

    public void setRatingId(String ratingId) {
        this.ratingId = ratingId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public RatingType getRatingType() {
        return ratingType;
    }

    public void setRatingType(RatingType ratingType) {
        this.ratingType = ratingType;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserIp() {
        return userIp;
    }

    public void setUserIp(String userIp) {
        this.userIp = userIp;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getErp() {
        return erp;
    }

    public void setErp(String erp) {
        this.erp = erp;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("rating_id", ratingId);
        map.put("trace_id", traceId);
        map.put("rating_type", ratingType != null ? ratingType.toString() : null);
        map.put("user_id", userId);
        map.put("user_ip", userIp);
        map.put("comment", comment);
        map.put("erp", erp);
        map.put("create_time", createTime);
        map.put("update_time", updateTime);
        return map;
    }

    public static ConversationRating fromMap(Map<String, Object> map) {
        ConversationRating rating = new ConversationRating();
        rating.setRatingId((String) map.getOrDefault("rating_id", null));
        rating.setTraceId((String) map.getOrDefault("trace_id", null));

        String ratingTypeStr = (String) map.getOrDefault("rating_type", null);
        if (ratingTypeStr != null) {
            rating.setRatingType(RatingType.fromValue(ratingTypeStr));
        }

        rating.setUserId((String) map.getOrDefault("user_id", null));
        rating.setUserIp((String) map.getOrDefault("user_ip", null));
        rating.setComment((String) map.getOrDefault("comment", null));
        rating.setErp((String) map.getOrDefault("erp", null));
        rating.setCreateTime((String) map.getOrDefault("create_time", null));
        rating.setUpdateTime((String) map.getOrDefault("update_time", null));
        return rating;
    }
}