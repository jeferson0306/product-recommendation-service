package com.skyportugal.recommendation.repository;

import com.skyportugal.recommendation.domain.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {}
