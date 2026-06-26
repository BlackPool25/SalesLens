package com.shreyas.saleslens.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.model.FieldMapping;
import com.shreyas.saleslens.model.StagedRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class TransformationService {

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, Set<String>> tableColumnsCache = new ConcurrentHashMap<>();

    private static final Map<String, Set<String>> STANDARD_COLUMNS;

    static {
        Map<String, Set<String>> map = new HashMap<>();
        map.put("customers", new HashSet<>(Arrays.asList("name", "email", "phone", "segment", "status")));
        map.put("products", new HashSet<>(Arrays.asList("sku", "name", "category", "sub_category", "unit_price", "currency")));
        map.put("orders", new HashSet<>(Arrays.asList("order_date", "ship_date", "ship_mode", "shipping_cost", "total_amount", "currency")));
        map.put("salespersons", new HashSet<>(Arrays.asList("name", "email", "team", "territory", "region", "active")));
        map.put("currency", new HashSet<>(Collections.singletonList("currency_code")));
        STANDARD_COLUMNS = Collections.unmodifiableMap(map);
    }

    public TransformationService() {
        this(new ObjectMapper(), null);
    }

    public TransformationService(
            @Autowired(required = false) ObjectMapper objectMapper,
            @Autowired(required = false) JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.jdbcTemplate = jdbcTemplate;
    }

    public void clearSchemaCache() {
        tableColumnsCache.clear();
        log.info("Cleared TransformationService schema cache.");
    }

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
            // Map to store temporary additional attributes per entity
            Map<String, Map<String, Object>> additionalAttributesMap = new LinkedHashMap<>();

            for (Map.Entry<String, Object> entry : rawPayload.entrySet()) {
                String sourceField = entry.getKey();
                Object valObj = entry.getValue();

                FieldMapping mapping = findMapping(sourceField, mappings);
                if (mapping != null) {
                    String entity = mapping.getCanonicalEntity();
                    String field = mapping.getCanonicalField();

                    // If entity or field is missing, skip the mapping (invalid/incomplete mapping)
                    if (entity == null || entity.trim().isEmpty() || field == null || field.trim().isEmpty()) {
                        continue;
                    }

                    boolean isFirstClass = columnExists(entity, field) &&
                            "AUTO_CONFIRMED".equalsIgnoreCase(mapping.getStatus());

                    if (isFirstClass) {
                        String singularEntity = entity.endsWith("s") ? entity.substring(0, entity.length() - 1) : entity;
                        result.put(singularEntity + "." + field, valObj == null ? null : valObj.toString());
                    } else {
                        // For unmapped/dynamic attributes (including PENDING status)
                        // If the value is not null, serialize it into additional_attributes
                        if (valObj != null) {
                            additionalAttributesMap.computeIfAbsent(entity.toLowerCase(), k -> new LinkedHashMap<>())
                                    .put(field, valObj);
                        }
                    }
                }
            }

            // Serialize all additional attributes Map to JSON strings and add to result
            for (Map.Entry<String, Map<String, Object>> entry : additionalAttributesMap.entrySet()) {
                String entity = entry.getKey();
                String singularEntity = entity.endsWith("s") ? entity.substring(0, entity.length() - 1) : entity;
                String jsonStr = objectMapper.writeValueAsString(entry.getValue());
                result.put(singularEntity + ".additional_attributes", jsonStr);
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to transform record: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    private FieldMapping findMapping(String sourceField, List<FieldMapping> mappings) {
        if (mappings == null) return null;
        for (FieldMapping m : mappings) {
            if (sourceField.equalsIgnoreCase(m.getSourceFieldName())) {
                return m;
            }
        }
        return null;
    }

    private boolean columnExists(String entity, String column) {
        if (entity == null || column == null) return false;
        String lowerEntity = entity.toLowerCase();
        String lowerColumn = column.toLowerCase();

        if (jdbcTemplate != null) {
            Set<String> dbCols = tableColumnsCache.computeIfAbsent(lowerEntity, e -> {
                try {
                    List<String> cols = jdbcTemplate.queryForList(
                            "SELECT column_name FROM information_schema.columns WHERE table_schema = 'canonical' AND table_name = ?",
                            String.class,
                            e
                    );
                    return new HashSet<>(cols);
                } catch (Exception ex) {
                    log.error("Failed to fetch columns for canonical.{}: {}", e, ex.getMessage());
                    return Collections.emptySet();
                }
            });
            if (!dbCols.isEmpty()) {
                return dbCols.contains(lowerColumn);
            }
        }

        Set<String> stdCols = STANDARD_COLUMNS.get(lowerEntity);
        return stdCols != null && stdCols.contains(lowerColumn);
    }
}
