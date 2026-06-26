package com.shreyas.saleslens.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class CanonicalOrderDto {
    private UUID id;
    private String externalRefs;
    private LocalDate orderDate;
    private LocalDate shipDate;
    private UUID customerId;
    private UUID salespersonId;
    private UUID regionId;
    private String shipMode;
    private BigDecimal shippingCost;
    private BigDecimal totalAmount;
    private String currency;
    private UUID sourceId;
    private UUID jobId;
    private BigDecimal qualityScore;
    private Boolean hasConflicts;
    private Instant createdAt;
}
