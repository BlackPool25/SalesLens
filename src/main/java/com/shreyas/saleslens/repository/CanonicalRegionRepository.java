package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.CanonicalRegion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface CanonicalRegionRepository extends JpaRepository<CanonicalRegion, UUID> {

    Optional<CanonicalRegion> findByName(String name);

    @Query(value = "SELECT * FROM canonical.regions WHERE external_refs @> jsonb_build_object(?1, ?2)", nativeQuery = true)
    Optional<CanonicalRegion> findByExternalRefsContaining(String key, String value);
}
