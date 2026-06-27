package com.shreyas.saleslens.service.ingestion;

import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.enums.JobStatus;
import com.shreyas.saleslens.model.enums.SourceType;
import com.shreyas.saleslens.repository.DataSourceRepository;
import com.shreyas.saleslens.repository.IngestionJobRepository;
import com.shreyas.saleslens.repository.SourceSchemaRepository;
import com.shreyas.saleslens.repository.StagedRecordRepository;
import com.shreyas.saleslens.service.canonical.CanonicalLoadService;
import com.shreyas.saleslens.service.quality.QualityEngineService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class StreamPipelineScheduler {

    private final DataSourceRepository dataSourceRepository;
    private final StreamIngestionJobManager jobManager;
    private final PipelineCompletionHandler pipelineCompletionHandler;
    private final IngestionJobRepository ingestionJobRepository;
    private final StagedRecordRepository stagedRecordRepository;
    private final SourceSchemaRepository sourceSchemaRepository;
    private final QualityEngineService qualityEngineService;
    private final CanonicalLoadService canonicalLoadService;
    private final MeterRegistry meterRegistry;

    @Value("${saleslens.batch.streaming.window-seconds:30}")
    private int windowSeconds;

    private final Set<UUID> runningSourceIds = ConcurrentHashMap.newKeySet();

    /**
     * Recovers orphaned streaming jobs left PENDING from a prior session.
     * Marks them FAILED so they are not lost without trace but also not retried
     * automatically (records may need manual reprocessing).
     */
    @PostConstruct
    @Transactional
    public void recoverOrphanedWindows() {
        List<IngestionJob> orphaned = ingestionJobRepository.findAll().stream()
                .filter(j -> j.getSource().getSourceType() == SourceType.KAFKA_STREAM
                        && j.getStatus() == JobStatus.PENDING)
                .toList();
        for (IngestionJob job : orphaned) {
            log.warn("Recovering orphaned streaming job {} for source {} from prior session",
                    job.getId(), job.getSource().getId());
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage("Job orphaned by prior shutdown - records may need reprocessing");
            ingestionJobRepository.save(job);
        }
        if (!orphaned.isEmpty()) {
            log.warn("Recovered {} orphaned streaming jobs — check for unprocessed records", orphaned.size());
        }
    }

    /**
     * Polls all active KAFKA_STREAM sources and processes ready windows.
     * Runs on a fixed delay — a new poll cycle starts only after the previous
     * one completes, preventing overlapping execution.
     */
    @Scheduled(fixedDelayString = "${saleslens.batch.streaming.poll-interval-ms:30000}")
    public void processStreamingWindows() {
        List<DataSource> sources = dataSourceRepository.findBySourceTypeAndActive(SourceType.KAFKA_STREAM, true);

        for (DataSource source : sources) {
            try {
                processSourceWindow(source);
            } catch (Exception e) {
                log.error("Failed to process streaming window for source {}: {}", source.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Processes a single source's streaming window:
     * 1. Checks pipeline is not already running for this source
     * 2. Verifies the window exists, has aged enough, and has records
     * 3. Rotates the window and triggers the pipeline (or quality+canonical if schema exists)
     * 4. Verifies pipeline outcomes and finalizes job status
     */
    private void processSourceWindow(DataSource source) {
        UUID sourceId = source.getId();

        // Guard: prevent overlapping pipeline runs for same source
        if (!runningSourceIds.add(sourceId)) {
            log.debug("Pipeline already running for source {}, skipping", sourceId);
            return;
        }

        IngestionJob job = null;

        try {
            // Check if there's an active window
            if (!jobManager.hasActiveWindow(sourceId)) {
                return;
            }

            // Get window state to check age and record count
            var windowState = jobManager.getWindowState(sourceId);
            if (windowState.isEmpty()) {
                return;
            }

            // Check window age against configured threshold
            Instant windowOpened = windowState.get().getWindowOpenedAt();
            if (Duration.between(windowOpened, Instant.now()).getSeconds() < windowSeconds) {
                log.debug("Window for source {} not yet ready (age < {}s)", sourceId, windowSeconds);
                return;
            }

            // Empty window guard — skip rotation if no records accumulated
            long stagedCount = stagedRecordRepository.findAllByJobId(windowState.get().getCurrentJob().getId()).size();
            if (stagedCount == 0) {
                log.debug("Empty window for source {}, not rotating", sourceId);
                return;
            }

            // Record window duration metric
            long windowDurationMs = Duration.between(windowOpened, Instant.now()).toMillis();
            meterRegistry.timer("saleslens.stream.window.duration",
                            "source", sourceId.toString())
                    .record(windowDurationMs, TimeUnit.MILLISECONDS);
            meterRegistry.gauge("saleslens.stream.window.records",
                    stagedCount);

            // Rotate the window — removes from active map and marks closed
            var rotatedJob = jobManager.rotateWindow(sourceId);
            if (rotatedJob.isEmpty()) {
                return;
            }
            job = rotatedJob.get();

            // Mark as RUNNING
            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(Instant.now());
            ingestionJobRepository.save(job);

            // OPTIMIZATION: Check if schema already exists — if so, skip inference
            // This avoids the expensive 500-record sampling + profiling on every 30-second tick
            // (See task-7-verification.txt findings)
            boolean schemaExists = sourceSchemaRepository.findTopBySourceIdOrderByVersionDesc(sourceId).isPresent();

            if (schemaExists) {
                log.debug("Schema already exists for source {}, skipping inference", sourceId);
                // Run quality and canonical directly (skip inference)
                qualityEngineService.runQualityEngine(job.getId());
                canonicalLoadService.loadCanonical(job.getId());
            } else {
                // First window — run full pipeline (bootstrap schema)
                pipelineCompletionHandler.runPipeline(job.getId());
            }

            // Re-read job to check actual outcomes
            // PipelineCompletionHandler swallows exceptions internally, so we must check
            IngestionJob updatedJob = ingestionJobRepository.findById(job.getId()).orElseThrow();
            long finalStagedCount = stagedRecordRepository.findAllByJobId(job.getId()).size();

            if (finalStagedCount > 0 && updatedJob.getTotalTransformed() == 0) {
                log.error("Pipeline silently failed for job {} - 0 records transformed out of {}",
                        job.getId(), finalStagedCount);
                updatedJob.setStatus(JobStatus.FAILED);
                updatedJob.setErrorMessage("Pipeline completed but 0 records transformed");
            } else {
                // Finalize job as COMPLETED
                updatedJob.setTotalRead((int) finalStagedCount);
                updatedJob.setTotalTransformed(updatedJob.getTotalTransformed());
                if (updatedJob.getStatus() != JobStatus.FAILED) {
                    updatedJob.setStatus(JobStatus.COMPLETED);
                }
            }
            updatedJob.setCompletedAt(Instant.now());
            ingestionJobRepository.save(updatedJob);

            log.info("Completed streaming window for source {} — job {} (status={}, records={})",
                    sourceId, job.getId(), updatedJob.getStatus(), finalStagedCount);

        } catch (Exception e) {
            log.error("Pipeline failed for source {}: {}", sourceId, e.getMessage(), e);
            if (job != null) {
                try {
                    job.setStatus(JobStatus.FAILED);
                    job.setErrorMessage(e.getMessage());
                    job.setCompletedAt(Instant.now());
                    ingestionJobRepository.save(job);
                } catch (Exception ex) {
                    log.warn("Could not mark failed job for source {}: {}", sourceId, ex.getMessage());
                }
            }
        } finally {
            runningSourceIds.remove(sourceId);
        }
    }
}
