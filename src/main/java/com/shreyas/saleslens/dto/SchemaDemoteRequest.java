package com.shreyas.saleslens.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchemaDemoteRequest {
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z_][a-zA-Z0-9_]*$")
    private String entityName;

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z_][a-zA-Z0-9_]*$")
    private String columnName;
}
