package com.shreyas.saleslens.service.conflict;

import com.shreyas.saleslens.model.*;
import com.shreyas.saleslens.model.enums.ConflictStatus;
import com.shreyas.saleslens.model.enums.ResolutionStrategy;
import com.shreyas.saleslens.repository.ConflictRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Detects field-level conflicts between existing canonical entities and incoming data.
 * <p>
 * A conflict exists when two sources provide different non-null values for the same field
 * on the same real-world entity. Null values never cause a conflict — the non-null value
 * wins silently.
 * <p>
 * Batch resolution thresholds are applied during detection to auto-resolve conflicts
 * based on source trust scores and field importance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConflictDetectionService {

    private final ConflictRecordRepository conflictRecordRepository;

    private static final Set<String> CUSTOMER_FIELDS = Set.of("segment", "region", "email");
    private static final Set<String> PRODUCT_FIELDS = Set.of("unitPrice", "category", "subCategory");
    private static final Set<String> SALESPERSON_FIELDS = Set.of("territory", "team");
    private static final Set<String> ORDER_FIELDS = Set.of("totalAmount", "shipMode");

    private static final Set<String> HIGH_IMPORTANCE_FIELDS = Set.of(
            "segment", "unitPrice", "totalAmount", "territory", "team", "region", "email", "category"
    );
    private static final Set<String> LOW_IMPORTANCE_FIELDS = Set.of(
            "shipMode", "subCategory"
    );

    private static final BigDecimal PRICE_THRESHOLD = new BigDecimal("0.01");
    private static final BigDecimal AMOUNT_THRESHOLD = new BigDecimal("0.01");
    private static final BigDecimal TRUST_GAP_THRESHOLD = new BigDecimal("0.3");
    private static final BigDecimal TRUST_HIGH_THRESHOLD = new BigDecimal("0.7");

    // ---- Overloaded detection methods per entity type ----

    public List<ConflictRecord> detectConflicts(CanonicalCustomer existing,
                                                 Map<String, String> incomingFields,
                                                 DataSource sourceB,
                                                 IngestionJob job) {
        return detectConflictsForEntity(existing, incomingFields, sourceB, job,
                CUSTOMER_FIELDS, existing.getPrimarySource());
    }

    public List<ConflictRecord> detectConflicts(CanonicalProduct existing,
                                                 Map<String, String> incomingFields,
                                                 DataSource sourceB,
                                                 IngestionJob job) {
        return detectConflictsForEntity(existing, incomingFields, sourceB, job,
                PRODUCT_FIELDS, existing.getPrimarySource());
    }

    public List<ConflictRecord> detectConflicts(CanonicalSalesperson existing,
                                                 Map<String, String> incomingFields,
                                                 DataSource sourceB,
                                                 IngestionJob job) {
        return detectConflictsForEntity(existing, incomingFields, sourceB, job,
                SALESPERSON_FIELDS, existing.getPrimarySource());
    }

    public List<ConflictRecord> detectConflicts(CanonicalOrder existing,
                                                 Map<String, String> incomingFields,
                                                 DataSource sourceB,
                                                 IngestionJob job) {
        return detectConflictsForEntity(existing, incomingFields, sourceB, job,
                ORDER_FIELDS, existing.getSource());
    }

    // ---- Core detection logic ----

    private List<ConflictRecord> detectConflictsForEntity(Object existing,
                                                           Map<String, String> incomingFields,
                                                           DataSource sourceB,
                                                           IngestionJob job,
                                                           Set<String> fieldsToCheck,
                                                           DataSource sourceA) {
        List<ConflictRecord> conflicts = new ArrayList<>();

        for (String fieldName : fieldsToCheck) {
            String valueA = getFieldValue(existing, fieldName);
            String valueB = incomingFields.get(fieldName);

            // NULL-VALUE GUARD: If both are null → skip. If one is null → skip (non-null wins).
            if (valueA == null || valueB == null) {
                continue;
            }

            // Check if values differ according to field-specific comparison rules
            if (!valuesDiffer(valueA, valueB, fieldName)) {
                continue;
            }

            // Compute resolution strategy and status based on batch thresholds
            BigDecimal trustA = sourceA.getTrustScore();
            BigDecimal trustB = sourceB.getTrustScore();
            BigDecimal trustGap = trustA.subtract(trustB).abs();

            ResolutionStrategy strategy;
            ConflictStatus status;

            // Large trust gap → TRUST_HIERARCHY auto-resolve
            if (trustGap.compareTo(TRUST_GAP_THRESHOLD) >= 0) {
                strategy = ResolutionStrategy.TRUST_HIERARCHY;
                status = ConflictStatus.RESOLVED;
            }
            // Low-importance fields with both trusts high → LATEST_WINS auto-resolve
            else if (LOW_IMPORTANCE_FIELDS.contains(fieldName)
                    && trustA.compareTo(TRUST_HIGH_THRESHOLD) > 0
                    && trustB.compareTo(TRUST_HIGH_THRESHOLD) > 0) {
                strategy = ResolutionStrategy.LATEST_WINS;
                status = ConflictStatus.RESOLVED;
            }
            // Everything else → FLAGGED_FOR_REVIEW
            else {
                strategy = ResolutionStrategy.FLAGGED_FOR_REVIEW;
                status = ConflictStatus.OPEN;
            }

            ConflictRecord record = new ConflictRecord();
            record.setEntityType(existing.getClass().getSimpleName());
            record.setEntityId(getEntityId(existing));
            record.setFieldName(fieldName);
            record.setSourceA(sourceA);
            record.setSourceB(sourceB);
            record.setValueA(valueA);
            record.setValueB(valueB);
            record.setResolutionStrategy(strategy);
            record.setStatus(status);

            conflicts.add(record);
        }

        if (!conflicts.isEmpty()) {
            conflictRecordRepository.saveAll(conflicts);
            log.info("Detected {} conflict(s) for entity {} ({})",
                    conflicts.size(), existing.getClass().getSimpleName(), getEntityId(existing));
        }

        return conflicts;
    }

    // ---- Value comparison ----

    private boolean valuesDiffer(String valueA, String valueB, String fieldName) {
        switch (fieldName) {
            case "unitPrice":
                return priceDiffExceedsThreshold(valueA, valueB);
            case "totalAmount":
                return amountDiffExceedsThreshold(valueA, valueB);
            default:
                return !valueA.equals(valueB);
        }
    }

    /**
     * Price difference: |priceA - priceB| / max(priceA, priceB) > 1%
     */
    private boolean priceDiffExceedsThreshold(String priceA, String priceB) {
        try {
            BigDecimal a = new BigDecimal(priceA);
            BigDecimal b = new BigDecimal(priceB);
            BigDecimal diff = a.subtract(b).abs();
            BigDecimal max = a.max(b);
            BigDecimal ratio = diff.divide(max, 4, RoundingMode.HALF_UP);
            return ratio.compareTo(PRICE_THRESHOLD) > 0;
        } catch (NumberFormatException e) {
            log.warn("Invalid price values: a={}, b={}", priceA, priceB);
            return true; // treat unparseable values as different
        }
    }

    /**
     * Amount difference: |totalA - totalB| > $0.01
     */
    private boolean amountDiffExceedsThreshold(String totalA, String totalB) {
        try {
            BigDecimal a = new BigDecimal(totalA);
            BigDecimal b = new BigDecimal(totalB);
            BigDecimal diff = a.subtract(b).abs();
            return diff.compareTo(AMOUNT_THRESHOLD) > 0;
        } catch (NumberFormatException e) {
            log.warn("Invalid amount values: a={}, b={}", totalA, totalB);
            return true; // treat unparseable values as different
        }
    }

    // ---- Reflection helpers ----

    /**
     * Extracts a field value from an existing entity using reflection.
     */
    private String getFieldValue(Object existing, String fieldName) {
        try {
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            Method getter;
            try {
                getter = existing.getClass().getMethod(getterName);
            } catch (NoSuchMethodException e) {
                // Try "is" prefix for boolean fields
                String isGetterName = "is" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                getter = existing.getClass().getMethod(isGetterName);
            }
            Object value = getter.invoke(existing);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.debug("Could not get field '{}' from {}: {}", fieldName,
                    existing.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private UUID getEntityId(Object existing) {
        try {
            Method getId = existing.getClass().getMethod("getId");
            return (UUID) getId.invoke(existing);
        } catch (Exception e) {
            throw new IllegalArgumentException("Entity must have a getId() method returning UUID", e);
        }
    }
}
