package com.shreyas.saleslens.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Canonical (unified) customer entity")
public class CanonicalCustomerDto {
    @Schema(description = "Unique canonical customer identifier")
    private UUID id;

    @Schema(description = "External references from source systems (JSONB)", example = "{\"source_a\": \"CUST-001\", \"source_b\": \"C-9912\"}")
    private String externalRefs;

    @Schema(description = "Customer full name", example = "John Doe")
    private String name;

    @Schema(description = "Customer email address", example = "johndoe@example.com")
    private String email;

    @Schema(description = "Customer phone number", example = "+1-555-123-4567")
    private String phone;

    @Schema(description = "Customer market segment", example = "Consumer")
    private String segment;

    @Schema(description = "Customer region", example = "East")
    private String region;

    @Schema(description = "Customer country", example = "United States")
    private String country;

    @Schema(description = "Customer city", example = "New York")
    private String city;

    @Schema(description = "Primary source identifier")
    private UUID primarySourceId;

    @Schema(description = "Overall quality score (0.0 - 1.0)", example = "0.95")
    private BigDecimal qualityScore;

    @Schema(description = "Whether unresolved conflicts exist", example = "false")
    private Boolean hasConflicts;

    @Schema(description = "Timestamp when the record was created")
    private Instant createdAt;

    @Schema(description = "Timestamp when the record was last updated")
    private Instant updatedAt;
}
