package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.SourceSchema;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceSchemaRepository extends JpaRepository<SourceSchema, UUID> {

    Optional<SourceSchema> findBySourceIdAndStatus(UUID sourceId, String status);

    List<SourceSchema> findBySourceIdOrderByVersionDesc(UUID sourceId);

    Optional<SourceSchema> findTopBySourceIdOrderByVersionDesc(UUID sourceId);
}
