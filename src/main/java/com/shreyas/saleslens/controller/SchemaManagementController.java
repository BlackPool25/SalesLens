package com.shreyas.saleslens.controller;

import com.shreyas.saleslens.dto.SchemaDemoteRequest;
import com.shreyas.saleslens.dto.SchemaPromoteRequest;
import com.shreyas.saleslens.service.SchemaManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schema")
@RequiredArgsConstructor
@Slf4j
public class SchemaManagementController {

    private final SchemaManagementService schemaManagementService;

    @PostMapping("/promote")
    public ResponseEntity<Void> promoteAttribute(@Valid @RequestBody SchemaPromoteRequest request) {
        log.info("Request to promote attribute '{}' on entity '{}'", request.getAttributeKey(), request.getEntityName());
        schemaManagementService.promoteAttribute(
                request.getEntityName(),
                request.getAttributeKey(),
                request.getDataType()
        );
        return ResponseEntity.ok().build();
    }

    @PostMapping("/demote")
    public ResponseEntity<Void> demoteColumn(@Valid @RequestBody SchemaDemoteRequest request) {
        log.info("Request to demote column '{}' on entity '{}'", request.getColumnName(), request.getEntityName());
        schemaManagementService.demoteColumn(
                request.getEntityName(),
                request.getColumnName()
        );
        return ResponseEntity.ok().build();
    }
}
