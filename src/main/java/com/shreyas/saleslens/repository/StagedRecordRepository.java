package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.StagedRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StagedRecordRepository extends JpaRepository<StagedRecord, UUID> {

    @Query("SELECT r FROM StagedRecord r WHERE r.job.id = :jobId")
    List<StagedRecord> findByJobId(@Param("jobId") UUID jobId, Pageable pageable);
}
