package com.shreyas.saleslens.dto;

import com.shreyas.saleslens.model.enums.SourceType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateSourceRequest {
    private String name;
    private SourceType sourceType;
    private BigDecimal trustScore;
    private Boolean active;
}
