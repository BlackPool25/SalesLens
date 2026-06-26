package com.shreyas.saleslens.service.quality;

import com.shreyas.saleslens.model.QualityIssue;
import com.shreyas.saleslens.model.QualityRun;
import com.shreyas.saleslens.model.StagedRecord;
import com.shreyas.saleslens.model.enums.IssueStatus;
import com.shreyas.saleslens.model.enums.QualityDimension;
import com.shreyas.saleslens.model.enums.QualitySeverity;
import com.shreyas.saleslens.repository.StagedRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that a staged record's {@code record_hash} has not already been loaded
 * for the same data source, preventing duplicate record processing.
 * Issues are HIGH severity.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UniquenessChecker implements QualityChecker {

    private final StagedRecordRepository stagedRecordRepository;

    @Override
    public QualityDimension dimension() {
        return QualityDimension.UNIQUENESS;
    }

    @Override
    public List<QualityIssue> check(StagedRecord record, QualityRun run) {
        List<QualityIssue> issues = new ArrayList<>();
        String hash = record.getRecordHash();

        if (hash == null || hash.isBlank()) {
            // No hash → cannot check uniqueness; skip
            return issues;
        }

        // Count how many staged records with the same hash exist for this source
        List<StagedRecord> duplicates = stagedRecordRepository
                .findBySourceIdAndRecordHash(record.getSource().getId(), hash);

        // More than 1 means this record itself + at least one existing duplicate
        long duplicateCount = duplicates.stream()
                .filter(sr -> !sr.getId().equals(record.getId()))
                .count();

        if (duplicateCount > 0) {
            QualityIssue issue = new QualityIssue();
            issue.setRun(run);
            issue.setSource(record.getSource());
            issue.setStagedRecord(record);
            issue.setSourceFieldName("record_hash");
            issue.setRuleCode("UNIQUENESS_DUPLICATE_HASH");
            issue.setSeverity(QualitySeverity.HIGH);
            issue.setDimension(QualityDimension.UNIQUENESS);
            issue.setMessage("Duplicate record detected: hash '" + hash +
                    "' already exists " + duplicateCount + " time(s) for source " + record.getSource().getId());
            issue.setStatus(IssueStatus.OPEN);
            issues.add(issue);
        }

        return issues;
    }
}
