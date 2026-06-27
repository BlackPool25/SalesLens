package com.shreyas.saleslens;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.kafka.listener.auto-startup=false",
    "spring.kafka.bootstrap-servers=localhost:9092",
    "jwt.secret=test-secret-key-for-testing-purposes-only"
})
@ActiveProfiles("test")
class SaleslensApplicationTests {

    @MockitoBean
    private org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory redisConnectionFactory;

    @MockitoBean
    private com.shreyas.saleslens.service.ingestion.StreamPipelineScheduler streamPipelineScheduler;

    @MockitoBean
    private com.shreyas.saleslens.service.ingestion.KafkaSourceRegistryService kafkaSourceRegistryService;

    @MockitoBean
    private CacheManager cacheManager;

	@Test
	void contextLoads() {
	}
}
