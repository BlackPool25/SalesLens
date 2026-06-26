package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.CanonicalSalesperson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CanonicalSalespersonRepositoryTest {

    @Mock
    private CanonicalSalespersonRepository repository;

    @Test
    void testFindByEmail() {
        CanonicalSalesperson salesperson = new CanonicalSalesperson();
        salesperson.setEmail("jane@example.com");
        when(repository.findByEmail("jane@example.com")).thenReturn(Optional.of(salesperson));

        Optional<CanonicalSalesperson> result = repository.findByEmail("jane@example.com");
        assertTrue(result.isPresent());
        assertEquals("jane@example.com", result.get().getEmail());
    }

    @Test
    void testFindByEmailNotFound() {
        when(repository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        Optional<CanonicalSalesperson> result = repository.findByEmail("unknown@example.com");
        assertFalse(result.isPresent());
    }

    @Test
    void testFindByExternalRefsContaining() {
        CanonicalSalesperson salesperson = new CanonicalSalesperson();
        when(repository.findByExternalRefsContaining("source", "SP-007"))
                .thenReturn(Optional.of(salesperson));

        Optional<CanonicalSalesperson> result = repository.findByExternalRefsContaining("source", "SP-007");
        assertTrue(result.isPresent());
    }

    @Test
    void testFindByExternalRefsContainingNotFound() {
        when(repository.findByExternalRefsContaining("source", "NONEXISTENT"))
                .thenReturn(Optional.empty());

        Optional<CanonicalSalesperson> result = repository.findByExternalRefsContaining("source", "NONEXISTENT");
        assertFalse(result.isPresent());
    }
}
