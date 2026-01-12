package com.jd.oxygent.core.oxygent.schemas.evaluation;

public enum RatingType {
    LIKE("like"),
    DISLIKE("dislike");

    private final String value;

    RatingType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static RatingType fromValue(String value) {
        for (RatingType type : RatingType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid rating type: " + value);
    }
}