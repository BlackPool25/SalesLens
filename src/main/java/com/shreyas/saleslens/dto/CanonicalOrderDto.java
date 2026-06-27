package com.shreyas.saleslens.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Canonical (unified) order entity")
public class CanonicalOrderDto {
    @Schema(description = "Unique canonical order identifier")
    private UUID id;

    @Schema(description = "External references from source systems (JSONB)", example = "{\"pos\": \"ORD-001\", \"ecom\": \"1001\"}")
    private String externalRefs;

    @Schema(description = "Order date", example = "2026-01-15")
    private LocalDate orderDate;

    @Schema(description = "Ship date", example = "2026-01-18")
    private LocalDate shipDate;

    @Schema(description = "Customer canonical identifier")
    private UUID customerId;

    @Schema(description = "Salesperson canonical identifier")
    private UUID salespersonId;

    @Schema(description = "Region canonical identifier")
    private UUID regionId;

    @Schema(description = "Shipping method", example = "Standard Class")
    private String shipMode;

    @Schema(description = "Shipping cost", example = "15.50")
    private BigDecimal shippingCost;

    @Schema(description = "Total order amount", example = "249.95")
    private BigDecimal totalAmount;

    @Schema(description = "Currency code (ISO 4217)", example = "USD")
    private String currency;

    @Schema(description = "Source data source identifier")
    private UUID sourceId;

    @Schema(description = "Job identifier that processed this record")
    private UUID jobId;

    @Schema(description = "Overall quality score (0.0 - 1.0)", example = "0.90")
    private BigDecimal qualityScore;

    @Schema(description = "Whether unresolved conflicts exist", example = "false")
    private Boolean hasConflicts;

    @Schema(description = "Timestamp when the record was created")
    private Instant createdAt;
}
