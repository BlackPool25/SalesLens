package com.shreyas.saleslens.batch.csv;

import com.shreyas.saleslens.model.JobStatus;
import com.shreyas.saleslens.repository.IngestionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CsvIngestionJobListener implements JobExecutionListener {

    private final IngestionJobRepository ingestionJobRepository;

    private static UUID readJobId(JobExecution jobExecution) {
        return UUID.fromString(jobExecution.getJobParameters().getString("ingestionJobId"));
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        UUID ingestionJobId = readJobId(jobExecution);
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
                job.setErrorMessage(jobExecution.getAllFailureExceptions().stream()
                        .findFirst()
                        .map(Throwable::getMessage)
                        .orElse("Unknown error"));
            } else {
                job.setStatus(JobStatus.PARTIAL);
            }
            ingestionJobRepository.save(job);
            log.info("Ingestion job {} finished: status={} read={} wrote={}",
                    ingestionJobId, job.getStatus(), readCount, writeCount);
        });
    }
}
