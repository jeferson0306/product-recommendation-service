package com.skyportugal.recommendation.client.dto;

import java.util.List;

public record ProductCatalogResponse(
        String category,
        int totalProducts,
        List<Product> products,
        Filters filters
) {

    public record Product(
            String productId,
            String name,
            double currentPrice,
            double originalPrice,
            double averageRating,
            int totalReviews,
            String availability
    ) {}

    public record Filters(PriceRange priceRange, List<String> brands) {}

    public record PriceRange(double min, double max) {}
}
