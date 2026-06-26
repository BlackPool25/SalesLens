package com.shreyas.saleslens.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;
import java.util.Map;

/**
 * Configures Redis cache regions with TTL and null-value policies
 * for the caching abstraction layer.
 */
@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration qualityCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues();

        RedisCacheConfiguration conflictCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(15))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .withInitialCacheConfigurations(Map.of(
                        "quality-cache", qualityCacheConfig,
                        "conflict-cache", conflictCacheConfig
                ))
                .build();
    }
}
