package com.jd.oxygent.core.oxygent.schemas.evaluation;

public class RatingResponse {
    private boolean success;
    private String ratingId;
    private RatingStats currentStats;
    private String message;

    public RatingResponse() {
        this.message = "";
    }

    public RatingResponse(boolean success, String ratingId, RatingStats currentStats, String message) {
        this.success = success;
        this.ratingId = ratingId;
        this.currentStats = currentStats;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getRatingId() {
        return ratingId;
    }

    public void setRatingId(String ratingId) {
        this.ratingId = ratingId;
    }

    public RatingStats getCurrentStats() {
        return currentStats;
    }

    public void setCurrentStats(RatingStats currentStats) {
        this.currentStats = currentStats;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}