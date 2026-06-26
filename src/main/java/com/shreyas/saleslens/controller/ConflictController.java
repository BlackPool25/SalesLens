package com.shreyas.saleslens.controller;

import com.shreyas.saleslens.dto.ConflictRecordDto;
import com.shreyas.saleslens.dto.ResolveConflictRequest;
import com.shreyas.saleslens.model.ConflictRecord;
import com.shreyas.saleslens.model.enums.ConflictStatus;
import com.shreyas.saleslens.repository.ConflictRecordRepository;
import com.shreyas.saleslens.service.conflict.ConflictResolutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/conflicts")
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ConflictController {

    private final ConflictResolutionService conflictResolutionService;
    private final ConflictRecordRepository conflictRecordRepository;

    @GetMapping
    public ResponseEntity<Page<ConflictRecordDto>> listConflicts(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String fieldName,
            @RequestParam(required = false) ConflictStatus status,
            @RequestParam(required = false) UUID sourceId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        log.info("Listing conflicts with filters: entityType={}, fieldName={}, status={}, sourceId={}",
                entityType, fieldName, status, sourceId);

        Page<ConflictRecord> records = conflictRecordRepository.findAll(pageable);

        // Apply in-memory filtering if any filters are specified
        if (entityType != null || fieldName != null || status != null || sourceId != null) {
            List<ConflictRecord> filtered = records.getContent().stream()
                    .filter(r -> entityType == null || entityType.equals(r.getEntityType()))
                    .filter(r -> fieldName == null || fieldName.equals(r.getFieldName()))
                    .filter(r -> status == null || status == r.getStatus())
                    .filter(r -> sourceId == null
                            || (r.getSourceA() != null && sourceId.equals(r.getSourceA().getId()))
                            || (r.getSourceB() != null && sourceId.equals(r.getSourceB().getId())))
                    .collect(Collectors.toList());
            records = new PageImpl<>(filtered, pageable, filtered.size());
        }

        Page<ConflictRecordDto> dtoPage = records.map(this::toDto);
        return ResponseEntity.ok(dtoPage);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConflictRecordDto> getConflict(@PathVariable UUID id) {
        log.info("Fetching conflict: {}", id);
        return conflictRecordRepository.findById(id)
                .map(record -> ResponseEntity.ok(toDto(record)))
                .orElse(ResponseEntity.notFound().build());
    }

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
