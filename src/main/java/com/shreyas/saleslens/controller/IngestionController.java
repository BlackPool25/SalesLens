package com.shreyas.saleslens.controller;

import com.shreyas.saleslens.service.ingestion.IngestionOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
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
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Ingestion", description = "Endpoints for ingesting sales data from CSV, Excel, and JDBC sources")
public class IngestionController {

    private final IngestionOrchestrator ingestionOrchestrator;

    @Operation(summary = "Upload CSV file", description = "Uploads a CSV file for ingestion, returning a job ID for status tracking")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "CSV ingestion job accepted"),
        @ApiResponse(responseCode = "400", description = "Invalid file type (only .csv accepted)"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN role)")
    })
    @PostMapping(value = "/csv", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> uploadCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sourceId") UUID sourceId) {

        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();

        boolean validExtension = filename != null && filename.toLowerCase().endsWith(".csv");
        boolean validContentType = contentType == null
                || contentType.equals("text/csv")
                || contentType.equals("application/vnd.ms-excel")
                || contentType.equals("application/octet-stream");

        if (!validExtension || !validContentType) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Only CSV files are accepted"));
        }

        UUID jobId = ingestionOrchestrator.ingestCsv(file, sourceId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "jobId", jobId,
                "status", "PENDING",
                "message", "CSV ingestion job accepted; poll /api/v1/jobs/" + jobId + " for status"
        ));
    }

    @Operation(summary = "Upload Excel file", description = "Uploads an .xlsx file for ingestion, returning a job ID for status tracking")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Excel ingestion job accepted"),
        @ApiResponse(responseCode = "400", description = "Invalid file type (only .xlsx accepted)"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN role)")
    })
    @PostMapping(value = "/excel", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> uploadExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sourceId") UUID sourceId) {

        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();

        boolean validExtension = filename != null && filename.toLowerCase().endsWith(".xlsx");
        boolean validContentType = contentType == null
                || contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                || contentType.equals("application/octet-stream");

        if (!validExtension || !validContentType) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Only .xlsx files are accepted"));
        }

        UUID jobId = ingestionOrchestrator.ingestExcel(file, sourceId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "jobId", jobId,
                "status", "PENDING",
                "message", "Excel ingestion job accepted; poll /api/v1/jobs/" + jobId + " for status"
        ));
    }

    @Operation(summary = "Trigger JDBC ingestion", description = "Manually triggers ingestion for a registered JDBC data source, returning a job ID for status tracking")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "JDBC ingestion job accepted"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN role)")
    })
    @PostMapping("/jdbc/{sourceId}")
    public ResponseEntity<Map<String, Object>> triggerJdbc(@PathVariable UUID sourceId) {
        UUID jobId = ingestionOrchestrator.triggerJdbcIngestion(sourceId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "jobId", jobId,
                "status", "PENDING",
                "message", "JDBC ingestion job accepted; poll /api/v1/jobs/" + jobId + " for status"
        ));
    }
}
