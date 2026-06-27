package com.shreyas.saleslens.service.conflict;

import com.shreyas.saleslens.model.*;
import com.shreyas.saleslens.model.enums.ConflictStatus;
import com.shreyas.saleslens.repository.*;
import com.shreyas.saleslens.service.cache.QualityCacheService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for resolving or suppressing detected data conflicts.
 * <p>
 * Resolution updates the canonical entity with the chosen value and marks the
 * conflict as RESOLVED. Suppression marks the conflict as SUPPRESSED without
 * changing the canonical value.
 * <p>
 * Optional LLM-powered resolution suggestions are available via
 * {@link #getSuggestedResolution(UUID)} — purely advisory and never blocking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConflictResolutionService {

    private final ConflictRecordRepository conflictRecordRepository;
    private final CanonicalCustomerRepository canonicalCustomerRepository;
    private final CanonicalProductRepository canonicalProductRepository;
    private final CanonicalSalespersonRepository canonicalSalespersonRepository;
    private final CanonicalOrderRepository canonicalOrderRepository;

    @Autowired(required = false)
    private ChatLanguageModel chatLanguageModel;

    @Autowired(required = false)
    private QualityCacheService qualityCacheService;

    /**
     * Resolve a conflict by updating the canonical entity with the chosen value.
     *
     * @param conflictId  ID of the conflict record to resolve
     * @param chosenValue the value to set on the canonical entity
     * @param resolvedBy  the user performing the resolution
     * @return Optional containing the updated ConflictRecord, or empty if not found
     * @throws IllegalArgumentException if chosenValue is null or empty
     */
    @Transactional
    public Optional<ConflictRecord> resolveConflict(UUID conflictId, String chosenValue, Users resolvedBy) {
        if (chosenValue == null || chosenValue.trim().isEmpty()) {
            throw new IllegalArgumentException("chosenValue must be non-null and non-empty");
        }

        Optional<ConflictRecord> optRecord = conflictRecordRepository.findById(conflictId);
        if (optRecord.isEmpty()) {
            return Optional.empty();
        }

        ConflictRecord record = optRecord.get();

        // If already resolved or suppressed, return as-is (no-op)
        if (record.getStatus() != ConflictStatus.OPEN) {
            log.debug("Conflict {} is already {} — no-op", conflictId, record.getStatus());
            return optRecord;
        }

        // Load the canonical entity and update the field
        Object entity = loadEntity(record.getEntityType(), record.getEntityId());
        if (entity != null) {
            setFieldValue(entity, record.getFieldName(), chosenValue);
            saveEntity(record.getEntityType(), entity);
            log.info("Updated {} {} field '{}' to '{}'",
                    record.getEntityType(), record.getEntityId(), record.getFieldName(), chosenValue);
        } else {
            log.warn("Entity {} with ID {} not found — resolving conflict without entity update",
                    record.getEntityType(), record.getEntityId());
        }

        // Update conflict record
        record.setStatus(ConflictStatus.RESOLVED);
        record.setResolvedBy(resolvedBy);
        record.setResolvedAt(Instant.now());

        ConflictRecord saved = conflictRecordRepository.save(record);
        log.info("Conflict {} resolved by user {}", conflictId, resolvedBy.getId());

        // Evict conflict cache for source A (and source B if available)
        if (qualityCacheService != null && record.getSourceA() != null) {
            qualityCacheService.evictConflictCache(record.getSourceA().getId());
        }

        return Optional.of(saved);
    }

    /**
     * Suppress a conflict without changing the canonical entity value.
     *
     * @param conflictId ID of the conflict record to suppress
     * @param resolvedBy the user performing the suppression
     * @return Optional containing the updated ConflictRecord, or empty if not found
     */
    @Transactional
    public Optional<ConflictRecord> suppressConflict(UUID conflictId, Users resolvedBy) {
        Optional<ConflictRecord> optRecord = conflictRecordRepository.findById(conflictId);
        if (optRecord.isEmpty()) {
            return Optional.empty();
        }

        ConflictRecord record = optRecord.get();
        record.setStatus(ConflictStatus.SUPPRESSED);
        record.setResolvedBy(resolvedBy);
        record.setResolvedAt(Instant.now());

        ConflictRecord saved = conflictRecordRepository.save(record);
        log.info("Conflict {} suppressed by user {}", conflictId, resolvedBy.getId());

        // Evict conflict cache for source A (and source B if available)
        if (qualityCacheService != null && record.getSourceA() != null) {
            qualityCacheService.evictConflictCache(record.getSourceA().getId());
        }

        return Optional.of(saved);
    }

    /**
     * Get an LLM-generated resolution suggestion for a flagged conflict.
     * <p>
     * This is purely advisory and never blocks. If the LLM is unavailable or
     * returns invalid output, the system falls back gracefully (returns null).
     *
     * @param conflictId ID of the conflict to get a suggestion for
     * @return a future containing the suggestion text, or null if LLM unavailable
     */
    @Async
    public CompletableFuture<String> getSuggestedResolution(UUID conflictId) {
        if (chatLanguageModel == null) {
            log.debug("LLM not available — skipping resolution suggestion");
            return CompletableFuture.completedFuture(null);
        }

        try {
            Optional<ConflictRecord> optRecord = conflictRecordRepository.findById(conflictId);
            if (optRecord.isEmpty()) {
                log.debug("Conflict {} not found — returning null suggestion", conflictId);
                return CompletableFuture.completedFuture(null);
            }

            ConflictRecord record = optRecord.get();
            String prompt = buildResolutionPrompt(record);

            log.debug("Requesting resolution suggestion for conflict: {} (field={})",
                    conflictId, record.getFieldName());

            String response = chatLanguageModel.generate(prompt);
            String suggestion = response != null ? response.trim() : null;

            if (suggestion != null && !suggestion.isEmpty()) {
                log.info("Generated resolution suggestion for conflict {} ({} chars)",
                        conflictId, suggestion.length());
                return CompletableFuture.completedFuture(suggestion);
            }

            log.warn("LLM returned empty suggestion for conflict {}", conflictId);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.warn("Failed to generate resolution suggestion for conflict {}: {}",
                    conflictId, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    // ---- Entity loading / saving ----

    private Object loadEntity(String entityType, UUID entityId) {
        return switch (entityType) {
            case "CanonicalCustomer" -> canonicalCustomerRepository.findById(entityId).orElse(null);
            case "CanonicalProduct" -> canonicalProductRepository.findById(entityId).orElse(null);
            case "CanonicalSalesperson" -> canonicalSalespersonRepository.findById(entityId).orElse(null);
            case "CanonicalOrder" -> canonicalOrderRepository.findById(entityId).orElse(null);
            default -> {
                log.warn("Unknown entity type: {}", entityType);
                yield null;
            }
        };
    }

    private void saveEntity(String entityType, Object entity) {
        switch (entityType) {
            case "CanonicalCustomer" -> canonicalCustomerRepository.save((CanonicalCustomer) entity);
            case "CanonicalProduct" -> canonicalProductRepository.save((CanonicalProduct) entity);
            case "CanonicalSalesperson" -> canonicalSalespersonRepository.save((CanonicalSalesperson) entity);
            case "CanonicalOrder" -> canonicalOrderRepository.save((CanonicalOrder) entity);
            default -> log.warn("Unknown entity type for save: {}", entityType);
        }
    }

    // ---- Reflection-based field update ----

    private void setFieldValue(Object entity, String fieldName, String value) {
        String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        Method[] methods = entity.getClass().getMethods();
        for (Method method : methods) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                Class<?> paramType = method.getParameterTypes()[0];
                try {
                    Object converted = convertValue(value, paramType);
                    method.invoke(entity, converted);
                    log.debug("Set {}.{} to {}", entity.getClass().getSimpleName(), fieldName, value);
                    return;
                } catch (Exception e) {
                    log.warn("Failed to set field '{}' on {}: {}", fieldName,
                            entity.getClass().getSimpleName(), e.getMessage());
                }
            }
        }
        log.warn("No setter found for field '{}' on {}", fieldName, entity.getClass().getSimpleName());
    }

    private Object convertValue(String value, Class<?> targetType) {
        if (targetType == String.class) {
            return value;
        }
        if (targetType == BigDecimal.class) {
            return new BigDecimal(value);
        }
        if (targetType == Long.class || targetType == long.class) {
            return Long.parseLong(value);
        }
        if (targetType == Integer.class || targetType == int.class) {
            return Integer.parseInt(value);
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.parseBoolean(value);
        }
        // Fallback: return as-is (will likely fail at invocation)
        return value;
    }

    // ---- LLM prompt ----

    private String buildResolutionPrompt(ConflictRecord record) {
        String trustA = record.getSourceA() != null
                ? String.valueOf(record.getSourceA().getTrustScore()) : "N/A";
        String trustB = record.getSourceB() != null
                ? String.valueOf(record.getSourceB().getTrustScore()) : "N/A";
        String sourceAName = record.getSourceA() != null
                ? record.getSourceA().getName() : "Source A";
        String sourceBName = record.getSourceB() != null
                ? record.getSourceB().getName() : "Source B";

        return String.format(
                "You are a data reconciliation analyst. A data conflict has been detected " +
                "between two sources for the same real-world entity.\n\n" +
                "Conflict Details:\n" +
                "- Entity Type: %s\n" +
                "- Entity ID: %s\n" +
                "- Field: %s\n" +
                "- %s (trust: %s) says: \"%s\"\n" +
                "- %s (trust: %s) says: \"%s\"\n\n" +
                "Recommend which value should be used and why. " +
                "Consider trust scores, data freshness, and data quality. " +
                "Provide a concise 2-3 sentence recommendation with reasoning. " +
                "Do not use markdown formatting. Respond in plain text only.",
                record.getEntityType(),
                record.getEntityId(),
                record.getFieldName(),
                sourceAName, trustA, record.getValueA(),
                sourceBName, trustB, record.getValueB()
        );
    }
}
