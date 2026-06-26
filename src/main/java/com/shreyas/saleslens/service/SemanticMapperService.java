package com.shreyas.saleslens.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.model.*;
import com.shreyas.saleslens.model.enums.InferredType;
import com.shreyas.saleslens.repository.DataSourceRepository;
import com.shreyas.saleslens.repository.FieldMappingRepository;
import com.shreyas.saleslens.repository.SourceSchemaFieldRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class SemanticMapperService {

    private final DataSourceRepository dataSourceRepository;
    private final FieldMappingRepository fieldMappingRepository;
    private final SourceSchemaFieldRepository sourceSchemaFieldRepository;
    private final ChatLanguageModel chatLanguageModel;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public static final List<CanonicalFieldInfo> REGISTRY = new CopyOnWriteArrayList<>(Arrays.asList(
            // customers
            new CanonicalFieldInfo("customers", "name", "FREE_TEXT", new ArrayList<>(Arrays.asList("customer_name", "cust_name", "client_name", "buyer_name", "customer"))),
            new CanonicalFieldInfo("customers", "email", "EMAIL", new ArrayList<>(Arrays.asList("mail", "email_address", "contact_email"))),
            new CanonicalFieldInfo("customers", "phone", "PHONE", new ArrayList<>(Arrays.asList("phone_number", "contact_no", "tel", "telephone"))),
            new CanonicalFieldInfo("customers", "segment", "CATEGORY", new ArrayList<>(Arrays.asList("cust_segment", "group", "division"))),

            // products
            new CanonicalFieldInfo("products", "sku", "FREE_TEXT", new ArrayList<>(Arrays.asList("sku", "product_code", "item_code", "prod_id"))),
            new CanonicalFieldInfo("products", "name", "FREE_TEXT", new ArrayList<>(Arrays.asList("product_name", "item", "description", "prod_name"))),
            new CanonicalFieldInfo("products", "category", "CATEGORY", new ArrayList<>(Arrays.asList("prod_category", "category", "dept", "department"))),
            new CanonicalFieldInfo("products", "sub_category", "CATEGORY", new ArrayList<>(Arrays.asList("sub_category", "subcategory", "group"))),
            new CanonicalFieldInfo("products", "unit_price", "DECIMAL", new ArrayList<>(Arrays.asList("price", "unit_price", "cost"))),
            new CanonicalFieldInfo("products", "currency", "CATEGORY", new ArrayList<>(Arrays.asList("curr", "currency_code", "valuta"))),

            // orders
            new CanonicalFieldInfo("orders", "order_date", "DATE", new ArrayList<>(Arrays.asList("order_date", "date", "transaction_date"))),
            new CanonicalFieldInfo("orders", "ship_date", "DATE", new ArrayList<>(Arrays.asList("ship_date", "shipping_date", "delivery_date"))),
            new CanonicalFieldInfo("orders", "ship_mode", "CATEGORY", new ArrayList<>(Arrays.asList("ship_mode", "shipping_method", "carrier"))),
            new CanonicalFieldInfo("orders", "shipping_cost", "DECIMAL", new ArrayList<>(Arrays.asList("shipping_cost", "freight", "shipping_fee"))),
            new CanonicalFieldInfo("orders", "total_amount", "DECIMAL", new ArrayList<>(Arrays.asList("sales", "revenue", "total", "amount", "order_total"))),
            new CanonicalFieldInfo("orders", "currency", "CATEGORY", new ArrayList<>(Arrays.asList("curr", "currency_code")))
    ));

    public SemanticMapperService(
            DataSourceRepository dataSourceRepository,
            FieldMappingRepository fieldMappingRepository,
            SourceSchemaFieldRepository sourceSchemaFieldRepository,
            @Autowired(required = false) ChatLanguageModel chatLanguageModel,
            @Autowired(required = false) JdbcTemplate jdbcTemplate,
            @Autowired(required = false) ObjectMapper objectMapper) {
        this.dataSourceRepository = dataSourceRepository;
        this.fieldMappingRepository = fieldMappingRepository;
        this.sourceSchemaFieldRepository = sourceSchemaFieldRepository;
        this.chatLanguageModel = chatLanguageModel;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Transactional
    public void generateMappings(UUID sourceId, SourceSchema schema) {
        log.info("Generating field mappings for sourceId: {}, schema version: {}", sourceId, schema.getVersion());

        DataSource source = dataSourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("DataSource not found: " + sourceId));

        List<SourceSchemaField> fields = sourceSchemaFieldRepository.findBySchemaId(schema.getId());

        // Load pre-existing mappings for this source so we don't re-run LLM on already-mapped fields
        Map<String, FieldMapping> existingByFieldName = new HashMap<>();
        fieldMappingRepository.findBySourceId(sourceId)
                .forEach(m -> existingByFieldName.put(m.getSourceFieldName(), m));

        List<FieldMapping> newMappings = new ArrayList<>();

        for (SourceSchemaField field : fields) {
            String sourceFieldName = field.getFieldName();
            InferredType inferredType = field.getInferredType();

            // Reuse existing mapping if already confirmed or pending — skip LLM entirely
            FieldMapping existing = existingByFieldName.get(sourceFieldName);
            if (existing != null && !"IGNORED".equals(existing.getStatus())) {
                log.info("[CACHED] Field '{}' => {}.{} (confidence={}, status={}) — skipping LLM",
                        sourceFieldName, existing.getCanonicalEntity(), existing.getCanonicalField(),
                        existing.getConfidence(), existing.getStatus());
                // Not added to newMappings — existing row stays untouched in DB
                existingByFieldName.remove(sourceFieldName);
                continue;
            }

            if (sourceFieldName == null || sourceFieldName.trim().isEmpty()) {
                FieldMapping mapping = new FieldMapping();
                mapping.setSource(source);
                mapping.setSourceFieldName(sourceFieldName);
                mapping.setCanonicalEntity("");
                mapping.setCanonicalField("");
                mapping.setConfidence(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                mapping.setStatus("IGNORED");
                newMappings.add(mapping);
                continue;
            }

            MappingDecision decision = null;

            if (chatLanguageModel != null && jdbcTemplate != null) {
                try {
                    List<String> sourceSamples = getSourceSamples(sourceId, sourceFieldName);

                    // Build prompt context
                    StringBuilder canonicalFieldsDesc = new StringBuilder();
                    for (CanonicalFieldInfo target : REGISTRY) {
                        List<String> canonicalSamples = getCanonicalSamples(target.entity, target.field);
                        canonicalFieldsDesc.append(String.format(
                            "- Entity: %s, Field: %s, Expected Type: %s, Synonyms: %s, Samples: %s\n",
                            target.entity, target.field, target.expectedType, target.synonyms, canonicalSamples
                        ));
                    }

                    String systemPrompt = "You are an intelligent schema mapping assistant.\n" +
                            "Your job is to map an incoming CSV source column to a target canonical database field.\n" +
                            "Here are the target canonical fields:\n" +
                            canonicalFieldsDesc.toString() + "\n" +
                            "You must respond with a JSON object containing:\n" +
                            "{\n" +
                            "  \"canonicalEntity\": \"name of target entity (e.g. customers, products, orders)\",\n" +
                            "  \"canonicalField\": \"name of target column\",\n" +
                            "  \"confidence\": score between 0.00 and 1.00\n" +
                            "}\n" +
                            "If no match is found, set canonicalEntity and canonicalField to empty strings, and confidence to 0.00.\n" +
                            "Response MUST be a single clean JSON block. Do not include markdown code block syntax (like ```json) or explanation.";

                    String userPrompt = String.format(
                            "Source Column Name: %s\n" +
                            "Inferred Type: %s\n" +
                            "Source Sample Values: %s\n" +
                            "Provide the mapping JSON block.",
                            sourceFieldName, inferredType != null ? inferredType.name() : "FREE_TEXT", sourceSamples
                    );

                    log.info("Sending prompt to Ollama for field: {}", sourceFieldName);
                    String rawResponse = chatLanguageModel.generate(systemPrompt + "\n\n" + userPrompt);
                    log.info("Ollama response for field {}: {}", sourceFieldName, rawResponse);

                    String cleanedJson = cleanJsonResponse(rawResponse);
                    decision = objectMapper.readValue(cleanedJson, MappingDecision.class);
                } catch (Exception e) {
                    log.error("Ollama mapping failed for field {}: {}. Falling back to heuristics.", sourceFieldName, e.getMessage(), e);
                }
            }

            FieldMapping mapping = new FieldMapping();
            mapping.setSource(source);
            mapping.setSourceFieldName(sourceFieldName);

            if (decision != null && decision.getCanonicalEntity() != null && !decision.getCanonicalEntity().trim().isEmpty() &&
                decision.getCanonicalField() != null && !decision.getCanonicalField().trim().isEmpty()) {

                mapping.setCanonicalEntity(decision.getCanonicalEntity().trim());
                mapping.setCanonicalField(decision.getCanonicalField().trim());

                BigDecimal conf = decision.getConfidence();
                if (conf == null) conf = BigDecimal.ZERO;
                mapping.setConfidence(conf.setScale(2, RoundingMode.HALF_UP));

                double highestScore = conf.doubleValue();
                if (highestScore >= 0.80) {
                    mapping.setStatus("AUTO_CONFIRMED");
                } else if (highestScore >= 0.55) {
                    mapping.setStatus("PENDING");
                } else {
                    mapping.setStatus("IGNORED");
                }
                log.info("[LLM] Field '{}' => {}.{} (confidence={}, status={})",
                        sourceFieldName, mapping.getCanonicalEntity(), mapping.getCanonicalField(),
                        mapping.getConfidence(), mapping.getStatus());
            } else {
                // Heuristic Fallback
                double highestScore = 0.0;
                CanonicalFieldInfo bestMatch = null;

                for (CanonicalFieldInfo target : REGISTRY) {
                    double score = computeConfidence(sourceFieldName, inferredType, target);
                    if (score > highestScore) {
                        highestScore = score;
                        bestMatch = target;
                    }
                }

                if (highestScore >= 0.55 && bestMatch != null) {
                    mapping.setCanonicalEntity(bestMatch.entity);
                    mapping.setCanonicalField(bestMatch.field);
                    mapping.setConfidence(BigDecimal.valueOf(highestScore).setScale(2, RoundingMode.HALF_UP));

                    if (highestScore >= 0.80) {
                        mapping.setStatus("AUTO_CONFIRMED");
                    } else {
                        mapping.setStatus("PENDING");
                    }
                    log.info("[HEURISTIC] Field '{}' => {}.{} (confidence={}, status={})",
                            sourceFieldName, mapping.getCanonicalEntity(), mapping.getCanonicalField(),
                            mapping.getConfidence(), mapping.getStatus());
                } else {
                    mapping.setCanonicalEntity("");
                    mapping.setCanonicalField("");
                    mapping.setConfidence(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    mapping.setStatus("IGNORED");
                    log.info("[HEURISTIC] Field '{}' => NO MATCH (status=IGNORED)", sourceFieldName);
                }
            }

            newMappings.add(mapping);
        }

        // Delete only mappings for fields that were completely dropped from the new schema
        // (i.e. they were in the old mapping table but aren't in the current schema field list)
        Set<String> currentFieldNames = fields.stream()
                .map(SourceSchemaField::getFieldName)
                .collect(java.util.stream.Collectors.toSet());
        existingByFieldName.keySet().stream()
                .filter(name -> !currentFieldNames.contains(name))
                .forEach(name -> {
                    log.info("[CLEANUP] Removing stale mapping for dropped field '{}'", name);
                    fieldMappingRepository.deleteBySourceIdAndSourceFieldName(sourceId, name);
                });

        if (!newMappings.isEmpty()) {
            fieldMappingRepository.saveAll(newMappings);
        }
        log.info("Saved {} new field mappings for sourceId: {} (skipped cached fields)", newMappings.size(), sourceId);
    }




    private List<String> getCanonicalSamples(String entity, String field) {
        if (jdbcTemplate == null) return Collections.emptyList();
        try {
            if (!isValidIdentifier(entity) || !isValidIdentifier(field)) {
                return Collections.emptyList();
            }
            return jdbcTemplate.queryForList(
                String.format("SELECT CAST(%s AS VARCHAR) FROM canonical.%s WHERE %s IS NOT NULL ORDER BY random() LIMIT 10", field, entity, field),
                String.class
            );
        } catch (Exception e) {
            log.warn("Failed to fetch samples for canonical.{}.{}: {}", entity, field, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> getSourceSamples(UUID sourceId, String fieldName) {
        if (jdbcTemplate == null) return Collections.emptyList();
        try {
            return jdbcTemplate.queryForList(
                "SELECT raw_payload->>? FROM staged_records WHERE source_id = ? AND raw_payload->>? IS NOT NULL ORDER BY random() LIMIT 10",
                String.class,
                fieldName, sourceId, fieldName
            );
        } catch (Exception e) {
            log.warn("Failed to fetch source samples for field {}: {}", fieldName, e.getMessage());
            return Collections.emptyList();
        }
    }

    private boolean isValidIdentifier(String s) {
        return s != null && s.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }

    private String cleanJsonResponse(String response) {
        if (response == null) return "{}";
        response = response.trim();
        if (response.startsWith("```json")) {
            response = response.substring(7);
        } else if (response.startsWith("```")) {
            response = response.substring(3);
        }
        if (response.endsWith("```")) {
            response = response.substring(0, response.length() - 3);
        }
        return response.trim();
    }

    private double computeConfidence(String sourceName, InferredType sourceInferredType, CanonicalFieldInfo target) {
        String normSource = normalize(sourceName);
        String normTarget = normalize(target.field);

        // 1. Exact match (Confidence = 1.0)
        if (normSource.equals(normTarget)) {
            return 1.0;
        }
        for (String synonym : target.synonyms) {
            if (normSource.equals(normalize(synonym))) {
                return 1.0;
            }
        }

        // 2. Levenshtein distance <= 2 (Confidence = 0.85)
        if (calculateLevenshtein(normSource, normTarget) <= 2) {
            return 0.85;
        }
        for (String synonym : target.synonyms) {
            if (calculateLevenshtein(normSource, normalize(synonym)) <= 2) {
                return 0.85;
            }
        }

        // 3. Token Overlap Score (Confidence = 0.70)
        if (calculateTokenOverlap(sourceName, target.field) >= 0.5) {
            return 0.70;
        }
        for (String synonym : target.synonyms) {
            if (calculateTokenOverlap(sourceName, synonym) >= 0.5) {
                return 0.70;
            }
        }

        // 4. Same inferred type (Confidence = 0.55)
        boolean typeMatches = false;
        if (sourceInferredType != null) {
            String sourceTypeName = sourceInferredType.name();
            if (sourceTypeName.equals(target.expectedType)) {
                typeMatches = true;
            } else if (target.expectedType.equals("DECIMAL") && sourceInferredType == InferredType.CURRENCY_AMOUNT) {
                typeMatches = true;
            }
        }
        if (typeMatches) {
            return 0.55;
        }

        return 0.0;
    }

    private String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private int calculateLevenshtein(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        int[] dp = new int[len2 + 1];
        for (int j = 0; j <= len2; j++) {
            dp[j] = j;
        }
        for (int i = 1; i <= len1; i++) {
            int prev = dp[0];
            dp[0] = i;
            for (int j = 1; j <= len2; j++) {
                int temp = dp[j];
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[j] = prev;
                } else {
                    dp[j] = Math.min(Math.min(dp[j - 1] + 1, dp[j] + 1), prev + 1);
                }
                prev = temp;
            }
        }
        return dp[len2];
    }

    private double calculateTokenOverlap(String source, String target) {
        if (source == null || target == null) {
            return 0.0;
        }
        String[] srcParts = source.toLowerCase().split("[ _]+");
        String[] tgtParts = target.toLowerCase().split("[ _]+");
        Set<String> srcTokens = new HashSet<>(Arrays.asList(srcParts));
        Set<String> tgtTokens = new HashSet<>(Arrays.asList(tgtParts));
        srcTokens.remove("");
        tgtTokens.remove("");
        if (srcTokens.isEmpty() || tgtTokens.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(srcTokens);
        intersection.retainAll(tgtTokens);
        if (intersection.isEmpty()) {
            return 0.0;
        }
        Set<String> union = new HashSet<>(srcTokens);
        union.addAll(tgtTokens);
        return (double) intersection.size() / union.size();
    }

    public static class CanonicalFieldInfo {
        public final String entity;
        public final String field;
        public final String expectedType;
        public final List<String> synonyms;

        public CanonicalFieldInfo(String entity, String field, String expectedType, List<String> synonyms) {
            this.entity = entity;
            this.field = field;
            this.expectedType = expectedType;
            this.synonyms = synonyms;
        }
    }

    public static void registerCanonicalField(String entity, String field, String expectedType) {
        boolean exists = REGISTRY.stream().anyMatch(info -> info.entity.equalsIgnoreCase(entity) && info.field.equalsIgnoreCase(field));
        if (!exists) {
            REGISTRY.add(new CanonicalFieldInfo(entity, field, expectedType, new ArrayList<>()));
        }
    }

    public static void deregisterCanonicalField(String entity, String field) {
        REGISTRY.removeIf(info -> info.entity.equalsIgnoreCase(entity) && info.field.equalsIgnoreCase(field));
    }

    @lombok.Data
    public static class MappingDecision {
        private String canonicalEntity;
        private String canonicalField;
        private BigDecimal confidence;
    }
}
