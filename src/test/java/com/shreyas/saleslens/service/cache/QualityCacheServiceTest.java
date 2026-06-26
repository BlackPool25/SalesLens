package com.shreyas.saleslens.service.cache;

import com.shreyas.saleslens.model.ConflictRecord;
import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.QualityScore;
import com.shreyas.saleslens.model.enums.ConflictStatus;
import com.shreyas.saleslens.repository.ConflictRecordRepository;
import com.shreyas.saleslens.repository.QualityScoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QualityCacheService.
 * <p>
 * Note: Spring's {@code @Cacheable} / {@code @CacheEvict} annotations require
 * the Spring AOP cache abstraction to take effect. In this pure Mockito test
 * (no Spring context), the annotations are inert — all method invocations flow
 * through the full method body. The tests are structured to verify the service
 * logic correctly, with comments explaining the production caching behavior.
 */
@ExtendWith(MockitoExtension.class)
class QualityCacheServiceTest {

    @Mock
    private QualityScoreRepository qualityScoreRepository;

    @Mock
    private ConflictRecordRepository conflictRecordRepository;

    @InjectMocks
    private QualityCacheService qualityCacheService;

    // ========== getQualityScores ==========

    @Test
    void getQualityScores_nullSourceId_returnsEmptyList() {
        var result = qualityCacheService.getQualityScores(null);

        assertThat(result).isEmpty();
        verifyNoInteractions(qualityScoreRepository);
    }

    @Test
    void getCachedQualityScores_cachesResult() {
        var sourceId = UUID.randomUUID();
        var score = new QualityScore();
        score.setId(UUID.randomUUID());
        score.setScoreOverall(new BigDecimal("0.85"));
        score.setLetterGrade("B");
        when(qualityScoreRepository.findBySourceIdOrderByCreatedAtDesc(sourceId))
                .thenReturn(List.of(score));

        // First call — cache miss in production, hits the repository
        var firstResult = qualityCacheService.getQualityScores(sourceId);
        // Second call — should hit the @Cacheable cache in production
        var secondResult = qualityCacheService.getQualityScores(sourceId);

        assertThat(firstResult).hasSize(1).containsExactly(score);
        assertThat(secondResult).hasSize(1).containsExactly(score);

        // In production (with Spring @Cacheable AOP), times(1) — second call hits the cache.
        // In this pure Mockito test without Spring AOP, both calls flow through:
        verify(qualityScoreRepository, times(2))
                .findBySourceIdOrderByCreatedAtDesc(sourceId);
    }

    @Test
    void getQualityScores_returnsOrderedScores() {
        var sourceId = UUID.randomUUID();
        var score1 = new QualityScore();
        score1.setId(UUID.randomUUID());
        var score2 = new QualityScore();
        score2.setId(UUID.randomUUID());
        when(qualityScoreRepository.findBySourceIdOrderByCreatedAtDesc(sourceId))
                .thenReturn(List.of(score1, score2));

        var result = qualityCacheService.getQualityScores(sourceId);

        assertThat(result).hasSize(2).containsExactly(score1, score2);
        verify(qualityScoreRepository).findBySourceIdOrderByCreatedAtDesc(sourceId);
    }

    // ========== getQualitySummary ==========

    @Test
    void getQualitySummary_nullSourceId_returnsEmptyMap() {
        var result = qualityCacheService.getQualitySummary(null);

        assertThat(result).isEmpty();
        verifyNoInteractions(qualityScoreRepository);
    }

    @Test
    void getQualitySummary_emptyScores_returnsSummaryWithNulls() {
        var sourceId = UUID.randomUUID();
        when(qualityScoreRepository.findBySourceIdOrderByCreatedAtDesc(sourceId))
                .thenReturn(List.of());

        var result = qualityCacheService.getQualitySummary(sourceId);

        assertThat(result)
                .containsEntry("sourceId", sourceId)
                .containsEntry("scoreOverall", null)
                .containsEntry("letterGrade", null)
                .containsEntry("scoreCount", 0);
    }

    @Test
    void getCachedQualitySummary_returnsSummary() {
        var sourceId = UUID.randomUUID();
        var scoreId = UUID.randomUUID();
        var scoreOverall = new BigDecimal("0.75");
        var letterGrade = "C";

        var score = new QualityScore();
        score.setId(scoreId);
        score.setScoreOverall(scoreOverall);
        score.setLetterGrade(letterGrade);
        when(qualityScoreRepository.findBySourceIdOrderByCreatedAtDesc(sourceId))
                .thenReturn(List.of(score));

        // First call
        var result1 = qualityCacheService.getQualitySummary(sourceId);
        // Second call — should hit @Cacheable in production
        var result2 = qualityCacheService.getQualitySummary(sourceId);

        assertThat(result1)
                .containsEntry("sourceId", sourceId)
                .containsEntry("scoreOverall", scoreOverall)
                .containsEntry("letterGrade", letterGrade)
                .containsEntry("scoreCount", 1)
                .containsEntry("latestScoreId", scoreId);
        assertThat(result2).isEqualTo(result1);

        // Without Spring cache AOP, both calls reach the repository:
        verify(qualityScoreRepository, times(2))
                .findBySourceIdOrderByCreatedAtDesc(sourceId);
    }

    // ========== getConflictSummary ==========

    @Test
    void getConflictSummary_nullSourceId_returnsZero() {
        var result = qualityCacheService.getConflictSummary(null);

        assertThat(result).isZero();
        verifyNoInteractions(conflictRecordRepository);
    }

    @Test
    void getConflictSummary_repositoryNotAvailable_returnsZero() {
        // Simulate the @Autowired(required = false) case where the bean is absent.
        // QualityCacheService uses @RequiredArgsConstructor for the final field only;
        // conflictRecordRepository is @Autowired(required=false) and will be null
        // when constructed with just the required repository.
        var serviceWithoutConflictRepo = new QualityCacheService(qualityScoreRepository);

        var result = serviceWithoutConflictRepo.getConflictSummary(UUID.randomUUID());

        assertThat(result).isZero();
        verifyNoInteractions(conflictRecordRepository);
    }

    @Test
    void getCachedConflictSummary_returnsSummary() {
        var sourceId = UUID.randomUUID();
        var cr = mockConflictRecord(sourceId, UUID.randomUUID());

        Page<ConflictRecord> page = new PageImpl<>(List.of(cr));
        when(conflictRecordRepository.findByStatus(ConflictStatus.OPEN, Pageable.unpaged()))
                .thenReturn(page);

        // First call — cache miss in production
        var result1 = qualityCacheService.getConflictSummary(sourceId);
        // Second call — should hit @Cacheable in production
        var result2 = qualityCacheService.getConflictSummary(sourceId);

        assertThat(result1).isEqualTo(1L);
        assertThat(result2).isEqualTo(1L);

        verify(conflictRecordRepository, times(2))
                .findByStatus(ConflictStatus.OPEN, Pageable.unpaged());
    }

    @Test
    void getConflictSummary_filtersBySource_whenSourceBMatches() {
        var sourceId = UUID.randomUUID();
        // ConflictRecord where sourceB.getId() matches, not sourceA
        var cr = mockConflictRecord(UUID.randomUUID(), sourceId);

        Page<ConflictRecord> page = new PageImpl<>(List.of(cr));
        when(conflictRecordRepository.findByStatus(ConflictStatus.OPEN, Pageable.unpaged()))
                .thenReturn(page);

        var result = qualityCacheService.getConflictSummary(sourceId);

        assertThat(result).isEqualTo(1L);
    }

    @Test
    void getConflictSummary_ignoresConflictsForOtherSources() {
        var sourceId = UUID.randomUUID();
        var otherSourceId = UUID.randomUUID();
        var cr = mockConflictRecord(otherSourceId, otherSourceId);

        Page<ConflictRecord> page = new PageImpl<>(List.of(cr));
        when(conflictRecordRepository.findByStatus(ConflictStatus.OPEN, Pageable.unpaged()))
                .thenReturn(page);

        var result = qualityCacheService.getConflictSummary(sourceId);

        assertThat(result).isZero();
    }

    // ========== evictQualityCache ==========

    @Test
    void evictQualityCache_evictsEntries() {
        var sourceId = UUID.randomUUID();
        // @CacheEvict is inactive in pure Mockito, but the method body should not throw
        assertThatCode(() -> qualityCacheService.evictQualityCache(sourceId))
                .doesNotThrowAnyException();
    }

    @Test
    void evictQualityCache_nullSourceId_noException() {
        assertThatCode(() -> qualityCacheService.evictQualityCache(null))
                .doesNotThrowAnyException();
    }

    // ========== evictConflictCache ==========

    @Test
    void evictConflictCache_evictsEntry() {
        var sourceId = UUID.randomUUID();
        assertThatCode(() -> qualityCacheService.evictConflictCache(sourceId))
                .doesNotThrowAnyException();
    }

    @Test
    void evictConflictCache_nullSourceId_noException() {
        assertThatCode(() -> qualityCacheService.evictConflictCache(null))
                .doesNotThrowAnyException();
    }

    // ========== helpers ==========

    /**
     * Creates a mock {@link ConflictRecord} where {@code sourceA.getId()} returns
     * {@code sourceAId} and {@code sourceB.getId()} returns {@code sourceBId}.
     */
    private static ConflictRecord mockConflictRecord(UUID sourceAId, UUID sourceBId) {
        var sourceA = mock(DataSource.class);
        when(sourceA.getId()).thenReturn(sourceAId);

        var sourceB = mock(DataSource.class);
        when(sourceB.getId()).thenReturn(sourceBId);

        var cr = mock(ConflictRecord.class);
        when(cr.getSourceA()).thenReturn(sourceA);
        when(cr.getSourceB()).thenReturn(sourceB);
        return cr;
    }
}
