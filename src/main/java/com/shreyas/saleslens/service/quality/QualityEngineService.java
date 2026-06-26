package com.shreyas.saleslens.service.quality;

import com.shreyas.saleslens.model.*;
import com.shreyas.saleslens.model.enums.JobStatus;
import com.shreyas.saleslens.model.enums.QualitySeverity;
import com.shreyas.saleslens.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QualityEngineService {

    private final List<QualityChecker> checkers;
    private final StagedRecordRepository stagedRecordRepository;
    private final IngestionJobRepository ingestionJobRepository;
    private final QualityRunRepository qualityRunRepository;
    private final QualityIssueRepository qualityIssueRepository;
    private final RejectedRecordRepository rejectedRecordRepository;
    private final QualityScoreService qualityScoreService;
    private final ProfilingService profilingService;

    @Transactional
    public void runQualityEngine(UUID jobId) {
        log.info("Starting Quality Engine for job {}", jobId);

        IngestionJob job = ingestionJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        // 1. Create a QualityRun
        QualityRun run = new QualityRun();
        run.setJob(job);
        run.setSource(job.getSource());
        run.setRunTimestamp(Instant.now());
        run.setFailedRecords(0);
        run = qualityRunRepository.save(run);

        List<QualityIssue> allIssues = new ArrayList<>();
        List<RejectedRecord> rejectedLog = new ArrayList<>();
        int passedCount = 0;
        int failedCount = 0;
        int recordsWithIssuesCount = 0;
        int totalRecords = 0;
        int page = 0;

        List<StagedRecord> records;
        do {
            records = stagedRecordRepository.findByJobId(jobId, PageRequest.of(page, 500));
            totalRecords += records.size();

            for (StagedRecord record : records) {
                List<QualityIssue> recordIssues = new ArrayList<>();

                // Run all checkers on the record
                for (QualityChecker checker : checkers) {
                    try {
                        List<QualityIssue> issues = checker.check(record, run);
                        if (issues != null) {
                            recordIssues.addAll(issues);
                        }
                    } catch (Exception e) {
                        log.error("Checker {} failed on record {}: {}",
                                checker.getClass().getSimpleName(), record.getId(), e.getMessage(), e);
                    }
                }

                boolean hasCritical = recordIssues.stream()
                        .anyMatch(i -> i.getSeverity() == QualitySeverity.CRITICAL);

                if (hasCritical) {
                    failedCount++;
                    // Catalog in rejected_records
                    RejectedRecord rejected = new RejectedRecord();
                    rejected.setRun(run);
                    rejected.setStagedRecord(record);
                    // Combine messages from critical issues as reason
                    String reason = recordIssues.stream()
                            .filter(i -> i.getSeverity() == QualitySeverity.CRITICAL)
                            .map(QualityIssue::getMessage)
                            .reduce((a, b) -> a + "; " + b)
                            .orElse("Critical violation");
                    rejected.setReason(reason);
                    rejectedLog.add(rejected);
                } else {
                    passedCount++;
                }

                if (!recordIssues.isEmpty()) {
                    recordsWithIssuesCount++;
                    allIssues.addAll(recordIssues);
                }
            }

            page++;
        } while (!records.isEmpty());

        // Run statistical baseline drift detection (ProfilingService)
        try {
            List<QualityIssue> driftIssues = profilingService.detectDrift(run, job.getSource().getId());
            if (driftIssues != null && !driftIssues.isEmpty()) {
                allIssues.addAll(driftIssues);
                log.info("ProfilingService: detected {} baseline drift issues", driftIssues.size());
            }
        } catch (Exception e) {
            log.error("ProfilingService drift detection failed: {}", e.getMessage(), e);
        }

        // Save issues
        qualityIssueRepository.saveAll(allIssues);

        // Save rejected records
        rejectedRecordRepository.saveAll(rejectedLog);

        // Update run stats
        run.setTotalRecords(totalRecords);
        run.setFailedRecords(recordsWithIssuesCount);
        qualityRunRepository.save(run);

        // Compute and save quality scores
        qualityScoreService.computeAndSaveScores(job, allIssues, totalRecords);

        // Update job stats
        job.setTotalQualityPass(passedCount);
        job.setTotalQualityFail(failedCount);
        ingestionJobRepository.save(job);

        log.info("Quality Engine completed for job {}: processed={}, issues={}, passed={}, failed={}",
                jobId, totalRecords, allIssues.size(), passedCount, failedCount);
    }
}
