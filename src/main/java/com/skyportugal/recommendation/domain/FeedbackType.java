package com.skyportugal.recommendation.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FeedbackType {
    LIKED, DISLIKED, PURCHASED, NOT_INTERESTED;

    @JsonCreator
    public static FeedbackType from(String value) {
        return valueOf(value.toUpperCase().replace("-", "_"));
    }

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }
}
