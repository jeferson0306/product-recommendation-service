package com.skyportugal.recommendation.api.dto;

import java.util.List;

public record RecommendationResponse(
        List<RecommendationItem> recommendations,
        int totalRecommendations
) {}
