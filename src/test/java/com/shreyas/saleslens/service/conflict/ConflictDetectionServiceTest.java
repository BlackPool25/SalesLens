package com.shreyas.saleslens.service.conflict;

import com.shreyas.saleslens.model.*;
import com.shreyas.saleslens.model.enums.ConflictStatus;
import com.shreyas.saleslens.model.enums.ResolutionStrategy;
import com.shreyas.saleslens.repository.ConflictRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConflictDetectionServiceTest {

    @Mock
    private ConflictRecordRepository conflictRecordRepository;

    private ConflictDetectionService detectionService;

    private DataSource sourceA;
    private DataSource sourceB;
    private IngestionJob job;
    private UUID entityId;

    @BeforeEach
    void setUp() {
        detectionService = new ConflictDetectionService(conflictRecordRepository);

        sourceA = new DataSource();
        sourceA.setId(UUID.randomUUID());
        sourceA.setName("Source A");
        sourceA.setTrustScore(new BigDecimal("0.90"));

        sourceB = new DataSource();
        sourceB.setId(UUID.randomUUID());
        sourceB.setName("Source B");
        sourceB.setTrustScore(new BigDecimal("0.50"));

        job = new IngestionJob();
        job.setId(UUID.randomUUID());

        entityId = UUID.randomUUID();
    }

    // ========== CUSTOMER TESTS ==========

    @Test
    void testCustomerConflict_TrustGapAboveThreshold_ResolvedByTrustHierarchy() {
        // sourceA trust=0.90, sourceB trust=0.50 → gap=0.40 >= 0.3 → TRUST_HIERARCHY, RESOLVED
        when(conflictRecordRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        CanonicalCustomer customer = createCustomer("Consumer", "West", "a@test.com");

        Map<String, String> incoming = Map.of(
                "segment", "Corporate",
                "region", "West",
                "email", "a@test.com"
        );

        List<ConflictRecord> conflicts = detectionService.detectConflicts(customer, incoming, sourceB, job);

        assertEquals(1, conflicts.size());
        ConflictRecord record = conflicts.get(0);
        assertEquals("segment", record.getFieldName());
        assertEquals("Consumer", record.getValueA());
        assertEquals("Corporate", record.getValueB());
        assertEquals(ResolutionStrategy.TRUST_HIERARCHY, record.getResolutionStrategy());
        assertEquals(ConflictStatus.RESOLVED, record.getStatus());
        assertEquals(sourceA, record.getSourceA());
        assertEquals(sourceB, record.getSourceB());
        assertEquals("CanonicalCustomer", record.getEntityType());
        assertEquals(entityId, record.getEntityId());
    }

    @Test
    void testCustomerConflict_TrustGapBelowThreshold_FlaggedForReview() {
        // sourceA trust=0.50, sourceB trust=0.40 → gap=0.10 < 0.3 → FLAGGED_FOR_REVIEW, OPEN
        sourceA.setTrustScore(new BigDecimal("0.50"));
        sourceB.setTrustScore(new BigDecimal("0.40"));
        when(conflictRecordRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        CanonicalCustomer customer = createCustomer("Consumer", "West", "a@test.com");

        Map<String, String> incoming = Map.of(
                "segment", "Corporate",
                "region", "West",
                "email", "a@test.com"
        );

        List<ConflictRecord> conflicts = detectionService.detectConflicts(customer, incoming, sourceB, job);

        assertEquals(1, conflicts.size());
        ConflictRecord record = conflicts.get(0);
        assertEquals("segment", record.getFieldName());
        assertEquals(ResolutionStrategy.FLAGGED_FOR_REVIEW, record.getResolutionStrategy());
        assertEquals(ConflictStatus.OPEN, record.getStatus());
    }

    // ========== SALESPERSON TESTS ==========

    @Test
    void testSalespersonConflict_TeamDiffers_ConflictCreated() {
        when(conflictRecordRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        CanonicalSalesperson salesperson = createSalesperson("East", "Alpha");

        Map<String, String> incoming = Map.of(
                "territory", "East",
                "team", "Beta"
        );

        List<ConflictRecord> conflicts = detectionService.detectConflicts(salesperson, incoming, sourceB, job);

        assertEquals(1, conflicts.size());
        ConflictRecord record = conflicts.get(0);
        assertEquals("team", record.getFieldName());
        assertEquals("Alpha", record.getValueA());
        assertEquals("Beta", record.getValueB());
    }

    @Test
    void testSalespersonConflict_TerritoryMatches_NoConflict() {
        CanonicalSalesperson salesperson = createSalesperson("East", "Alpha");

        Map<String, String> incoming = Map.of(
                "territory", "East",
                "team", "Alpha"
        );

        List<ConflictRecord> conflicts = detectionService.detectConflicts(salesperson, incoming, sourceB, job);

        assertTrue(conflicts.isEmpty());
        verify(conflictRecordRepository, never()).saveAll(anyList());
    }

    // ========== NULL GUARD TESTS ==========

    @Test
    void testNullGuard_ExistingValueNull_IncomingNonNull_NoConflict() {
        CanonicalCustomer customer = createCustomer("Consumer", "West", null);
        customer.setEmail(null);

        Map<String, String> incoming = Map.of(
                "segment", "Consumer",
                "region", "West",
                "email", "b@test.com"
        );

        List<ConflictRecord> conflicts = detectionService.detectConflicts(customer, incoming, sourceB, job);

        assertTrue(conflicts.isEmpty());
        verify(conflictRecordRepository, never()).saveAll(anyList());
    }

    @Test
    void testNullGuard_BothNull_NoConflict() {
        CanonicalCustomer customer = createCustomer("Consumer", "West", null);
        customer.setEmail(null);

        Map<String, String> incoming = Map.of(
                "segment", "Consumer",
                "region", "West"
        );

        List<ConflictRecord> conflicts = detectionService.detectConflicts(customer, incoming, sourceB, job);

        assertTrue(conflicts.isEmpty());
        verify(conflictRecordRepository, never()).saveAll(anyList());
    }

    @Test
    void testNullGuard_IncomingNull_ExistingNonNull_NoConflict() {
        CanonicalCustomer customer = createCustomer("Consumer", "West", "a@test.com");

        Map<String, String> incoming = Map.of(
                "segment", "Consumer",
                "region", "West"
        );

        List<ConflictRecord> conflicts = detectionService.detectConflicts(customer, incoming, sourceB, job);

        assertTrue(conflicts.isEmpty());
        verify(conflictRecordRepository, never()).saveAll(anyList());
    }

    // ========== PRODUCT PRICE TESTS ==========

    @Test
    void testProductPrice_WithinOnePercent_NoConflict() {
        CanonicalProduct product = createProduct("Furniture", "Chairs", new BigDecimal("10.00"));

        Map<String, String> incoming = Map.of(
                "category", "Furniture",
                "subCategory", "Chairs",
                "unitPrice", "10.05"
        );

        List<ConflictRecord> conflicts = detectionService.detectConflicts(product, incoming, sourceB, job);

        assertTrue(conflicts.isEmpty());
        verify(conflictRecordRepository, never()).saveAll(anyList());
    }

    @Test
    void testProductPrice_AboveOnePercent_ConflictCreated() {
        when(conflictRecordRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        CanonicalProduct product = createProduct("Furniture", "Chairs", new BigDecimal("10.00"));

        Map<String, String> incoming = Map.of(
                "category", "Furniture",
                "subCategory", "Chairs",
                "unitPrice", "10.20"
        );

        List<ConflictRecord> conflicts = detectionService.detectConflicts(product, incoming, sourceB, job);

        assertEquals(1, conflicts.size());
        assertEquals("unitPrice", conflicts.get(0).getFieldName());
        assertEquals("10.00", conflicts.get(0).getValueA());
        assertEquals("10.20", conflicts.get(0).getValueB());
    }

    // ========== ORDER TOTAL TESTS ==========

    @Test
    void testOrderTotal_DiffExactlyOneCent_NoConflict() {
        CanonicalOrder order = createOrder(new BigDecimal("100.00"), "Standard");

        Map<String, String> incoming = Map.of(
                "totalAmount", "100.01",
                "shipMode", "Standard"
        );

        List<ConflictRecord> conflicts = detectionService.detectConflicts(order, incoming, sourceB, job);

        assertTrue(conflicts.isEmpty());
        verify(conflictRecordRepository, never()).saveAll(anyList());
    }

    @Test
    void testOrderTotal_DiffExceedsOneCent_ConflictCreated() {
        when(conflictRecordRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        CanonicalOrder order = createOrder(new BigDecimal("100.00"), "Standard");

        Map<String, String> incoming = Map.of(
                "totalAmount", "100.02",
                "shipMode", "Standard"
        );

        List<ConflictRecord> conflicts = detectionService.detectConflicts(order, incoming, sourceB, job);

        assertEquals(1, conflicts.size());
        assertEquals("totalAmount", conflicts.get(0).getFieldName());
        assertEquals("100.00", conflicts.get(0).getValueA());
        assertEquals("100.02", conflicts.get(0).getValueB());
    }

    // ========== SAME VALUES ==========

    @Test
    void testAllFieldsSame_NoConflicts() {
        CanonicalCustomer customer = createCustomer("Consumer", "West", "a@test.com");

        Map<String, String> incoming = Map.of(
                "segment", "Consumer",
                "region", "West",
                "email", "a@test.com"
        );

        List<ConflictRecord> conflicts = detectionService.detectConflicts(customer, incoming, sourceB, job);

        assertTrue(conflicts.isEmpty());
        verify(conflictRecordRepository, never()).saveAll(anyList());
    }

    // ========== LOW-IMPORTANCE FIELD EDGE CASES ==========

    @Test
    void testLowImportanceField_BothTrustsAboveSeven_LatestWins() {
        sourceA.setTrustScore(new BigDecimal("0.80"));
        sourceB.setTrustScore(new BigDecimal("0.75"));
        when(conflictRecordRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        CanonicalOrder order = createOrder(new BigDecimal("100.00"), "Standard");

        Map<String, String> incoming = Map.of(
                "totalAmount", "100.00",
                "shipMode", "Express"
        );

        List<ConflictRecord> conflicts = detectionService.detectConflicts(order, incoming, sourceB, job);

        assertEquals(1, conflicts.size());
        ConflictRecord record = conflicts.get(0);
        assertEquals("shipMode", record.getFieldName());
        assertEquals(ResolutionStrategy.LATEST_WINS, record.getResolutionStrategy());
        assertEquals(ConflictStatus.RESOLVED, record.getStatus());
    }

    @Test
    void testLowImportanceField_TrustGapAtThreshold_TRUST_HIERARCHY() {
        // trustGap = |0.60 - 0.90| = 0.30 >= 0.3 → TRUST_HIERARCHY
        sourceA.setTrustScore(new BigDecimal("0.60"));
        sourceB.setTrustScore(new BigDecimal("0.90"));
        when(conflictRecordRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        CanonicalOrder order = createOrder(new BigDecimal("100.00"), "Standard");

        Map<String, String> incoming = Map.of(
                "totalAmount", "100.00",
                "shipMode", "Express"
        );

        List<ConflictRecord> conflicts = detectionService.detectConflicts(order, incoming, sourceB, job);

        assertEquals(1, conflicts.size());
        ConflictRecord record = conflicts.get(0);
        assertEquals(ResolutionStrategy.TRUST_HIERARCHY, record.getResolutionStrategy());
        assertEquals(ConflictStatus.RESOLVED, record.getStatus());
    }

    @Test
    void testLowImportanceField_TrustsBelowThreshold_FlaggedForReview() {
        // trustGap = |0.60 - 0.65| = 0.05 < 0.3
        // not both > 0.7 → FLAGGED_FOR_REVIEW
        sourceA.setTrustScore(new BigDecimal("0.60"));
        sourceB.setTrustScore(new BigDecimal("0.65"));
        when(conflictRecordRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        CanonicalOrder order = createOrder(new BigDecimal("100.00"), "Standard");

        Map<String, String> incoming = Map.of(
                "totalAmount", "100.00",
                "shipMode", "Express"
        );

        List<ConflictRecord> conflicts = detectionService.detectConflicts(order, incoming, sourceB, job);

        assertEquals(1, conflicts.size());
        ConflictRecord record = conflicts.get(0);
        assertEquals(ResolutionStrategy.FLAGGED_FOR_REVIEW, record.getResolutionStrategy());
        assertEquals(ConflictStatus.OPEN, record.getStatus());
    }

    // ========== BIGDECIMAL COMPARISON EDGE CASE ==========

    @Test
    void testBigDecimalComparison_030Vs030_Equal() {
        BigDecimal a = new BigDecimal("0.3");
        BigDecimal b = new BigDecimal("0.30");
        assertEquals(0, a.compareTo(b), "0.3 compared to 0.30 should be 0 with compareTo");

        BigDecimal c = new BigDecimal("0.31");
        assertTrue(a.compareTo(c) < 0, "0.3 should be less than 0.31");
        assertTrue(a.compareTo(b) >= 0);
    }

    @Test
    void testTrustGapExactly030_TrustHierarchy() {
        // gap = |0.60 - 0.30| = 0.30 >= 0.3 → TRUST_HIERARCHY
        sourceA.setTrustScore(new BigDecimal("0.60"));
        sourceB.setTrustScore(new BigDecimal("0.30"));
        when(conflictRecordRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        CanonicalCustomer customer = createCustomer("Consumer", "West", "a@test.com");

        Map<String, String> incoming = Map.of(
                "segment", "Corporate",
                "region", "West",
                "email", "a@test.com"
        );

        List<ConflictRecord> conflicts = detectionService.detectConflicts(customer, incoming, sourceB, job);

        assertEquals(1, conflicts.size());
        assertEquals(ConflictStatus.RESOLVED, conflicts.get(0).getStatus());
    }

    // ========== HELPER METHODS ==========

    private CanonicalCustomer createCustomer(String segment, String region, String email) {
        CanonicalCustomer customer = new CanonicalCustomer();
        customer.setId(entityId);
        customer.setSegment(segment);
        customer.setRegion(region);
        customer.setEmail(email != null ? email : "a@test.com");
        customer.setPrimarySource(sourceA);
        return customer;
    }

    private CanonicalSalesperson createSalesperson(String territory, String team) {
        CanonicalSalesperson salesperson = new CanonicalSalesperson();
        salesperson.setId(entityId);
        salesperson.setTerritory(territory);
        salesperson.setTeam(team);
        salesperson.setPrimarySource(sourceA);
        return salesperson;
    }

    private CanonicalProduct createProduct(String category, String subCategory, BigDecimal unitPrice) {
        CanonicalProduct product = new CanonicalProduct();
        product.setId(entityId);
        product.setCategory(category);
        product.setSubCategory(subCategory);
        product.setUnitPrice(unitPrice);
        product.setPrimarySource(sourceA);
        return product;
    }

    private CanonicalOrder createOrder(BigDecimal totalAmount, String shipMode) {
        CanonicalOrder order = new CanonicalOrder();
        order.setId(entityId);
        order.setTotalAmount(totalAmount);
        order.setShipMode(shipMode);
        order.setSource(sourceA);
        return order;
    }
}
