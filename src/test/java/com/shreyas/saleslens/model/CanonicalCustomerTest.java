package com.shreyas.saleslens.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalCustomerTest {

    @Test
    void testDefaultValues() {
        CanonicalCustomer customer = new CanonicalCustomer();
        assertFalse(customer.getHasConflicts(), "hasConflicts should default to false");
        assertNull(customer.getId());
        assertNull(customer.getName());
        assertNull(customer.getEmail());
        assertNull(customer.getExternalRefs());
        assertNull(customer.getAdditionalAttributes());
    }

    @Test
    void testSetAndGetFields() {
        CanonicalCustomer customer = new CanonicalCustomer();

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        customer.setId(id);
        customer.setExternalRefs("{\"source_a\": \"CUST-001\"}");
        customer.setName("Acme Corp");
        customer.setEmail("contact@acme.com");
        customer.setPhone("+1-555-0100");
        customer.setSegment("Enterprise");
        customer.setRegion("North America");
        customer.setCountry("US");
        customer.setCity("New York");
        customer.setAdditionalAttributes("{\"loyalty_tier\": \"gold\"}");
        customer.setQualityScore(new BigDecimal("0.9500"));
        customer.setHasConflicts(true);
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);

        assertEquals(id, customer.getId());
        assertEquals("{\"source_a\": \"CUST-001\"}", customer.getExternalRefs());
        assertEquals("Acme Corp", customer.getName());
        assertEquals("contact@acme.com", customer.getEmail());
        assertEquals("+1-555-0100", customer.getPhone());
        assertEquals("Enterprise", customer.getSegment());
        assertEquals("North America", customer.getRegion());
        assertEquals("US", customer.getCountry());
        assertEquals("New York", customer.getCity());
        assertEquals("{\"loyalty_tier\": \"gold\"}", customer.getAdditionalAttributes());
        assertEquals(0, new BigDecimal("0.9500").compareTo(customer.getQualityScore()));
        assertTrue(customer.getHasConflicts());
        assertEquals(now, customer.getCreatedAt());
        assertEquals(now, customer.getUpdatedAt());
    }

    @Test
    void testExternalRefsAcceptsValidJson() {
        CanonicalCustomer customer = new CanonicalCustomer();
        String json = """
                {"erp_id": "ERP-001", "crm_id": "CRM-987"}
                """.trim();
        customer.setExternalRefs(json);
        assertTrue(customer.getExternalRefs().contains("erp_id"));
        assertTrue(customer.getExternalRefs().contains("ERP-001"));
    }

    @Test
    void testAdditionalAttributesAcceptsValidJson() {
        CanonicalCustomer customer = new CanonicalCustomer();
        String json = """
                {"vip": true, "since": "2020-01-01"}
                """.trim();
        customer.setAdditionalAttributes(json);
        assertTrue(customer.getAdditionalAttributes().contains("vip"));
    }

    @Test
    void testPrePersistSetsTimestamps() {
        CanonicalCustomer customer = new CanonicalCustomer();
        assertNull(customer.getCreatedAt());
        assertNull(customer.getUpdatedAt());

        customer.onCreate();

        assertNotNull(customer.getCreatedAt());
        assertNotNull(customer.getUpdatedAt());
    }

    @Test
    void testHasConflictsCanBeResetToFalse() {
        CanonicalCustomer customer = new CanonicalCustomer();
        customer.setHasConflicts(true);
        assertTrue(customer.getHasConflicts());
        customer.setHasConflicts(false);
        assertFalse(customer.getHasConflicts());
    }
}
