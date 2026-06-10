package com.shreyas.saleslens.service.ingestion;

import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.enums.JobStatus;
import com.shreyas.saleslens.repository.DataSourceRepository;
import com.shreyas.saleslens.repository.IngestionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionOrchestrator {

    private final JobOperator jobOperator;
    private final Job csvIngestionJob;
    private final DataSourceRepository dataSourceRepository;
    private final IngestionJobRepository ingestionJobRepository;

    public UUID ingestCsv(MultipartFile file, UUID sourceId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded CSV is empty");
        }
        DataSource source = dataSourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + sourceId));

        IngestionJob job = createPendingJob(source);
        Path tempFile = saveToTemp(file);

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("filePath", tempFile.toString(), false)
                    .addString("ingestionJobId", job.getId().toString())
                    .addString("sourceId", sourceId.toString(), false)
                    .toJobParameters();

            JobExecution execution = jobOperator.start(csvIngestionJob, params);
            log.info("Launched CSV ingestion job {} (status={})", job.getId(), execution.getStatus());
            return job.getId();
        } catch (Exception e) {
            markFailed(job, e.getMessage());
            throw new RuntimeException("Failed to launch ingestion job", e);
        } finally {
            cleanup(tempFile);
        }
    }

    private IngestionJob createPendingJob(DataSource source) {
        IngestionJob job = new IngestionJob();
        job.setSource(source);
        job.setStatus(JobStatus.PENDING);
        job.setTotalRead(0);
        job.setTotalTransformed(0);
        job.setTotalQualityPass(0);
        job.setTotalQualityFail(0);
        job.setTotalLoaded(0);
        job.setTotalConflicted(0);
        return ingestionJobRepository.save(job);
    }

    private void markFailed(IngestionJob job, String message) {
        job.setStatus(JobStatus.FAILED);
        job.setErrorMessage(message);
        job.setCompletedAt(java.time.Instant.now());
        ingestionJobRepository.save(job);
    }

    private Path saveToTemp(MultipartFile file) {
        try {
            Path tempFile = Files.createTempFile("csv-ingestion-", ".csv");
            file.transferTo(tempFile.toFile());
            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save uploaded file", e);
        }
    }

    private void cleanup(Path tempFile) {
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            log.warn("Failed to delete temp file {}: {}", tempFile, e.getMessage());
        }
    }
}
