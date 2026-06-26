package com.shreyas.saleslens.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalProductTest {

    @Test
    void testDefaultValues() {
        CanonicalProduct product = new CanonicalProduct();
        assertTrue(product.getActive(), "active should default to true");
        assertFalse(product.getHasConflicts(), "hasConflicts should default to false");
        assertNull(product.getId());
        assertNull(product.getSku());
        assertNull(product.getName());
    }

    @Test
    void testSetAndGetFields() {
        CanonicalProduct product = new CanonicalProduct();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        product.setId(id);
        product.setExternalRefs("{\"source_b\": \"PROD-555\"}");
        product.setSku("SKU-001");
        product.setName("Widget Pro");
        product.setCategory("Electronics");
        product.setSubCategory("Accessories");
        product.setUnitPrice(new BigDecimal("29.9900"));
        product.setCurrency("USD");
        product.setActive(false);
        product.setAdditionalAttributes("{\"color\": \"black\"}");
        product.setQualityScore(new BigDecimal("0.8800"));
        product.setHasConflicts(true);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);

        assertEquals(id, product.getId());
        assertEquals("{\"source_b\": \"PROD-555\"}", product.getExternalRefs());
        assertEquals("SKU-001", product.getSku());
        assertEquals("Widget Pro", product.getName());
        assertEquals("Electronics", product.getCategory());
        assertEquals("Accessories", product.getSubCategory());
        assertEquals(0, new BigDecimal("29.9900").compareTo(product.getUnitPrice()));
        assertEquals("USD", product.getCurrency());
        assertFalse(product.getActive());
        assertEquals("{\"color\": \"black\"}", product.getAdditionalAttributes());
        assertEquals(0, new BigDecimal("0.8800").compareTo(product.getQualityScore()));
        assertTrue(product.getHasConflicts());
        assertEquals(now, product.getCreatedAt());
        assertEquals(now, product.getUpdatedAt());
    }

    @Test
    void testJsonbFieldsAcceptValidJson() {
        CanonicalProduct product = new CanonicalProduct();
        product.setExternalRefs("{\"legacy_id\": 123}");
        product.setAdditionalAttributes("{\"warehouse\": \"A\"}");
        assertDoesNotThrow(() -> {
            String refs = product.getExternalRefs();
            String attrs = product.getAdditionalAttributes();
            assertNotNull(refs);
            assertNotNull(attrs);
        });
    }

    @Test
    void testPrePersistSetsTimestamps() {
        CanonicalProduct product = new CanonicalProduct();
        assertNull(product.getCreatedAt());
        assertNull(product.getUpdatedAt());

        product.onCreate();

        assertNotNull(product.getCreatedAt());
        assertNotNull(product.getUpdatedAt());
    }

    @Test
    void testActiveDefaultsToTrue() {
        CanonicalProduct p1 = new CanonicalProduct();
        assertTrue(p1.getActive());

        CanonicalProduct p2 = new CanonicalProduct();
        p2.setActive(false);
        assertFalse(p2.getActive());

        p2.setActive(true);
        assertTrue(p2.getActive());
    }

    @Test
    void testHasConflictsCanBeToggled() {
        CanonicalProduct product = new CanonicalProduct();
        assertFalse(product.getHasConflicts());
        product.setHasConflicts(true);
        assertTrue(product.getHasConflicts());
        product.setHasConflicts(false);
        assertFalse(product.getHasConflicts());
    }
}
