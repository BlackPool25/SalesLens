package com.shreyas.saleslens.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class FieldMappingResponse {
    private UUID id;
    private UUID sourceId;
    private String sourceFieldName;
    private String canonicalEntity;
    private String canonicalField;
    private BigDecimal confidence;
    private String status;
    private String transformRule;
}
