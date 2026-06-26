package com.shreyas.saleslens.service.ingestion;

import com.shreyas.saleslens.service.canonical.CanonicalLoadService;
import com.shreyas.saleslens.service.inference.SchemaInferenceService;
import com.shreyas.saleslens.service.quality.QualityEngineService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipelineCompletionHandlerTest {

    @Mock
    private SchemaInferenceService schemaInferenceService;

    @Mock
    private QualityEngineService qualityEngineService;

    @Mock
    private CanonicalLoadService canonicalLoadService;

    @InjectMocks
    private PipelineCompletionHandler handler;

    private final UUID jobId = UUID.randomUUID();

    @Test
    void runPipeline_whenAllSucceed_callsAllServicesInOrder() {
        handler.runPipeline(jobId);

        verify(schemaInferenceService).runInference(jobId);
        verify(qualityEngineService).runQualityEngine(jobId);
        verify(canonicalLoadService).loadCanonical(jobId);
    }

    @Test
    void runPipeline_whenInferenceFails_qualityAndCanonicalStillRun() {
        doThrow(new RuntimeException("Inference failed"))
                .when(schemaInferenceService).runInference(jobId);

        handler.runPipeline(jobId);

        verify(schemaInferenceService).runInference(jobId);
        verify(qualityEngineService).runQualityEngine(jobId);
        verify(canonicalLoadService).loadCanonical(jobId);
    }

    @Test
    void runPipeline_whenQualityFails_canonicalStillRuns() {
        doThrow(new RuntimeException("Quality failed"))
                .when(qualityEngineService).runQualityEngine(jobId);

        handler.runPipeline(jobId);

        verify(schemaInferenceService).runInference(jobId);
        verify(qualityEngineService).runQualityEngine(jobId);
        verify(canonicalLoadService).loadCanonical(jobId);
    }

    @Test
    void runPipeline_whenAllFail_doesNotPropagateException() {
        doThrow(new RuntimeException("Inference failed"))
                .when(schemaInferenceService).runInference(jobId);
        doThrow(new RuntimeException("Quality failed"))
                .when(qualityEngineService).runQualityEngine(jobId);
        doThrow(new RuntimeException("Canonical failed"))
                .when(canonicalLoadService).loadCanonical(jobId);

        // Must not throw
        handler.runPipeline(jobId);

        verify(schemaInferenceService).runInference(jobId);
        verify(qualityEngineService).runQualityEngine(jobId);
        verify(canonicalLoadService).loadCanonical(jobId);
    }
}
