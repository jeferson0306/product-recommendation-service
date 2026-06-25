package com.skyportugal.recommendation;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.skyportugal.recommendation.api.dto.FeedbackRequest;
import com.skyportugal.recommendation.api.dto.RecommendationResponse;
import com.skyportugal.recommendation.domain.FeedbackType;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecommendationIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(RecommendationIntegrationTest.class);

    private static WireMockServer wireMockServer;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CacheManager cacheManager;

    private WebTestClient client;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(9999));
        wireMockServer.start();
        log.info("WireMock started on port 9999");
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
        log.info("WireMock stopped");
    }

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });

        client = webTestClient.mutate()
                .defaultHeaders(h -> h.setBasicAuth("user", "secret"))
                .build();

        wireMockServer.resetAll();
        stubUserProfile();
        stubProductCatalog("electronics", electronicsCatalogJson());
        stubProductCatalog("books", booksCatalogJson());
        stubProductCatalog("sports", sportsCatalogJson());
    }

    @Test
    @Order(1)
    void fetchesAndLogsFullRecommendationResponse() {
        log.info("┌─────────────────────────────────────────────────────────────");
        log.info("│ TEST 1: Full recommendation flow — userId=user-123");
        log.info("├─────────────────────────────────────────────────────────────");

        client.get()
                .uri("/recommendations?userId=user-123")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(RecommendationResponse.class)
                .consumeWith(result -> {
                    var response = result.getResponseBody();
                    assertThat(response).isNotNull();

                    log.info("│ HTTP Status          : {}", result.getStatus());
                    log.info("│ Total Recommendations: {}", response.totalRecommendations());
                    log.info("├─────────────────────────────────────────────────────────────");

                    response.recommendations().forEach(item ->
                            log.info("│  [{}] {} | €{} (was €{}) | -{}% | ⭐ {} | {} | {}",
                                    item.productId(),
                                    item.productName(),
                                    item.currentPrice(),
                                    item.originalPrice(),
                                    item.discount(),
                                    item.averageRating(),
                                    item.availability(),
                                    item.recommendationReason())
                    );

                    log.info("└─────────────────────────────────────────────────────────────");

                    assertThat(response.totalRecommendations()).isEqualTo(4);
                    assertThat(response.recommendations()).allMatch(
                            item -> item.currentPrice() >= 20.0 && item.currentPrice() <= 300.0,
                            "all products must be within user price range [20, 300]"
                    );
                    assertThat(response.recommendations()).allMatch(
                            item -> "IN_STOCK".equals(item.availability()) || "LOW_STOCK".equals(item.availability()),
                            "only available products must be returned"
                    );
                    assertThat(response.recommendations())
                            .anyMatch(item -> item.recommendationReason().contains("discount"))
                            .anyMatch(item -> item.recommendationReason().contains("Highly rated"));
                });
    }

    @Test
    @Order(2)
    void filtersByCategoryAndLogsResult() {
        log.info("┌─────────────────────────────────────────────────────────────");
        log.info("│ TEST 2: Filter by category=electronics");
        log.info("├─────────────────────────────────────────────────────────────");

        client.get()
                .uri("/recommendations?userId=user-123&category=electronics")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(RecommendationResponse.class)
                .consumeWith(result -> {
                    var response = result.getResponseBody();

                    log.info("│ HTTP Status: {}", result.getStatus());
                    log.info("│ Recommendations for 'electronics' only:");
                    response.recommendations().forEach(item ->
                            log.info("│  [{}] {} | €{} | {} | {}",
                                    item.productId(), item.productName(),
                                    item.currentPrice(), item.availability(),
                                    item.recommendationReason())
                    );
                    log.info("└─────────────────────────────────────────────────────────────");

                    assertThat(response.recommendations())
                            .allMatch(item -> "electronics".equals(item.category()));
                    assertThat(response.totalRecommendations()).isEqualTo(2);
                });
    }

    @Test
    @Order(3)
    void logsUserNotFoundError() {
        log.info("┌─────────────────────────────────────────────────────────────");
        log.info("│ TEST 3: User not found — userId=notfound");
        log.info("├─────────────────────────────────────────────────────────────");

        client.get()
                .uri("/recommendations?userId=notfound")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(String.class)
                .consumeWith(result -> {
                    log.info("│ HTTP Status  : {} (expected 404)", result.getStatus());
                    log.info("│ Response body: {}", result.getResponseBody());
                    log.info("└─────────────────────────────────────────────────────────────");
                });
    }

    @Test
    @Order(4)
    void logsPartialResultsWhenOneCategoryFails() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/products/category/electronics"))
                .atPriority(1)
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Service Unavailable\"}")
                        .withFixedDelay(50)));

        log.info("┌─────────────────────────────────────────────────────────────");
        log.info("│ TEST 4: Partial results — electronics returns 503");
        log.info("├─────────────────────────────────────────────────────────────");

        client.get()
                .uri("/recommendations?userId=user-123")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(RecommendationResponse.class)
                .consumeWith(result -> {
                    var response = result.getResponseBody();

                    log.info("│ HTTP Status: {} (200 even with partial failure)", result.getStatus());
                    log.info("│ Recommendations returned: {} (electronics skipped)", response.totalRecommendations());
                    response.recommendations().forEach(item ->
                            log.info("│  [{}] {} | category={} | €{}",
                                    item.productId(), item.productName(),
                                    item.category(), item.currentPrice())
                    );
                    log.info("└─────────────────────────────────────────────────────────────");

                    assertThat(response.recommendations())
                            .noneMatch(item -> "electronics".equals(item.category()));
                    assertThat(response.recommendations()).isNotEmpty();
                });
    }

    @Test
    @Order(5)
    void logsUnauthenticatedRequest() {
        log.info("┌─────────────────────────────────────────────────────────────");
        log.info("│ TEST 5: No credentials — expect 401");
        log.info("├─────────────────────────────────────────────────────────────");

        webTestClient.get()
                .uri("/recommendations?userId=user-123")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(String.class)
                .consumeWith(result ->
                        log.info("│ HTTP Status: {} | WWW-Authenticate: {}",
                                result.getStatus(),
                                result.getResponseHeaders().getFirst("WWW-Authenticate"))
                );

        log.info("└─────────────────────────────────────────────────────────────");
    }

    @Test
    @Order(6)
    void logsSubmittedFeedback() {
        log.info("┌─────────────────────────────────────────────────────────────");
        log.info("│ TEST 6: Submit feedback — userId=user-123, productId=PROD-E001");
        log.info("├─────────────────────────────────────────────────────────────");

        var request = new FeedbackRequest("user-123", "PROD-E001", FeedbackType.LIKED, "Great headphones!");

        log.info("│ Request body: userId={} productId={} feedback={} comment={}",
                request.userId(), request.productId(), request.feedback(), request.comment());

        client.post()
                .uri("/recommendations/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .consumeWith(result -> {
                    log.info("│ HTTP Status  : {}", result.getStatus());
                    log.info("│ Response body: {}", result.getResponseBody());
                });

        log.info("└─────────────────────────────────────────────────────────────");
    }

    private void stubUserProfile() {
        wireMockServer.stubFor(get(urlPathMatching("/api/users/(notfound|unknown|invalid)/profile"))
                .atPriority(1)
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"User Not Found\",\"message\":\"User profile not found\",\"userId\":\"notfound\"}")));

        wireMockServer.stubFor(get(urlPathMatching("/api/users/[^/]+/profile"))
                .atPriority(2)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userProfileJson())
                        .withFixedDelay(100)));
    }

    private void stubProductCatalog(String category, String body) {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/products/category/" + category))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)
                        .withFixedDelay(50)));
    }

    private static String userProfileJson() {
        return """
                {
                  "userId": "user-123",
                  "demographics": {
                    "age": 30, "gender": "male", "location": "Lisbon",
                    "income": "50000-75000", "education": "Bachelor"
                  },
                  "preferences": {
                    "categories": ["electronics", "books", "sports"],
                    "priceRange": {"min": 20, "max": 300},
                    "brands": ["Apple", "Sony"],
                    "style": "minimalist"
                  },
                  "purchaseHistory": {
                    "totalOrders": 15, "averageOrderValue": 150.0,
                    "lastPurchaseDate": "2024-01-15",
                    "favoriteCategories": ["electronics"],
                    "seasonalPatterns": {"spring": 10, "summer": 8, "fall": 15, "winter": 12}
                  },
                  "behaviorPatterns": {
                    "browsingFrequency": "daily", "purchaseFrequency": "monthly",
                    "priceSensitivity": "medium", "brandLoyalty": "high"
                  }
                }
                """;
    }

    private static String electronicsCatalogJson() {
        return """
                {
                  "category": "electronics", "totalProducts": 3,
                  "products": [
                    {"productId": "PROD-E001", "name": "Premium Wireless Headphones",
                     "currentPrice": 149.99, "originalPrice": 249.99,
                     "averageRating": 4.5, "totalReviews": 342, "availability": "IN_STOCK"},
                    {"productId": "PROD-E002", "name": "Bluetooth Speaker",
                     "currentPrice": 89.99, "originalPrice": 89.99,
                     "averageRating": 4.3, "totalReviews": 128, "availability": "IN_STOCK"},
                    {"productId": "PROD-E003", "name": "Professional Monitor",
                     "currentPrice": 350.00, "originalPrice": 420.00,
                     "averageRating": 4.7, "totalReviews": 89, "availability": "IN_STOCK"}
                  ],
                  "filters": {"priceRange": {"min": 10.0, "max": 500.0}, "brands": ["Sony", "Samsung"]}
                }
                """;
    }

    private static String booksCatalogJson() {
        return """
                {
                  "category": "books", "totalProducts": 2,
                  "products": [
                    {"productId": "PROD-B001", "name": "Clean Code",
                     "currentPrice": 35.00, "originalPrice": 45.00,
                     "averageRating": 4.8, "totalReviews": 512, "availability": "IN_STOCK"},
                    {"productId": "PROD-B002", "name": "Design Patterns",
                     "currentPrice": 15.00, "originalPrice": 55.00,
                     "averageRating": 4.6, "totalReviews": 280, "availability": "LOW_STOCK"}
                  ],
                  "filters": {"priceRange": {"min": 5.0, "max": 100.0}, "brands": []}
                }
                """;
    }

    private static String sportsCatalogJson() {
        return """
                {
                  "category": "sports", "totalProducts": 2,
                  "products": [
                    {"productId": "PROD-S001", "name": "Running Shoes Pro",
                     "currentPrice": 120.00, "originalPrice": 150.00,
                     "averageRating": 4.2, "totalReviews": 89, "availability": "IN_STOCK"},
                    {"productId": "PROD-S002", "name": "Yoga Mat Premium",
                     "currentPrice": 25.00, "originalPrice": 25.00,
                     "averageRating": 3.8, "totalReviews": 45, "availability": "OUT_OF_STOCK"}
                  ],
                  "filters": {"priceRange": {"min": 10.0, "max": 200.0}, "brands": ["Nike", "Adidas"]}
                }
                """;
    }
}
