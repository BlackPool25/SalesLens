package com.shreyas.saleslens.service.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.StagedRecord;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

public final class StagedRecordHelper {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private StagedRecordHelper() {}

    public static String toJson(Map<String, String> row) {
        try {
            return objectMapper.writeValueAsString(row);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize row to JSON", e);
        }
    }
    
    public static String sha256(String input) {
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
    
    public static StagedRecord toStagedRecord(IngestionJob job, DataSource source,
                                        int rowNum, Map<String, String> payload) {
        String rawPayload = toJson(payload);
        StagedRecord record = new StagedRecord();
        record.setJob(job);
        record.setSource(source);
        record.setRawPayload(rawPayload);
        record.setRowNumber(rowNum);
        record.setRecordHash(sha256(rawPayload));
        return record;
    }
}
