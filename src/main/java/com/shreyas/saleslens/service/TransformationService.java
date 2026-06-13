package com.shreyas.saleslens.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.model.FieldMapping;
import com.shreyas.saleslens.model.StagedRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransformationService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, String> transform(StagedRecord record, List<FieldMapping> mappings) {
        if (record == null || record.getRawPayload() == null) {
            return Collections.emptyMap();
        }

        try {
            Map<String, Object> rawPayload = objectMapper.readValue(
                    record.getRawPayload(),
                    new TypeReference<Map<String, Object>>() {}
            );

            Map<String, String> result = new LinkedHashMap<>();

            for (FieldMapping mapping : mappings) {
                if (!"AUTO_CONFIRMED".equalsIgnoreCase(mapping.getStatus())) {
                    continue;
                }

                String sourceField = mapping.getSourceFieldName();
                if (sourceField == null || !rawPayload.containsKey(sourceField)) {
                    continue;
                }

                String entity = mapping.getCanonicalEntity();
                String field = mapping.getCanonicalField();

                if (entity == null || entity.isEmpty() || field == null || field.isEmpty()) {
                    continue;
                }

                // Convert plural entity to singular form
                if (entity.endsWith("s")) {
                    entity = entity.substring(0, entity.length() - 1);
                }

                String targetKey = entity + "." + field;
                Object valObj = rawPayload.get(sourceField);
                String valStr = valObj == null ? null : valObj.toString();

                result.put(targetKey, valStr);
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to transform record: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
