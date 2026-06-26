package com.shreyas.saleslens.service.canonical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.model.*;
import com.shreyas.saleslens.repository.*;
import com.shreyas.saleslens.service.TransformationService;
import com.shreyas.saleslens.service.conflict.ConflictDetectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CanonicalLoadServiceTest {

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
    @Mock private LineageService lineageService;
    @Mock private ConflictDetectionService conflictDetectionService;
    @Mock private TransformationService transformationService;

    private ObjectMapper objectMapper;
    private CanonicalLoadService canonicalLoadService;

    private UUID jobId;
    private DataSource source;
    private IngestionJob job;

    @Captor private ArgumentCaptor<CanonicalCustomer> customerCaptor;
    @Captor private ArgumentCaptor<CanonicalProduct> productCaptor;
    @Captor private ArgumentCaptor<CanonicalSalesperson> salespersonCaptor;
    @Captor private ArgumentCaptor<CanonicalRegion> regionCaptor;
    @Captor private ArgumentCaptor<CanonicalOrder> orderCaptor;
    @Captor private ArgumentCaptor<CanonicalOrderLineItem> lineItemCaptor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

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

        jobId = UUID.randomUUID();
        source = new DataSource();
        source.setId(UUID.randomUUID());
        source.setName("Test Source");

        job = new IngestionJob();
        job.setId(jobId);
        job.setSource(source);
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private void mockNoQualityRun() {
        when(qualityRunRepository.findTopByJobIdOrderByRunTimestampDesc(jobId))
                .thenReturn(Optional.empty());
    }

    private void mockJobAndMappings(List<FieldMapping> mappings) {
        when(ingestionJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fieldMappingRepository.findBySourceId(source.getId())).thenReturn(mappings != null ? mappings : List.of());
        when(qualityScoreRepository.findByJobId(jobId)).thenReturn(Optional.empty());
        when(conflictRecordRepository.count()).thenReturn(0L);
    }

    @SafeVarargs
    private void mockStagedRecords(Map<String, String>... transformedMaps) {
        List<StagedRecord> records = new ArrayList<>();
        List<Map<String, String>> transforms = new ArrayList<>();

        for (int i = 0; i < transformedMaps.length; i++) {
            StagedRecord record = new StagedRecord();
            record.setId(UUID.randomUUID());
            record.setJob(job);
            record.setSource(source);
            record.setRawPayload("{\"row\":" + i + "}");
            records.add(record);
            transforms.add(transformedMaps[i]);
        }

        when(stagedRecordRepository.findByJobId(eq(jobId), any(PageRequest.class)))
                .thenReturn(records, Collections.emptyList());

        for (int i = 0; i < records.size(); i++) {
            when(transformationService.transform(same(records.get(i)), anyList()))
                    .thenReturn(transforms.get(i));
        }
    }

    private void mockNoExistingEntities() {
        lenient().when(canonicalCustomerRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        lenient().when(canonicalCustomerRepository.findByNameAndPhone(anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(canonicalProductRepository.findBySku(anyString())).thenReturn(Optional.empty());
        lenient().when(canonicalSalespersonRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        lenient().when(canonicalRegionRepository.findByName(anyString())).thenReturn(Optional.empty());
        lenient().when(canonicalCustomerRepository.findByExternalRefsContaining(anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(canonicalProductRepository.findByExternalRefsContaining(anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(canonicalSalespersonRepository.findByExternalRefsContaining(anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(canonicalRegionRepository.findByExternalRefsContaining(anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(canonicalOrderRepository.findByExternalRefsContaining(anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(canonicalOrderRepository.findByOrderDateAndTotalAmountAndSource(any(), any(), any())).thenReturn(Optional.empty());
    }

    private void mockSaveReturnsEntityWithId() {
        lenient().when(canonicalCustomerRepository.save(any(CanonicalCustomer.class)))
                .thenAnswer(invocation -> {
                    CanonicalCustomer c = invocation.getArgument(0);
                    if (c.getId() == null) c.setId(UUID.randomUUID());
                    return c;
                });
        lenient().when(canonicalProductRepository.save(any(CanonicalProduct.class)))
                .thenAnswer(invocation -> {
                    CanonicalProduct p = invocation.getArgument(0);
                    if (p.getId() == null) p.setId(UUID.randomUUID());
                    return p;
                });
        lenient().when(canonicalSalespersonRepository.save(any(CanonicalSalesperson.class)))
                .thenAnswer(invocation -> {
                    CanonicalSalesperson s = invocation.getArgument(0);
                    if (s.getId() == null) s.setId(UUID.randomUUID());
                    return s;
                });
        lenient().when(canonicalRegionRepository.save(any(CanonicalRegion.class)))
                .thenAnswer(invocation -> {
                    CanonicalRegion r = invocation.getArgument(0);
                    if (r.getId() == null) r.setId(UUID.randomUUID());
                    return r;
                });
        lenient().when(canonicalOrderRepository.save(any(CanonicalOrder.class)))
                .thenAnswer(invocation -> {
                    CanonicalOrder o = invocation.getArgument(0);
                    if (o.getId() == null) o.setId(UUID.randomUUID());
                    return o;
                });
        lenient().when(canonicalOrderLineItemRepository.save(any(CanonicalOrderLineItem.class)))
                .thenAnswer(invocation -> {
                    CanonicalOrderLineItem li = invocation.getArgument(0);
                    if (li.getId() == null) li.setId(UUID.randomUUID());
                    return li;
                });
    }

    private void setupFullCustomerProductRecord() {
        mockNoQualityRun();
        mockJobAndMappings(List.of());
        mockNoExistingEntities();
        mockSaveReturnsEntityWithId();
    }

    // =========================================================================
    // Test: PASS 1 entities created (customer, product, salesperson, region)
    // =========================================================================

    @Test
    void testPass1_CustomerProductSalespersonRegionCreated() {
        // Record with customer, product, salesperson, region data
        Map<String, String> recordData = new LinkedHashMap<>();
        recordData.put("customer.name", "John Doe");
        recordData.put("customer.email", "john@test.com");
        recordData.put("customer.segment", "Consumer");
        recordData.put("product.sku", "PROD-001");
        recordData.put("product.name", "Widget");
        recordData.put("product.unitPrice", "10.00");
        recordData.put("salesperson.name", "Jane Smith");
        recordData.put("salesperson.email", "jane@test.com");
        recordData.put("salesperson.team", "Alpha");
        recordData.put("region.name", "West");
        recordData.put("region.country", "US");

        setupFullCustomerProductRecord();
        mockStagedRecords(recordData);

        canonicalLoadService.loadCanonical(jobId);

        // Verify customer created
        verify(canonicalCustomerRepository).save(customerCaptor.capture());
        CanonicalCustomer savedCustomer = customerCaptor.getValue();
        assertEquals("John Doe", savedCustomer.getName());
        assertEquals("john@test.com", savedCustomer.getEmail());
        assertEquals("Consumer", savedCustomer.getSegment());
        assertEquals(source, savedCustomer.getPrimarySource());
        assertFalse(savedCustomer.getHasConflicts());
        assertNotNull(savedCustomer.getExternalRefs());

        // Verify product created
        verify(canonicalProductRepository).save(productCaptor.capture());
        CanonicalProduct savedProduct = productCaptor.getValue();
        assertEquals("PROD-001", savedProduct.getSku());
        assertEquals("Widget", savedProduct.getName());

        // Verify salesperson created
        verify(canonicalSalespersonRepository).save(salespersonCaptor.capture());
        CanonicalSalesperson savedSalesperson = salespersonCaptor.getValue();
        assertEquals("Jane Smith", savedSalesperson.getName());
        assertEquals("jane@test.com", savedSalesperson.getEmail());

        // Verify region created
        verify(canonicalRegionRepository).save(regionCaptor.capture());
        CanonicalRegion savedRegion = regionCaptor.getValue();
        assertEquals("West", savedRegion.getName());

        // Verify lineage written for each entity (4 entities)
        verify(lineageService, atLeast(4)).writeLineage(any(), anyString(), any(), any(), any(), anyString());

        // Verify job stats
        verify(ingestionJobRepository).save(job);
        assertEquals(4, job.getTotalLoaded());
    }

    // =========================================================================
    // Test: PASS 2 order references PASS 1 entity UUIDs
    // =========================================================================

    @Test
    void testPass2_OrderReferencesPass1EntityUUIDs() {
        // Record with customer and order data
        Map<String, String> recordData = new LinkedHashMap<>();
        recordData.put("customer.name", "John Doe");
        recordData.put("customer.email", "john@test.com");
        recordData.put("order.orderDate", "2024-01-15");
        recordData.put("order.totalAmount", "150.00");
        recordData.put("order.shipMode", "Standard");

        setupFullCustomerProductRecord();
        mockStagedRecords(recordData);

        canonicalLoadService.loadCanonical(jobId);

        // Customer should be saved
        verify(canonicalCustomerRepository).save(customerCaptor.capture());
        UUID customerId = customerCaptor.getValue().getId();
        assertNotNull(customerId);

        // Order should be saved with reference to the customer
        verify(canonicalOrderRepository).save(orderCaptor.capture());
        CanonicalOrder savedOrder = orderCaptor.getValue();
        assertNotNull(savedOrder.getCustomer());
        assertEquals(customerId, savedOrder.getCustomer().getId());
        assertEquals(LocalDate.of(2024, 1, 15), savedOrder.getOrderDate());
        assertEquals(0, new BigDecimal("150.00").compareTo(savedOrder.getTotalAmount()));
        assertEquals(source, savedOrder.getSource());
        assertEquals(job, savedOrder.getJob());

        // Job stats: 2 entities (customer + order)
        assertEquals(2, job.getTotalLoaded());
    }

    // =========================================================================
    // Test: PASS 3 order_line_item references PASS 1-2 UUIDs
    // =========================================================================

    @Test
    void testPass3_OrderLineItemReferencesPass1And2UUIDs() {
        // Record with customer, product, order, and line item data
        Map<String, String> recordData = new LinkedHashMap<>();
        recordData.put("customer.name", "John Doe");
        recordData.put("customer.email", "john@test.com");
        recordData.put("product.sku", "PROD-001");
        recordData.put("product.name", "Widget");
        recordData.put("order.orderDate", "2024-01-15");
        recordData.put("order.totalAmount", "150.00");
        recordData.put("order_line_item.quantity", "3");
        recordData.put("order_line_item.unitPrice", "50.00");
        recordData.put("order_line_item.lineTotal", "150.00");

        setupFullCustomerProductRecord();
        mockStagedRecords(recordData);

        canonicalLoadService.loadCanonical(jobId);

        // Customer created
        verify(canonicalCustomerRepository).save(customerCaptor.capture());
        UUID customerId = customerCaptor.getValue().getId();

        // Product created
        verify(canonicalProductRepository).save(productCaptor.capture());
        UUID productId = productCaptor.getValue().getId();

        // Order created
        verify(canonicalOrderRepository).save(orderCaptor.capture());
        UUID orderId = orderCaptor.getValue().getId();

        // Verify line item references product and order
        verify(canonicalOrderLineItemRepository).save(lineItemCaptor.capture());
        CanonicalOrderLineItem savedLineItem = lineItemCaptor.getValue();
        assertNotNull(savedLineItem.getOrder());
        assertEquals(orderId, savedLineItem.getOrder().getId());
        assertNotNull(savedLineItem.getProduct());
        assertEquals(productId, savedLineItem.getProduct().getId());
        assertEquals(Integer.valueOf(3), savedLineItem.getQuantity());
        assertEquals(0, new BigDecimal("150.00").compareTo(savedLineItem.getLineTotal()));

        // Job stats: 4 entities (customer + product + order + line_item)
        assertEquals(4, job.getTotalLoaded());
    }

    // =========================================================================
    // Test: Rejected records excluded from loading
    // =========================================================================

    @Test
    void testRejectedRecordsExcluded() {
        QualityRun run = new QualityRun();
        run.setId(UUID.randomUUID());

        StagedRecord rejectedRecord = new StagedRecord();
        rejectedRecord.setId(UUID.randomUUID());

        RejectedRecord rejected = new RejectedRecord();
        rejected.setRun(run);
        rejected.setStagedRecord(rejectedRecord);

        StagedRecord goodRecord = new StagedRecord();
        goodRecord.setId(UUID.randomUUID());
        goodRecord.setJob(job);
        goodRecord.setSource(source);

        when(qualityRunRepository.findTopByJobIdOrderByRunTimestampDesc(jobId))
                .thenReturn(Optional.of(run));
        when(rejectedRecordRepository.findByRunId(run.getId()))
                .thenReturn(List.of(rejected));
        when(ingestionJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fieldMappingRepository.findBySourceId(source.getId())).thenReturn(List.of());
        when(qualityScoreRepository.findByJobId(jobId)).thenReturn(Optional.empty());
        when(conflictRecordRepository.count()).thenReturn(0L);

        // Staged records: rejected first, then good
        when(stagedRecordRepository.findByJobId(eq(jobId), any(PageRequest.class)))
                .thenReturn(List.of(rejectedRecord, goodRecord), Collections.emptyList());

        // Only the good record gets transformed (rejected record is skipped before transform)
        Map<String, String> goodTransform = new LinkedHashMap<>();
        goodTransform.put("customer.name", "John");
        goodTransform.put("customer.email", "john@test.com");
        when(transformationService.transform(same(goodRecord), anyList())).thenReturn(goodTransform);

        mockNoExistingEntities();
        mockSaveReturnsEntityWithId();

        canonicalLoadService.loadCanonical(jobId);

        // Only 1 entity should be loaded (the good record's customer)
        verify(canonicalCustomerRepository, times(1)).save(any(CanonicalCustomer.class));
        assertEquals(1, job.getTotalLoaded());
    }

    // =========================================================================
    // Test: job.totalLoaded and totalConflicted updated
    // =========================================================================

    @Test
    void testJobStatsUpdated() {
        Map<String, String> recordData = new LinkedHashMap<>();
        recordData.put("customer.name", "John");
        recordData.put("customer.email", "john@test.com");

        setupFullCustomerProductRecord();
        mockStagedRecords(recordData);

        when(conflictRecordRepository.count()).thenReturn(3L);

        canonicalLoadService.loadCanonical(jobId);

        verify(ingestionJobRepository).save(job);
        assertEquals(1, job.getTotalLoaded());
        assertEquals(3, job.getTotalConflicted());
    }

    // =========================================================================
    // Test: additional_attributes JSONB populated for unmapped fields
    // =========================================================================

    @Test
    void testAdditionalAttributesPopulated() {
        Map<String, String> recordData = new LinkedHashMap<>();
        recordData.put("customer.name", "John");
        recordData.put("customer.email", "john@test.com");
        recordData.put("customer.additional_attributes", "{\"source_field\":\"custom_value\"}");

        setupFullCustomerProductRecord();
        mockStagedRecords(recordData);

        canonicalLoadService.loadCanonical(jobId);

        verify(canonicalCustomerRepository).save(customerCaptor.capture());
        CanonicalCustomer savedCustomer = customerCaptor.getValue();
        assertEquals("{\"source_field\":\"custom_value\"}", savedCustomer.getAdditionalAttributes());
    }

    // =========================================================================
    // Test: null incoming value does NOT overwrite existing (partial update)
    // =========================================================================

    @Test
    void testNullIncomingValueDoesNotOverwriteExisting() {
        // Setup: existing customer from same source — same-source partial update
        CanonicalCustomer existingCustomer = new CanonicalCustomer();
        existingCustomer.setId(UUID.randomUUID());
        existingCustomer.setName("Existing Name");
        existingCustomer.setEmail("match@test.com");
        existingCustomer.setPhone("555-0100"); // existing phone, not in incoming data
        existingCustomer.setSegment("Corporate");
        existingCustomer.setPrimarySource(source);

        // Incoming data has name and segment, but NO phone (phone should remain unchanged)
        Map<String, String> recordData = new LinkedHashMap<>();
        recordData.put("customer.name", "Updated Name");
        recordData.put("customer.email", "match@test.com"); // same email so business key matches
        recordData.put("customer.segment", "Consumer");

        setupFullCustomerProductRecord();
        mockStagedRecords(recordData);

        // Mock business key lookup to find the existing customer
        when(canonicalCustomerRepository.findByEmail("match@test.com")).thenReturn(Optional.of(existingCustomer));
        when(canonicalCustomerRepository.findByExternalRefsContaining(anyString(), anyString())).thenReturn(Optional.empty());

        canonicalLoadService.loadCanonical(jobId);

        verify(canonicalCustomerRepository, times(1)).save(customerCaptor.capture());
        CanonicalCustomer savedCustomer = customerCaptor.getValue();

        // Name was updated (incoming non-null)
        assertEquals("Updated Name", savedCustomer.getName());
        // Segment was updated (incoming non-null)
        assertEquals("Consumer", savedCustomer.getSegment());
        // Phone was NOT in incoming data → should keep existing value (555-0100)
        assertEquals("555-0100", savedCustomer.getPhone());
    }

    // =========================================================================
    // Test: same-source duplicate → UPDATE (not INSERT)
    // =========================================================================

    @Test
    void testSameSourceDuplicateCallsUpdate() {
        // Existing customer with same source
        CanonicalCustomer existingCustomer = new CanonicalCustomer();
        existingCustomer.setId(UUID.randomUUID());
        existingCustomer.setName("Original Name");
        existingCustomer.setEmail("same@test.com");
        existingCustomer.setPrimarySource(source);

        Map<String, String> recordData = new LinkedHashMap<>();
        recordData.put("customer.name", "Updated Name");
        recordData.put("customer.email", "same@test.com");

        setupFullCustomerProductRecord();
        mockStagedRecords(recordData);

        // Mock external_refs to not match, but business key (email) to find existing
        when(canonicalCustomerRepository.findByEmail("same@test.com")).thenReturn(Optional.of(existingCustomer));

        canonicalLoadService.loadCanonical(jobId);

        // Save should be called exactly once (UPDATE, not two saves)
        verify(canonicalCustomerRepository, times(1)).save(customerCaptor.capture());
        CanonicalCustomer saved = customerCaptor.getValue();
        // Should have updated name
        assertEquals("Updated Name", saved.getName());
        // Should still be the same entity (same ID)
        assertEquals(existingCustomer.getId(), saved.getId());
    }

    // =========================================================================
    // Test: no QualityRun exists → all records pass
    // =========================================================================

    @Test
    void testNoQualityRunAllRecordsPass() {
        Map<String, String> recordData = new LinkedHashMap<>();
        recordData.put("customer.name", "John");
        recordData.put("customer.email", "john@test.com");

        mockNoQualityRun();
        mockJobAndMappings(List.of());
        mockNoExistingEntities();
        mockSaveReturnsEntityWithId();
        mockStagedRecords(recordData);

        canonicalLoadService.loadCanonical(jobId);

        verify(canonicalCustomerRepository, times(1)).save(any(CanonicalCustomer.class));
        assertEquals(1, job.getTotalLoaded());
    }

    // =========================================================================
    // Test: external_refs JSON wrapped correctly
    // =========================================================================

    @Test
    void testExternalRefsWrappedCorrectly() throws Exception {
        Map<String, String> recordData = new LinkedHashMap<>();
        recordData.put("customer.name", "John");
        recordData.put("customer.email", "john@test.com");

        setupFullCustomerProductRecord();
        mockStagedRecords(recordData);

        canonicalLoadService.loadCanonical(jobId);

        verify(canonicalCustomerRepository).save(customerCaptor.capture());
        CanonicalCustomer savedCustomer = customerCaptor.getValue();

        // externalRefs should be valid JSON
        String externalRefs = savedCustomer.getExternalRefs();
        assertNotNull(externalRefs);
        assertTrue(externalRefs.startsWith("{"));
        assertTrue(externalRefs.endsWith("}"));

        // Parse the JSON and verify it contains the expected structure
        @SuppressWarnings("unchecked")
        Map<String, String> refsMap = objectMapper.readValue(externalRefs, Map.class);
        assertTrue(refsMap.containsKey("email"));
        assertEquals("john@test.com", refsMap.get("email"));
    }

    // =========================================================================
    // Test: cross-source with non-conflicting field → LATEST_WINS, no ConflictRecord
    // =========================================================================

    @Test
    void testCrossSourceNonConflictingFieldLatestWins() {
        // Existing customer from a DIFFERENT source
        DataSource otherSource = new DataSource();
        otherSource.setId(UUID.randomUUID());
        otherSource.setName("Other Source");
        otherSource.setTrustScore(new BigDecimal("0.8"));

        CanonicalCustomer existingCustomer = new CanonicalCustomer();
        existingCustomer.setId(UUID.randomUUID());
        existingCustomer.setName("Original Name");
        existingCustomer.setEmail("cross@test.com");
        existingCustomer.setCity("New York");  // city is NOT in FR-06.2 scope
        existingCustomer.setSegment("Consumer");
        existingCustomer.setPrimarySource(otherSource);

        Map<String, String> recordData = new LinkedHashMap<>();
        recordData.put("customer.name", "Original Name");
        recordData.put("customer.email", "cross@test.com");
        recordData.put("customer.city", "Los Angeles"); // non-conflicting field differs

        setupFullCustomerProductRecord();
        mockStagedRecords(recordData);

        // Business key match finds existing
        when(canonicalCustomerRepository.findByEmail("cross@test.com")).thenReturn(Optional.of(existingCustomer));

        // ConflictDetectionService should NOT be called for non-FR-06.2 fields
        when(conflictDetectionService.detectConflicts(any(CanonicalCustomer.class), anyMap(), any(), any()))
                .thenReturn(List.of());

        canonicalLoadService.loadCanonical(jobId);

        verify(canonicalCustomerRepository, atLeast(1)).save(customerCaptor.capture());
        CanonicalCustomer saved = customerCaptor.getValue();

        // city should be "Los Angeles" (LATEST_WINS)
        assertEquals("Los Angeles", saved.getCity());

        // ConflictDetectionService may be called (depends on whether other FR-06.2 fields differ)
        // But city is not in FR-06.2 scope, so no conflict record for it
        verify(conflictRecordRepository, never()).save(any(ConflictRecord.class));
    }

    // =========================================================================
    // Test: cross-source with segment conflict → ConflictDetectionService called
    // =========================================================================

    @Test
    void testCrossSourceSegmentConflictDetectionServiceCalled() {
        // Existing customer from a DIFFERENT source
        DataSource otherSource = new DataSource();
        otherSource.setId(UUID.randomUUID());
        otherSource.setName("Other Source");
        otherSource.setTrustScore(new BigDecimal("0.8"));

        CanonicalCustomer existingCustomer = new CanonicalCustomer();
        existingCustomer.setId(UUID.randomUUID());
        existingCustomer.setName("John");
        existingCustomer.setEmail("conflict@test.com");
        existingCustomer.setSegment("Consumer");  // existing value
        existingCustomer.setPrimarySource(otherSource);

        Map<String, String> recordData = new LinkedHashMap<>();
        recordData.put("customer.name", "John");
        recordData.put("customer.email", "conflict@test.com");
        recordData.put("customer.segment", "Corporate");  // differs from existing

        setupFullCustomerProductRecord();
        mockStagedRecords(recordData);

        when(canonicalCustomerRepository.findByEmail("conflict@test.com")).thenReturn(Optional.of(existingCustomer));

        // ConflictDetectionService should be called with conflicting segment
        when(conflictDetectionService.detectConflicts(
                any(CanonicalCustomer.class),
                argThat(map -> "Corporate".equals(map.get("segment"))),
                eq(source),
                eq(job)
        )).thenReturn(List.of(new ConflictRecord()));

        canonicalLoadService.loadCanonical(jobId);

        // ConflictDetectionService should have been called
        verify(conflictDetectionService).detectConflicts(
                any(CanonicalCustomer.class),
                anyMap(),
                eq(source),
                eq(job));
    }

    // =========================================================================
    // Test: groupByEntityType utility
    // =========================================================================

    @Test
    void testGroupByEntityType() {
        Map<String, String> transformed = new LinkedHashMap<>();
        transformed.put("customer.name", "John");
        transformed.put("customer.email", "john@test.com");
        transformed.put("product.sku", "PROD-001");
        transformed.put("order.totalAmount", "150.00");

        Map<String, Map<String, String>> grouped = canonicalLoadService.groupByEntityType(transformed);

        assertEquals(3, grouped.size());
        assertTrue(grouped.containsKey("customer"));
        assertTrue(grouped.containsKey("product"));
        assertTrue(grouped.containsKey("order"));

        assertEquals("John", grouped.get("customer").get("name"));
        assertEquals("john@test.com", grouped.get("customer").get("email"));
        assertEquals("PROD-001", grouped.get("product").get("sku"));
        assertEquals("150.00", grouped.get("order").get("totalAmount"));
    }

    // =========================================================================
    // Test: snakeToCamel utility
    // =========================================================================

    @Test
    void testSnakeToCamel() {
        assertEquals("totalAmount", CanonicalLoadService.snakeToCamel("total_amount"));
        assertEquals("unitPrice", CanonicalLoadService.snakeToCamel("unit_price"));
        assertEquals("additionalAttributes", CanonicalLoadService.snakeToCamel("additional_attributes"));
        assertEquals("name", CanonicalLoadService.snakeToCamel("name"));
        assertEquals("orderDate", CanonicalLoadService.snakeToCamel("orderDate")); // already camel
        assertEquals("", CanonicalLoadService.snakeToCamel(""));
    }

    // =========================================================================
    // Test: buildExternalRefs with no identifier fields uses business key fallback
    // =========================================================================

    @Test
    void testBuildExternalRefsBusinessKeyFallback() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("name", "John");
        fields.put("email", "john@test.com");

        String externalRefs = canonicalLoadService.buildExternalRefs("customer", fields, source);

        assertNotNull(externalRefs);
        assertTrue(externalRefs.contains("email"));
        assertTrue(externalRefs.contains("john@test.com"));
    }

    // =========================================================================
    // Test: buildExternalRefs with identifier field uses source name as key
    // =========================================================================

    @Test
    void testBuildExternalRefsWithIdentifierField() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("name", "John");
        fields.put("externalId", "EXT-123");  // matches "external" pattern

        String externalRefs = canonicalLoadService.buildExternalRefs("customer", fields, source);

        assertNotNull(externalRefs);
        assertTrue(externalRefs.contains(source.getName()));
        assertTrue(externalRefs.contains("EXT-123"));
    }

    // =========================================================================
    // Test: findExistingEntity uses external_refs then business key
    // =========================================================================

    @Test
    void testFindExistingEntityExternalRefsFirst() {
        CanonicalCustomer mockCustomer = new CanonicalCustomer();
        mockCustomer.setId(UUID.randomUUID());
        mockCustomer.setEmail("found@test.com");

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("name", "Found");
        fields.put("email", "found@test.com");
        fields.put("_externalRefs", "{\"Test Source\":\"EXT-123\"}");

        when(canonicalCustomerRepository.findByExternalRefsContaining("Test Source", "EXT-123"))
                .thenReturn(Optional.of(mockCustomer));

        Object result = canonicalLoadService.findExistingEntity("customer", fields, source);

        assertSame(mockCustomer, result);
        verify(canonicalCustomerRepository).findByExternalRefsContaining("Test Source", "EXT-123");
        // Business key should not be called since external_refs matched
        verify(canonicalCustomerRepository, never()).findByEmail(anyString());
    }

    // =========================================================================
    // Test: cross-source with segment → ConflictRecord created
    // =========================================================================

    @Test
    void testCrossSourceSegmentConflictCreatesConflictRecord() {
        DataSource otherSource = new DataSource();
        otherSource.setId(UUID.randomUUID());
        otherSource.setName("Other Source");
        otherSource.setTrustScore(new BigDecimal("0.8"));

        CanonicalCustomer existingCustomer = new CanonicalCustomer();
        existingCustomer.setId(UUID.randomUUID());
        existingCustomer.setName("John");
        existingCustomer.setEmail("seg@test.com");
        existingCustomer.setSegment("Consumer");  // existing value
        existingCustomer.setPrimarySource(otherSource);

        Map<String, String> recordData = new LinkedHashMap<>();
        recordData.put("customer.name", "John");
        recordData.put("customer.email", "seg@test.com");
        recordData.put("customer.segment", "Corporate");  // different

        setupFullCustomerProductRecord();
        mockStagedRecords(recordData);

        when(canonicalCustomerRepository.findByEmail("seg@test.com")).thenReturn(Optional.of(existingCustomer));

        // Mock conflict detection service to return a conflict
        ConflictRecord conflictRecord = new ConflictRecord();
        conflictRecord.setId(UUID.randomUUID());
        when(conflictDetectionService.detectConflicts(
                any(CanonicalCustomer.class),
                anyMap(),
                eq(source),
                eq(job)
        )).thenReturn(List.of(conflictRecord));

        canonicalLoadService.loadCanonical(jobId);

        // ConflictDetectionService should have been called
        verify(conflictDetectionService).detectConflicts(
                any(CanonicalCustomer.class),
                anyMap(),
                eq(source),
                eq(job));
    }

    // =========================================================================
    // Test: cross-source salesperson territory conflict
    // =========================================================================

    @Test
    void testCrossSourceSalespersonConflict() {
        DataSource otherSource = new DataSource();
        otherSource.setId(UUID.randomUUID());
        otherSource.setName("Other Source");
        otherSource.setTrustScore(new BigDecimal("0.8"));

        CanonicalSalesperson existingSalesperson = new CanonicalSalesperson();
        existingSalesperson.setId(UUID.randomUUID());
        existingSalesperson.setName("Jane");
        existingSalesperson.setEmail("jane@test.com");
        existingSalesperson.setTerritory("North");  // existing value
        existingSalesperson.setPrimarySource(otherSource);

        Map<String, String> recordData = new LinkedHashMap<>();
        recordData.put("salesperson.name", "Jane");
        recordData.put("salesperson.email", "jane@test.com");
        recordData.put("salesperson.territory", "South");  // different

        setupFullCustomerProductRecord();
        mockStagedRecords(recordData);

        when(canonicalSalespersonRepository.findByEmail("jane@test.com")).thenReturn(Optional.of(existingSalesperson));
        when(conflictDetectionService.detectConflicts(
                any(CanonicalSalesperson.class),
                anyMap(),
                eq(source),
                eq(job)
        )).thenReturn(List.of(new ConflictRecord()));

        canonicalLoadService.loadCanonical(jobId);

        verify(conflictDetectionService).detectConflicts(
                any(CanonicalSalesperson.class),
                anyMap(),
                eq(source),
                eq(job));
    }
}
