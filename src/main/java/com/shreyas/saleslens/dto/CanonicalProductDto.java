package com.shreyas.saleslens.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CanonicalProductDto {
    private UUID id;
    private String externalRefs;
    private String sku;
    private String name;
    private String category;
    private String subCategory;
    private BigDecimal unitPrice;
    private String currency;
    private Boolean active;
    private UUID primarySourceId;
    private BigDecimal qualityScore;
    private Boolean hasConflicts;
    private Instant createdAt;
    private Instant updatedAt;
}
