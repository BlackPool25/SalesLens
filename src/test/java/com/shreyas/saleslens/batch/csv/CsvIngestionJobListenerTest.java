package com.shreyas.saleslens.batch.csv;

import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.enums.JobStatus;
import com.shreyas.saleslens.repository.IngestionJobRepository;
import com.shreyas.saleslens.service.canonical.CanonicalLoadService;
import com.shreyas.saleslens.service.inference.SchemaInferenceService;
import com.shreyas.saleslens.service.quality.QualityEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CsvIngestionJobListenerTest {

    @Mock
    private IngestionJobRepository ingestionJobRepository;

    @Mock
    private SchemaInferenceService schemaInferenceService;

    @Mock
    private QualityEngineService qualityEngineService;

    @Mock
    private CanonicalLoadService canonicalLoadService;

    @Mock
    private JobExecution jobExecution;

    private CsvIngestionJobListener listener;

    private UUID jobId;

    private IngestionJob job;

    @BeforeEach
    void setUp() {
        listener = new CsvIngestionJobListener(
                ingestionJobRepository, schemaInferenceService,
                canonicalLoadService, qualityEngineService);
        jobId = UUID.randomUUID();
        job = new IngestionJob();
        job.setStatus(JobStatus.RUNNING);

        JobParameters jobParams = mock(JobParameters.class);
        when(jobExecution.getJobParameters()).thenReturn(jobParams);
        when(jobParams.getString("ingestionJobId")).thenReturn(jobId.toString());
        when(ingestionJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobExecution.getStepExecutions()).thenReturn(Collections.emptySet());
    }

    @Test
    void afterJob_whenCompleted_callsLoadCanonical() {
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);

        listener.afterJob(jobExecution);

        verify(canonicalLoadService).loadCanonical(jobId);
    }

    @Test
    void afterJob_whenFailed_doesNotCallLoadCanonical() {
        when(jobExecution.getStatus()).thenReturn(BatchStatus.FAILED);
        when(jobExecution.getFailureExceptions()).thenReturn(Collections.emptyList());

        listener.afterJob(jobExecution);

        verify(canonicalLoadService, never()).loadCanonical(any());
    }
}
