package com.skyportugal.recommendation.service;

import com.skyportugal.recommendation.client.ProductCatalogClient;
import com.skyportugal.recommendation.client.UserProfileClient;
import com.skyportugal.recommendation.client.dto.ProductCatalogResponse;
import com.skyportugal.recommendation.client.dto.UserProfileResponse;
import com.skyportugal.recommendation.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private UserProfileClient userProfileClient;

    @Mock
    private ProductCatalogClient productCatalogClient;

    @InjectMocks
    private RecommendationService recommendationService;

    private UserProfileResponse userProfile;
    private ProductCatalogResponse catalogResponse;

    @BeforeEach
    void setUp() {
        userProfile = new UserProfileResponse(
                "user-1",
                new UserProfileResponse.Demographics(30, "male", "Lisbon", "50000-75000", "Bachelor"),
                new UserProfileResponse.Preferences(
                        List.of("electronics", "books"),
                        new UserProfileResponse.PriceRange(20.0, 300.0),
                        List.of("Apple", "Sony"),
                        "minimalist"
                ),
                new UserProfileResponse.PurchaseHistory(10, 150.0, "2024-01-01", List.of("electronics"), null),
                new UserProfileResponse.BehaviorPatterns("daily", "monthly", "medium", "high")
        );

        catalogResponse = new ProductCatalogResponse(
                "electronics",
                3,
                List.of(
                        new ProductCatalogResponse.Product("PROD-001", "Premium electronics Item", 149.99, 249.99, 4.5, 342, "IN_STOCK"),
                        new ProductCatalogResponse.Product("PROD-002", "Standard electronics Item", 89.99, 119.99, 4.2, 128, "IN_STOCK"),
                        new ProductCatalogResponse.Product("PROD-003", "Expensive electronics Item", 400.0, 450.0, 4.8, 200, "IN_STOCK")
                ),
                new ProductCatalogResponse.Filters(new ProductCatalogResponse.PriceRange(10.0, 500.0), List.of("Apple", "Sony"))
        );
    }

    @Test
    void returnsFilteredRecommendationsWithinPriceRange() {
        when(userProfileClient.fetchProfile("user-1")).thenReturn(Mono.just(userProfile));
        when(productCatalogClient.fetchByCategory(anyString())).thenReturn(Mono.just(catalogResponse));

        StepVerifier.create(recommendationService.getRecommendations("user-1", null))
                .assertNext(response -> {
                    assertThat(response.recommendations()).isNotEmpty();
                    assertThat(response.recommendations()).allMatch(
                            item -> item.currentPrice() >= 20.0 && item.currentPrice() <= 300.0
                    );
                    assertThat(response.totalRecommendations()).isEqualTo(response.recommendations().size());
                })
                .verifyComplete();
    }

    @Test
    void filtersByCategoryWhenSpecified() {
        when(userProfileClient.fetchProfile("user-1")).thenReturn(Mono.just(userProfile));
        when(productCatalogClient.fetchByCategory("electronics")).thenReturn(Mono.just(catalogResponse));

        StepVerifier.create(recommendationService.getRecommendations("user-1", "electronics"))
                .assertNext(response ->
                        assertThat(response.recommendations()).allMatch(
                                item -> "electronics".equals(item.category())
                        )
                )
                .verifyComplete();
    }

    @Test
    void propagatesUserNotFoundException() {
        when(userProfileClient.fetchProfile("unknown")).thenReturn(
                Mono.error(new UserNotFoundException("unknown")));

        StepVerifier.create(recommendationService.getRecommendations("unknown", null))
                .expectError(UserNotFoundException.class)
                .verify();
    }

    @Test
    void returnsPartialResultsWhenOneCategoryFails() {
        var booksCatalog = new ProductCatalogResponse("books", 1,
                List.of(new ProductCatalogResponse.Product("PROD-100", "Java Book", 40.0, 60.0, 4.7, 80, "IN_STOCK")),
                new ProductCatalogResponse.Filters(new ProductCatalogResponse.PriceRange(5.0, 100.0), List.of()));

        when(userProfileClient.fetchProfile("user-1")).thenReturn(Mono.just(userProfile));
        when(productCatalogClient.fetchByCategory("electronics")).thenReturn(Mono.error(new RuntimeException("timeout")));
        when(productCatalogClient.fetchByCategory("books")).thenReturn(Mono.just(booksCatalog));

        StepVerifier.create(recommendationService.getRecommendations("user-1", null))
                .assertNext(response ->
                        assertThat(response.recommendations()).anyMatch(item -> "books".equals(item.category()))
                )
                .verifyComplete();
    }

    @Test
    void computesDiscountCorrectly() {
        when(userProfileClient.fetchProfile("user-1")).thenReturn(Mono.just(userProfile));
        when(productCatalogClient.fetchByCategory(anyString())).thenReturn(Mono.just(catalogResponse));

        StepVerifier.create(recommendationService.getRecommendations("user-1", "electronics"))
                .assertNext(response -> {
                    var premiumItem = response.recommendations().stream()
                            .filter(i -> i.productId().equals("PROD-001"))
                            .findFirst();
                    assertThat(premiumItem).isPresent();
                    assertThat(premiumItem.get().discount()).isEqualTo(40);
                    assertThat(premiumItem.get().recommendationReason()).contains("discount");
                })
                .verifyComplete();
    }
}
