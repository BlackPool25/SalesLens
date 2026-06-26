package com.shreyas.saleslens.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;

/**
 * Test configuration that provides a simple in-memory {@link ConcurrentMapCacheManager}
 * for {@code @WebMvcTest} slices. The production {@link CacheConfig} registers a
 * {@code RedisCacheManager} which requires a full Redis connection — not available
 * in sliced test contexts. This test config satisfies the {@code CacheManager}
 * dependency needed by {@code @EnableCaching} on the application class without
 * pulling in Redis infrastructure.
 */
@TestConfiguration
public class TestCacheConfig {

    @Bean
    public ConcurrentMapCacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }
}
