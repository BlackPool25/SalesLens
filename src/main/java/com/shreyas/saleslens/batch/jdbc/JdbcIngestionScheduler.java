package com.shreyas.saleslens.batch.jdbc;

import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.enums.JobStatus;
import com.shreyas.saleslens.model.enums.SourceType;
import com.shreyas.saleslens.repository.DataSourceRepository;
import com.shreyas.saleslens.repository.IngestionJobRepository;
import com.shreyas.saleslens.service.ingestion.JdbcConnectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class JdbcIngestionScheduler {

    private final DataSourceRepository dataSourceRepository;
    private final IngestionJobRepository ingestionJobRepository;
    @SuppressWarnings("unused")
    private final JdbcConnectionService jdbcConnectionService;
    private final JobOperator jobOperator;
    private final Job jdbcIngestionJob;

    private final Set<UUID> runningSourceIds = ConcurrentHashMap.newKeySet();

    public JdbcIngestionScheduler(
            DataSourceRepository dataSourceRepository,
            IngestionJobRepository ingestionJobRepository,
            JdbcConnectionService jdbcConnectionService,
            JobOperator jobOperator,
            @Qualifier("jdbcIngestionJob") Job jdbcIngestionJob) {
        this.dataSourceRepository = dataSourceRepository;
        this.ingestionJobRepository = ingestionJobRepository;
        this.jdbcConnectionService = jdbcConnectionService;
        this.jobOperator = jobOperator;
        this.jdbcIngestionJob = jdbcIngestionJob;
    }

    @Scheduled(fixedDelayString = "${saleslens.batch.jdbc.poll-interval-ms:60000}")
    public void checkAndScheduleJobs() {
        List<DataSource> activeSources = dataSourceRepository
                .findBySourceTypeInAndActive(List.of(SourceType.JDBC_POSTGRES, SourceType.JDBC_MYSQL), true);

        for (DataSource source : activeSources) {
            UUID sourceId = source.getId();

            if (runningSourceIds.contains(sourceId)) {
                log.debug("JDBC source {} is currently running a job; skipping", sourceId);
                continue;
            }

            String cronExpr = source.getCronExpression();
            if (cronExpr == null || cronExpr.isBlank()) {
                continue;
            }

            CronExpression cron;
            try {
                cron = CronExpression.parse(cronExpr);
            } catch (Exception e) {
                log.error("Invalid cron expression '{}' for source {}: {}", cronExpr, sourceId, e.getMessage());
                continue;
            }

            Instant syncInstant = source.getLastSyncAt();
            if (syncInstant == null) {
                syncInstant = source.getCreatedAt();
            }
            if (syncInstant == null) {
                syncInstant = Instant.EPOCH;
            }

            LocalDateTime lastSync = LocalDateTime.ofInstant(syncInstant, ZoneOffset.UTC);
            LocalDateTime nextRun = cron.next(lastSync);
            if (nextRun != null && !nextRun.isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
                continue;
            }

            runningSourceIds.add(sourceId);
            try {
                IngestionJob job = createPendingJob(source);

                JobParameters params = new JobParametersBuilder()
                        .addString("sourceId", sourceId.toString(), false)
                        .addString("ingestionJobId", job.getId().toString())
                        .toJobParameters();

                jobOperator.start(jdbcIngestionJob, params);

                source.setLastSyncAt(Instant.now());
                dataSourceRepository.save(source);

                log.info("Scheduled JDBC ingestion job {} for source {} (cron: {})",
                        job.getId(), sourceId, cronExpr);
            } catch (Exception e) {
                log.error("Failed to launch scheduled JDBC job for source {}: {}", sourceId, e.getMessage());
            } finally {
                runningSourceIds.remove(sourceId);
            }
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
}
