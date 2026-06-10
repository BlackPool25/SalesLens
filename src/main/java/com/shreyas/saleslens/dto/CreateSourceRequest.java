package com.shreyas.saleslens.dto;

import com.shreyas.saleslens.model.enums.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateSourceRequest {
    @NotBlank
    private String name;

    @NotNull
    private SourceType sourceType;

    @NotNull
    private BigDecimal trustScore;

    @NotNull
    private Boolean active;

    private String connectionConfig;
}
