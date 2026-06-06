package com.shreyas.saleslens.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class DataSourceResponse {
    private UUID id;
    private String name;
    private String sourceType;
    private BigDecimal trustScore;
    private Boolean active;
    private Instant lastSyncAt;
    private Instant createdAt;
    private Long createdBy;
}
