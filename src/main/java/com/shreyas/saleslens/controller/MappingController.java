package com.shreyas.saleslens.controller;

import com.shreyas.saleslens.dto.FieldMappingResponse;
import com.shreyas.saleslens.model.FieldMapping;
import com.shreyas.saleslens.repository.FieldMappingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/v1/sources/{sourceId}/mappings")
@RequiredArgsConstructor
@Slf4j
@Transactional
@PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
@Tag(name = "Field Mapping", description = "Endpoints for managing semantic field mappings between source schemas and the canonical model")
public class MappingController {

    private final FieldMappingRepository fieldMappingRepository;

    @Operation(summary = "Get field mappings", description = "Returns a paginated list of field mappings for a given data source")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mappings retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN or ANALYST role)")
    })
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Page<FieldMappingResponse>> getFieldMappings(
            @PathVariable UUID sourceId,
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("Fetching field mappings for sourceId: {}", sourceId);
        Page<FieldMappingResponse> responses = fieldMappingRepository.findBySourceId(sourceId, pageable)
                .map(this::toResponse);
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Confirm a field mapping", description = "Confirms an auto-suggested field mapping, changing its status to AUTO_CONFIRMED")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mapping confirmed successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN or ANALYST role)"),
        @ApiResponse(responseCode = "404", description = "Mapping not found")
    })
    @PutMapping("/{mappingId}/confirm")
    public ResponseEntity<FieldMappingResponse> confirmMapping(
            @PathVariable UUID sourceId,
            @PathVariable UUID mappingId) {
        log.info("Confirming mappingId: {} for sourceId: {}", mappingId, sourceId);
        return fieldMappingRepository.findById(mappingId)
                .filter(m -> m.getSource().getId().equals(sourceId))
                .map(m -> {
                    m.setStatus("AUTO_CONFIRMED");
                    FieldMapping saved = fieldMappingRepository.save(m);
                    return ResponseEntity.ok(toResponse(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Override a field mapping", description = "Overrides a field mapping with a manually specified canonical entity and field")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mapping overridden successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN or ANALYST role)"),
        @ApiResponse(responseCode = "404", description = "Mapping not found")
    })
    @PutMapping("/{mappingId}/override")
    public ResponseEntity<FieldMappingResponse> overrideMapping(
            @PathVariable UUID sourceId,
            @PathVariable UUID mappingId,
            @RequestParam String canonicalEntity,
            @RequestParam String canonicalField) {
        log.info("Overriding mappingId: {} for sourceId: {} to {}.{}", mappingId, sourceId, canonicalEntity, canonicalField);
        return fieldMappingRepository.findById(mappingId)
                .filter(m -> m.getSource().getId().equals(sourceId))
                .map(m -> {
                    m.setCanonicalEntity(canonicalEntity);
                    m.setCanonicalField(canonicalField);
                    m.setStatus("AUTO_CONFIRMED");
                    FieldMapping saved = fieldMappingRepository.save(m);
                    return ResponseEntity.ok(toResponse(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Ignore a field mapping", description = "Marks a field mapping as IGNORED, excluding it from the canonical transformation")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mapping ignored successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN or ANALYST role)"),
        @ApiResponse(responseCode = "404", description = "Mapping not found")
    })
    @PutMapping("/{mappingId}/ignore")
    public ResponseEntity<FieldMappingResponse> ignoreMapping(
            @PathVariable UUID sourceId,
            @PathVariable UUID mappingId) {
        log.info("Ignoring mappingId: {} for sourceId: {}", mappingId, sourceId);
        return fieldMappingRepository.findById(mappingId)
                .filter(m -> m.getSource().getId().equals(sourceId))
                .map(m -> {
                    m.setStatus("IGNORED");
                    FieldMapping saved = fieldMappingRepository.save(m);
                    return ResponseEntity.ok(toResponse(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private FieldMappingResponse toResponse(FieldMapping mapping) {
        FieldMappingResponse response = new FieldMappingResponse();
        response.setId(mapping.getId());
        if (mapping.getSource() != null) {
            response.setSourceId(mapping.getSource().getId());
        }
        response.setSourceFieldName(mapping.getSourceFieldName());
        response.setCanonicalEntity(mapping.getCanonicalEntity());
        response.setCanonicalField(mapping.getCanonicalField());
        response.setConfidence(mapping.getConfidence());
        response.setStatus(mapping.getStatus());
        response.setTransformRule(mapping.getTransformRule());
        return response;
    }
}
