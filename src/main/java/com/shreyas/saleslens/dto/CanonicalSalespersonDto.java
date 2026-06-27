package com.shreyas.saleslens.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Canonical (unified) salesperson entity")
public class CanonicalSalespersonDto {
    @Schema(description = "Unique salesperson identifier")
    private UUID id;

    @Schema(description = "External references from source systems (JSONB)", example = "{\"hr\": \"SP-001\", \"crm\": \"SAL-07\"}")
    private String externalRefs;

    @Schema(description = "Salesperson full name", example = "Jane Smith")
    private String name;

    @Schema(description = "Salesperson email address", example = "jane.smith@company.com")
    private String email;

    @Schema(description = "Sales team name", example = "East Coast")
    private String team;

    @Schema(description = "Sales territory", example = "Northeast")
    private String territory;

    @Schema(description = "Sales region", example = "East")
    private String region;

    @Schema(description = "Whether the salesperson is active", example = "true")
    private Boolean active;

    @Schema(description = "Primary source identifier")
    private UUID primarySourceId;

    @Schema(description = "Timestamp when the record was created")
    private Instant createdAt;

    @Schema(description = "Timestamp when the record was last updated")
    private Instant updatedAt;
}
