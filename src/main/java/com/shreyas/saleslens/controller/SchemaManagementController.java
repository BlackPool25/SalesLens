package com.shreyas.saleslens.controller;

import com.shreyas.saleslens.dto.SchemaDemoteRequest;
import com.shreyas.saleslens.dto.SchemaPromoteRequest;
import com.shreyas.saleslens.service.SchemaManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schema")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Schema Management", description = "Endpoints for promoting and demoting attributes in the canonical schema registry")
public class SchemaManagementController {

    private final SchemaManagementService schemaManagementService;

    @Operation(summary = "Promote an attribute", description = "Promotes a new attribute to the canonical schema registry")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Attribute promoted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request body / validation error"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN role)")
    })
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

    @Operation(summary = "Demote a column", description = "Demotes an existing column from the canonical schema registry")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Column demoted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request body / validation error"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN role)")
    })
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
