package com.shreyas.saleslens.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Schema(description = "Inferred schema for a data source")
public class SchemaResponse {
    @Schema(description = "Unique schema identifier")
    private UUID id;

    @Schema(description = "Schema version number (increments on changes)", example = "1")
    private int version;

    @Schema(description = "Current status of the schema", example = "ACTIVE")
    private String status;

    @Schema(description = "Fields in the schema with their inferred types")
    private List<FieldResponse> fields;

    @Schema(description = "Timestamp when schema was inferred", example = "2026-06-26T12:00:00Z")
    private String inferredAt;

    @Getter
    @Setter
    @Schema(description = "Individual field within a schema")
    public static class FieldResponse {
        @Schema(description = "Name of the field", example = "order_date")
        private String fieldName;

        @Schema(description = "Inferred data type", example = "DATE")
        private String inferredType;

        @Schema(description = "Detected format pattern if applicable", example = "yyyy-MM-dd")
        private String detectedFormat;

        @Schema(description = "Whether null values are allowed", example = "true")
        private boolean nullable;

        @Schema(description = "Sample values from the data")
        private List<String> sampleValues;
    }
}
