package com.shreyas.saleslens.service.quality;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.model.DataProfile;
import com.shreyas.saleslens.model.FieldProfile;
import com.shreyas.saleslens.model.QualityIssue;
import com.shreyas.saleslens.model.QualityRun;
import com.shreyas.saleslens.model.enums.IssueStatus;
import com.shreyas.saleslens.model.enums.QualityDimension;
import com.shreyas.saleslens.model.enums.QualitySeverity;
import com.shreyas.saleslens.repository.DataProfileRepository;
import com.shreyas.saleslens.repository.FieldProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Statistical baseline drift detection service.
 * <p>
 * Compares the current batch's FieldProfile statistics against historical baselines
 * from previous ingestion runs. Detects three types of drift:
 * <ul>
 *   <li>Null rate drift ({@link QualityDimension#COMPLETENESS})</li>
 *   <li>Value distribution skew ({@link QualityDimension#VALIDITY})</li>
 *   <li>Range expansion ({@link QualityDimension#ACCURACY})</li>
 * </ul>
 * <p>
 * Requires at least 3 batches of profiling data before activating (cold-start guard).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfilingService {

    private final DataProfileRepository dataProfileRepository;
    private final FieldProfileRepository fieldProfileRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Null rate drift threshold: >20 percentage points from historical average. */
    private static final BigDecimal NULL_RATE_DRIFT_THRESHOLD = new BigDecimal("0.20");

    /** Value distribution skew: >50% new values in top-10 vs historical union. */
    private static final double TOP_VALUES_SKEW_THRESHOLD = 0.5;

    /** Minimum number of profile batches required before drift detection activates. */
    private static final int MIN_BATCHES_FOR_DRIFT = 3;

    /** Standard deviations for range expansion check. */
    private static final int RANGE_SIGMA = 3;

    /**
     * Detect statistical drift between the current batch and historical baselines.
     *
     * @param run      the current QualityRun (issues are associated with this run)
     * @param sourceId the data source to analyze
     * @return list of QualityIssues for detected drift, or empty list if insufficient data
     */
    public List<QualityIssue> detectDrift(QualityRun run, UUID sourceId) {
        List<QualityIssue> issues = new ArrayList<>();

        // 1. Load all available profiles for this source (ordered newest first)
        List<DataProfile> allProfiles = dataProfileRepository.findTop3BySourceIdOrderByCreatedAtDesc(sourceId);

        // 2. Cold-start guard: need at least MIN_BATCHES_FOR_DRIFT batches
        if (allProfiles.size() < MIN_BATCHES_FOR_DRIFT) {
            log.info("Drift detection: only {} profiles for source {}, need {}. Skipping.",
                    allProfiles.size(), sourceId, MIN_BATCHES_FOR_DRIFT);
            return issues;
        }

        // 3. The current batch is the most recent profile
        DataProfile currentProfile = allProfiles.get(0);
        // Historical baselines are the remaining profiles (index 1..n-1)
        List<DataProfile> historicalProfiles = allProfiles.subList(1, allProfiles.size());

        // 4. Load FieldProfiles for current and historical batches
        List<FieldProfile> currentFields = fieldProfileRepository.findByProfileId(currentProfile.getId());
        if (currentFields.isEmpty()) {
            log.warn("Current batch has no field profiles for source {}", sourceId);
            return issues;
        }

        // Group historical field profiles by field name for baseline computation
        Map<String, List<FieldProfile>> historicalFieldsByName = new HashMap<>();
        for (DataProfile histProfile : historicalProfiles) {
            List<FieldProfile> histFields = fieldProfileRepository.findByProfileId(histProfile.getId());
            for (FieldProfile fp : histFields) {
                historicalFieldsByName.computeIfAbsent(fp.getFieldName(), k -> new ArrayList<>()).add(fp);
            }
        }
        if (historicalFieldsByName.isEmpty()) {
            return issues;
        }

        // 5. For each field in the current batch, run drift checks
        for (FieldProfile currentField : currentFields) {
            String fieldName = currentField.getFieldName();
            List<FieldProfile> histFields = historicalFieldsByName.get(fieldName);
            if (histFields == null || histFields.size() < MIN_BATCHES_FOR_DRIFT - 1) {
                // Not enough historical data for this specific field
                continue;
            }

            // --- Null rate drift (COMPLETENESS) ---
            BigDecimal historicalAvgNullRate = computeAverageNullRate(histFields);
            BigDecimal currentNullRate = currentField.getNullRate() != null ? currentField.getNullRate() : BigDecimal.ZERO;
            BigDecimal nullRateDiff = currentNullRate.subtract(historicalAvgNullRate).abs();
            if (nullRateDiff.compareTo(NULL_RATE_DRIFT_THRESHOLD) > 0) {
                issues.add(buildDriftIssue(run, sourceId, fieldName,
                        "COMPLETENESS_NULL_RATE_DRIFT",
                        QualityDimension.COMPLETENESS,
                        QualitySeverity.MEDIUM,
                        String.format(
                                "Field '%s' null rate has shifted from historical avg %.1f%% to %.1f%% (diff: %.1f%%)",
                                fieldName,
                                historicalAvgNullRate.multiply(BigDecimal.valueOf(100)),
                                currentNullRate.multiply(BigDecimal.valueOf(100)),
                                nullRateDiff.multiply(BigDecimal.valueOf(100)))));
            }

            // --- Value distribution skew (VALIDITY) ---
            if (isDistributionSkewed(currentField, histFields)) {
                issues.add(buildDriftIssue(run, sourceId, fieldName,
                        "VALIDITY_VALUE_DISTRIBUTION_DRIFT",
                        QualityDimension.VALIDITY,
                        QualitySeverity.MEDIUM,
                        String.format(
                                "Field '%s' top-10 values have changed significantly from historical baseline",
                                fieldName)));
            }

            // --- Range expansion (ACCURACY) — only for numeric-like types ---
            if (isRangeExpanded(currentField, histFields)) {
                issues.add(buildDriftIssue(run, sourceId, fieldName,
                        "ACCURACY_RANGE_EXPANSION_DRIFT",
                        QualityDimension.ACCURACY,
                        QualitySeverity.MEDIUM,
                        String.format(
                                "Field '%s' min/max range has expanded beyond %d standard deviations of historical baseline",
                                fieldName, RANGE_SIGMA)));
            }
        }

        log.info("Drift detection for source {}: found {} issues", sourceId, issues.size());
        return issues;
    }

    /**
     * Compute the average null rate from a list of historical FieldProfiles.
     */
    private BigDecimal computeAverageNullRate(List<FieldProfile> histFields) {
        if (histFields.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (FieldProfile fp : histFields) {
            BigDecimal nr = fp.getNullRate() != null ? fp.getNullRate() : BigDecimal.ZERO;
            sum = sum.add(nr);
        }
        return sum.divide(BigDecimal.valueOf(histFields.size()), 4, RoundingMode.HALF_UP);
    }

    /**
     * Check if the current batch's top-10 values differ significantly from historical baseline.
     * <p>
     * Skew is detected if more than 50% of the current top-10 values are NOT present
     * in the union of historical top-10 values.
     */
    private boolean isDistributionSkewed(FieldProfile currentField, List<FieldProfile> histFields) {
        Set<String> currentTop10 = parseTopValues(currentField.getTopValues());
        if (currentTop10.isEmpty()) return false;

        // Build union of historical top-10 values
        Set<String> historicalTop10Union = new HashSet<>();
        for (FieldProfile fp : histFields) {
            historicalTop10Union.addAll(parseTopValues(fp.getTopValues()));
        }
        if (historicalTop10Union.isEmpty()) return false;

        // Count how many current top-10 values are NOT in historical union
        long newValues = currentTop10.stream()
                .filter(v -> !historicalTop10Union.contains(v))
                .count();

        return (double) newValues / currentTop10.size() > TOP_VALUES_SKEW_THRESHOLD;
    }

    /**
     * Check if the current batch's numeric range has expanded beyond 3σ of historical baseline.
     * <p>
     * Uses min/max values from FieldProfile, aggregated across historical batches.
     */
    private boolean isRangeExpanded(FieldProfile currentField, List<FieldProfile> histFields) {
        // Collect all min/max numeric values from historical profiles
        List<BigDecimal> historicalValues = new ArrayList<>();
        for (FieldProfile fp : histFields) {
            addIfNumeric(historicalValues, fp.getMinValue());
            addIfNumeric(historicalValues, fp.getMaxValue());
        }

        if (historicalValues.size() < 2) return false;

        // Parse current min/max
        BigDecimal currentMin = parseNumeric(currentField.getMinValue());
        BigDecimal currentMax = parseNumeric(currentField.getMaxValue());
        if (currentMin == null && currentMax == null) return false;

        // Compute mean and stddev of historical values
        double[] values = historicalValues.stream().mapToDouble(BigDecimal::doubleValue).toArray();
        double mean = computeMean(values);
        double stddev = computeStddev(values, mean);

        if (stddev == 0) return false; // No variation in historical data — skip

        double lowerBound = mean - RANGE_SIGMA * stddev;
        double upperBound = mean + RANGE_SIGMA * stddev;

        // Check if current min/max are outside bounds
        if (currentMin != null && currentMin.doubleValue() < lowerBound) return true;
        if (currentMax != null && currentMax.doubleValue() > upperBound) return true;

        return false;
    }

    private double computeMean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private double computeStddev(double[] values, double mean) {
        double sumSqDiff = 0;
        for (double v : values) {
            double diff = v - mean;
            sumSqDiff += diff * diff;
        }
        return Math.sqrt(sumSqDiff / values.length);
    }

    private void addIfNumeric(List<BigDecimal> values, String str) {
        BigDecimal val = parseNumeric(str);
        if (val != null) values.add(val);
    }

    private BigDecimal parseNumeric(String str) {
        if (str == null || str.trim().isEmpty()) return null;
        try {
            return new BigDecimal(str.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parse a JSON array of top values like ["val1","val2","val3"] into a Set.
     */
    private Set<String> parseTopValues(String topValuesJson) {
        if (topValuesJson == null || topValuesJson.trim().isEmpty()) {
            return Collections.emptySet();
        }
        try {
            List<String> list = objectMapper.readValue(topValuesJson, new TypeReference<List<String>>() {});
            return new HashSet<>(list);
        } catch (Exception e) {
            log.warn("Failed to parse top_values JSON: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    private QualityIssue buildDriftIssue(QualityRun run, UUID sourceId, String fieldName,
                                          String ruleCode, QualityDimension dimension,
                                          QualitySeverity severity, String message) {
        QualityIssue issue = new QualityIssue();
        issue.setRun(run);
        // We need a DataSource reference for the source field.
        // The run already has a source reference via run.getSource().
        // But QualityIssue.setSource() expects a DataSource entity.
        // We set source from the run's source reference.
        issue.setSource(run.getSource());
        issue.setStagedRecord(null); // Drift issues are batch-level, not per-record
        issue.setSourceFieldName(fieldName);
        issue.setRuleCode(ruleCode);
        issue.setSeverity(severity);
        issue.setDimension(dimension);
        issue.setMessage(message);
        issue.setStatus(IssueStatus.OPEN);
        return issue;
    }
}
