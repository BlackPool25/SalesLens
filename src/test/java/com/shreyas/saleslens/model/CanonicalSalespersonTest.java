package com.shreyas.saleslens.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalSalespersonTest {

    @Test
    void testDefaultValues() {
        CanonicalSalesperson salesperson = new CanonicalSalesperson();
        assertTrue(salesperson.getActive(), "active should default to true");
        assertNull(salesperson.getId());
        assertNull(salesperson.getName());
        assertNull(salesperson.getEmail());
    }

    @Test
    void testSetAndGetFields() {
        CanonicalSalesperson salesperson = new CanonicalSalesperson();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        salesperson.setId(id);
        salesperson.setExternalRefs("{\"source_c\": \"SP-007\"}");
        salesperson.setName("Jane Doe");
        salesperson.setEmail("jane.doe@example.com");
        salesperson.setTeam("North Region");
        salesperson.setTerritory("West Coast");
        salesperson.setRegion("US-West");
        salesperson.setActive(false);
        salesperson.setAdditionalAttributes("{\"hire_date\": \"2023-06-15\"}");
        salesperson.setCreatedAt(now);
        salesperson.setUpdatedAt(now);

        assertEquals(id, salesperson.getId());
        assertEquals("{\"source_c\": \"SP-007\"}", salesperson.getExternalRefs());
        assertEquals("Jane Doe", salesperson.getName());
        assertEquals("jane.doe@example.com", salesperson.getEmail());
        assertEquals("North Region", salesperson.getTeam());
        assertEquals("West Coast", salesperson.getTerritory());
        assertEquals("US-West", salesperson.getRegion());
        assertFalse(salesperson.getActive());
        assertEquals("{\"hire_date\": \"2023-06-15\"}", salesperson.getAdditionalAttributes());
        assertEquals(now, salesperson.getCreatedAt());
        assertEquals(now, salesperson.getUpdatedAt());
    }

    @Test
    void testPrePersistSetsTimestamps() {
        CanonicalSalesperson salesperson = new CanonicalSalesperson();
        assertNull(salesperson.getCreatedAt());
        assertNull(salesperson.getUpdatedAt());

        salesperson.onCreate();

        assertNotNull(salesperson.getCreatedAt());
        assertNotNull(salesperson.getUpdatedAt());
    }

    @Test
    void testDoesNotHaveQualityScore() throws NoSuchMethodException {
        // Verify getQualityScore method does not exist
        assertThrows(NoSuchMethodException.class,
                () -> CanonicalSalesperson.class.getMethod("getQualityScore"));
    }

    @Test
    void testDoesNotHaveHasConflicts() {
        // Verify getHasConflicts and isHasConflicts methods do not exist
        assertThrows(NoSuchMethodException.class,
                () -> CanonicalSalesperson.class.getMethod("getHasConflicts"));
        assertThrows(NoSuchMethodException.class,
                () -> CanonicalSalesperson.class.getMethod("isHasConflicts"));
    }

    @Test
    void testJsonbFieldsAcceptValidJson() {
        CanonicalSalesperson salesperson = new CanonicalSalesperson();
        salesperson.setExternalRefs("{\"hr_id\": \"EMP-456\"}");
        salesperson.setAdditionalAttributes("{\"certified\": true}");
        assertDoesNotThrow(() -> {
            assertNotNull(salesperson.getExternalRefs());
            assertNotNull(salesperson.getAdditionalAttributes());
        });
    }

    @Test
    void testActiveDefaultsAndToggling() {
        CanonicalSalesperson sp = new CanonicalSalesperson();
        assertTrue(sp.getActive());
        sp.setActive(false);
        assertFalse(sp.getActive());
        sp.setActive(true);
        assertTrue(sp.getActive());
    }
}
