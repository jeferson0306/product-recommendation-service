package com.skyportugal.recommendation.service;

import com.skyportugal.recommendation.api.dto.FeedbackRequest;
import com.skyportugal.recommendation.domain.Feedback;
import com.skyportugal.recommendation.domain.FeedbackType;
import com.skyportugal.recommendation.repository.FeedbackRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    @InjectMocks
    private FeedbackService feedbackService;

    @Test
    void savesPersistsFeedbackAndReturnsSuccess() {
        when(feedbackRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new FeedbackRequest("user-1", "PROD-001", FeedbackType.LIKED, "Great product");

        StepVerifier.create(feedbackService.save(request))
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo("success");
                    assertThat(response.message()).isEqualTo("Feedback recorded successfully");
                })
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("user-1");
        assertThat(captor.getValue().getProductId()).isEqualTo("PROD-001");
        assertThat(captor.getValue().getFeedbackType()).isEqualTo(FeedbackType.LIKED);
    }
}
