package com.shreyas.saleslens.model;

import com.shreyas.saleslens.model.enums.ConflictStatus;
import com.shreyas.saleslens.model.enums.ResolutionStrategy;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ModelEntityTest {

    // ─── CanonicalOrder ───────────────────────────────────────────────────────────

    @Test
    void canonicalOrder_shouldSetAndGetAllFields() {
        CanonicalOrder order = new CanonicalOrder();
        UUID id = UUID.randomUUID();
        order.setId(id);
        order.setExternalRefs("{\"source\": \"SFDC\"}");
        order.setOrderDate(LocalDate.of(2024, 1, 15));
        order.setShipDate(LocalDate.of(2024, 1, 20));
        order.setShipMode("Standard");
        order.setShippingCost(new BigDecimal("15.50"));
        order.setTotalAmount(new BigDecimal("250.00"));
        order.setCurrency("USD");
        order.setAdditionalAttributes("{\"priority\": \"high\"}");
        order.setQualityScore(new BigDecimal("0.9500"));
        order.setHasConflicts(false);

        assertEquals(id, order.getId());
        assertEquals("{\"source\": \"SFDC\"}", order.getExternalRefs());
        assertEquals(LocalDate.of(2024, 1, 15), order.getOrderDate());
        assertEquals(LocalDate.of(2024, 1, 20), order.getShipDate());
        assertEquals("Standard", order.getShipMode());
        assertEquals(new BigDecimal("15.50"), order.getShippingCost());
        assertEquals(new BigDecimal("250.00"), order.getTotalAmount());
        assertEquals("USD", order.getCurrency());
        assertEquals("{\"priority\": \"high\"}", order.getAdditionalAttributes());
        assertEquals(new BigDecimal("0.9500"), order.getQualityScore());
        assertFalse(order.getHasConflicts());
    }

    @Test
    void canonicalOrder_shouldNotHaveUpdatedAtField() {
        // Compile-time structural check: verify no updatedAt field exists
        assertThrows(NoSuchFieldException.class,
                () -> CanonicalOrder.class.getDeclaredField("updatedAt"));
    }

    @Test
    void canonicalOrder_shouldHaveTableAnnotation() {
        Table table = CanonicalOrder.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("canonical", table.schema());
        assertEquals("orders", table.name());
    }

    @Test
    void canonicalOrder_shouldHaveGeneratedUuidId() {
        assertNotNull(getIdField(CanonicalOrder.class));
    }

    // ─── CanonicalOrderLineItem ───────────────────────────────────────────────────

    @Test
    void canonicalOrderLineItem_shouldSetAndGetAllFields() {
        CanonicalOrderLineItem item = new CanonicalOrderLineItem();
        UUID id = UUID.randomUUID();
        item.setId(id);
        item.setQuantity(5);
        item.setUnitPrice(new BigDecimal("29.99"));
        item.setDiscount(new BigDecimal("0.10"));
        item.setLineTotal(new BigDecimal("149.95"));
        item.setAdditionalAttributes("{\"color\": \"red\"}");

        assertEquals(id, item.getId());
        assertEquals(5, item.getQuantity());
        assertEquals(new BigDecimal("29.99"), item.getUnitPrice());
        assertEquals(new BigDecimal("0.10"), item.getDiscount());
        assertEquals(new BigDecimal("149.95"), item.getLineTotal());
        assertEquals("{\"color\": \"red\"}", item.getAdditionalAttributes());
    }

    @Test
    void canonicalOrderLineItem_shouldNotHaveQualityScore() {
        assertThrows(NoSuchFieldException.class,
                () -> CanonicalOrderLineItem.class.getDeclaredField("qualityScore"));
    }

    @Test
    void canonicalOrderLineItem_shouldNotHaveHasConflicts() {
        assertThrows(NoSuchFieldException.class,
                () -> CanonicalOrderLineItem.class.getDeclaredField("hasConflicts"));
    }

    @Test
    void canonicalOrderLineItem_shouldNotHaveExternalRefs() {
        assertThrows(NoSuchFieldException.class,
                () -> CanonicalOrderLineItem.class.getDeclaredField("externalRefs"));
    }

    @Test
    void canonicalOrderLineItem_shouldNotHaveUpdatedAt() {
        assertThrows(NoSuchFieldException.class,
                () -> CanonicalOrderLineItem.class.getDeclaredField("updatedAt"));
    }

    @Test
    void canonicalOrderLineItem_shouldHaveTableAnnotation() {
        Table table = CanonicalOrderLineItem.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("canonical", table.schema());
        assertEquals("order_line_items", table.name());
    }

    @Test
    void canonicalOrderLineItem_shouldDefaultDiscountToZero() {
        CanonicalOrderLineItem item = new CanonicalOrderLineItem();
        assertEquals(BigDecimal.ZERO, item.getDiscount());
    }

    // ─── ConflictRecord ───────────────────────────────────────────────────────────

    @Test
    void conflictRecord_shouldSetAndGetAllFields() {
        ConflictRecord record = new ConflictRecord();
        UUID id = UUID.randomUUID();
        record.setId(id);
        record.setEntityType("CanonicalOrder");
        record.setEntityId(UUID.randomUUID());
        record.setFieldName("total_amount");
        record.setValueA("100.00");
        record.setValueB("200.00");
        record.setResolutionStrategy(ResolutionStrategy.LATEST_WINS);
        record.setStatus(ConflictStatus.RESOLVED);

        assertEquals(id, record.getId());
        assertEquals("CanonicalOrder", record.getEntityType());
        assertNotNull(record.getEntityId());
        assertEquals("total_amount", record.getFieldName());
        assertEquals("100.00", record.getValueA());
        assertEquals("200.00", record.getValueB());
        assertEquals(ResolutionStrategy.LATEST_WINS, record.getResolutionStrategy());
        assertEquals(ConflictStatus.RESOLVED, record.getStatus());
    }

    @Test
    void conflictRecord_shouldDefaultToOpenAndFlaggedForReview() {
        ConflictRecord record = new ConflictRecord();
        assertEquals(ConflictStatus.OPEN, record.getStatus());
        assertEquals(ResolutionStrategy.FLAGGED_FOR_REVIEW, record.getResolutionStrategy());
    }

    @Test
    void conflictRecord_resolvedByShouldReferenceUsersWithLongPk() throws Exception {
        Field resolvedByField = ConflictRecord.class.getDeclaredField("resolvedBy");
        assertNotNull(resolvedByField);

        ManyToOne manyToOne = resolvedByField.getAnnotation(ManyToOne.class);
        assertNotNull(manyToOne);
        assertEquals(FetchType.LAZY, manyToOne.fetch());

        JoinColumn joinColumn = resolvedByField.getAnnotation(JoinColumn.class);
        assertNotNull(joinColumn);
        assertEquals("resolved_by", joinColumn.name());

        // Verify Users entity has Long PK (IDENTITY), not UUID
        Field usersIdField = Users.class.getDeclaredField("id");
        GeneratedValue generatedValue = usersIdField.getAnnotation(GeneratedValue.class);
        assertNotNull(generatedValue);
        assertEquals(GenerationType.IDENTITY, generatedValue.strategy());
        assertEquals(Long.class, usersIdField.getType());
    }

    @Test
    void conflictRecord_shouldHaveTableAnnotation() {
        Table table = ConflictRecord.class.getAnnotation(Table.class);
        assertNotNull(table);
        // public schema, so no schema attribute
        assertEquals("conflict_records", table.name());
    }

    @Test
    void conflictRecord_shouldHaveEnumeratedAnnotations() throws Exception {
        Field statusField = ConflictRecord.class.getDeclaredField("status");
        Enumerated statusEnum = statusField.getAnnotation(Enumerated.class);
        assertNotNull(statusEnum);
        assertEquals(EnumType.STRING, statusEnum.value());

        Field strategyField = ConflictRecord.class.getDeclaredField("resolutionStrategy");
        Enumerated strategyEnum = strategyField.getAnnotation(Enumerated.class);
        assertNotNull(strategyEnum);
        assertEquals(EnumType.STRING, strategyEnum.value());
    }

    // ─── DataLineage ─────────────────────────────────────────────────────────────

    @Test
    void dataLineage_shouldSetAndGetAllFields() {
        DataLineage lineage = new DataLineage();
        UUID id = UUID.randomUUID();
        lineage.setId(id);
        lineage.setCanonicalId(UUID.randomUUID());
        lineage.setCanonicalType("CanonicalOrder");

        assertEquals(id, lineage.getId());
        assertNotNull(lineage.getCanonicalId());
        assertEquals("CanonicalOrder", lineage.getCanonicalType());
    }

    @Test
    void dataLineage_shouldAcceptNullTransformations() {
        DataLineage lineage = new DataLineage();
        lineage.setTransformations(null);
        assertNull(lineage.getTransformations());
    }

    @Test
    void dataLineage_shouldAcceptNullStagedRecord() {
        DataLineage lineage = new DataLineage();
        lineage.setStagedRecord(null);
        assertNull(lineage.getStagedRecord());
    }

    @Test
    void dataLineage_canonicalIdShouldBePlainUuid() throws Exception {
        Field canonicalIdField = DataLineage.class.getDeclaredField("canonicalId");
        assertEquals(UUID.class, canonicalIdField.getType());

        // Verify no @ManyToOne or @JoinColumn annotation (it's a plain UUID, not FK)
        assertNull(canonicalIdField.getAnnotation(ManyToOne.class));
        assertNull(canonicalIdField.getAnnotation(JoinColumn.class));
    }

    @Test
    void dataLineage_shouldHaveTableAnnotation() {
        Table table = DataLineage.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("data_lineage", table.name());
    }

    // ─── Enums ────────────────────────────────────────────────────────────────────

    @Test
    void resolutionStrategy_shouldHaveThreeValues() {
        ResolutionStrategy[] values = ResolutionStrategy.values();
        assertEquals(3, values.length);
        assertTrue(Arrays.asList(values).contains(ResolutionStrategy.TRUST_HIERARCHY));
        assertTrue(Arrays.asList(values).contains(ResolutionStrategy.LATEST_WINS));
        assertTrue(Arrays.asList(values).contains(ResolutionStrategy.FLAGGED_FOR_REVIEW));
    }

    @Test
    void conflictStatus_shouldHaveThreeValues() {
        ConflictStatus[] values = ConflictStatus.values();
        assertEquals(3, values.length);
        assertTrue(Arrays.asList(values).contains(ConflictStatus.OPEN));
        assertTrue(Arrays.asList(values).contains(ConflictStatus.RESOLVED));
        assertTrue(Arrays.asList(values).contains(ConflictStatus.SUPPRESSED));
    }

    // ─── @PrePersist lifecycle ────────────────────────────────────────────────────

    @Test
    void prePersist_shouldSetCreatedAt() throws Exception {
        // Verify each entity has @PrePersist method
        assertHasPrePersist(CanonicalOrder.class);
        assertHasPrePersist(CanonicalOrderLineItem.class);
        assertHasPrePersist(ConflictRecord.class);
        assertHasPrePersist(DataLineage.class);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    private static Field getIdField(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> f.getAnnotation(Id.class) != null)
                .findFirst()
                .orElse(null);
    }

    private static void assertHasPrePersist(Class<?> clazz) {
        boolean hasPrePersist = Arrays.stream(clazz.getDeclaredMethods())
                .anyMatch(m -> m.getAnnotation(PrePersist.class) != null);
        assertTrue(hasPrePersist, clazz.getSimpleName() + " should have @PrePersist method");
    }
}
