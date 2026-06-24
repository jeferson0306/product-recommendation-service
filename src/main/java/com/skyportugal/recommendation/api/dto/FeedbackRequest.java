package com.skyportugal.recommendation.api.dto;

import com.skyportugal.recommendation.domain.FeedbackType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FeedbackRequest(
        @NotBlank String userId,
        @NotBlank String productId,
        @NotNull FeedbackType feedback,
        String comment
) {}
