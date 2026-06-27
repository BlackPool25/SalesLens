package com.shreyas.saleslens.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request body for resolving a conflict with the chosen value")
public class ResolveConflictRequest {

    @NotNull(message = "chosenValue is required")
    @Schema(description = "The value selected to resolve the conflict", example = "john@example.com")
    private String chosenValue;
}
