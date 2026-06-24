package com.skyportugal.recommendation.controller;

import com.skyportugal.recommendation.api.dto.FeedbackRequest;
import com.skyportugal.recommendation.api.dto.FeedbackResponse;
import com.skyportugal.recommendation.api.dto.RecommendationResponse;
import com.skyportugal.recommendation.service.FeedbackService;
import com.skyportugal.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
@Slf4j
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final FeedbackService feedbackService;

    @GetMapping
    public Mono<RecommendationResponse> getRecommendations(
            @RequestParam String userId,
            @RequestParam(required = false) String category) {
        log.info("GET /recommendations userId={} category={}", userId, category);
        return recommendationService.getRecommendations(userId, category);
    }

    @PostMapping("/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<FeedbackResponse> submitFeedback(@Valid @RequestBody FeedbackRequest request) {
        log.info("POST /recommendations/feedback userId={} productId={}", request.userId(), request.productId());
        return feedbackService.save(request);
    }
}
