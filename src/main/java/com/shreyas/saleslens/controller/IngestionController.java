package com.shreyas.saleslens.controller;

import com.shreyas.saleslens.service.ingestion.IngestionOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionOrchestrator ingestionOrchestrator;

    @PostMapping(value = "/csv", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> uploadCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sourceId") UUID sourceId) {

        UUID jobId = ingestionOrchestrator.ingestCsv(file, sourceId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "jobId", jobId,
                "status", "PENDING",
                "message", "CSV ingestion job accepted; poll /api/v1/jobs/" + jobId + " for status"
        ));
    }
}
