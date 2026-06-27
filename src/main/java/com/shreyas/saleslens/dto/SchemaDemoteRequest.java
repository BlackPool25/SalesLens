package com.shreyas.saleslens.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to demote a field from the canonical schema")
public class SchemaDemoteRequest {
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z_][a-zA-Z0-9_]*$")
    @Schema(description = "Canonical entity name", example = "customers")
    private String entityName;

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z_][a-zA-Z0-9_]*$")
    @Schema(description = "Column name to demote from canonical", example = "obsolete_field")
    private String columnName;
}
