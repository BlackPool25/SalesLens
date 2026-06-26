package com.shreyas.saleslens.batch.csv;

import com.shreyas.saleslens.model.enums.JobStatus;
import com.shreyas.saleslens.repository.IngestionJobRepository;
import com.shreyas.saleslens.service.inference.SchemaInferenceService;
import com.shreyas.saleslens.service.canonical.CanonicalLoadService;
import com.shreyas.saleslens.service.quality.QualityEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CsvIngestionJobListener implements JobExecutionListener {

    private final IngestionJobRepository ingestionJobRepository;
    private final SchemaInferenceService schemaInferenceService;
    private final CanonicalLoadService canonicalLoadService;
    private final QualityEngineService qualityEngineService;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        UUID ingestionJobId = readJobId(jobExecution);
        if (ingestionJobId == null) {
            log.warn("Job {} launched without ingestionJobId parameter; skipping state reconciliation",
                    jobExecution.getJobInstance().getJobName());
            return;
        }
        ingestionJobRepository.findById(ingestionJobId).ifPresent(job -> {
            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(Instant.now());
            ingestionJobRepository.save(job);
            log.info("Ingestion job {} started", ingestionJobId);
        });
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        UUID ingestionJobId = readJobId(jobExecution);
        if (ingestionJobId == null) {
            log.warn("Job {} completed without ingestionJobId parameter; skipping state reconciliation",
                    jobExecution.getJobInstance().getJobName());
            return;
        }
        ingestionJobRepository.findById(ingestionJobId).ifPresent(job -> {
            long readCount = jobExecution.getStepExecutions().stream()
                    .mapToLong(s -> s.getReadCount())
                    .sum();
            long writeCount = jobExecution.getStepExecutions().stream()
                    .mapToLong(s -> s.getWriteCount())
                    .sum();

            job.setTotalRead((int) readCount);
            job.setTotalTransformed((int) writeCount);
            job.setCompletedAt(Instant.now());

            if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
                job.setStatus(JobStatus.COMPLETED);
            } else if (jobExecution.getStatus() == BatchStatus.FAILED) {
                job.setStatus(JobStatus.FAILED);
                job.setErrorMessage(jobExecution.getFailureExceptions().stream()
                        .findFirst()
                        .map(Throwable::getMessage)
                        .orElse("Unknown error"));
            } else {
                job.setStatus(JobStatus.PARTIAL);
            }
            ingestionJobRepository.save(job);

            if (job.getStatus() == JobStatus.COMPLETED) {
                try {
                    schemaInferenceService.runInference(ingestionJobId);
                } catch (Exception e) {
                    log.warn("Schema inference failed for job {}: {}", ingestionJobId, e.getMessage());
                }

                try {
                    qualityEngineService.runQualityEngine(ingestionJobId);
                } catch (Exception e) {
                    log.error("Quality Engine failed for job {}: {}", ingestionJobId, e.getMessage(), e);
                }

                try {
                    canonicalLoadService.loadCanonical(ingestionJobId);
                } catch (Exception e) {
                    log.error("Canonical load failed for job {}: {}", ingestionJobId, e.getMessage(), e);
                }
            }

            log.info("Ingestion job {} finished: status={} read={} wrote={}",
                    ingestionJobId, job.getStatus(), readCount, writeCount);
        });

        // Clean up the temp CSV file now that the job has completed
        String filePath = jobExecution.getJobParameters().getString("filePath");
        if (filePath != null) {
            try {
                Files.deleteIfExists(Path.of(filePath));
                log.debug("Deleted temp file {}", filePath);
            } catch (IOException e) {
                log.warn("Failed to delete temp file {}: {}", filePath, e.getMessage());
            }
        }
    }

    private static UUID readJobId(JobExecution jobExecution) {
        String value = jobExecution.getJobParameters().getString("ingestionJobId");
        return value == null ? null : UUID.fromString(value);
    }
}

