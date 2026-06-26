package com.shreyas.saleslens.service.ingestion;

import com.shreyas.saleslens.service.canonical.CanonicalLoadService;
import com.shreyas.saleslens.service.inference.SchemaInferenceService;
import com.shreyas.saleslens.service.quality.QualityEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineCompletionHandler {

    private final SchemaInferenceService schemaInferenceService;
    private final QualityEngineService qualityEngineService;
    private final CanonicalLoadService canonicalLoadService;

    public void runPipeline(UUID ingestionJobId) {
        try {
            schemaInferenceService.runInference(ingestionJobId);
        } catch (Exception e) {
            log.warn("Schema inference failed for job {}: {}", ingestionJobId, e.getMessage());
        }

        try {
            qualityEngineService.runQualityEngine(ingestionJobId);
        } catch (Exception e) {
            log.error("Quality Engine failed for job {}: {}", ingestionJobId, e.getMessage(), e);
        }

        try {
            canonicalLoadService.loadCanonical(ingestionJobId);
        } catch (Exception e) {
            log.error("Canonical load failed for job {}: {}", ingestionJobId, e.getMessage(), e);
        }
    }
}
