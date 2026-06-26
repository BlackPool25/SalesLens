package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.CanonicalProduct;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CanonicalProductRepositoryTest {

    @Mock
    private CanonicalProductRepository repository;

    @Test
    void testFindBySku() {
        CanonicalProduct product = new CanonicalProduct();
        product.setSku("SKU-001");
        when(repository.findBySku("SKU-001")).thenReturn(Optional.of(product));

        Optional<CanonicalProduct> result = repository.findBySku("SKU-001");
        assertTrue(result.isPresent());
        assertEquals("SKU-001", result.get().getSku());
    }

    @Test
    void testFindBySkuNotFound() {
        when(repository.findBySku("NONEXISTENT")).thenReturn(Optional.empty());

        Optional<CanonicalProduct> result = repository.findBySku("NONEXISTENT");
        assertFalse(result.isPresent());
    }

    @Test
    void testFindByExternalRefsContaining() {
        CanonicalProduct product = new CanonicalProduct();
        when(repository.findByExternalRefsContaining("source", "PROD-555"))
                .thenReturn(Optional.of(product));

        Optional<CanonicalProduct> result = repository.findByExternalRefsContaining("source", "PROD-555");
        assertTrue(result.isPresent());
    }

    @Test
    void testFindByExternalRefsContainingNotFound() {
        when(repository.findByExternalRefsContaining("source", "NONEXISTENT"))
                .thenReturn(Optional.empty());

        Optional<CanonicalProduct> result = repository.findByExternalRefsContaining("source", "NONEXISTENT");
        assertFalse(result.isPresent());
    }
}
