package com.shreyas.saleslens.batch.csv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.StagedRecord;
import com.shreyas.saleslens.repository.DataSourceRepository;
import com.shreyas.saleslens.repository.IngestionJobRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@StepScope
public class CsvItemProcessor implements ItemProcessor<FieldSet, StagedRecord> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final IngestionJob jobRef;
    private final DataSource sourceRef;
    private final AtomicInteger rowCounter = new AtomicInteger(1);

    public CsvItemProcessor(
            IngestionJobRepository ingestionJobRepository,
            DataSourceRepository dataSourceRepository,
            @Value("#{jobParameters['ingestionJobId']}") String ingestionJobId,
            @Value("#{jobParameters['sourceId']}") String sourceId) {
        this.jobRef = ingestionJobRepository.getReferenceById(UUID.fromString(ingestionJobId));
        this.sourceRef = dataSourceRepository.getReferenceById(UUID.fromString(sourceId));
    }

    @Override
    public StagedRecord process(FieldSet fieldSet) {
        Map<String, String> row = new LinkedHashMap<>();
        String[] names = fieldSet.getNames();
        for (int i = 0; i < names.length; i++) {
            String value = fieldSet.readString(i);
            row.put(names[i], value.isEmpty() ? null : value);
        }

        String rawPayload;
        try {
            rawPayload = objectMapper.writeValueAsString(row);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize CSV row to JSON", e);
        }

        StagedRecord record = new StagedRecord();
        record.setJob(jobRef);
        record.setSource(sourceRef);
        record.setRawPayload(rawPayload);
        record.setRowNumber(rowCounter.getAndIncrement());
        record.setRecordHash(sha256(rawPayload));
        return record;
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
