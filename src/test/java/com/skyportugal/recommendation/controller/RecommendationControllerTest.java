package com.skyportugal.recommendation.controller;

import com.skyportugal.recommendation.api.dto.FeedbackRequest;
import com.skyportugal.recommendation.api.dto.FeedbackResponse;
import com.skyportugal.recommendation.api.dto.RecommendationItem;
import com.skyportugal.recommendation.api.dto.RecommendationResponse;
import com.skyportugal.recommendation.domain.FeedbackType;
import com.skyportugal.recommendation.exception.UserNotFoundException;
import com.skyportugal.recommendation.service.FeedbackService;
import com.skyportugal.recommendation.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebFluxTest(RecommendationController.class)
@Import(com.skyportugal.recommendation.config.SecurityConfig.class)
class RecommendationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private RecommendationService recommendationService;

    @MockBean
    private FeedbackService feedbackService;

    @Test
    @WithMockUser
    void returnsRecommendationsForValidUser() {
        var response = new RecommendationResponse(
                List.of(new RecommendationItem(
                        "user-1", "PROD-001", "Premium electronics Item", "electronics",
                        149.99, 249.99, 40, 4.5, 342, "IN_STOCK",
                        "Great deal with 40% discount", "2025-01-01T00:00:00Z"
                )),
                1
        );
        when(recommendationService.getRecommendations(anyString(), any())).thenReturn(Mono.just(response));

        webTestClient.get()
                .uri("/recommendations?userId=user-1")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalRecommendations").isEqualTo(1)
                .jsonPath("$.recommendations[0].productId").isEqualTo("PROD-001");
    }

    @Test
    @WithMockUser
    void returns404WhenUserNotFound() {
        when(recommendationService.getRecommendations(anyString(), any()))
                .thenReturn(Mono.error(new UserNotFoundException("notfound")));

        webTestClient.get()
                .uri("/recommendations?userId=notfound")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error").isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void returns401WhenUnauthenticated() {
        webTestClient.get()
                .uri("/recommendations?userId=user-1")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @WithMockUser
    void acceptsFeedbackAndReturnsSuccess() {
        when(feedbackService.save(any())).thenReturn(
                Mono.just(new FeedbackResponse("success", "Feedback recorded successfully")));

        var request = new FeedbackRequest("user-1", "PROD-001", FeedbackType.LIKED, "Great product");

        webTestClient.post()
                .uri("/recommendations/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("success");
    }

    @Test
    @WithMockUser
    void returns400ForInvalidFeedbackRequest() {
        var invalidRequest = new FeedbackRequest("", "", null, null);

        webTestClient.post()
                .uri("/recommendations/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest();
    }
}
