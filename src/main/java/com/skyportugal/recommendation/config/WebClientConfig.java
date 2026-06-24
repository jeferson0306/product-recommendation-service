package com.skyportugal.recommendation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean("userProfileWebClient")
    public WebClient userProfileWebClient(
            @Value("${external.services.user-profile.base-url}") String baseUrl) {
        return buildWebClient(baseUrl);
    }

    @Bean("productCatalogWebClient")
    public WebClient productCatalogWebClient(
            @Value("${external.services.product-catalog.base-url}") String baseUrl) {
        return buildWebClient(baseUrl);
    }

    private WebClient buildWebClient(String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }
}
