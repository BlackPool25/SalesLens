package com.shreyas.saleslens.service.conflict;

import com.shreyas.saleslens.model.*;
import com.shreyas.saleslens.model.enums.ConflictStatus;
import com.shreyas.saleslens.model.enums.ResolutionStrategy;
import com.shreyas.saleslens.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConflictResolutionServiceTest {

    @Mock
    private ConflictRecordRepository conflictRecordRepository;
    @Mock
    private CanonicalCustomerRepository canonicalCustomerRepository;
    @Mock
    private CanonicalProductRepository canonicalProductRepository;
    @Mock
    private CanonicalSalespersonRepository canonicalSalespersonRepository;
    @Mock
    private CanonicalOrderRepository canonicalOrderRepository;

    private ConflictResolutionService resolutionService;

    private Users resolver;
    private UUID conflictId;
    private UUID entityId;

    @BeforeEach
    void setUp() {
        resolutionService = new ConflictResolutionService(
                conflictRecordRepository,
                canonicalCustomerRepository,
                canonicalProductRepository,
                canonicalSalespersonRepository,
                canonicalOrderRepository
        );

        resolver = new Users();
        resolver.setId(1L);
        resolver.setUsername("admin");

        conflictId = UUID.randomUUID();
        entityId = UUID.randomUUID();
    }

    // ========== RESOLVE TESTS ==========

    @Test
    void testResolveConflict_EntityUpdatedAndConflictResolved() {
        DataSource sourceA = new DataSource();
        sourceA.setId(UUID.randomUUID());
        sourceA.setTrustScore(new BigDecimal("0.90"));

        CanonicalCustomer customer = new CanonicalCustomer();
        customer.setId(entityId);
        customer.setSegment("Consumer");
        customer.setRegion("West");
        customer.setEmail("a@test.com");
        customer.setPrimarySource(sourceA);

        ConflictRecord record = createConflictRecord("CanonicalCustomer", "segment",
                "Consumer", "Corporate", ConflictStatus.OPEN);

        when(conflictRecordRepository.findById(conflictId)).thenReturn(Optional.of(record));
        when(canonicalCustomerRepository.findById(entityId)).thenReturn(Optional.of(customer));
        when(canonicalCustomerRepository.save(any(CanonicalCustomer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(conflictRecordRepository.save(any(ConflictRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<ConflictRecord> result = resolutionService.resolveConflict(conflictId, "Corporate", resolver);

        assertTrue(result.isPresent());
        ConflictRecord resolved = result.get();
        assertEquals(ConflictStatus.RESOLVED, resolved.getStatus());
        assertEquals(resolver, resolved.getResolvedBy());
        assertNotNull(resolved.getResolvedAt());

        // Verify the entity was updated
        assertEquals("Corporate", customer.getSegment());
        verify(canonicalCustomerRepository).save(customer);
    }

    @Test
    void testResolveConflict_AlreadyResolved_NoOp() {
        ConflictRecord record = createConflictRecord("CanonicalCustomer", "segment",
                "Consumer", "Corporate", ConflictStatus.RESOLVED);
        record.setResolvedAt(Instant.now());
        record.setResolvedBy(resolver);

        when(conflictRecordRepository.findById(conflictId)).thenReturn(Optional.of(record));

        Optional<ConflictRecord> result = resolutionService.resolveConflict(conflictId, "Corporate", resolver);

        assertTrue(result.isPresent());
        assertEquals(ConflictStatus.RESOLVED, result.get().getStatus());
        // Entity should NOT be loaded or modified
        verify(canonicalCustomerRepository, never()).findById(any());
        verify(canonicalCustomerRepository, never()).save(any());
    }

    @Test
    void testResolveConflict_NotFound_ReturnsEmpty() {
        when(conflictRecordRepository.findById(conflictId)).thenReturn(Optional.empty());

        Optional<ConflictRecord> result = resolutionService.resolveConflict(conflictId, "Corporate", resolver);

        assertTrue(result.isEmpty());
        verify(canonicalCustomerRepository, never()).findById(any());
        verify(conflictRecordRepository, never()).save(any());
    }

    @Test
    void testResolveConflict_InvalidChosenValue_ThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> resolutionService.resolveConflict(conflictId, "", resolver));
        assertThrows(IllegalArgumentException.class,
                () -> resolutionService.resolveConflict(conflictId, "   ", resolver));
        assertThrows(IllegalArgumentException.class,
                () -> resolutionService.resolveConflict(conflictId, null, resolver));
    }

    // ========== SUPPRESS TESTS ==========

    @Test
    void testSuppressConflict_StatusChanged_EntityNotModified() {
        DataSource sourceA = new DataSource();
        sourceA.setId(UUID.randomUUID());

        CanonicalCustomer customer = new CanonicalCustomer();
        customer.setId(entityId);
        customer.setSegment("Consumer");
        customer.setPrimarySource(sourceA);

        ConflictRecord record = createConflictRecord("CanonicalCustomer", "segment",
                "Consumer", "Corporate", ConflictStatus.OPEN);

        when(conflictRecordRepository.findById(conflictId)).thenReturn(Optional.of(record));
        when(conflictRecordRepository.save(any(ConflictRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<ConflictRecord> result = resolutionService.suppressConflict(conflictId, resolver);

        assertTrue(result.isPresent());
        ConflictRecord suppressed = result.get();
        assertEquals(ConflictStatus.SUPPRESSED, suppressed.getStatus());
        assertEquals(resolver, suppressed.getResolvedBy());
        assertNotNull(suppressed.getResolvedAt());

        // Entity should NOT be loaded or modified
        verify(canonicalCustomerRepository, never()).findById(any());
        verify(canonicalCustomerRepository, never()).save(any());
    }

    @Test
    void testSuppressConflict_NotFound_ReturnsEmpty() {
        when(conflictRecordRepository.findById(conflictId)).thenReturn(Optional.empty());

        Optional<ConflictRecord> result = resolutionService.suppressConflict(conflictId, resolver);

        assertTrue(result.isEmpty());
        verify(conflictRecordRepository, never()).save(any());
    }

    // ========== LLM ADVISORY (getSuggestedResolution) ==========

    @Test
    void testGetSuggestedResolution_NoLLM_ReturnsNull() {
        // chatLanguageModel is null by default (autowired required=false)
        // The method should handle null LLM gracefully and return null
        CompletableFuture<String> future = resolutionService.getSuggestedResolution(conflictId);
        assertNull(future.join());
    }

    // ========== HELPER ==========

    private ConflictRecord createConflictRecord(String entityType, String fieldName,
                                                 String valueA, String valueB,
                                                 ConflictStatus status) {
        DataSource sourceA = new DataSource();
        sourceA.setId(UUID.randomUUID());
        sourceA.setTrustScore(new BigDecimal("0.90"));

        DataSource sourceB = new DataSource();
        sourceB.setId(UUID.randomUUID());
        sourceB.setTrustScore(new BigDecimal("0.50"));

        ConflictRecord record = new ConflictRecord();
        record.setId(conflictId);
        record.setEntityType(entityType);
        record.setEntityId(entityId);
        record.setFieldName(fieldName);
        record.setSourceA(sourceA);
        record.setSourceB(sourceB);
        record.setValueA(valueA);
        record.setValueB(valueB);
        record.setResolutionStrategy(ResolutionStrategy.FLAGGED_FOR_REVIEW);
        record.setStatus(status);
        return record;
    }
}
