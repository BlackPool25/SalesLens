package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.QualityScore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QualityScoreRepository extends JpaRepository<QualityScore, UUID> {
    Optional<QualityScore> findByJobId(UUID jobId);
    List<QualityScore> findBySourceIdOrderByCreatedAtDesc(UUID sourceId);

    Page<QualityScore> findBySourceIdOrderByCreatedAtDesc(UUID sourceId, Pageable pageable);
}
