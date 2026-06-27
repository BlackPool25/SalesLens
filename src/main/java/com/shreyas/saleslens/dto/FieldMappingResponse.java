package com.shreyas.saleslens.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Schema(description = "Field mapping between source and canonical schema")
public class FieldMappingResponse {
    @Schema(description = "Unique mapping identifier")
    private UUID id;

    @Schema(description = "Source data source identifier")
    private UUID sourceId;

    @Schema(description = "Original field name in the source data", example = "cust_email")
    private String sourceFieldName;

    @Schema(description = "Mapped canonical entity name", example = "customers")
    private String canonicalEntity;

    @Schema(description = "Mapped canonical field name", example = "email")
    private String canonicalField;

    @Schema(description = "Mapping confidence score (0.0 - 1.0)", example = "0.95")
    private BigDecimal confidence;

    @Schema(description = "Mapping status", example = "CONFIRMED")
    private String status;

    @Schema(description = "Optional transformation rule applied to the field value")
    private String transformRule;
}
