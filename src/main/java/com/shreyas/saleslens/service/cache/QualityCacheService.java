package com.shreyas.saleslens.service.cache;

import com.shreyas.saleslens.model.QualityScore;
import com.shreyas.saleslens.model.enums.ConflictStatus;
import com.shreyas.saleslens.repository.ConflictRecordRepository;
import com.shreyas.saleslens.repository.QualityScoreRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cache-aware service for quality scores and conflict data.
 * Uses Spring's @Cacheable abstraction backed by Redis.
 */
@Service
@Slf4j
public class QualityCacheService {

    private final QualityScoreRepository qualityScoreRepository;

    @Nullable
    private ConflictRecordRepository conflictRecordRepository;

    public QualityCacheService(QualityScoreRepository qualityScoreRepository) {
        this.qualityScoreRepository = qualityScoreRepository;
    }

    @Autowired(required = false)
    public QualityCacheService(QualityScoreRepository qualityScoreRepository,
                               @Nullable ConflictRecordRepository conflictRecordRepository) {
        this.qualityScoreRepository = qualityScoreRepository;
        this.conflictRecordRepository = conflictRecordRepository;
    }

    /**
     * Retrieves all quality scores for a source, ordered by creation date descending.
     * Result is cached in the "quality-cache" region under key "scores:{sourceId}".
     */
    @Cacheable(value = "quality-cache", key = "'scores:' + #sourceId")
    public List<QualityScore> getQualityScores(UUID sourceId) {
        if (sourceId == null) {
            log.warn("getQualityScores called with null sourceId");
            return List.of();
        }
        log.debug("Fetching quality scores for source {}", sourceId);
        return qualityScoreRepository.findBySourceIdOrderByCreatedAtDesc(sourceId);
    }

    /**
     * Builds and caches a quality summary map for a source.
     * Contains the latest overall score, letter grade, and score count.
     */
    @Cacheable(value = "quality-cache", key = "'summary:' + #sourceId")
    public Map<String, Object> getQualitySummary(UUID sourceId) {
        if (sourceId == null) {
            log.warn("getQualitySummary called with null sourceId");
            return Map.of();
        }
        log.debug("Building quality summary for source {}", sourceId);
        List<QualityScore> scores = qualityScoreRepository.findBySourceIdOrderByCreatedAtDesc(sourceId);
        if (scores.isEmpty()) {
            Map<String, Object> emptySummary = new HashMap<>();
            emptySummary.put("sourceId", sourceId);
            emptySummary.put("scoreOverall", null);
            emptySummary.put("letterGrade", null);
            emptySummary.put("scoreCount", 0);
            return emptySummary;
        }
        QualityScore latest = scores.get(0);
        Map<String, Object> summary = new HashMap<>();
        summary.put("sourceId", sourceId);
        summary.put("scoreOverall", latest.getScoreOverall());
        summary.put("letterGrade", latest.getLetterGrade());
        summary.put("scoreCount", scores.size());
        summary.put("latestScoreId", latest.getId());
        return summary;
    }

    /**
     * Returns the count of open conflicts involving the given source.
     * Result is cached in the "conflict-cache" region under key "conflicts:{sourceId}".
     * Returns 0 if ConflictRecordRepository is not available.
     */
    @Cacheable(value = "conflict-cache", key = "'conflicts:' + #sourceId")
    public long getConflictSummary(UUID sourceId) {
        if (sourceId == null) {
            log.warn("getConflictSummary called with null sourceId");
            return 0L;
        }
        if (conflictRecordRepository == null) {
            log.debug("ConflictRecordRepository not available, returning 0 for source {}", sourceId);
            return 0L;
        }
        log.debug("Counting open conflicts for source {}", sourceId);
        return conflictRecordRepository.findByStatus(ConflictStatus.OPEN, Pageable.unpaged())
                .getContent()
                .stream()
                .filter(cr -> {
                    boolean aMatches = cr.getSourceA().getId().equals(sourceId);
                    boolean bMatches = cr.getSourceB().getId().equals(sourceId);
                    return aMatches || bMatches;
                })
                .count();
    }

    /**
     * Evicts both the scores and summary cache entries for the given source
     * from the "quality-cache" region.
     */
    @Caching(evict = {
            @CacheEvict(value = "quality-cache", key = "'scores:' + #sourceId"),
            @CacheEvict(value = "quality-cache", key = "'summary:' + #sourceId")
    })
    public void evictQualityCache(UUID sourceId) {
        if (sourceId != null) {
            log.debug("Evicted quality-cache entries for source {}", sourceId);
        }
    }

    /**
     * Evicts the conflict summary cache entry for the given source
     * from the "conflict-cache" region.
     */
    @CacheEvict(value = "conflict-cache", key = "'conflicts:' + #sourceId")
    public void evictConflictCache(UUID sourceId) {
        if (sourceId != null) {
            log.debug("Evicted conflict-cache entry for source {}", sourceId);
        }
    }
}
