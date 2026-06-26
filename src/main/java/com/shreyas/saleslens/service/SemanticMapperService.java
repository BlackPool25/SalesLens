package com.shreyas.saleslens.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.model.*;
import com.shreyas.saleslens.model.enums.InferredType;
import com.shreyas.saleslens.repository.DataSourceRepository;
import com.shreyas.saleslens.repository.FieldMappingRepository;
import com.shreyas.saleslens.repository.SourceSchemaFieldRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;

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

        // Load pre-existing mappings for this source so we don't re-process already-mapped fields
        Map<String, FieldMapping> existingByFieldName = new HashMap<>();
        fieldMappingRepository.findBySourceId(sourceId)
                .forEach(m -> existingByFieldName.put(m.getSourceFieldName(), m));

        List<FieldMapping> newMappings = new ArrayList<>();
        boolean llmAvailable = chatLanguageModel != null && jdbcTemplate != null;

        for (SourceSchemaField field : fields) {
            String sourceFieldName = field.getFieldName();
            InferredType inferredType = field.getInferredType();

            // 1. Reuse existing mapping if already confirmed or pending
            FieldMapping existing = existingByFieldName.get(sourceFieldName);
            if (existing != null && !"IGNORED".equals(existing.getStatus())) {
                log.info("[CACHED] Field '{}' => {}.{} (confidence={}, status={}) — skipping",
                        sourceFieldName, existing.getCanonicalEntity(), existing.getCanonicalField(),
                        existing.getConfidence(), existing.getStatus());
                existingByFieldName.remove(sourceFieldName);
                continue;
            }

            // 2. Skip null/empty field names
            if (sourceFieldName == null || sourceFieldName.trim().isEmpty()) {
                FieldMapping mapping = createIgnoredMapping(source, sourceFieldName);
                newMappings.add(mapping);
                continue;
            }

            // 3. Run heuristic chain FIRST (always — zero external deps, milliseconds)
            HeuristicResult heuristic = runHeuristicChain(sourceFieldName, inferredType);

            // 4. Optionally run LLM if available (advisory only)
            double bestScore = heuristic.score;
            String bestEntity = heuristic.entity;
            String bestField = heuristic.field;

            if (llmAvailable) {
                try {
                    MappingDecision llmDecision = runLlmWithRetry(sourceFieldName, inferredType, sourceId);
                    if (llmDecision != null && isValidMapping(llmDecision)) {
                        double llmConf = llmDecision.getConfidence() != null
                                ? llmDecision.getConfidence().doubleValue() : 0.0;
                        if (llmConf > bestScore) {
                            bestScore = llmConf;
                            bestEntity = llmDecision.getCanonicalEntity().trim();
                            bestField = llmDecision.getCanonicalField().trim();
                            log.info("[LLM] Field '{}' => {}.{} (confidence={}) — overrides heuristic ({})",
                                    sourceFieldName, bestEntity, bestField, llmConf, heuristic.score);
                        }
                    }
                } catch (Exception e) {
                    log.warn("[LLM] Field '{}' advisory failed: {}. Using heuristic result.", sourceFieldName, e.getMessage());
                }
            }

            // 5. Apply threshold logic with best available result
            FieldMapping mapping = buildMapping(source, sourceFieldName, bestEntity, bestField, bestScore);
            newMappings.add(mapping);
        }

        // Delete stale mappings for dropped fields
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

    /**
     * Runs the heuristic chain: exact → Levenshtein → token overlap → type fallback.
     * Returns the best match with its confidence score.
     */
    private HeuristicResult runHeuristicChain(String sourceFieldName, InferredType inferredType) {
        double highestScore = 0.0;
        String bestEntity = "";
        String bestField = "";

        for (CanonicalFieldInfo target : REGISTRY) {
            double score = computeConfidence(sourceFieldName, inferredType, target);
            if (score > highestScore) {
                highestScore = score;
                bestEntity = target.entity;
                bestField = target.field;
            }
        }

        return new HeuristicResult(highestScore, bestEntity, bestField);
    }

    /**
     * Attempts LLM mapping with up to 2 retries on parse failure.
     * Returns null if LLM unavailable, all retries fail, or output is invalid.
     */
    private MappingDecision runLlmWithRetry(String sourceFieldName, InferredType inferredType, UUID sourceId) {
        if (chatLanguageModel == null || jdbcTemplate == null) return null;

        List<String> sourceSamples = getSourceSamples(sourceId, sourceFieldName);

        // Build prompt context with canonical registry info
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
                canonicalFieldsDesc + "\n" +
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

        // Retry up to 3 attempts (initial + 2 retries)
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ChatRequest request = ChatRequest.builder()
                        .messages(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .rootElement(JsonObjectSchema.builder()
                                                .addStringProperty("canonicalEntity")
                                                .addStringProperty("canonicalField")
                                                .addNumberProperty("confidence")
                                                .required("canonicalEntity", "canonicalField", "confidence")
                                                .build())
                                        .build())
                                .build())
                        .build();

                log.info("[LLM] Attempt {}/{} for field: {}", attempt, maxAttempts, sourceFieldName);
                AiMessage response = chatLanguageModel.chat(request).aiMessage();
                String rawResponse = response.text();
                log.debug("[LLM] Response for field {}: {}", sourceFieldName, rawResponse);

                String cleanedJson = cleanJsonResponse(rawResponse);
                MappingDecision decision = objectMapper.readValue(cleanedJson, MappingDecision.class);

                if (decision.getCanonicalEntity() != null && !decision.getCanonicalEntity().trim().isEmpty()
                        && decision.getCanonicalField() != null && !decision.getCanonicalField().trim().isEmpty()
                        && decision.getConfidence() != null) {
                    log.info("[LLM] Attempt {} succeeded for field '{}': {}.{} (confidence={})",
                            attempt, sourceFieldName, decision.getCanonicalEntity(),
                            decision.getCanonicalField(), decision.getConfidence());
                    return decision;
                }

                log.warn("[LLM] Attempt {} returned empty mapping for field '{}', retrying...", attempt, sourceFieldName);
            } catch (Exception e) {
                log.warn("[LLM] Attempt {} failed for field '{}': {}. {}",
                        attempt, sourceFieldName, e.getMessage(),
                        attempt < maxAttempts ? "Retrying..." : "All attempts exhausted.");
            }
        }

        log.warn("[LLM] All {} attempts failed for field '{}'. Falling back to heuristic.", maxAttempts, sourceFieldName);
        return null;
    }

    /**
     * Validates that an LLM MappingDecision references a real entity/field in the canonical registry.
     */
    private boolean isValidMapping(MappingDecision decision) {
        if (decision == null) return false;
        String entity = decision.getCanonicalEntity();
        String field = decision.getCanonicalField();
        if (entity == null || entity.trim().isEmpty()) return false;
        if (field == null || field.trim().isEmpty()) return false;
        return findInRegistry(entity.trim(), field.trim()) != null;
    }

    /**
     * Looks up a CanonicalFieldInfo by entity and field name, case-insensitive.
     * Returns null if not found.
     */
    private CanonicalFieldInfo findInRegistry(String entity, String field) {
        for (CanonicalFieldInfo info : REGISTRY) {
            if (info.entity.equalsIgnoreCase(entity) && info.field.equalsIgnoreCase(field)) {
                return info;
            }
        }
        return null;
    }

    private FieldMapping createIgnoredMapping(DataSource source, String sourceFieldName) {
        FieldMapping mapping = new FieldMapping();
        mapping.setSource(source);
        mapping.setSourceFieldName(sourceFieldName);
        mapping.setCanonicalEntity("");
        mapping.setCanonicalField("");
        mapping.setConfidence(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        mapping.setStatus("IGNORED");
        return mapping;
    }

    private FieldMapping buildMapping(DataSource source, String sourceFieldName,
                                       String canonicalEntity, String canonicalField, double confidence) {
        FieldMapping mapping = new FieldMapping();
        mapping.setSource(source);
        mapping.setSourceFieldName(sourceFieldName);

        if (confidence >= 0.55 && !canonicalEntity.isEmpty() && !canonicalField.isEmpty()) {
            mapping.setCanonicalEntity(canonicalEntity);
            mapping.setCanonicalField(canonicalField);
            mapping.setConfidence(BigDecimal.valueOf(confidence).setScale(2, RoundingMode.HALF_UP));
            mapping.setStatus(confidence >= 0.80 ? "AUTO_CONFIRMED" : "PENDING");
            log.info("[MAPPING] Field '{}' => {}.{} (confidence={}, status={})",
                    sourceFieldName, canonicalEntity, canonicalField,
                    mapping.getConfidence(), mapping.getStatus());
        } else {
            mapping.setCanonicalEntity("");
            mapping.setCanonicalField("");
            mapping.setConfidence(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            mapping.setStatus("IGNORED");
            log.info("[MAPPING] Field '{}' => NO MATCH (status=IGNORED)", sourceFieldName);
        }

        return mapping;
    }

    private static class HeuristicResult {
        final double score;
        final String entity;
        final String field;

        HeuristicResult(double score, String entity, String field) {
            this.score = score;
            this.entity = entity;
            this.field = field;
        }
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
