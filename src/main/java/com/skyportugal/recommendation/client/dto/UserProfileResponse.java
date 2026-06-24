package com.skyportugal.recommendation.client.dto;

import java.util.List;
import java.util.Map;

public record UserProfileResponse(
        String userId,
        Demographics demographics,
        Preferences preferences,
        PurchaseHistory purchaseHistory,
        BehaviorPatterns behaviorPatterns
) {

    public record Demographics(
            int age,
            String gender,
            String location,
            String income,
            String education
    ) {}

    public record Preferences(
            List<String> categories,
            PriceRange priceRange,
            List<String> brands,
            String style
    ) {}

    public record PriceRange(double min, double max) {}

    public record PurchaseHistory(
            int totalOrders,
            double averageOrderValue,
            String lastPurchaseDate,
            List<String> favoriteCategories,
            Map<String, Integer> seasonalPatterns
    ) {}

    public record BehaviorPatterns(
            String browsingFrequency,
            String purchaseFrequency,
            String priceSensitivity,
            String brandLoyalty
    ) {}
}
