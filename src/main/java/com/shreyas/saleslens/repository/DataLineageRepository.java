package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.DataLineage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DataLineageRepository extends JpaRepository<DataLineage, UUID> {

    List<DataLineage> findByCanonicalIdOrderByCreatedAtDesc(UUID canonicalId);
}
