package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.CanonicalSalesperson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface CanonicalSalespersonRepository extends JpaRepository<CanonicalSalesperson, UUID> {

    Optional<CanonicalSalesperson> findByEmail(String email);

    @Query(value = "SELECT * FROM canonical.salespersons WHERE external_refs @> jsonb_build_object(?1, ?2)", nativeQuery = true)
    Optional<CanonicalSalesperson> findByExternalRefsContaining(String key, String value);
}
