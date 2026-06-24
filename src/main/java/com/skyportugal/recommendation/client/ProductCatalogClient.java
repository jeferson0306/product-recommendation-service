package com.skyportugal.recommendation.client;

import com.skyportugal.recommendation.client.dto.ProductCatalogResponse;
import com.skyportugal.recommendation.exception.ServiceUnavailableException;
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
public class ProductCatalogClient {

    private final WebClient webClient;
    private final Cache cache;

    public ProductCatalogClient(
            @Qualifier("productCatalogWebClient") WebClient webClient,
            CacheManager cacheManager) {
        this.webClient = webClient;
        this.cache = cacheManager.getCache("productCatalog");
    }

    @CircuitBreaker(name = "productCatalogService", fallbackMethod = "fallback")
    public Mono<ProductCatalogResponse> fetchByCategory(String category) {
        var cached = cache.get(category, ProductCatalogResponse.class);
        if (cached != null) {
            log.debug("Cache hit for product catalog category={}", category);
            return Mono.just(cached);
        }

        return webClient.get()
                .uri("/api/products/category/{category}", category)
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        Mono.error(new ServiceUnavailableException("Product catalog service returned 5xx for category: " + category)))
                .bodyToMono(ProductCatalogResponse.class)
                .timeout(Duration.ofSeconds(2))
                .retryWhen(Retry.backoff(3, Duration.ofMillis(200))
                        .filter(e -> e instanceof ServiceUnavailableException))
                .doOnSuccess(response -> cache.put(category, response))
                .doOnError(e -> log.warn("Failed to fetch products category={} error={}", category, e.getMessage()));
    }

    private Mono<ProductCatalogResponse> fallback(String category, Throwable cause) {
        log.warn("Circuit breaker open for product catalog service category={}", category);
        return Mono.error(new ServiceUnavailableException("Product catalog service is currently unavailable", cause));
    }
}
