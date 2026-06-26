package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.CanonicalRegion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CanonicalRegionRepositoryTest {

    @Mock
    private CanonicalRegionRepository repository;

    @Test
    void testFindByName() {
        CanonicalRegion region = new CanonicalRegion();
        region.setName("North America");
        when(repository.findByName("North America")).thenReturn(Optional.of(region));

        Optional<CanonicalRegion> result = repository.findByName("North America");
        assertTrue(result.isPresent());
        assertEquals("North America", result.get().getName());
    }

    @Test
    void testFindByNameNotFound() {
        when(repository.findByName("Unknown Region")).thenReturn(Optional.empty());

        Optional<CanonicalRegion> result = repository.findByName("Unknown Region");
        assertFalse(result.isPresent());
    }

    @Test
    void testFindByExternalRefsContaining() {
        CanonicalRegion region = new CanonicalRegion();
        when(repository.findByExternalRefsContaining("source", "REG-001"))
                .thenReturn(Optional.of(region));

        Optional<CanonicalRegion> result = repository.findByExternalRefsContaining("source", "REG-001");
        assertTrue(result.isPresent());
    }

    @Test
    void testFindByExternalRefsContainingNotFound() {
        when(repository.findByExternalRefsContaining("source", "NONEXISTENT"))
                .thenReturn(Optional.empty());

        Optional<CanonicalRegion> result = repository.findByExternalRefsContaining("source", "NONEXISTENT");
        assertFalse(result.isPresent());
    }
}
