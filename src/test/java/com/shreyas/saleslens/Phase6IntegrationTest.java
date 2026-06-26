package com.shreyas.saleslens;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.model.*;
import com.shreyas.saleslens.model.enums.*;
import com.shreyas.saleslens.repository.*;
import com.shreyas.saleslens.service.TransformationService;
import com.shreyas.saleslens.service.canonical.*;
import com.shreyas.saleslens.service.conflict.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 6 integration test exercising the full load → detect → resolve pipeline
 * end-to-end with mocked repositories (no real DB).
 *
 * <ol>
 *   <li>Source A load: creates canonical customer, order, and line item</li>
 *   <li>Source B load: same customer email but different segment → conflict</li>
 *   <li>Conflict resolution: pick a value via ConflictResolutionService</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class Phase6IntegrationTest {

    // ---- Repository mocks ----

    @Mock private IngestionJobRepository ingestionJobRepository;
    @Mock private DataSourceRepository dataSourceRepository;
    @Mock private QualityRunRepository qualityRunRepository;
    @Mock private QualityScoreRepository qualityScoreRepository;
    @Mock private RejectedRecordRepository rejectedRecordRepository;
    @Mock private FieldMappingRepository fieldMappingRepository;
    @Mock private StagedRecordRepository stagedRecordRepository;
    @Mock private CanonicalCustomerRepository canonicalCustomerRepository;
    @Mock private CanonicalProductRepository canonicalProductRepository;
    @Mock private CanonicalSalespersonRepository canonicalSalespersonRepository;
    @Mock private CanonicalRegionRepository canonicalRegionRepository;
    @Mock private CanonicalOrderRepository canonicalOrderRepository;
    @Mock private CanonicalOrderLineItemRepository canonicalOrderLineItemRepository;
    @Mock private ConflictRecordRepository conflictRecordRepository;
    @Mock private DataLineageRepository dataLineageRepository;

    @Mock private TransformationService transformationService;

    // ---- Services under test (real instances with mocked repos) ----

    private ObjectMapper objectMapper;
    private CanonicalLoadService canonicalLoadService;
    private ConflictDetectionService conflictDetectionService;
    private ConflictResolutionService conflictResolutionService;
    private LineageService lineageService;

    // ---- Shared test fixtures ----

    private DataSource sourceA;
    private DataSource sourceB;
    private UUID jobAId;
    private UUID jobBId;
    private IngestionJob jobA;
    private IngestionJob jobB;
    private Users testUser;

    // ---- Argument captors ----

    @Captor private ArgumentCaptor<CanonicalCustomer> customerCaptor;
    @Captor private ArgumentCaptor<CanonicalOrder> orderCaptor;
    @Captor private ArgumentCaptor<CanonicalOrderLineItem> lineItemCaptor;
    @Captor private ArgumentCaptor<List<ConflictRecord>> conflictListCaptor;

    // =========================================================================
    // Setup
    // =========================================================================

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // Real services wired with mocked repositories
        lineageService = new LineageService(dataLineageRepository);
        conflictDetectionService = new ConflictDetectionService(conflictRecordRepository);
        conflictResolutionService = new ConflictResolutionService(
                conflictRecordRepository,
                canonicalCustomerRepository,
                canonicalProductRepository,
                canonicalSalespersonRepository,
                canonicalOrderRepository
        );
        canonicalLoadService = new CanonicalLoadService(
                ingestionJobRepository,
                dataSourceRepository,
                qualityRunRepository,
                qualityScoreRepository,
                rejectedRecordRepository,
                fieldMappingRepository,
                stagedRecordRepository,
                canonicalCustomerRepository,
                canonicalProductRepository,
                canonicalSalespersonRepository,
                canonicalRegionRepository,
                canonicalOrderRepository,
                canonicalOrderLineItemRepository,
                conflictRecordRepository,
                lineageService,
                conflictDetectionService,
                transformationService,
                objectMapper
        );

        // DataSource A (trustScore = 0.9)
        sourceA = new DataSource();
        sourceA.setId(UUID.randomUUID());
        sourceA.setName("Source A");
        sourceA.setTrustScore(new BigDecimal("0.9"));
        sourceA.setActive(true);

        jobAId = UUID.randomUUID();
        jobA = new IngestionJob();
        jobA.setId(jobAId);
        jobA.setSource(sourceA);
        jobA.setStatus(JobStatus.COMPLETED);

        // DataSource B (trustScore = 0.6)
        sourceB = new DataSource();
        sourceB.setId(UUID.randomUUID());
        sourceB.setName("Source B");
        sourceB.setTrustScore(new BigDecimal("0.6"));
        sourceB.setActive(true);

        jobBId = UUID.randomUUID();
        jobB = new IngestionJob();
        jobB.setId(jobBId);
        jobB.setSource(sourceB);
        jobB.setStatus(JobStatus.COMPLETED);

        testUser = new Users();
        testUser.setId(1L);
        testUser.setUsername("testuser");

        mockSaveReturnsEntityWithId();
    }

    // =========================================================================
    // Full pipeline test: load → detect → resolve
    // =========================================================================

    @Test
    void testFullLoadDetectResolvePipeline() {
        // ==================================================================
        // PART 1: Initial canonical load from Source A (trustScore = 0.9)
        //
        // One rich staged record creates:
        //   - 1 CanonicalCustomer  (email = john@test.com, segment = Consumer)
        //   - 1 CanonicalOrder
        //   - 1 CanonicalOrderLineItem
        // ==================================================================

        setupSourceALoad();

        canonicalLoadService.loadCanonical(jobAId);

        // --- Verify customer saved ---
        verify(canonicalCustomerRepository).save(customerCaptor.capture());
        CanonicalCustomer savedCustomer = customerCaptor.getValue();
        assertNotNull(savedCustomer);
        assertEquals("john@test.com", savedCustomer.getEmail());
        assertEquals("Consumer", savedCustomer.getSegment());
        assertEquals(sourceA, savedCustomer.getPrimarySource());
        assertFalse(savedCustomer.getHasConflicts());

        // --- Verify order saved ---
        verify(canonicalOrderRepository).save(orderCaptor.capture());
        CanonicalOrder savedOrder = orderCaptor.getValue();
        assertNotNull(savedOrder);
        assertEquals(LocalDate.of(2024, 1, 15), savedOrder.getOrderDate());
        assertEquals(0, new BigDecimal("150.00").compareTo(savedOrder.getTotalAmount()));
        assertNotNull(savedOrder.getCustomer());

        // --- Verify order line item saved ---
        verify(canonicalOrderLineItemRepository).save(lineItemCaptor.capture());
        CanonicalOrderLineItem savedLineItem = lineItemCaptor.getValue();
        assertNotNull(savedLineItem);
        assertEquals(Integer.valueOf(3), savedLineItem.getQuantity());
        assertEquals(0, new BigDecimal("150.00").compareTo(savedLineItem.getLineTotal()));
        assertNotNull(savedLineItem.getOrder());
        assertNotNull(savedLineItem.getProduct());

        // --- Verify job stats ---
        assertTrue(jobA.getTotalLoaded() >= 3,
                "totalLoaded should be at least 3 (customer + order + line_item)");

        // --- Verify lineage saved ---
        verify(dataLineageRepository, atLeast(3)).save(any(DataLineage.class));

        // ==================================================================
        // PART 2: Source B load (trustScore = 0.6)
        //
        // Same customer email "john@test.com" but segment = "Corporate".
        // Cross-source conflict detection fires — existing "Consumer" vs "Corporate".
        // Trust gap = |0.9 - 0.6| = 0.3 → TRUST_HIERARCHY / RESOLVED.
        // hasConflicts is set to true on the canonical customer.
        // ==================================================================

        ArgumentCaptor<CanonicalCustomer> customerCaptor2 =
                ArgumentCaptor.forClass(CanonicalCustomer.class);

        setupSourceBLoad();

        canonicalLoadService.loadCanonical(jobBId);

        // --- Verify conflict records saved ---
        verify(conflictRecordRepository).saveAll(conflictListCaptor.capture());
        List<ConflictRecord> conflictRecords = conflictListCaptor.getValue();
        assertFalse(conflictRecords.isEmpty(), "Should have detected at least one conflict");
        ConflictRecord segmentConflict = conflictRecords.get(0);
        assertEquals("segment", segmentConflict.getFieldName());
        assertEquals("Consumer", segmentConflict.getValueA());
        assertEquals("Corporate", segmentConflict.getValueB());
        assertEquals("CanonicalCustomer", segmentConflict.getEntityType());

        // --- Verify cross-source customer update has hasConflicts=true ---
        verify(canonicalCustomerRepository, atLeast(2)).save(customerCaptor2.capture());
        List<CanonicalCustomer> allSavedCustomers = customerCaptor2.getAllValues();
        CanonicalCustomer conflictedCustomer =
                allSavedCustomers.get(allSavedCustomers.size() - 1);
        assertTrue(conflictedCustomer.getHasConflicts(),
                "Customer must have hasConflicts=true after cross-source conflict");

        // ==================================================================
        // PART 3: Resolve the conflict via ConflictResolutionService
        //
        // Pick "Consumer" as the canonical value.
        // ==================================================================

        UUID conflictId = segmentConflict.getId();
        UUID entityId = segmentConflict.getEntityId();

        // Setup mocks for resolution
        when(conflictRecordRepository.findById(conflictId))
                .thenReturn(Optional.of(segmentConflict));

        CanonicalCustomer entityForResolution = new CanonicalCustomer();
        entityForResolution.setId(entityId);
        entityForResolution.setSegment("Corporate");
        when(canonicalCustomerRepository.findById(entityId))
                .thenReturn(Optional.of(entityForResolution));

        when(conflictRecordRepository.save(any(ConflictRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Execute resolution
        Optional<ConflictRecord> resolved =
                conflictResolutionService.resolveConflict(conflictId, "Consumer", testUser);

        // --- Verify resolution ---
        assertTrue(resolved.isPresent(), "Resolution should return the updated record");
        // Conflict was auto-resolved via TRUST_HIERARCHY (trust gap = 0.3 >= 0.3),
        // so status is already RESOLVED from detection.
        assertEquals(ConflictStatus.RESOLVED, resolved.get().getStatus(),
                "Conflict status must be RESOLVED after resolution");
    }

    // =========================================================================
    // Part 1 mock setup: Source A load
    // =========================================================================

    private void setupSourceALoad() {
        // Job lookup
        when(ingestionJobRepository.findById(jobAId)).thenReturn(Optional.of(jobA));

        // No quality run — all records pass
        when(qualityRunRepository.findTopByJobIdOrderByRunTimestampDesc(jobAId))
                .thenReturn(Optional.empty());

        // Field mappings (AUTO_CONFIRMED for all fields)
        when(fieldMappingRepository.findBySourceId(sourceA.getId()))
                .thenReturn(createFieldMappings(sourceA));

        // No quality score
        when(qualityScoreRepository.findByJobId(jobAId)).thenReturn(Optional.empty());

        // No conflicts initially
        when(conflictRecordRepository.count()).thenReturn(0L);

        // One rich staged record with customer + product + order + line_item data
        StagedRecord recordA = new StagedRecord();
        recordA.setId(UUID.randomUUID());
        recordA.setJob(jobA);
        recordA.setSource(sourceA);
        recordA.setRawPayload("{\"source\":\"A\",\"type\":\"full\"}");

        when(stagedRecordRepository.findByJobId(eq(jobAId), any(PageRequest.class)))
                .thenReturn(List.of(recordA), Collections.emptyList());

        // Transform output spanning all four entity types
        Map<String, String> transformA = new LinkedHashMap<>();
        transformA.put("customer.name", "John Doe");
        transformA.put("customer.email", "john@test.com");
        transformA.put("customer.segment", "Consumer");
        transformA.put("product.sku", "PROD-001");
        transformA.put("product.name", "Widget");
        transformA.put("product.unitPrice", "10.00");
        transformA.put("order.orderDate", "2024-01-15");
        transformA.put("order.totalAmount", "150.00");
        transformA.put("order.shipMode", "Standard");
        transformA.put("order_line_item.quantity", "3");
        transformA.put("order_line_item.unitPrice", "50.00");
        transformA.put("order_line_item.lineTotal", "150.00");

        when(transformationService.transform(same(recordA), anyList()))
                .thenReturn(transformA);

        // No existing entities (all inserts)
        mockNoExistingEntities();

        // Mock saveAll for conflict records (no-op, not actually invoked in Part 1)
        when(conflictRecordRepository.saveAll(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));

        // Mock data lineage saves
        when(dataLineageRepository.save(any(DataLineage.class)))
                .thenAnswer(inv -> {
                    DataLineage dl = inv.getArgument(0);
                    if (dl.getId() == null) dl.setId(UUID.randomUUID());
                    return dl;
                });
    }

    // =========================================================================
    // Part 2 mock setup: Source B load — cross-source conflict on segment
    // =========================================================================

    private void setupSourceBLoad() {
        // Job lookup
        when(ingestionJobRepository.findById(jobBId)).thenReturn(Optional.of(jobB));

        // No quality run
        when(qualityRunRepository.findTopByJobIdOrderByRunTimestampDesc(jobBId))
                .thenReturn(Optional.empty());

        // Field mappings for Source B
        when(fieldMappingRepository.findBySourceId(sourceB.getId()))
                .thenReturn(createFieldMappings(sourceB));

        // No quality score
        when(qualityScoreRepository.findByJobId(jobBId)).thenReturn(Optional.empty());

        // One conflict record will be saved
        when(conflictRecordRepository.count()).thenReturn(1L);

        // One staged record for Source B
        StagedRecord recordB = new StagedRecord();
        recordB.setId(UUID.randomUUID());
        recordB.setJob(jobB);
        recordB.setSource(sourceB);
        recordB.setRawPayload("{\"source\":\"B\",\"type\":\"customer\"}");

        when(stagedRecordRepository.findByJobId(eq(jobBId), any(PageRequest.class)))
                .thenReturn(List.of(recordB), Collections.emptyList());

        // Transform: same email as Source A but different segment → conflict
        Map<String, String> transformB = new LinkedHashMap<>();
        transformB.put("customer.name", "John Doe");
        transformB.put("customer.email", "john@test.com");
        transformB.put("customer.segment", "Corporate");

        when(transformationService.transform(same(recordB), anyList()))
                .thenReturn(transformB);

        // Existing customer found by email (business key match)
        CanonicalCustomer existingCustomer = new CanonicalCustomer();
        existingCustomer.setId(UUID.randomUUID());
        existingCustomer.setName("John Doe");
        existingCustomer.setEmail("john@test.com");
        existingCustomer.setSegment("Consumer");
        existingCustomer.setPrimarySource(sourceA);
        existingCustomer.setHasConflicts(false);

        when(canonicalCustomerRepository.findByEmail("john@test.com"))
                .thenReturn(Optional.of(existingCustomer));
        when(canonicalCustomerRepository.findByNameAndPhone(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(canonicalCustomerRepository.findByExternalRefsContaining(anyString(), anyString()))
                .thenReturn(Optional.empty());

        // Mock saveAll to return conflicts (will be called by ConflictDetectionService)
        when(conflictRecordRepository.saveAll(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));

        // Mock data lineage saves
        when(dataLineageRepository.save(any(DataLineage.class)))
                .thenAnswer(inv -> {
                    DataLineage dl = inv.getArgument(0);
                    if (dl.getId() == null) dl.setId(UUID.randomUUID());
                    return dl;
                });
    }

    // =========================================================================
    // Helper: AUTO_CONFIRMED FieldMapping list
    // =========================================================================

    private List<FieldMapping> createFieldMappings(DataSource source) {
        List<FieldMapping> mappings = new ArrayList<>();

        // Customer
        mappings.add(createMapping(source, "name", "customer", "name"));
        mappings.add(createMapping(source, "email", "customer", "email"));
        mappings.add(createMapping(source, "segment", "customer", "segment"));

        // Product
        mappings.add(createMapping(source, "sku", "product", "sku"));
        mappings.add(createMapping(source, "product_name", "product", "name"));
        mappings.add(createMapping(source, "unit_price", "product", "unitPrice"));

        // Order
        mappings.add(createMapping(source, "order_date", "order", "orderDate"));
        mappings.add(createMapping(source, "total_amount", "order", "totalAmount"));
        mappings.add(createMapping(source, "ship_mode", "order", "shipMode"));

        // Order Line Item
        mappings.add(createMapping(source, "quantity", "order_line_item", "quantity"));
        mappings.add(createMapping(source, "unit_price", "order_line_item", "unitPrice"));
        mappings.add(createMapping(source, "line_total", "order_line_item", "lineTotal"));

        return mappings;
    }

    private static FieldMapping createMapping(DataSource source, String sourceField,
                                               String entity, String field) {
        FieldMapping mapping = new FieldMapping();
        mapping.setId(UUID.randomUUID());
        mapping.setSource(source);
        mapping.setSourceFieldName(sourceField);
        mapping.setCanonicalEntity(entity);
        mapping.setCanonicalField(field);
        mapping.setConfidence(new BigDecimal("1.00"));
        mapping.setStatus("AUTO_CONFIRMED");
        return mapping;
    }

    // =========================================================================
    // Helper: All entity lookups return empty (no pre-existing entities)
    // =========================================================================

    private void mockNoExistingEntities() {
        lenient().when(canonicalCustomerRepository.findByEmail(anyString()))
                .thenReturn(Optional.empty());
        lenient().when(canonicalCustomerRepository.findByNameAndPhone(anyString(), anyString()))
                .thenReturn(Optional.empty());
        lenient().when(canonicalProductRepository.findBySku(anyString()))
                .thenReturn(Optional.empty());
        lenient().when(canonicalSalespersonRepository.findByEmail(anyString()))
                .thenReturn(Optional.empty());
        lenient().when(canonicalRegionRepository.findByName(anyString()))
                .thenReturn(Optional.empty());
        lenient().when(canonicalCustomerRepository.findByExternalRefsContaining(anyString(), anyString()))
                .thenReturn(Optional.empty());
        lenient().when(canonicalProductRepository.findByExternalRefsContaining(anyString(), anyString()))
                .thenReturn(Optional.empty());
        lenient().when(canonicalSalespersonRepository.findByExternalRefsContaining(anyString(), anyString()))
                .thenReturn(Optional.empty());
        lenient().when(canonicalRegionRepository.findByExternalRefsContaining(anyString(), anyString()))
                .thenReturn(Optional.empty());
        lenient().when(canonicalOrderRepository.findByExternalRefsContaining(anyString(), anyString()))
                .thenReturn(Optional.empty());
        lenient().when(canonicalOrderRepository.findByOrderDateAndTotalAmountAndSource(any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    // =========================================================================
    // Helper: Save mocks that assign UUID IDs
    // =========================================================================

    private void mockSaveReturnsEntityWithId() {
        lenient().when(canonicalCustomerRepository.save(any(CanonicalCustomer.class)))
                .thenAnswer(inv -> {
                    CanonicalCustomer c = inv.getArgument(0);
                    if (c.getId() == null) c.setId(UUID.randomUUID());
                    return c;
                });
        lenient().when(canonicalProductRepository.save(any(CanonicalProduct.class)))
                .thenAnswer(inv -> {
                    CanonicalProduct p = inv.getArgument(0);
                    if (p.getId() == null) p.setId(UUID.randomUUID());
                    return p;
                });
        lenient().when(canonicalSalespersonRepository.save(any(CanonicalSalesperson.class)))
                .thenAnswer(inv -> {
                    CanonicalSalesperson s = inv.getArgument(0);
                    if (s.getId() == null) s.setId(UUID.randomUUID());
                    return s;
                });
        lenient().when(canonicalRegionRepository.save(any(CanonicalRegion.class)))
                .thenAnswer(inv -> {
                    CanonicalRegion r = inv.getArgument(0);
                    if (r.getId() == null) r.setId(UUID.randomUUID());
                    return r;
                });
        lenient().when(canonicalOrderRepository.save(any(CanonicalOrder.class)))
                .thenAnswer(inv -> {
                    CanonicalOrder o = inv.getArgument(0);
                    if (o.getId() == null) o.setId(UUID.randomUUID());
                    return o;
                });
        lenient().when(canonicalOrderLineItemRepository.save(any(CanonicalOrderLineItem.class)))
                .thenAnswer(inv -> {
                    CanonicalOrderLineItem li = inv.getArgument(0);
                    if (li.getId() == null) li.setId(UUID.randomUUID());
                    return li;
                });
    }
}
