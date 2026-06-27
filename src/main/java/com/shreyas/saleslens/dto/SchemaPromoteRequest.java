package com.shreyas.saleslens.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to promote a field to the canonical schema")
public class SchemaPromoteRequest {
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z_][a-zA-Z0-9_]*$")
    @Schema(description = "Canonical entity name", example = "customers")
    private String entityName;

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z_][a-zA-Z0-9_]*$")
    @Schema(description = "Canonical field name to promote", example = "email")
    private String attributeKey;

    @NotBlank
    @Schema(description = "Data type of the field", example = "STRING")
    private String dataType;
}
