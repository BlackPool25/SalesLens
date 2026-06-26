package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.QualityRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QualityRunRepository extends JpaRepository<QualityRun, UUID> {
    List<QualityRun> findByJobId(UUID jobId);
    Optional<QualityRun> findTopByJobIdOrderByRunTimestampDesc(UUID jobId);
}
