package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.RejectedRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RejectedRecordRepository extends JpaRepository<RejectedRecord, UUID> {
    List<RejectedRecord> findByRunId(UUID runId);
}
