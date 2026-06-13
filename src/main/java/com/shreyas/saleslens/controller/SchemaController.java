package com.shreyas.saleslens.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.dto.SchemaResponse;
import com.shreyas.saleslens.model.SourceSchema;
import com.shreyas.saleslens.model.SourceSchemaField;
import com.shreyas.saleslens.repository.SourceSchemaFieldRepository;
import com.shreyas.saleslens.repository.SourceSchemaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/sources/{sourceId}/schema")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchemaController {

    private final SourceSchemaRepository sourceSchemaRepository;
    private final SourceSchemaFieldRepository sourceSchemaFieldRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public ResponseEntity<SchemaResponse> getCurrentSchema(@PathVariable UUID sourceId) {
        return sourceSchemaRepository.findBySourceIdAndStatus(sourceId, SourceSchema.STATUS_ACTIVE)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/drift")
    public ResponseEntity<List<SchemaResponse>> getSchemaHistory(@PathVariable UUID sourceId) {
        List<SourceSchema> schemas = sourceSchemaRepository.findBySourceIdOrderByVersionDesc(sourceId);
        List<SchemaResponse> responses = schemas.stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    private SchemaResponse toResponse(SourceSchema schema) {
        SchemaResponse response = new SchemaResponse();
        response.setId(schema.getId());
        response.setVersion(schema.getVersion());
        response.setStatus(schema.getStatus());
        response.setInferredAt(schema.getCreatedAt().toString());

        List<SourceSchemaField> fields = sourceSchemaFieldRepository.findBySchemaId(schema.getId());
        List<SchemaResponse.FieldResponse> fieldResponses = fields.stream().map(f -> {
            SchemaResponse.FieldResponse fr = new SchemaResponse.FieldResponse();
            fr.setFieldName(f.getFieldName());
            fr.setInferredType(f.getInferredType().name());
            fr.setDetectedFormat(f.getDetectedFormat());
            fr.setNullable(f.isNullable());

            List<String> samples = new ArrayList<>();
            if (f.getSampleValues() != null) {
                try {
                    samples = objectMapper.readValue(f.getSampleValues(), new TypeReference<List<String>>() {});
                } catch (Exception e) {
                }
            }
            fr.setSampleValues(samples);
            return fr;
        }).collect(Collectors.toList());

        response.setFields(fieldResponses);
        return response;
    }
}
