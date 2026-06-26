package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.CanonicalCustomer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CanonicalCustomerRepositoryTest {

    @Mock
    private CanonicalCustomerRepository repository;

    @Test
    void testFindByEmail() {
        CanonicalCustomer customer = new CanonicalCustomer();
        customer.setEmail("test@test.com");
        when(repository.findByEmail("test@test.com")).thenReturn(Optional.of(customer));

        Optional<CanonicalCustomer> result = repository.findByEmail("test@test.com");
        assertTrue(result.isPresent());
        assertEquals("test@test.com", result.get().getEmail());
    }

    @Test
    void testFindByEmailNotFound() {
        when(repository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        Optional<CanonicalCustomer> result = repository.findByEmail("unknown@test.com");
        assertFalse(result.isPresent());
    }

    @Test
    void testFindByNameAndPhone() {
        CanonicalCustomer customer = new CanonicalCustomer();
        customer.setName("John");
        customer.setPhone("555-0100");
        when(repository.findByNameAndPhone("John", "555-0100")).thenReturn(Optional.of(customer));

        Optional<CanonicalCustomer> result = repository.findByNameAndPhone("John", "555-0100");
        assertTrue(result.isPresent());
        assertEquals("John", result.get().getName());
        assertEquals("555-0100", result.get().getPhone());
    }

    @Test
    void testFindByExternalRefsContaining() {
        CanonicalCustomer customer = new CanonicalCustomer();
        when(repository.findByExternalRefsContaining("source", "ERP-001"))
                .thenReturn(Optional.of(customer));

        Optional<CanonicalCustomer> result = repository.findByExternalRefsContaining("source", "ERP-001");
        assertTrue(result.isPresent());
    }

    @Test
    void testFindByExternalRefsContainingNotFound() {
        when(repository.findByExternalRefsContaining("source", "NONEXISTENT"))
                .thenReturn(Optional.empty());

        Optional<CanonicalCustomer> result = repository.findByExternalRefsContaining("source", "NONEXISTENT");
        assertFalse(result.isPresent());
    }
}
