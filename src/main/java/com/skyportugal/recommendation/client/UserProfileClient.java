package com.skyportugal.recommendation.client;

import com.skyportugal.recommendation.client.dto.UserProfileResponse;
import com.skyportugal.recommendation.exception.ServiceUnavailableException;
import com.skyportugal.recommendation.exception.UserNotFoundException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Component
@Slf4j
public class UserProfileClient {

    private final WebClient webClient;
    private final Cache cache;

    public UserProfileClient(
            @Qualifier("userProfileWebClient") WebClient webClient,
            CacheManager cacheManager) {
        this.webClient = webClient;
        this.cache = cacheManager.getCache("userProfiles");
    }

    @CircuitBreaker(name = "userProfileService", fallbackMethod = "fallback")
    public Mono<UserProfileResponse> fetchProfile(String userId) {
        var cached = cache.get(userId, UserProfileResponse.class);
        if (cached != null) {
            log.debug("Cache hit for user profile userId={}", userId);
            return Mono.just(cached);
        }

        return webClient.get()
                .uri("/api/users/{userId}/profile", userId)
                .retrieve()
                .onStatus(status -> status.value() == 404, response ->
                        Mono.error(new UserNotFoundException(userId)))
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        Mono.error(new ServiceUnavailableException("User profile service returned 5xx")))
                .bodyToMono(UserProfileResponse.class)
                .timeout(Duration.ofSeconds(5))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(500))
                        .filter(e -> !(e instanceof UserNotFoundException)))
                .doOnSuccess(profile -> cache.put(userId, profile))
                .doOnError(e -> log.warn("Failed to fetch user profile userId={} error={}", userId, e.getMessage()));
    }

    private Mono<UserProfileResponse> fallback(String userId, Throwable cause) {
        if (cause instanceof UserNotFoundException) {
            return Mono.error(cause);
        }
        log.warn("Circuit breaker open for user profile service userId={}", userId);
        return Mono.error(new ServiceUnavailableException("User profile service is currently unavailable", cause));
    }
}
