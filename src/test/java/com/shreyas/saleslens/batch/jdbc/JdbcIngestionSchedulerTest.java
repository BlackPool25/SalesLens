package com.shreyas.saleslens.batch.jdbc;

import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.enums.JobStatus;
import com.shreyas.saleslens.model.enums.SourceType;
import com.shreyas.saleslens.repository.DataSourceRepository;
import com.shreyas.saleslens.repository.IngestionJobRepository;
import com.shreyas.saleslens.service.ingestion.JdbcConnectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcIngestionSchedulerTest {

    @Mock
    private DataSourceRepository dataSourceRepository;
    @Mock
    private IngestionJobRepository ingestionJobRepository;
    @Mock
    private JdbcConnectionService jdbcConnectionService;
    @Mock
    private JobOperator jobOperator;
    @Mock
    private Job jdbcIngestionJob;

    @Captor
    private ArgumentCaptor<JobParameters> jobParamsCaptor;

    private JdbcIngestionScheduler scheduler;

    private void createScheduler() {
        scheduler = new JdbcIngestionScheduler(
                dataSourceRepository, ingestionJobRepository,
                jdbcConnectionService, jobOperator, jdbcIngestionJob);
    }

    private DataSource createSource(SourceType type, boolean active, String cronExpression, Instant lastSyncAt) {
        DataSource source = new DataSource();
        source.setId(UUID.randomUUID());
        source.setSourceType(type);
        source.setActive(active);
        source.setCronExpression(cronExpression);
        source.setLastSyncAt(lastSyncAt);
        source.setCreatedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        return source;
    }

    private IngestionJob stubPendingJob(DataSource source) {
        IngestionJob job = new IngestionJob();
        job.setId(UUID.randomUUID());
        job.setSource(source);
        job.setStatus(JobStatus.PENDING);
        when(ingestionJobRepository.save(any(IngestionJob.class))).thenReturn(job);
        return job;
    }

    // ---------------------------------------------------------------
    // 1) Active source with past-due cron → job is launched
    // ---------------------------------------------------------------
    @Test
    void checkAndScheduleJobs_activeSourceDueCron_launchesJob() throws Exception {
        DataSource source = createSource(
                SourceType.JDBC_POSTGRES, true,
                "0 * * * * *",                        // every minute
                Instant.now().minus(1, ChronoUnit.DAYS));

        when(dataSourceRepository.findBySourceTypeInAndActive(
                List.of(SourceType.JDBC_POSTGRES, SourceType.JDBC_MYSQL), true))
                .thenReturn(List.of(source));

        IngestionJob createdJob = stubPendingJob(source);

        createScheduler();
        scheduler.checkAndScheduleJobs();

        verify(jobOperator).start(eq(jdbcIngestionJob), jobParamsCaptor.capture());

        JobParameters params = jobParamsCaptor.getValue();
        assertThat(params.getString("sourceId")).isEqualTo(source.getId().toString());
        assertThat(params.getString("ingestionJobId")).isEqualTo(createdJob.getId().toString());

        verify(dataSourceRepository).save(source);
        assertThat(source.getLastSyncAt()).isNotNull();
    }

    // ---------------------------------------------------------------
    // 2) Active source with future-only cron → no job
    // ---------------------------------------------------------------
    @Test
    void checkAndScheduleJobs_activeSourceFutureCron_skipsJob() throws Exception {
        DataSource source = createSource(
                SourceType.JDBC_POSTGRES, true,
                "0 0 0 1 1 *",                         // next Jan 1 (future)
                Instant.now().minus(1, ChronoUnit.DAYS));

        when(dataSourceRepository.findBySourceTypeInAndActive(
                List.of(SourceType.JDBC_POSTGRES, SourceType.JDBC_MYSQL), true))
                .thenReturn(List.of(source));

        createScheduler();
        scheduler.checkAndScheduleJobs();

        verify(jobOperator, never()).start(any(Job.class), any());
    }

    // ---------------------------------------------------------------
    // 3) Repository only returns active sources; zero results → no job
    // ---------------------------------------------------------------
    @Test
    void checkAndScheduleJobs_inactiveSource_skipped() throws Exception {
        when(dataSourceRepository.findBySourceTypeInAndActive(
                List.of(SourceType.JDBC_POSTGRES, SourceType.JDBC_MYSQL), true))
                .thenReturn(List.of());

        createScheduler();
        scheduler.checkAndScheduleJobs();

        verify(jobOperator, never()).start(any(Job.class), any());
    }

    // ---------------------------------------------------------------
    // 4) Source with null cron expression → manual trigger only
    // ---------------------------------------------------------------
    @Test
    void checkAndScheduleJobs_nullCron_skipped() throws Exception {
        DataSource source = createSource(
                SourceType.JDBC_POSTGRES, true,
                null,                                  // no cron
                Instant.now().minus(1, ChronoUnit.DAYS));

        when(dataSourceRepository.findBySourceTypeInAndActive(
                List.of(SourceType.JDBC_POSTGRES, SourceType.JDBC_MYSQL), true))
                .thenReturn(List.of(source));

        createScheduler();
        scheduler.checkAndScheduleJobs();

        verify(jobOperator, never()).start(any(Job.class), any());
    }

    // ---------------------------------------------------------------
    // 5) Source with invalid cron expression → error logged, skipped
    // ---------------------------------------------------------------
    @Test
    void checkAndScheduleJobs_invalidCron_loggedAndSkipped() throws Exception {
        DataSource source = createSource(
                SourceType.JDBC_POSTGRES, true,
                "not-a-valid-cron",                     // invalid
                Instant.now().minus(1, ChronoUnit.DAYS));

        when(dataSourceRepository.findBySourceTypeInAndActive(
                List.of(SourceType.JDBC_POSTGRES, SourceType.JDBC_MYSQL), true))
                .thenReturn(List.of(source));

        createScheduler();
        // Should not propagate the exception
        scheduler.checkAndScheduleJobs();

        verify(jobOperator, never()).start(any(Job.class), any());
    }

    // ---------------------------------------------------------------
    // 6) SourceId already in runningSourceIds → skipped (no overlap)
    // ---------------------------------------------------------------
    @Test
    void checkAndScheduleJobs_alreadyRunning_skipped() throws Exception {
        DataSource source = createSource(
                SourceType.JDBC_POSTGRES, true,
                "0 * * * * *",
                Instant.now().minus(1, ChronoUnit.DAYS));

        when(dataSourceRepository.findBySourceTypeInAndActive(
                List.of(SourceType.JDBC_POSTGRES, SourceType.JDBC_MYSQL), true))
                .thenReturn(List.of(source));

        createScheduler();

        // Inject source ID into runningSourceIds via reflection
        @SuppressWarnings("unchecked")
        Set<UUID> runningIds = (Set<UUID>) ReflectionTestUtils.getField(scheduler, "runningSourceIds");
        assertThat(runningIds).isNotNull();
        runningIds.add(source.getId());

        scheduler.checkAndScheduleJobs();

        verify(jobOperator, never()).start(any(Job.class), any());
    }
}
