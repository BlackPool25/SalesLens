package com.shreyas.saleslens.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Canonical (unified) region entity")
public class CanonicalRegionDto {
    @Schema(description = "Unique region identifier")
    private UUID id;

    @Schema(description = "Region name", example = "East")
    private String name;

    @Schema(description = "Country name", example = "United States")
    private String country;

    @Schema(description = "Zone classification", example = "Domestic")
    private String zone;

    @Schema(description = "Timestamp when the record was created")
    private Instant createdAt;
}
