package com.shreyas.saleslens.batch.csv;

import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.enums.JobStatus;
import com.shreyas.saleslens.repository.IngestionJobRepository;
import com.shreyas.saleslens.service.ingestion.PipelineCompletionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CsvIngestionJobListenerRegressionTest {

    @Mock
    private IngestionJobRepository ingestionJobRepository;

    @Mock
    private PipelineCompletionHandler pipelineCompletionHandler;

    @Mock
    private JobExecution jobExecution;

    private CsvIngestionJobListener listener;

    private UUID jobId;

    private IngestionJob job;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        listener = new CsvIngestionJobListener(
                ingestionJobRepository, pipelineCompletionHandler);
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
    void afterJob_whenCompleted_callsRunPipeline() {
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);

        listener.afterJob(jobExecution);

        verify(pipelineCompletionHandler).runPipeline(jobId);
    }

    @Test
    void afterJob_whenFailed_doesNotCallRunPipeline() {
        when(jobExecution.getStatus()).thenReturn(BatchStatus.FAILED);
        when(jobExecution.getFailureExceptions()).thenReturn(Collections.emptyList());

        listener.afterJob(jobExecution);

        verify(pipelineCompletionHandler, never()).runPipeline(any());
    }

    @Test
    void afterJob_cleansUpTempFile() throws IOException {
        Path tempFile = tempDir.resolve("test.csv");
        Files.createFile(tempFile);
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobExecution.getJobParameters().getString("filePath")).thenReturn(tempFile.toString());

        listener.afterJob(jobExecution);

        verify(pipelineCompletionHandler).runPipeline(jobId);
    }

    @Test
    void afterJob_whenNoFilePath_doesNotThrow() {
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobExecution.getJobParameters().getString("filePath")).thenReturn(null);

        listener.afterJob(jobExecution);

        verify(pipelineCompletionHandler).runPipeline(jobId);
    }
}
