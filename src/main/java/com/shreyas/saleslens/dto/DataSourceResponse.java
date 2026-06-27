package com.shreyas.saleslens.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Schema(description = "Data source details returned to clients")
public class DataSourceResponse {
    @Schema(description = "Unique data source identifier")
    private UUID id;

    @Schema(description = "Name of the data source", example = "Superstore Sales")
    private String name;

    @Schema(description = "Type of data source", example = "CSV_FILE")
    private String sourceType;

    @Schema(description = "Trust score (0.0 - 1.0)", example = "0.9")
    private BigDecimal trustScore;

    @Schema(description = "Whether this source is active", example = "true")
    private Boolean active;

    @Schema(description = "Timestamp of last successful sync")
    private Instant lastSyncAt;

    @Schema(description = "Timestamp when this source was created")
    private Instant createdAt;

    @Schema(description = "User ID of the creator")
    private Long createdBy;
}
