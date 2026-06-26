package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.CanonicalCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface CanonicalCustomerRepository extends JpaRepository<CanonicalCustomer, UUID> {

    Optional<CanonicalCustomer> findByEmail(String email);

    Optional<CanonicalCustomer> findByNameAndPhone(String name, String phone);

    @Query(value = "SELECT * FROM canonical.customers WHERE external_refs @> jsonb_build_object(?1, ?2)", nativeQuery = true)
    Optional<CanonicalCustomer> findByExternalRefsContaining(String key, String value);
}
