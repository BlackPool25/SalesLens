package com.shreyas.saleslens.service.ingestion;

import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.SourceSchema;
import com.shreyas.saleslens.model.StagedRecord;
import com.shreyas.saleslens.model.enums.JobStatus;
import com.shreyas.saleslens.model.enums.SourceType;
import com.shreyas.saleslens.repository.DataSourceRepository;
import com.shreyas.saleslens.repository.IngestionJobRepository;
import com.shreyas.saleslens.repository.SourceSchemaRepository;
import com.shreyas.saleslens.repository.StagedRecordRepository;
import com.shreyas.saleslens.service.canonical.CanonicalLoadService;
import com.shreyas.saleslens.service.quality.QualityEngineService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StreamPipelineSchedulerTest {

    @Mock
    private DataSourceRepository dataSourceRepository;
    @Mock
    private StreamIngestionJobManager jobManager;
    @Mock
    private PipelineCompletionHandler pipelineCompletionHandler;
    @Mock
    private IngestionJobRepository ingestionJobRepository;
    @Mock
    private StagedRecordRepository stagedRecordRepository;
    @Mock
    private SourceSchemaRepository sourceSchemaRepository;
    @Mock
    private QualityEngineService qualityEngineService;
    @Mock
    private CanonicalLoadService canonicalLoadService;
    @Mock
    private MeterRegistry meterRegistry;
    @Mock
    private Timer timer;

    private StreamPipelineScheduler scheduler;

    private DataSource source;
    private IngestionJob job;
    private UUID sourceId;
    private UUID jobId;

    @BeforeEach
    void setUp() {
        scheduler = new StreamPipelineScheduler(
                dataSourceRepository, jobManager, pipelineCompletionHandler,
                ingestionJobRepository, stagedRecordRepository, sourceSchemaRepository,
                qualityEngineService, canonicalLoadService, meterRegistry);

        ReflectionTestUtils.setField(scheduler, "windowSeconds", 0);

        sourceId = UUID.randomUUID();
        jobId = UUID.randomUUID();

        source = new DataSource();
        source.setId(sourceId);
        source.setSourceType(SourceType.KAFKA_STREAM);
        source.setActive(true);

        job = new IngestionJob();
        job.setId(jobId);
        job.setSource(source);
        job.setStatus(JobStatus.PENDING);
        job.setTotalRead(0);
        job.setTotalTransformed(0);
        job.setTotalQualityPass(0);
        job.setTotalQualityFail(0);
        job.setTotalLoaded(0);
        job.setTotalConflicted(0);

    }

    @Test
    void processStreamingWindows_happyPath_triggersPipeline() {
        when(meterRegistry.timer(anyString(), anyString(), anyString())).thenReturn(timer);
        var windowState = new StreamIngestionJobManager.WindowState(job);

        when(dataSourceRepository.findBySourceTypeAndActive(SourceType.KAFKA_STREAM, true))
                .thenReturn(List.of(source));
        when(jobManager.hasActiveWindow(sourceId)).thenReturn(true);
        when(jobManager.getWindowState(sourceId)).thenReturn(Optional.of(windowState));
        when(stagedRecordRepository.findAllByJobId(jobId))
                .thenReturn(List.of(new StagedRecord(), new StagedRecord()));
        when(jobManager.rotateWindow(sourceId)).thenReturn(Optional.of(job));
        when(ingestionJobRepository.save(any(IngestionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sourceSchemaRepository.findTopBySourceIdOrderByVersionDesc(sourceId))
                .thenReturn(Optional.empty());

        job.setTotalTransformed(2);
        when(ingestionJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        scheduler.processStreamingWindows();

        verify(pipelineCompletionHandler).runPipeline(jobId);
        verify(qualityEngineService, never()).runQualityEngine(any());
        verify(canonicalLoadService, never()).loadCanonical(any());

        verify(ingestionJobRepository, atLeastOnce()).save(job);
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    void processStreamingWindows_happyPath_schemaExists_skipsInference() {
        when(meterRegistry.timer(anyString(), anyString(), anyString())).thenReturn(timer);
        var windowState = new StreamIngestionJobManager.WindowState(job);

        when(dataSourceRepository.findBySourceTypeAndActive(SourceType.KAFKA_STREAM, true))
                .thenReturn(List.of(source));
        when(jobManager.hasActiveWindow(sourceId)).thenReturn(true);
        when(jobManager.getWindowState(sourceId)).thenReturn(Optional.of(windowState));
        when(stagedRecordRepository.findAllByJobId(jobId))
                .thenReturn(List.of(new StagedRecord(), new StagedRecord()));
        when(jobManager.rotateWindow(sourceId)).thenReturn(Optional.of(job));
        when(ingestionJobRepository.save(any(IngestionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sourceSchemaRepository.findTopBySourceIdOrderByVersionDesc(sourceId))
                .thenReturn(Optional.of(new SourceSchema()));

        job.setTotalTransformed(2);
        when(ingestionJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        scheduler.processStreamingWindows();

        verify(pipelineCompletionHandler, never()).runPipeline(any());
        verify(qualityEngineService).runQualityEngine(jobId);
        verify(canonicalLoadService).loadCanonical(jobId);
    }

    @Test
    void processStreamingWindows_noActiveWindow_skips() {
        when(dataSourceRepository.findBySourceTypeAndActive(SourceType.KAFKA_STREAM, true))
                .thenReturn(List.of(source));
        when(jobManager.hasActiveWindow(sourceId)).thenReturn(false);

        scheduler.processStreamingWindows();

        verify(jobManager, never()).rotateWindow(any());
        verify(pipelineCompletionHandler, never()).runPipeline(any());
    }

    @Test
    void processStreamingWindows_alreadyRunning_skips() {
        when(dataSourceRepository.findBySourceTypeAndActive(SourceType.KAFKA_STREAM, true))
                .thenReturn(List.of(source));

        // Manually add the sourceId to runningSourceIds to simulate already running
        @SuppressWarnings("unchecked")
        Set<UUID> runningIds = (Set<UUID>) ReflectionTestUtils.getField(scheduler, "runningSourceIds");
        assertThat(runningIds).isNotNull();
        runningIds.add(sourceId);

        scheduler.processStreamingWindows();

        verify(jobManager, never()).rotateWindow(any());
        verify(pipelineCompletionHandler, never()).runPipeline(any());
    }

    @Test
    void processStreamingWindows_emptyWindow_skips() {
        var windowState = new StreamIngestionJobManager.WindowState(job);

        when(dataSourceRepository.findBySourceTypeAndActive(SourceType.KAFKA_STREAM, true))
                .thenReturn(List.of(source));
        when(jobManager.hasActiveWindow(sourceId)).thenReturn(true);
        when(jobManager.getWindowState(sourceId)).thenReturn(Optional.of(windowState));
        when(stagedRecordRepository.findAllByJobId(jobId)).thenReturn(List.of());

        scheduler.processStreamingWindows();

        verify(jobManager, never()).rotateWindow(any());
        verify(pipelineCompletionHandler, never()).runPipeline(any());
    }

    @Test
    void processStreamingWindows_pipelineFails_marksJobFailed() {
        when(meterRegistry.timer(anyString(), anyString(), anyString())).thenReturn(timer);
        var windowState = new StreamIngestionJobManager.WindowState(job);

        when(dataSourceRepository.findBySourceTypeAndActive(SourceType.KAFKA_STREAM, true))
                .thenReturn(List.of(source));
        when(jobManager.hasActiveWindow(sourceId)).thenReturn(true);
        when(jobManager.getWindowState(sourceId)).thenReturn(Optional.of(windowState));
        when(stagedRecordRepository.findAllByJobId(jobId))
                .thenReturn(List.of(new StagedRecord()));
        when(jobManager.rotateWindow(sourceId)).thenReturn(Optional.of(job));
        when(ingestionJobRepository.save(any(IngestionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sourceSchemaRepository.findTopBySourceIdOrderByVersionDesc(sourceId))
                .thenReturn(Optional.empty());

        doThrow(new RuntimeException("Pipeline crashed")).when(pipelineCompletionHandler).runPipeline(jobId);

        scheduler.processStreamingWindows();

        verify(pipelineCompletionHandler).runPipeline(jobId);
        verify(ingestionJobRepository, atLeastOnce()).save(job);
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorMessage()).contains("Pipeline crashed");
    }
}
