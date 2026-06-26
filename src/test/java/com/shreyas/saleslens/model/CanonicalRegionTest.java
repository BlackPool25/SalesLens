package com.shreyas.saleslens.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalRegionTest {

    @Test
    void testDefaultValues() {
        CanonicalRegion region = new CanonicalRegion();
        assertNull(region.getId());
        assertNull(region.getName());
        assertNull(region.getCountry());
        assertNull(region.getZone());
    }

    @Test
    void testSetAndGetFields() {
        CanonicalRegion region = new CanonicalRegion();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        region.setId(id);
        region.setName("North America");
        region.setCountry("US");
        region.setZone("NAM");
        region.setCreatedAt(now);

        assertEquals(id, region.getId());
        assertEquals("North America", region.getName());
        assertEquals("US", region.getCountry());
        assertEquals("NAM", region.getZone());
        assertEquals(now, region.getCreatedAt());
    }

    @Test
    void testPrePersistSetsCreatedAt() {
        CanonicalRegion region = new CanonicalRegion();
        assertNull(region.getCreatedAt());

        region.onCreate();

        assertNotNull(region.getCreatedAt());
    }

    @Test
    void testDoesNotHaveQualityScore() {
        assertThrows(NoSuchMethodException.class,
                () -> CanonicalRegion.class.getMethod("getQualityScore"));
    }

    @Test
    void testDoesNotHaveHasConflicts() {
        assertThrows(NoSuchMethodException.class,
                () -> CanonicalRegion.class.getMethod("getHasConflicts"));
        assertThrows(NoSuchMethodException.class,
                () -> CanonicalRegion.class.getMethod("isHasConflicts"));
    }

    @Test
    void testDoesNotHavePrimarySource() {
        assertThrows(NoSuchMethodException.class,
                () -> CanonicalRegion.class.getMethod("getPrimarySource"));
    }

    @Test
    void testDoesNotHaveUpdatedAt() {
        assertThrows(NoSuchMethodException.class,
                () -> CanonicalRegion.class.getMethod("getUpdatedAt"));
    }

    @Test
    void testDoesNotHaveExternalRefs() {
        assertThrows(NoSuchMethodException.class,
                () -> CanonicalRegion.class.getMethod("getExternalRefs"));
    }

    @Test
    void testDoesNotHaveAdditionalAttributes() {
        assertThrows(NoSuchMethodException.class,
                () -> CanonicalRegion.class.getMethod("getAdditionalAttributes"));
    }
}
