package com.shreyas.saleslens.controller;

import com.shreyas.saleslens.dto.FieldMappingResponse;
import com.shreyas.saleslens.model.FieldMapping;
import com.shreyas.saleslens.repository.FieldMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/sources/{sourceId}/mappings")
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MappingController {

    private final FieldMappingRepository fieldMappingRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<FieldMappingResponse>> getFieldMappings(@PathVariable UUID sourceId) {
        log.info("Fetching field mappings for sourceId: {}", sourceId);
        List<FieldMapping> mappings = fieldMappingRepository.findBySourceId(sourceId);
        List<FieldMappingResponse> responses = mappings.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

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
