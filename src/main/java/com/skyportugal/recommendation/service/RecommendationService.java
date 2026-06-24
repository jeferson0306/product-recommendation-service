package com.skyportugal.recommendation.service;

import com.skyportugal.recommendation.api.dto.RecommendationItem;
import com.skyportugal.recommendation.api.dto.RecommendationResponse;
import com.skyportugal.recommendation.client.ProductCatalogClient;
import com.skyportugal.recommendation.client.UserProfileClient;
import com.skyportugal.recommendation.client.dto.ProductCatalogResponse;
import com.skyportugal.recommendation.client.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecommendationService {

    private final UserProfileClient userProfileClient;
    private final ProductCatalogClient productCatalogClient;

    public Mono<RecommendationResponse> getRecommendations(String userId, String categoryFilter) {
        return userProfileClient.fetchProfile(userId)
                .flatMap(profile -> {
                    var categories = resolveCategories(profile, categoryFilter);
                    return fetchAllProducts(categories)
                            .map(products -> buildResponse(userId, profile, products));
                });
    }

    private List<String> resolveCategories(UserProfileResponse profile, String categoryFilter) {
        if (categoryFilter != null && !categoryFilter.isBlank()) {
            return List.of(categoryFilter);
        }
        return profile.preferences().categories().stream().distinct().toList();
    }

    private Mono<List<ProductWithCategory>> fetchAllProducts(List<String> categories) {
        return Flux.fromIterable(categories)
                .flatMap(category -> productCatalogClient.fetchByCategory(category)
                        .flatMapMany(response -> Flux.fromIterable(response.products())
                                .map(product -> new ProductWithCategory(product, category)))
                        .onErrorResume(e -> {
                            log.warn("Skipping category={} due to error: {}", category, e.getMessage());
                            return Flux.empty();
                        }))
                .collectList();
    }

    private RecommendationResponse buildResponse(
            String userId,
            UserProfileResponse profile,
            List<ProductWithCategory> products) {

        var priceRange = profile.preferences().priceRange();
        var generatedAt = Instant.now().toString();

        var items = products.stream()
                .filter(p -> isWithinPriceRange(p.product(), priceRange))
                .filter(p -> isAvailable(p.product()))
                .map(p -> toRecommendationItem(userId, p.product(), p.category(), generatedAt))
                .collect(Collectors.toList());

        log.info("Built {} recommendations for userId={}", items.size(), userId);
        return new RecommendationResponse(items, items.size());
    }

    private boolean isWithinPriceRange(ProductCatalogResponse.Product product, UserProfileResponse.PriceRange range) {
        return product.currentPrice() >= range.min() && product.currentPrice() <= range.max();
    }

    private boolean isAvailable(ProductCatalogResponse.Product product) {
        return "IN_STOCK".equals(product.availability()) || "LOW_STOCK".equals(product.availability());
    }

    private RecommendationItem toRecommendationItem(
            String userId,
            ProductCatalogResponse.Product product,
            String category,
            String generatedAt) {

        int discount = computeDiscount(product.currentPrice(), product.originalPrice());
        return new RecommendationItem(
                userId,
                product.productId(),
                product.name(),
                category,
                product.currentPrice(),
                product.originalPrice(),
                discount,
                product.averageRating(),
                product.totalReviews(),
                product.availability(),
                buildReason(product, category, discount),
                generatedAt
        );
    }

    private int computeDiscount(double currentPrice, double originalPrice) {
        if (originalPrice <= 0 || currentPrice >= originalPrice) return 0;
        return (int) Math.round((1.0 - currentPrice / originalPrice) * 100);
    }

    private String buildReason(ProductCatalogResponse.Product product, String category, int discount) {
        if (discount > 0) return "Great deal with " + discount + "% discount";
        if (product.averageRating() >= 4.0) return "Highly rated product in " + category;
        return "Matches your preferred category: " + category;
    }

    private record ProductWithCategory(ProductCatalogResponse.Product product, String category) {}
}
