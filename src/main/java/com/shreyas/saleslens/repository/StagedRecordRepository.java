package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.StagedRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StagedRecordRepository extends JpaRepository<StagedRecord, UUID> {
}
