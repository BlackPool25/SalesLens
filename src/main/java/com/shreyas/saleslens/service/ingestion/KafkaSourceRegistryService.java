package com.shreyas.saleslens.service.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.enums.SourceType;
import com.shreyas.saleslens.repository.DataSourceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaSourceRegistryService {

    private final DataSourceRepository dataSourceRepository;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    private volatile Map<String, UUID> sourceSystemCache = Collections.emptyMap();

    @PostConstruct
    public void init() {
        log.info("Warming Kafka source system cache...");
        refreshCache();
    }

    @Scheduled(fixedDelayString = "${saleslens.batch.streaming.source-cache-ttl-ms:300000}")
    public void refreshCache() {
        try {
            List<DataSource> activeKafkaSources = dataSourceRepository.findBySourceTypeAndActive(SourceType.KAFKA_STREAM, true);
            Map<String, UUID> newCache = new ConcurrentHashMap<>();

            for (DataSource source : activeKafkaSources) {
                String connectionConfig = source.getConnectionConfig();
                if (connectionConfig == null || connectionConfig.isBlank()) {
                    continue;
                }
                try {
                    JsonNode root = objectMapper.readTree(connectionConfig);
                    JsonNode sourceSystems = root.get("sourceSystems");
                    if (sourceSystems != null && sourceSystems.isArray()) {
                        for (JsonNode sys : sourceSystems) {
                            String systemName = sys.asText();
                            if (systemName != null && !systemName.isBlank()) {
                                newCache.put(systemName, source.getId());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse connectionConfig for source {}: {}", source.getId(), e.getMessage());
                }
            }

            sourceSystemCache = newCache;
            log.debug("Refreshed Kafka source system cache with {} entries", newCache.size());
        } catch (Exception e) {
            log.error("Failed to refresh Kafka source system cache", e);
        }
    }

    public DataSource resolveSource(String sourceSystem) {
        UUID cachedId = sourceSystemCache.get(sourceSystem);
        if (cachedId != null) {
            Optional<DataSource> cached = dataSourceRepository.findById(cachedId);
            if (cached.isPresent()) {
                return cached.get();
            }
            // Cache miss on ID — entity was deleted; fall through to refresh
            log.debug("Cached DataSource {} for sourceSystem {} not found in DB, refreshing", cachedId, sourceSystem);
        }

        // Cache miss — query via native JSONB operator
        meterRegistry.counter("saleslens.stream.source.cache-miss").increment();

        List<DataSource> results = dataSourceRepository.findBySourceSystem(sourceSystem);
        if (results.isEmpty()) {
            meterRegistry.counter("saleslens.stream.source.unknown").increment();
            throw new UnknownSourceException(sourceSystem);
        }

        DataSource source = results.getFirst();
        // Update cache with the new mapping
        Map<String, UUID> updatedCache = new ConcurrentHashMap<>(sourceSystemCache);
        updatedCache.put(sourceSystem, source.getId());
        sourceSystemCache = updatedCache;

        return source;
    }
}
