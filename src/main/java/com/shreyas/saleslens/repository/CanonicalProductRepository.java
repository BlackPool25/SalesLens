package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.CanonicalProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface CanonicalProductRepository extends JpaRepository<CanonicalProduct, UUID> {

    Optional<CanonicalProduct> findBySku(String sku);

    @Query(value = "SELECT * FROM canonical.products WHERE external_refs @> jsonb_build_object(?1, ?2)", nativeQuery = true)
    Optional<CanonicalProduct> findByExternalRefsContaining(String key, String value);
}
