package com.shreyas.saleslens.service;

import com.shreyas.saleslens.model.*;
import com.shreyas.saleslens.model.enums.InferredType;
import com.shreyas.saleslens.repository.DataSourceRepository;
import com.shreyas.saleslens.repository.FieldMappingRepository;
import com.shreyas.saleslens.repository.SourceSchemaFieldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SemanticMapperService {

    private final DataSourceRepository dataSourceRepository;
    private final FieldMappingRepository fieldMappingRepository;
    private final SourceSchemaFieldRepository sourceSchemaFieldRepository;

    private static final List<CanonicalFieldInfo> REGISTRY = Arrays.asList(
            // customers
            new CanonicalFieldInfo("customers", "name", "FREE_TEXT", Arrays.asList("customer_name", "cust_name", "client_name", "buyer_name", "customer")),
            new CanonicalFieldInfo("customers", "email", "EMAIL", Arrays.asList("mail", "email_address", "contact_email")),
            new CanonicalFieldInfo("customers", "phone", "PHONE", Arrays.asList("phone_number", "contact_no", "tel", "telephone")),
            new CanonicalFieldInfo("customers", "segment", "CATEGORY", Arrays.asList("cust_segment", "group", "division")),

            // products
            new CanonicalFieldInfo("products", "sku", "FREE_TEXT", Arrays.asList("sku", "product_code", "item_code", "prod_id")),
            new CanonicalFieldInfo("products", "name", "FREE_TEXT", Arrays.asList("product_name", "item", "description", "prod_name")),
            new CanonicalFieldInfo("products", "category", "CATEGORY", Arrays.asList("prod_category", "category", "dept", "department")),
            new CanonicalFieldInfo("products", "sub_category", "CATEGORY", Arrays.asList("sub_category", "subcategory", "group")),
            new CanonicalFieldInfo("products", "unit_price", "DECIMAL", Arrays.asList("price", "unit_price", "cost")),
            new CanonicalFieldInfo("products", "currency", "CATEGORY", Arrays.asList("curr", "currency_code", "valuta")),

            // orders
            new CanonicalFieldInfo("orders", "order_date", "DATE", Arrays.asList("order_date", "date", "transaction_date")),
            new CanonicalFieldInfo("orders", "ship_date", "DATE", Arrays.asList("ship_date", "shipping_date", "delivery_date")),
            new CanonicalFieldInfo("orders", "ship_mode", "CATEGORY", Arrays.asList("ship_mode", "shipping_method", "carrier")),
            new CanonicalFieldInfo("orders", "shipping_cost", "DECIMAL", Arrays.asList("shipping_cost", "freight", "shipping_fee")),
            new CanonicalFieldInfo("orders", "total_amount", "DECIMAL", Arrays.asList("sales", "revenue", "total", "amount", "order_total")),
            new CanonicalFieldInfo("orders", "currency", "CATEGORY", Arrays.asList("curr", "currency_code"))
    );

    @Transactional
    public void generateMappings(UUID sourceId, SourceSchema schema) {
        log.info("Generating field mappings for sourceId: {}, schema version: {}", sourceId, schema.getVersion());
        
        DataSource source = dataSourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("DataSource not found: " + sourceId));

        // Delete old mappings for this source
        fieldMappingRepository.deleteBySourceId(sourceId);

        List<SourceSchemaField> fields = sourceSchemaFieldRepository.findBySchemaId(schema.getId());
        List<FieldMapping> newMappings = new ArrayList<>();

        for (SourceSchemaField field : fields) {
            String sourceFieldName = field.getFieldName();
            InferredType inferredType = field.getInferredType();

            double highestScore = 0.0;
            CanonicalFieldInfo bestMatch = null;

            if (sourceFieldName != null && !sourceFieldName.trim().isEmpty()) {
                for (CanonicalFieldInfo target : REGISTRY) {
                    double score = computeConfidence(sourceFieldName, inferredType, target);
                    if (score > highestScore) {
                        highestScore = score;
                        bestMatch = target;
                    }
                }
            }

            FieldMapping mapping = new FieldMapping();
            mapping.setSource(source);
            mapping.setSourceFieldName(sourceFieldName);

            if (highestScore >= 0.55 && bestMatch != null) {
                mapping.setCanonicalEntity(bestMatch.entity);
                mapping.setCanonicalField(bestMatch.field);
                mapping.setConfidence(BigDecimal.valueOf(highestScore).setScale(2, RoundingMode.HALF_UP));
                
                if (highestScore >= 0.80) {
                    mapping.setStatus("AUTO_CONFIRMED");
                } else {
                    mapping.setStatus("PENDING");
                }
            } else {
                mapping.setCanonicalEntity("");
                mapping.setCanonicalField("");
                mapping.setConfidence(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                mapping.setStatus("IGNORED");
            }

            newMappings.add(mapping);
        }

        fieldMappingRepository.saveAll(newMappings);
        log.info("Saved {} field mappings for sourceId: {}", newMappings.size(), sourceId);
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

    private static class CanonicalFieldInfo {
        private final String entity;
        private final String field;
        private final String expectedType;
        private final List<String> synonyms;

        public CanonicalFieldInfo(String entity, String field, String expectedType, List<String> synonyms) {
            this.entity = entity;
            this.field = field;
            this.expectedType = expectedType;
            this.synonyms = synonyms;
        }
    }
}
