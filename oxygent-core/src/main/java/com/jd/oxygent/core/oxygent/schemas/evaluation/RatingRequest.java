package com.jd.oxygent.core.oxygent.schemas.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RatingRequest {
    @JsonProperty("trace_id")
    private String traceId;
    @JsonProperty("rating_type")
    private RatingType ratingType;
    private String comment;
    private String erp;

    public RatingRequest() {
    }

    public RatingRequest(String traceId, RatingType ratingType, String comment, String erp) {
        this.traceId = traceId;
        this.ratingType = ratingType;
        this.comment = comment;
        this.erp = erp;
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
}