package com.shreyas.saleslens.service.quality;

import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.QualityIssue;
import com.shreyas.saleslens.model.QualityScore;
import com.shreyas.saleslens.model.enums.QualityDimension;
import com.shreyas.saleslens.model.enums.QualitySeverity;
import com.shreyas.saleslens.repository.QualityScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class QualityScoreService {

    private final QualityScoreRepository qualityScoreRepository;

    @Transactional
    public QualityScore computeAndSaveScores(IngestionJob job, List<QualityIssue> issues, int totalRecords) {
        log.info("Computing quality scores for job {}, total records: {}, total issues: {}",
                job.getId(), totalRecords, issues.size());

        if (totalRecords <= 0) {
            // Safe fallback if no records were processed
            totalRecords = 1;
        }

        BigDecimal scoreCompleteness = computeDimensionScore(issues, QualityDimension.COMPLETENESS, totalRecords);
        BigDecimal scoreValidity     = computeDimensionScore(issues, QualityDimension.VALIDITY,     totalRecords);
        BigDecimal scoreUniqueness   = computeDimensionScore(issues, QualityDimension.UNIQUENESS,   totalRecords);
        BigDecimal scoreConsistency  = computeDimensionScore(issues, QualityDimension.CONSISTENCY,  totalRecords);
        BigDecimal scoreTimeliness   = computeDimensionScore(issues, QualityDimension.TIMELINESS,   totalRecords);
        BigDecimal scoreAccuracy     = computeDimensionScore(issues, QualityDimension.ACCURACY,     totalRecords);

        // Overall Weighted Average:
        // Completeness: 20%
        // Validity: 25%
        // Uniqueness: 20%
        // Consistency: 20%
        // Timeliness: 10%
        // Accuracy: 5%
        BigDecimal weightCompleteness = BigDecimal.valueOf(0.20);
        BigDecimal weightValidity     = BigDecimal.valueOf(0.25);
        BigDecimal weightUniqueness   = BigDecimal.valueOf(0.20);
        BigDecimal weightConsistency  = BigDecimal.valueOf(0.20);
        BigDecimal weightTimeliness   = BigDecimal.valueOf(0.10);
        BigDecimal weightAccuracy     = BigDecimal.valueOf(0.05);

        BigDecimal scoreOverall = scoreCompleteness.multiply(weightCompleteness)
                .add(scoreValidity.multiply(weightValidity))
                .add(scoreUniqueness.multiply(weightUniqueness))
                .add(scoreConsistency.multiply(weightConsistency))
                .add(scoreTimeliness.multiply(weightTimeliness))
                .add(scoreAccuracy.multiply(weightAccuracy))
                .setScale(4, RoundingMode.HALF_UP);

        String letterGrade = determineLetterGrade(scoreOverall);

        // Save or update score entry
        QualityScore qs = qualityScoreRepository.findByJobId(job.getId())
                .orElseGet(() -> {
                    QualityScore newQs = new QualityScore();
                    newQs.setJob(job);
                    newQs.setSource(job.getSource());
                    return newQs;
                });

        qs.setScoreCompleteness(scoreCompleteness);
        qs.setScoreValidity(scoreValidity);
        qs.setScoreUniqueness(scoreUniqueness);
        qs.setScoreConsistency(scoreConsistency);
        qs.setScoreTimeliness(scoreTimeliness);
        qs.setScoreAccuracy(scoreAccuracy);
        qs.setScoreOverall(scoreOverall);
        qs.setLetterGrade(letterGrade);

        return qualityScoreRepository.save(qs);
    }

    private BigDecimal computeDimensionScore(List<QualityIssue> issues, QualityDimension dimension, int totalRecords) {
        long criticalCount = countIssues(issues, dimension, QualitySeverity.CRITICAL);
        long highCount     = countIssues(issues, dimension, QualitySeverity.HIGH);
        long mediumCount   = countIssues(issues, dimension, QualitySeverity.MEDIUM);
        long lowCount      = countIssues(issues, dimension, QualitySeverity.LOW);

        // Formula: Score = 1.0 - (critical * 1.0 + high * 0.5 + medium * 0.2 + low * 0.05) / totalRecords
        double penalty = (criticalCount * 1.0 + highCount * 0.5 + mediumCount * 0.2 + lowCount * 0.05) / totalRecords;
        double scoreVal = 1.0 - penalty;

        // Clamp between 0.0 and 1.0
        if (scoreVal < 0.0) scoreVal = 0.0;
        if (scoreVal > 1.0) scoreVal = 1.0;

        return BigDecimal.valueOf(scoreVal).setScale(4, RoundingMode.HALF_UP);
    }

    private long countIssues(List<QualityIssue> issues, QualityDimension dimension, QualitySeverity severity) {
        return issues.stream()
                .filter(i -> i.getDimension() == dimension && i.getSeverity() == severity)
                .count();
    }

    private String determineLetterGrade(BigDecimal scoreOverall) {
        double score = scoreOverall.doubleValue();
        if (score >= 0.95) return "A";
        if (score >= 0.85) return "B";
        if (score >= 0.70) return "C";
        if (score >= 0.55) return "D";
        return "F";
    }
}
