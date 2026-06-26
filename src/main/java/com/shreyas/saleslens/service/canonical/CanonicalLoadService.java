package com.shreyas.saleslens.service.canonical;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.model.*;
import com.shreyas.saleslens.repository.*;
import com.shreyas.saleslens.service.TransformationService;
import com.shreyas.saleslens.service.conflict.ConflictDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CanonicalLoadService {

    private final IngestionJobRepository ingestionJobRepository;
    private final DataSourceRepository dataSourceRepository;
    private final QualityRunRepository qualityRunRepository;
    private final QualityScoreRepository qualityScoreRepository;
    private final RejectedRecordRepository rejectedRecordRepository;
    private final FieldMappingRepository fieldMappingRepository;
    private final StagedRecordRepository stagedRecordRepository;

    private final CanonicalCustomerRepository canonicalCustomerRepository;
    private final CanonicalProductRepository canonicalProductRepository;
    private final CanonicalSalespersonRepository canonicalSalespersonRepository;
    private final CanonicalRegionRepository canonicalRegionRepository;
    private final CanonicalOrderRepository canonicalOrderRepository;
    private final CanonicalOrderLineItemRepository canonicalOrderLineItemRepository;

    private final ConflictRecordRepository conflictRecordRepository;
    private final LineageService lineageService;
    private final ConflictDetectionService conflictDetectionService;
    private final TransformationService transformationService;
    private final ObjectMapper objectMapper;

    private static final int PAGE_SIZE = 500;

    // Identifier patterns for external_refs
    private static final List<String> IDENTIFIER_PATTERNS = List.of("id", "ref", "code", "external");

    // Entity types that have certain fields
    private static final Set<String> ENTITIES_WITH_EXTERNAL_REFS = Set.of("customer", "product", "salesperson", "order");
    private static final Set<String> ENTITIES_WITH_QUALITY_SCORE = Set.of("customer", "product", "order");
    private static final Set<String> ENTITIES_WITH_ADDITIONAL_ATTRIBUTES = Set.of("customer", "product", "salesperson", "order", "order_line_item");

    private static final Set<String> PASS_1_ENTITIES = Set.of("customer", "product", "salesperson", "region");

    // =========================================================================
    // Main entry point
    // =========================================================================

    public void loadCanonical(UUID jobId) {
        log.info("Starting canonical load for job {}", jobId);

        // 1. Load job + source
        IngestionJob job = ingestionJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        DataSource source = job.getSource();

        // 2. Find latest QualityRun (optional)
        Optional<QualityRun> optRun = qualityRunRepository.findTopByJobIdOrderByRunTimestampDesc(jobId);
        Set<UUID> rejectedRecordIds = new HashSet<>();

        if (optRun.isPresent()) {
            QualityRun run = optRun.get();
            // 3. Collect rejected staged record IDs
            rejectedRecordIds = rejectedRecordRepository.findByRunId(run.getId()).stream()
                    .map(r -> r.getStagedRecord() != null ? r.getStagedRecord().getId() : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            log.info("Quality run {} found for job {}: {} rejected records to exclude",
                    run.getId(), jobId, rejectedRecordIds.size());
        } else {
            log.info("No quality run found for job {}, loading all records", jobId);
        }

        // 4. Load field mappings for this source (all statuses — TransformService routes them)
        List<FieldMapping> allMappings = fieldMappingRepository.findBySourceId(source.getId());
        log.info("Loaded {} field mappings for source {}", allMappings.size(), source.getId());

        // 5. Load quality score for qualityScore field on canonical entities
        BigDecimal overallScore = null;
        try {
            Optional<QualityScore> optScore = qualityScoreRepository.findByJobId(jobId);
            if (optScore.isPresent()) {
                overallScore = optScore.get().getScoreOverall();
            }
        } catch (Exception e) {
            log.warn("Could not load quality score for job {}: {}", jobId, e.getMessage());
        }

        // --- Data structures ---
        // Map<stagedRecordId, Map<entityType, Map<fieldName, value>>>
        Map<UUID, Map<String, Map<String, String>>> recordEntityData = new LinkedHashMap<>();

        // Per-record entity-type -> resolved UUID, for FK resolution across passes
        // Map<stagedRecordId, Map<entityType, UUID>>
        Map<UUID, Map<String, UUID>> recordResolvedIds = new LinkedHashMap<>();

        int totalLoaded = 0;
        int totalConflicted;

        // 6. Paginate staged records, transform, group by entity type
        int page = 0;
        List<StagedRecord> records;
        do {
            records = stagedRecordRepository.findByJobId(jobId, PageRequest.of(page, PAGE_SIZE));

            for (StagedRecord record : records) {
                if (rejectedRecordIds.contains(record.getId())) {
                    log.debug("Skipping rejected record {}", record.getId());
                    continue;
                }

                Map<String, String> transformed;
                try {
                    transformed = transformationService.transform(record, allMappings);
                } catch (Exception e) {
                    log.warn("Transform failed for record {}: {}", record.getId(), e.getMessage());
                    continue;
                }

                if (transformed == null || transformed.isEmpty()) {
                    continue;
                }

                Map<String, Map<String, String>> entityFieldMaps = groupByEntityType(transformed);

                // Build external_refs for each entity type
                for (Map.Entry<String, Map<String, String>> entry : entityFieldMaps.entrySet()) {
                    String entityType = entry.getKey();
                    Map<String, String> entityFields = entry.getValue();

                    String externalRefs = buildExternalRefs(entityType, entityFields, source);
                    entityFields.put("_externalRefs", externalRefs);
                }

                recordEntityData.put(record.getId(), entityFieldMaps);
                recordResolvedIds.put(record.getId(), new LinkedHashMap<>());
            }

            page++;
        } while (!records.isEmpty());

        log.info("Transformed {} staged records, grouped into entity data", recordEntityData.size());

        // ====================================================================
        // PASS 1: Standalone entities - customers, products, salespersons, regions
        // ====================================================================
        for (String entityType : PASS_1_ENTITIES) {
            totalLoaded += processStandaloneEntities(entityType, recordEntityData, recordResolvedIds,
                    job, source, overallScore);
        }

        // ====================================================================
        // PASS 2: Orders (FKs to customer, salesperson, region from Pass 1)
        // ====================================================================
        totalLoaded += processOrders(recordEntityData, recordResolvedIds, job, source, overallScore);

        // ====================================================================
        // PASS 3: Order line items (FKs to order, product from Passes 1 & 2)
        // ====================================================================
        totalLoaded += processOrderLineItems(recordEntityData, recordResolvedIds, job, source);

        // Count total conflicts
        totalConflicted = (int) conflictRecordRepository.count();

        // 15. Update job stats
        job.setTotalLoaded(totalLoaded);
        job.setTotalConflicted(totalConflicted);
        ingestionJobRepository.save(job);

        log.info("Canonical load complete for job {}: loaded={}, conflicted={}", jobId, totalLoaded, totalConflicted);
    }

    // =========================================================================
    // PASS 1: Standalone entities
    // =========================================================================

    private int processStandaloneEntities(String entityType,
                                           Map<UUID, Map<String, Map<String, String>>> recordEntityData,
                                           Map<UUID, Map<String, UUID>> recordResolvedIds,
                                           IngestionJob job, DataSource source, BigDecimal overallScore) {
        int loaded = 0;

        for (Map.Entry<UUID, Map<String, Map<String, String>>> recordEntry : recordEntityData.entrySet()) {
            UUID recordId = recordEntry.getKey();
            Map<String, Map<String, String>> entityData = recordEntry.getValue();

            Map<String, String> entityFields = entityData.get(entityType);
            if (entityFields == null || entityFields.isEmpty()) {
                continue;
            }

            Object savedEntity = saveOrUpdateEntity(entityType, entityFields, job, source, overallScore);
            if (savedEntity != null) {
                UUID savedId = getEntityId(savedEntity);
                recordResolvedIds.get(recordId).put(entityType, savedId);
                loaded++;
            }
        }

        return loaded;
    }

    // =========================================================================
    // PASS 2: Orders
    // =========================================================================

    private int processOrders(Map<UUID, Map<String, Map<String, String>>> recordEntityData,
                               Map<UUID, Map<String, UUID>> recordResolvedIds,
                               IngestionJob job, DataSource source, BigDecimal overallScore) {
        int loaded = 0;

        for (Map.Entry<UUID, Map<String, Map<String, String>>> recordEntry : recordEntityData.entrySet()) {
            UUID recordId = recordEntry.getKey();
            Map<String, Map<String, String>> entityData = recordEntry.getValue();

            Map<String, String> orderFields = entityData.get("order");
            if (orderFields == null || orderFields.isEmpty()) {
                continue;
            }

            Map<String, UUID> resolvedIds = recordResolvedIds.get(recordId);
            UUID customerId = resolvedIds.get("customer");
            UUID salespersonId = resolvedIds.get("salesperson");
            UUID regionId = resolvedIds.get("region");

            if (customerId == null) {
                log.warn("Order references missing customer for record {}, skipping", recordId);
                continue;
            }

            // Find existing order
            Optional<CanonicalOrder> existingOrder = findExistingOrder(orderFields, source);

            CanonicalOrder order;
            if (existingOrder.isPresent()) {
                order = existingOrder.get();
                DataSource existingSource = order.getSource();
                if (existingSource != null && existingSource.getId().equals(source.getId())) {
                    // Same-source update
                    applySameSourceOrderUpdate(order, orderFields, overallScore);
                } else {
                    // Cross-source: conflict detection + update
                    order = applyCrossSourceOrderUpdate(order, orderFields, job, source, overallScore);
                    order = canonicalOrderRepository.save(order);
                }
            } else {
                order = createNewOrder(orderFields, job, source, customerId, salespersonId, regionId, overallScore);
                order = canonicalOrderRepository.save(order);
            }

            // Set additional_attributes
            String addAttr = orderFields.get("additional_attributes");
            if (addAttr != null) {
                order.setAdditionalAttributes(addAttr);
                order = canonicalOrderRepository.save(order);
            }

            // Write lineage
            String lineageTransformations = buildTransformationsJson(orderFields, "order");
            lineageService.writeLineage(order.getId(), "order", source, job, null, lineageTransformations);

            resolvedIds.put("order", order.getId());
            loaded++;
        }

        return loaded;
    }

    // =========================================================================
    // PASS 3: Order line items
    // =========================================================================

    private int processOrderLineItems(Map<UUID, Map<String, Map<String, String>>> recordEntityData,
                                       Map<UUID, Map<String, UUID>> recordResolvedIds,
                                       IngestionJob job, DataSource source) {
        int loaded = 0;

        for (Map.Entry<UUID, Map<String, Map<String, String>>> recordEntry : recordEntityData.entrySet()) {
            UUID recordId = recordEntry.getKey();
            Map<String, Map<String, String>> entityData = recordEntry.getValue();

            Map<String, String> lineItemFields = entityData.get("order_line_item");
            if (lineItemFields == null || lineItemFields.isEmpty()) {
                continue;
            }

            Map<String, UUID> resolvedIds = recordResolvedIds.get(recordId);
            UUID orderId = resolvedIds.get("order");
            UUID productId = resolvedIds.get("product");

            if (orderId == null || productId == null) {
                log.warn("Order line item missing order({}) or product({}) for record {}, skipping",
                        orderId, productId, recordId);
                continue;
            }

            // Create new line item (always additive)
            CanonicalOrderLineItem lineItem = new CanonicalOrderLineItem();

            // Set FK references
            CanonicalOrder orderRef = new CanonicalOrder();
            orderRef.setId(orderId);
            lineItem.setOrder(orderRef);

            CanonicalProduct productRef = new CanonicalProduct();
            productRef.setId(productId);
            lineItem.setProduct(productRef);

            // Apply field values
            applyAllFields(lineItem, lineItemFields);

            // Set additional_attributes
            String addAttr = lineItemFields.get("additional_attributes");
            if (addAttr != null) {
                lineItem.setAdditionalAttributes(addAttr);
            }

            lineItem = canonicalOrderLineItemRepository.save(lineItem);

            String lineageTransformations = buildTransformationsJson(lineItemFields, "order_line_item");
            lineageService.writeLineage(lineItem.getId(), "order_line_item", source, job, null, lineageTransformations);

            loaded++;
        }

        return loaded;
    }

    // =========================================================================
    // Entity resolution (match existing)
    // =========================================================================

    Object findExistingEntity(String entityType, Map<String, String> entityFields, DataSource source) {
        String externalRefs = entityFields.get("_externalRefs");
        Map<String, String> refsMap = parseExternalRefs(externalRefs);

        // Try external_refs matching (entities that have the column)
        if (ENTITIES_WITH_EXTERNAL_REFS.contains(entityType)) {
            for (Map.Entry<String, String> refEntry : refsMap.entrySet()) {
                try {
                    Optional<?> result = findByExternalRefs(entityType, refEntry.getKey(), refEntry.getValue());
                    if (result.isPresent()) {
                        return result.get();
                    }
                } catch (Exception e) {
                    log.debug("External ref lookup failed for {} {}={}: {}",
                            entityType, refEntry.getKey(), refEntry.getValue(), e.getMessage());
                }
            }
        }

        // Fallback to business key per entity type
        return findBusinessKey(entityType, entityFields, source);
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> findByExternalRefs(String entityType, String key, String value) {
        return switch (entityType) {
            case "customer" -> (Optional<T>) canonicalCustomerRepository.findByExternalRefsContaining(key, value);
            case "product" -> (Optional<T>) canonicalProductRepository.findByExternalRefsContaining(key, value);
            case "salesperson" -> (Optional<T>) canonicalSalespersonRepository.findByExternalRefsContaining(key, value);
            case "region" -> (Optional<T>) canonicalRegionRepository.findByExternalRefsContaining(key, value);
            case "order" -> (Optional<T>) canonicalOrderRepository.findByExternalRefsContaining(key, value);
            default -> Optional.empty();
        };
    }

    private Object findBusinessKey(String entityType, Map<String, String> entityFields, DataSource source) {
        return switch (entityType) {
            case "customer" -> findCustomerByBusinessKey(entityFields);
            case "product" -> findProductByBusinessKey(entityFields);
            case "salesperson" -> findSalespersonByBusinessKey(entityFields);
            case "region" -> findRegionByBusinessKey(entityFields);
            default -> null;
        };
    }

    private CanonicalCustomer findCustomerByBusinessKey(Map<String, String> fields) {
        String email = fields.get("email");
        if (email != null && !email.isBlank()) {
            Optional<CanonicalCustomer> result = canonicalCustomerRepository.findByEmail(email);
            if (result.isPresent()) return result.get();
        }
        String name = fields.get("name");
        String phone = fields.get("phone");
        if (name != null && !name.isBlank() && phone != null && !phone.isBlank()) {
            Optional<CanonicalCustomer> result = canonicalCustomerRepository.findByNameAndPhone(name, phone);
            if (result.isPresent()) return result.get();
        }
        return null;
    }

    private CanonicalProduct findProductByBusinessKey(Map<String, String> fields) {
        String sku = fields.get("sku");
        if (sku != null && !sku.isBlank()) {
            return canonicalProductRepository.findBySku(sku).orElse(null);
        }
        return null;
    }

    private CanonicalSalesperson findSalespersonByBusinessKey(Map<String, String> fields) {
        String email = fields.get("email");
        if (email != null && !email.isBlank()) {
            return canonicalSalespersonRepository.findByEmail(email).orElse(null);
        }
        return null;
    }

    private CanonicalRegion findRegionByBusinessKey(Map<String, String> fields) {
        String name = fields.get("name");
        if (name != null && !name.isBlank()) {
            return canonicalRegionRepository.findByName(name).orElse(null);
        }
        return null;
    }

    private Optional<CanonicalOrder> findExistingOrder(Map<String, String> orderFields, DataSource source) {
        // Try external_refs first
        String externalRefs = orderFields.get("_externalRefs");
        if (externalRefs != null && !externalRefs.isBlank() && !"{}".equals(externalRefs)) {
            try {
                Map<String, String> refsMap = objectMapper.readValue(externalRefs, new TypeReference<>() {});
                for (Map.Entry<String, String> refEntry : refsMap.entrySet()) {
                    Optional<CanonicalOrder> result = canonicalOrderRepository.findByExternalRefsContaining(
                            refEntry.getKey(), refEntry.getValue());
                    if (result.isPresent()) return result;
                }
            } catch (Exception e) {
                log.debug("Failed to parse order external refs: {}", e.getMessage());
            }
        }

        // Fallback: business key (orderDate + totalAmount + source)
        String orderDateStr = orderFields.get("orderDate");
        String totalAmountStr = orderFields.get("totalAmount");
        if (orderDateStr != null && !orderDateStr.isBlank() && totalAmountStr != null && !totalAmountStr.isBlank()) {
            try {
                LocalDate orderDate = LocalDate.parse(orderDateStr);
                BigDecimal totalAmount = new BigDecimal(totalAmountStr);
                return canonicalOrderRepository.findByOrderDateAndTotalAmountAndSource(orderDate, totalAmount, source);
            } catch (Exception e) {
                log.debug("Order business key lookup failed: {}", e.getMessage());
            }
        }

        return Optional.empty();
    }

    // =========================================================================
    // Save or update entity (handles same-source vs cross-source)
    // =========================================================================

    private Object saveOrUpdateEntity(String entityType, Map<String, String> entityFields,
                                       IngestionJob job, DataSource source, BigDecimal overallScore) {
        Object existing = findExistingEntity(entityType, entityFields, source);

        if (existing == null) {
            // Create new
            return createNew(entityType, entityFields, source, overallScore);
        }

        // Existing - determine same-source vs cross-source
        DataSource primarySource = getEntityPrimarySource(existing);
        if (primarySource != null && primarySource.getId().equals(source.getId())) {
            // Same-source: partial update
            return applySameSourceUpdate(existing, entityType, entityFields, overallScore);
        } else {
            // Cross-source: conflict detection + update
            return applyCrossSourceUpdate(existing, entityType, entityFields, job, source, overallScore);
        }
    }

    private DataSource getEntityPrimarySource(Object entity) {
        return switch (entity) {
            case CanonicalCustomer c -> c.getPrimarySource();
            case CanonicalProduct p -> p.getPrimarySource();
            case CanonicalSalesperson s -> s.getPrimarySource();
            case CanonicalRegion r -> null; // No primary source
            case CanonicalOrder o -> o.getSource(); // Uses 'source' not 'primarySource'
            default -> null;
        };
    }

    // =========================================================================
    // Entity creation
    // =========================================================================

    private Object createNew(String entityType, Map<String, String> fields, DataSource source, BigDecimal overallScore) {
        return switch (entityType) {
            case "customer" -> createNewCustomer(fields, source, overallScore);
            case "product" -> createNewProduct(fields, source, overallScore);
            case "salesperson" -> createNewSalesperson(fields, source);
            case "region" -> createNewRegion(fields);
            default -> null;
        };
    }

    private CanonicalCustomer createNewCustomer(Map<String, String> fields, DataSource source, BigDecimal overallScore) {
        CanonicalCustomer entity = new CanonicalCustomer();
        applyAllFields(entity, fields);
        entity.setExternalRefs(fields.get("_externalRefs"));
        entity.setPrimarySource(source);
        entity.setQualityScore(overallScore);
        entity.setHasConflicts(false);

        String addAttr = fields.get("additional_attributes");
        if (addAttr != null) {
            entity.setAdditionalAttributes(addAttr);
        }

        entity = canonicalCustomerRepository.save(entity);

        String lineageJson = buildTransformationsJson(fields, "customer");
        lineageService.writeLineage(entity.getId(), "customer", source, null, null, lineageJson);

        return entity;
    }

    private CanonicalProduct createNewProduct(Map<String, String> fields, DataSource source, BigDecimal overallScore) {
        CanonicalProduct entity = new CanonicalProduct();
        applyAllFields(entity, fields);
        entity.setExternalRefs(fields.get("_externalRefs"));
        entity.setPrimarySource(source);
        entity.setQualityScore(overallScore);
        entity.setHasConflicts(false);

        String addAttr = fields.get("additional_attributes");
        if (addAttr != null) {
            entity.setAdditionalAttributes(addAttr);
        }

        entity = canonicalProductRepository.save(entity);

        String lineageJson = buildTransformationsJson(fields, "product");
        lineageService.writeLineage(entity.getId(), "product", source, null, null, lineageJson);

        return entity;
    }

    private CanonicalSalesperson createNewSalesperson(Map<String, String> fields, DataSource source) {
        CanonicalSalesperson entity = new CanonicalSalesperson();
        applyAllFields(entity, fields);
        entity.setExternalRefs(fields.get("_externalRefs"));
        entity.setPrimarySource(source);
        if (entity.getActive() == null) {
            entity.setActive(true);
        }

        String addAttr = fields.get("additional_attributes");
        if (addAttr != null) {
            entity.setAdditionalAttributes(addAttr);
        }

        entity = canonicalSalespersonRepository.save(entity);

        String lineageJson = buildTransformationsJson(fields, "salesperson");
        lineageService.writeLineage(entity.getId(), "salesperson", source, null, null, lineageJson);

        return entity;
    }

    private CanonicalRegion createNewRegion(Map<String, String> fields) {
        CanonicalRegion entity = new CanonicalRegion();
        applyAllFields(entity, fields);

        entity = canonicalRegionRepository.save(entity);

        String lineageJson = buildTransformationsJson(fields, "region");
        lineageService.writeLineage(entity.getId(), "region", null, null, null, lineageJson);

        return entity;
    }

    // =========================================================================
    // Same-source partial update
    // =========================================================================

    private Object applySameSourceUpdate(Object entity, String entityType,
                                          Map<String, String> fields, BigDecimal overallScore) {
        applyAllFields(entity, fields);

        // Set quality score if entity supports it
        if (overallScore != null && ENTITIES_WITH_QUALITY_SCORE.contains(entityType)) {
            setFieldValue(entity, "qualityScore", overallScore.toString());
        }

        // Set updatedAt
        setFieldValue(entity, "updatedAt", Instant.now().toString());

        // Set additional_attributes
        String addAttr = fields.get("additional_attributes");
        if (addAttr != null && ENTITIES_WITH_ADDITIONAL_ATTRIBUTES.contains(entityType)) {
            setFieldValue(entity, "additionalAttributes", addAttr);
        }

        Object saved = saveEntity(entityType, entity);

        String lineageJson = buildTransformationsJson(fields, entityType);
        UUID entityId = getEntityId(saved);
        lineageService.writeLineage(entityId, entityType, null, null, null, lineageJson);

        return saved;
    }

    // =========================================================================
    // Cross-source update (with conflict detection)
    // =========================================================================

    private Object applyCrossSourceUpdate(Object entity, String entityType,
                                           Map<String, String> fields,
                                           IngestionJob job, DataSource sourceB, BigDecimal overallScore) {
        // Remove internal fields before passing to ConflictDetectionService
        Map<String, String> cleanFields = stripInternalFields(fields);

        // Call ConflictDetectionService ONCE with the full field map
        boolean hasConflicts = false;
        try {
            List<ConflictRecord> conflicts = detectConflictsForEntity(entity, cleanFields, sourceB, job);
            hasConflicts = conflicts != null && !conflicts.isEmpty();
        } catch (Exception e) {
            log.warn("Conflict detection failed for {}: {}", entityType, e.getMessage());
        }

        // Apply all incoming non-null values (LATEST_WINS default)
        for (Map.Entry<String, String> entry : cleanFields.entrySet()) {
            String fieldName = entry.getKey();
            String newValue = entry.getValue();

            if ("additional_attributes".equals(fieldName)) {
                continue;
            }

            String existingValue = getFieldValue(entity, fieldName);

            // NULL-VALUE GUARD: both null -> skip; one null -> non-null wins; both non-null -> incoming wins
            if (existingValue == null && newValue == null) {
                continue;
            }
            if (newValue != null) {
                setFieldValue(entity, fieldName, newValue);
            }
        }

        // Set quality score
        if (overallScore != null && ENTITIES_WITH_QUALITY_SCORE.contains(entityType)) {
            setFieldValue(entity, "qualityScore", overallScore.toString());
        }

        // Set has_conflicts if entity supports it
        if (Set.of("customer", "product", "order").contains(entityType)) {
            setFieldValue(entity, "hasConflicts", String.valueOf(hasConflicts));
        }

        // Set updatedAt
        setFieldValue(entity, "updatedAt", Instant.now().toString());

        // Set additional_attributes
        String addAttr = fields.get("additional_attributes");
        if (addAttr != null && ENTITIES_WITH_ADDITIONAL_ATTRIBUTES.contains(entityType)) {
            setFieldValue(entity, "additionalAttributes", addAttr);
        }

        Object saved = saveEntity(entityType, entity);

        String lineageJson = buildTransformationsJson(fields, entityType);
        UUID entityId = getEntityId(saved);
        lineageService.writeLineage(entityId, entityType, sourceB, job, null, lineageJson);

        return saved;
    }

    @SuppressWarnings("unchecked")
    private <T> List<ConflictRecord> detectConflictsForEntity(Object entity, Map<String, String> cleanFields,
                                                                DataSource sourceB, IngestionJob job) {
        return switch (entity) {
            case CanonicalCustomer c ->
                    (List<ConflictRecord>) (List<?>) conflictDetectionService.detectConflicts(c, cleanFields, sourceB, job);
            case CanonicalProduct p ->
                    (List<ConflictRecord>) (List<?>) conflictDetectionService.detectConflicts(p, cleanFields, sourceB, job);
            case CanonicalSalesperson s ->
                    (List<ConflictRecord>) (List<?>) conflictDetectionService.detectConflicts(s, cleanFields, sourceB, job);
            case CanonicalOrder o ->
                    (List<ConflictRecord>) (List<?>) conflictDetectionService.detectConflicts(o, cleanFields, sourceB, job);
            default -> List.of();
        };
    }

    // =========================================================================
    // Order-specific cross-source update
    // =========================================================================

    private CanonicalOrder applyCrossSourceOrderUpdate(CanonicalOrder order,
                                                        Map<String, String> incomingFields,
                                                        IngestionJob job, DataSource sourceB,
                                                        BigDecimal overallScore) {
        Map<String, String> cleanFields = stripInternalFields(incomingFields);

        boolean hasConflicts = false;
        try {
            List<ConflictRecord> conflicts = conflictDetectionService.detectConflicts(order, cleanFields, sourceB, job);
            hasConflicts = conflicts != null && !conflicts.isEmpty();
        } catch (Exception e) {
            log.warn("Conflict detection failed for order: {}", e.getMessage());
        }

        for (Map.Entry<String, String> entry : cleanFields.entrySet()) {
            String fieldName = entry.getKey();
            String newValue = entry.getValue();
            if ("additional_attributes".equals(fieldName)) continue;

            String existingValue = getFieldValue(order, fieldName);
            if (existingValue == null && newValue == null) continue;
            if (newValue != null) {
                setFieldValue(order, fieldName, newValue);
            }
        }

        if (overallScore != null) {
            order.setQualityScore(overallScore);
        }
        order.setHasConflicts(hasConflicts);

        return order;
    }

    private void applySameSourceOrderUpdate(CanonicalOrder order, Map<String, String> fields,
                                             BigDecimal overallScore) {
        applyAllFields(order, fields);
        if (overallScore != null) {
            order.setQualityScore(overallScore);
        }
    }

    // =========================================================================
    // Order creation
    // =========================================================================

    private CanonicalOrder createNewOrder(Map<String, String> fields,
                                           IngestionJob job, DataSource source,
                                           UUID customerId, UUID salespersonId, UUID regionId,
                                           BigDecimal overallScore) {
        CanonicalOrder entity = new CanonicalOrder();
        applyAllFields(entity, fields);
        entity.setExternalRefs(fields.get("_externalRefs"));
        entity.setSource(source);
        entity.setJob(job);
        entity.setQualityScore(overallScore);
        entity.setHasConflicts(false);

        // Set FK references using entity proxies
        if (customerId != null) {
            CanonicalCustomer customerRef = new CanonicalCustomer();
            customerRef.setId(customerId);
            entity.setCustomer(customerRef);
        }
        if (salespersonId != null) {
            CanonicalSalesperson salespersonRef = new CanonicalSalesperson();
            salespersonRef.setId(salespersonId);
            entity.setSalesperson(salespersonRef);
        }
        if (regionId != null) {
            CanonicalRegion regionRef = new CanonicalRegion();
            regionRef.setId(regionId);
            entity.setRegion(regionRef);
        }

        return entity;
    }

    // =========================================================================
    // External refs building
    // =========================================================================

    String buildExternalRefs(String entityType, Map<String, String> entityFields, DataSource source) {
        // Scan for identifier-like field names
        for (Map.Entry<String, String> entry : entityFields.entrySet()) {
            String fieldName = entry.getKey();
            String value = entry.getValue();

            if (fieldName.startsWith("_") || "additional_attributes".equals(fieldName)) {
                continue;
            }

            if (isIdentifierField(fieldName) && value != null && !value.isBlank()) {
                try {
                    return objectMapper.writeValueAsString(Map.of(source.getName(), value));
                } catch (Exception e) {
                    log.warn("Failed to serialize external refs for {}: {}", entityType, e.getMessage());
                }
            }
        }

        // Fallback: business key fields
        return buildBusinessKeyExternalRefs(entityType, entityFields);
    }

    private boolean isIdentifierField(String fieldName) {
        String lower = fieldName.toLowerCase();
        for (String pattern : IDENTIFIER_PATTERNS) {
            if (lower.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private String buildBusinessKeyExternalRefs(String entityType, Map<String, String> entityFields) {
        try {
            return switch (entityType) {
                case "customer", "salesperson" -> {
                    String email = entityFields.get("email");
                    if (email != null && !email.isBlank()) {
                        yield objectMapper.writeValueAsString(Map.of("email", email));
                    }
                    yield null;
                }
                case "product" -> {
                    String sku = entityFields.get("sku");
                    if (sku != null && !sku.isBlank()) {
                        yield objectMapper.writeValueAsString(Map.of("sku", sku));
                    }
                    yield null;
                }
                case "region" -> {
                    String name = entityFields.get("name");
                    if (name != null && !name.isBlank()) {
                        yield objectMapper.writeValueAsString(Map.of("name", name));
                    }
                    yield null;
                }
                case "order" -> {
                    String totalAmount = entityFields.get("totalAmount");
                    if (totalAmount != null && !totalAmount.isBlank()) {
                        yield objectMapper.writeValueAsString(Map.of("totalAmount", totalAmount));
                    }
                    yield null;
                }
                default -> null;
            };
        } catch (Exception e) {
            log.warn("Failed to build business key external refs for {}: {}", entityType, e.getMessage());
            return "{}";
        }
    }

    // =========================================================================
    // Reflection-based field operations
    // =========================================================================

    private void applyAllFields(Object entity, Map<String, String> fields) {
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String fieldName = entry.getKey();
            String value = entry.getValue();

            // Skip internal fields and additional_attributes (handled separately)
            if (fieldName.startsWith("_") || "additional_attributes".equals(fieldName)) {
                continue;
            }

            if (value != null) {
                setFieldValue(entity, fieldName, value);
            }
        }
    }

    private void setFieldValue(Object entity, String fieldName, String value) {
        if (value == null) return;

        String camelField = snakeToCamel(fieldName);
        String setterName = "set" + Character.toUpperCase(camelField.charAt(0)) + camelField.substring(1);

        try {
            Method setter = Arrays.stream(entity.getClass().getMethods())
                    .filter(m -> m.getName().equals(setterName) && m.getParameterCount() == 1)
                    .findFirst()
                    .orElse(null);

            if (setter == null) {
                log.debug("No setter {} on {}", setterName, entity.getClass().getSimpleName());
                return;
            }

            Object converted = convertToType(value, setter.getParameterTypes()[0]);
            if (converted != null) {
                setter.invoke(entity, converted);
            }
        } catch (Exception e) {
            log.debug("Failed to set {} on {}: {}", fieldName, entity.getClass().getSimpleName(), e.getMessage());
        }
    }

    private String getFieldValue(Object entity, String fieldName) {
        String camelField = snakeToCamel(fieldName);
        String getterName = "get" + Character.toUpperCase(camelField.charAt(0)) + camelField.substring(1);

        try {
            Method getter = Arrays.stream(entity.getClass().getMethods())
                    .filter(m -> m.getName().equals(getterName) && m.getParameterCount() == 0)
                    .findFirst()
                    .orElse(null);

            if (getter == null) {
                // Try "is" prefix for boolean fields
                String isGetterName = "is" + Character.toUpperCase(camelField.charAt(0)) + camelField.substring(1);
                getter = Arrays.stream(entity.getClass().getMethods())
                        .filter(m -> m.getName().equals(isGetterName) && m.getParameterCount() == 0)
                        .findFirst()
                        .orElse(null);
            }

            if (getter == null) return null;
            Object value = getter.invoke(entity);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.debug("Could not get field '{}' from {}: {}", fieldName, entity.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private Object convertToType(String value, Class<?> targetType) {
        try {
            if (targetType == String.class) {
                return value;
            } else if (targetType == BigDecimal.class) {
                return new BigDecimal(value);
            } else if (targetType == Integer.class || targetType == int.class) {
                return Integer.valueOf(value);
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                return Boolean.valueOf(value);
            } else if (targetType == LocalDate.class) {
                return LocalDate.parse(value);
            } else if (targetType == Instant.class) {
                return Instant.parse(value);
            }
        } catch (Exception e) {
            log.debug("Cannot convert '{}' to {}: {}", value, targetType.getSimpleName(), e.getMessage());
        }
        return null;
    }

    private UUID getEntityId(Object entity) {
        try {
            Method getId = entity.getClass().getMethod("getId");
            return (UUID) getId.invoke(entity);
        } catch (Exception e) {
            log.error("Cannot get ID from {}", entity.getClass().getSimpleName(), e);
            return null;
        }
    }

    private Object saveEntity(String entityType, Object entity) {
        return switch (entityType) {
            case "customer" -> canonicalCustomerRepository.save((CanonicalCustomer) entity);
            case "product" -> canonicalProductRepository.save((CanonicalProduct) entity);
            case "salesperson" -> canonicalSalespersonRepository.save((CanonicalSalesperson) entity);
            case "region" -> canonicalRegionRepository.save((CanonicalRegion) entity);
            case "order" -> canonicalOrderRepository.save((CanonicalOrder) entity);
            default -> throw new IllegalArgumentException("Unknown entity type: " + entityType);
        };
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    Map<String, Map<String, String>> groupByEntityType(Map<String, String> transformed) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : transformed.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            int dotIndex = key.indexOf('.');
            if (dotIndex < 0) {
                log.debug("Skipping key without entity prefix: {}", key);
                continue;
            }

            String entityType = key.substring(0, dotIndex);
            String fieldName = key.substring(dotIndex + 1);

            result.computeIfAbsent(entityType, k -> new LinkedHashMap<>())
                    .put(fieldName, value);
        }

        return result;
    }

    static String snakeToCamel(String snakeCase) {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < snakeCase.length(); i++) {
            char c = snakeCase.charAt(i);
            if (c == '_') {
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Map<String, String> parseExternalRefs(String externalRefs) {
        if (externalRefs == null || externalRefs.isBlank() || "{}".equals(externalRefs)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(externalRefs, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse external refs JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String buildTransformationsJson(Map<String, String> fields, String entityType) {
        try {
            List<Map<String, String>> transforms = new ArrayList<>();
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                if (entry.getKey().startsWith("_")) continue;
                Map<String, String> step = new LinkedHashMap<>();
                step.put("step", "transform");
                step.put("toField", entityType + "." + entry.getKey());
                step.put("outputValue", entry.getValue());
                transforms.add(step);
            }
            return objectMapper.writeValueAsString(transforms);
        } catch (Exception e) {
            log.warn("Failed to build transformations JSON: {}", e.getMessage());
            return "[]";
        }
    }

    private Map<String, String> stripInternalFields(Map<String, String> fields) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!entry.getKey().startsWith("_")) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
}
