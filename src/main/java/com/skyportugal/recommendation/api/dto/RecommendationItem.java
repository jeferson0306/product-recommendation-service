package com.skyportugal.recommendation.api.dto;

public record RecommendationItem(
        String userId,
        String productId,
        String productName,
        String category,
        double currentPrice,
        double originalPrice,
        int discount,
        double averageRating,
        int totalReviews,
        String availability,
        String recommendationReason,
        String generatedAt
) {}
