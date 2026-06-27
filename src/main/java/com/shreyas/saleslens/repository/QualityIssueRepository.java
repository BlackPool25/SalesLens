package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.QualityIssue;
import com.shreyas.saleslens.model.enums.IssueStatus;
import com.shreyas.saleslens.model.enums.QualityDimension;
import com.shreyas.saleslens.model.enums.QualitySeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QualityIssueRepository extends JpaRepository<QualityIssue, UUID> {

    List<QualityIssue> findByRunId(UUID runId);

    List<QualityIssue> findByRunIdAndDimension(UUID runId, QualityDimension dimension);

    List<QualityIssue> findByRunIdAndSeverity(UUID runId, QualitySeverity severity);

    long countByRunIdAndSeverity(UUID runId, QualitySeverity severity);

    long countByRunIdAndDimension(UUID runId, QualityDimension dimension);

    @Query("""
        SELECT qi FROM QualityIssue qi
        WHERE (:sourceId IS NULL OR qi.source.id = :sourceId)
          AND (:severity IS NULL OR qi.severity = :severity)
          AND (:dimension IS NULL OR qi.dimension = :dimension)
          AND (:status IS NULL OR qi.status = :status)
          AND (:jobId IS NULL OR qi.run.job.id = :jobId)
        """)
    Page<QualityIssue> findFiltered(
            @Param("sourceId") UUID sourceId,
            @Param("severity") QualitySeverity severity,
            @Param("dimension") QualityDimension dimension,
            @Param("status") IssueStatus status,
            @Param("jobId") UUID jobId,
            Pageable pageable
    );
}
