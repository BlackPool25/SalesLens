package com.shreyas.saleslens.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResolveConflictRequest {

    @NotNull(message = "chosenValue is required")
    private String chosenValue;
}
