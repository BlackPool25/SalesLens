package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.ConflictRecord;
import com.shreyas.saleslens.model.enums.ConflictStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ConflictRecordRepository extends JpaRepository<ConflictRecord, UUID> {

    List<ConflictRecord> findByEntityId(UUID entityId);

    Page<ConflictRecord> findByStatus(ConflictStatus status, Pageable pageable);

    Page<ConflictRecord> findByEntityTypeAndFieldName(String entityType, String fieldName, Pageable pageable);

    Page<ConflictRecord> findByFieldName(String fieldName, Pageable pageable);

    @Query("""
        SELECT cr FROM ConflictRecord cr
        WHERE (:entityType IS NULL OR cr.entityType = :entityType)
          AND (:fieldName IS NULL OR cr.fieldName = :fieldName)
          AND (:status IS NULL OR cr.status = :status)
          AND (:sourceId IS NULL OR cr.sourceA.id = :sourceId OR cr.sourceB.id = :sourceId)
        """)
    Page<ConflictRecord> findFiltered(
            @Param("entityType") String entityType,
            @Param("fieldName") String fieldName,
            @Param("status") ConflictStatus status,
            @Param("sourceId") UUID sourceId,
            Pageable pageable);
}
