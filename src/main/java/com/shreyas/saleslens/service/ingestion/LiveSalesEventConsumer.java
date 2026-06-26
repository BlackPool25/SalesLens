package com.shreyas.saleslens.service.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.StagedRecord;
import com.shreyas.saleslens.repository.StagedRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class LiveSalesEventConsumer {

    private final KafkaSourceRegistryService sourceRegistry;
    private final StreamIngestionJobManager jobManager;
    private final StagedRecordRepository stagedRecordRepository;
    private final ObjectMapper objectMapper;

    private final AtomicInteger rowCounter = new AtomicInteger(0);

    @KafkaListener(
            topics = "${saleslens.kafka.stream-topic:sales.live}",
            groupId = "${saleslens.kafka.stream-group-id:saleslens-live}",
            containerFactory = "kafkaStreamListenerContainerFactory"
    )
    public void consume(String message,
                        @Header("kafka_receivedTopic") String topic,
                        @Header("kafka_offset") long offset,
                        @Header("kafka_receivedPartitionId") int partition) {

        // 1. Parse JSON
        Map<String, Object> rawMap;
        try {
            rawMap = objectMapper.readValue(message, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Invalid JSON message on topic {} at offset {}: {}", topic, offset, e.getMessage());
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage());
        }

        // 2. Validate required fields
        String eventId = getRequiredString(rawMap, "event_id");
        String sourceSystem = getRequiredString(rawMap, "source_system");
        String eventTime = getRequiredString(rawMap, "event_time");

        // Validate event_time is ISO-8601
        try {
            Instant.parse(eventTime);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid event_time format (must be ISO-8601): " + eventTime);
        }

        // 3. Lookup DataSource via source_system
        DataSource source = sourceRegistry.resolveSource(sourceSystem);

        // 4. Get current window IngestionJob
        IngestionJob job = jobManager.getOrCreateWindowJob(source);

        // 5. Build string map for dedup hash (rawPayload = ORIGINAL JSON string)
        Map<String, String> stringMap = new LinkedHashMap<>();
        rawMap.forEach((k, v) -> stringMap.put(k, v == null ? null : v.toString()));

        // 6. Create StagedRecord - rawPayload is the ORIGINAL Kafka message JSON
        int rowNum = rowCounter.incrementAndGet();
        String recordHash = StagedRecordHelper.sha256(message);  // hash of raw JSON for dedup
        StagedRecord record = new StagedRecord();
        record.setJob(job);
        record.setSource(source);
        record.setRawPayload(message);  // original JSON string
        record.setRecordHash(recordHash);
        record.setRowNumber(rowNum);

        // 7. Save with small transaction scope
        try {
            saveStagedRecord(record);
            log.debug("Staged Kafka event {} for source {} in job {}", eventId, sourceSystem, job.getId());
        } catch (DataIntegrityViolationException e) {
            // Duplicate hash — message already processed
            log.warn("Duplicate Kafka event {} for source {}, skipping (hash={})", eventId, sourceSystem, recordHash);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void saveStagedRecord(StagedRecord record) {
        stagedRecordRepository.save(record);
    }

    private String getRequiredString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return value.toString();
    }
}
