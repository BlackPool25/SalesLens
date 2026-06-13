package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.SourceSchemaField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SourceSchemaFieldRepository extends JpaRepository<SourceSchemaField, UUID> {

    List<SourceSchemaField> findBySchemaId(UUID schemaId);
}
