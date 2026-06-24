package com.skyportugal.recommendation.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        var manager = new CaffeineCacheManager();
        manager.registerCustomCache("userProfiles",
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(30))
                        .maximumSize(1000)
                        .build());
        manager.registerCustomCache("productCatalog",
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(2))
                        .maximumSize(500)
                        .build());
        return manager;
    }
}
