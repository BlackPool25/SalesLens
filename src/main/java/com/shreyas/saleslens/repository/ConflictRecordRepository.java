package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.ConflictRecord;
import com.shreyas.saleslens.model.enums.ConflictStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConflictRecordRepository extends JpaRepository<ConflictRecord, UUID> {

    List<ConflictRecord> findByEntityId(UUID entityId);

    Page<ConflictRecord> findByStatus(ConflictStatus status, Pageable pageable);

    Page<ConflictRecord> findByEntityTypeAndFieldName(String entityType, String fieldName, Pageable pageable);

    Page<ConflictRecord> findByFieldName(String fieldName, Pageable pageable);
}
