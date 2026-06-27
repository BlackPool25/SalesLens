package com.shreyas.saleslens.controller;

import com.shreyas.saleslens.dto.ConflictRecordDto;
import com.shreyas.saleslens.dto.ResolveConflictRequest;
import com.shreyas.saleslens.model.ConflictRecord;
import com.shreyas.saleslens.model.enums.ConflictStatus;
import com.shreyas.saleslens.repository.ConflictRecordRepository;
import com.shreyas.saleslens.service.conflict.ConflictResolutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conflicts")
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
@Tag(name = "Conflicts", description = "Endpoints for resolving and managing data conflicts between sources")
public class ConflictController {

    private final ConflictResolutionService conflictResolutionService;
    private final ConflictRecordRepository conflictRecordRepository;

    @Operation(summary = "List conflicts", description = "Returns a paginated list of data conflicts with optional filtering by entity type, field name, status, and source")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conflicts retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN or ANALYST role)")
    })
    @GetMapping
    public ResponseEntity<Page<ConflictRecordDto>> listConflicts(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String fieldName,
            @RequestParam(required = false) ConflictStatus status,
            @RequestParam(required = false) UUID sourceId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        log.info("Listing conflicts with filters: entityType={}, fieldName={}, status={}, sourceId={}",
                entityType, fieldName, status, sourceId);

        Page<ConflictRecord> records = conflictRecordRepository.findFiltered(
                entityType, fieldName, status, sourceId, pageable);

        Page<ConflictRecordDto> dtoPage = records.map(this::toDto);
        return ResponseEntity.ok(dtoPage);
    }

    @Operation(summary = "Get conflict by ID", description = "Returns a single conflict with full field-level detail and source provenance")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conflict retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN or ANALYST role)"),
        @ApiResponse(responseCode = "404", description = "Conflict not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ConflictRecordDto> getConflict(@PathVariable UUID id) {
        log.info("Fetching conflict: {}", id);
        return conflictRecordRepository.findById(id)
                .map(record -> ResponseEntity.ok(toDto(record)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Resolve conflict", description = "Resolves a conflict by providing the chosen canonical value")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conflict resolved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request body / validation error"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN or ANALYST role)"),
        @ApiResponse(responseCode = "404", description = "Conflict not found")
    })
    @PutMapping("/{id}/resolve")
    @Transactional
    public ResponseEntity<ConflictRecordDto> resolveConflict(
            @PathVariable UUID id,
            @RequestBody @Valid ResolveConflictRequest request) {
        log.info("Resolving conflict: {}", id);
        return conflictResolutionService.resolveConflict(id, request.getChosenValue(), null)
                .map(record -> ResponseEntity.ok(toDto(record)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Suppress conflict", description = "Suppresses a conflict permanently without resolution")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conflict suppressed successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN or ANALYST role)"),
        @ApiResponse(responseCode = "404", description = "Conflict not found")
    })
    @PutMapping("/{id}/suppress")
    @Transactional
    public ResponseEntity<ConflictRecordDto> suppressConflict(@PathVariable UUID id) {
        log.info("Suppressing conflict: {}", id);
        return conflictResolutionService.suppressConflict(id, null)
                .map(record -> ResponseEntity.ok(toDto(record)))
                .orElse(ResponseEntity.notFound().build());
    }

    private ConflictRecordDto toDto(ConflictRecord record) {
        return ConflictRecordDto.builder()
                .id(record.getId())
                .entityType(record.getEntityType())
                .entityId(record.getEntityId())
                .fieldName(record.getFieldName())
                .sourceAId(record.getSourceA() != null ? record.getSourceA().getId() : null)
                .sourceBId(record.getSourceB() != null ? record.getSourceB().getId() : null)
                .valueA(record.getValueA())
                .valueB(record.getValueB())
                .resolutionStrategy(record.getResolutionStrategy() != null ? record.getResolutionStrategy().name() : null)
                .status(record.getStatus())
                .resolvedBy(record.getResolvedBy() != null ? record.getResolvedBy().getId() : null)
                .resolvedAt(record.getResolvedAt())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
