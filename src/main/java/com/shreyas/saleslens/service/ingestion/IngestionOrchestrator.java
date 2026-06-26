package com.shreyas.saleslens.service.ingestion;

import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.enums.JobStatus;
import com.shreyas.saleslens.model.enums.SourceType;
import com.shreyas.saleslens.repository.DataSourceRepository;
import com.shreyas.saleslens.repository.IngestionJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class IngestionOrchestrator {

    private final JobOperator jobOperator;
    private final Job csvIngestionJob;
    private final Job excelIngestionJob;
    private final Job jdbcIngestionJob;
    private final DataSourceRepository dataSourceRepository;
    private final IngestionJobRepository ingestionJobRepository;

    public IngestionOrchestrator(
            JobOperator jobOperator,
            @Qualifier("csvIngestionJob") Job csvIngestionJob,
            @Qualifier("excelIngestionJob") Job excelIngestionJob,
            @Qualifier("jdbcIngestionJob") Job jdbcIngestionJob,
            DataSourceRepository dataSourceRepository,
            IngestionJobRepository ingestionJobRepository) {
        this.jobOperator = jobOperator;
        this.csvIngestionJob = csvIngestionJob;
        this.excelIngestionJob = excelIngestionJob;
        this.jdbcIngestionJob = jdbcIngestionJob;
        this.dataSourceRepository = dataSourceRepository;
        this.ingestionJobRepository = ingestionJobRepository;
    }

    public UUID ingestCsv(MultipartFile file, UUID sourceId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded CSV is empty");
        }
        DataSource source = dataSourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + sourceId));

        IngestionJob job = createPendingJob(source);
        Path tempFile = saveToTemp(file, ".csv");

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
        }
    }

    public UUID ingestExcel(MultipartFile file, UUID sourceId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded Excel file is empty");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("Only .xlsx files are accepted");
        }
        DataSource source = dataSourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + sourceId));

        IngestionJob job = createPendingJob(source);
        Path tempFile = saveToTemp(file, ".xlsx");

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("filePath", tempFile.toString(), false)
                    .addString("ingestionJobId", job.getId().toString())
                    .addString("sourceId", sourceId.toString(), false)
                    .toJobParameters();

            JobExecution execution = jobOperator.start(excelIngestionJob, params);
            log.info("Launched Excel ingestion job {} (status={})", job.getId(), execution.getStatus());
            return job.getId();
        } catch (Exception e) {
            markFailed(job, e.getMessage());
            throw new RuntimeException("Failed to launch Excel ingestion job", e);
        }
    }

    public UUID triggerJdbcIngestion(UUID sourceId) {
        DataSource source = dataSourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + sourceId));

        if (source.getSourceType() != SourceType.JDBC_POSTGRES && source.getSourceType() != SourceType.JDBC_MYSQL) {
            throw new IllegalArgumentException("Source " + sourceId + " is not a JDBC source");
        }

        if (!source.getActive()) {
            throw new IllegalArgumentException("Source " + sourceId + " is not active");
        }

        IngestionJob job = createPendingJob(source);

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("sourceId", sourceId.toString(), false)
                    .addString("ingestionJobId", job.getId().toString())
                    .toJobParameters();

            jobOperator.start(jdbcIngestionJob, params);

            // Update lastSyncAt to prevent scheduler from immediately re-launching
            source.setLastSyncAt(Instant.now());
            dataSourceRepository.save(source);

            log.info("Launched JDBC ingestion job {} for source {}", job.getId(), sourceId);
            return job.getId();
        } catch (Exception e) {
            markFailed(job, e.getMessage());
            throw new RuntimeException("Failed to launch JDBC ingestion job", e);
        }
    }

    IngestionJob createPendingJob(DataSource source) {
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

    private Path saveToTemp(MultipartFile file, String suffix) {
        try {
            Path tempFile = Files.createTempFile("ingestion-", suffix);
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
