package com.skyportugal.recommendation.service;

import com.skyportugal.recommendation.api.dto.FeedbackRequest;
import com.skyportugal.recommendation.api.dto.FeedbackResponse;
import com.skyportugal.recommendation.domain.Feedback;
import com.skyportugal.recommendation.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Slf4j
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;

    public Mono<FeedbackResponse> save(FeedbackRequest request) {
        return Mono.fromCallable(() -> persist(request))
                .subscribeOn(Schedulers.boundedElastic())
                .map(saved -> {
                    log.info("Feedback saved: userId={} productId={} type={}", saved.getUserId(), saved.getProductId(), saved.getFeedbackType());
                    return new FeedbackResponse("success", "Feedback recorded successfully");
                });
    }

    private Feedback persist(FeedbackRequest request) {
        var feedback = Feedback.builder()
                .userId(request.userId())
                .productId(request.productId())
                .feedbackType(request.feedback())
                .comment(request.comment())
                .build();
        return feedbackRepository.save(feedback);
    }
}
