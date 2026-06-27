package com.shreyas.saleslens.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Canonical (unified) product entity")
public class CanonicalProductDto {
    @Schema(description = "Unique canonical product identifier")
    private UUID id;

    @Schema(description = "External references from source systems (JSONB)", example = "{\"erp\": \"SKU-001\", \"pos\": \"PROD-ALPHA\"}")
    private String externalRefs;

    @Schema(description = "Stock Keeping Unit identifier", example = "SKU-ALPHA-001")
    private String sku;

    @Schema(description = "Product name", example = "Widget Alpha")
    private String name;

    @Schema(description = "Product category", example = "Electronics")
    private String category;

    @Schema(description = "Product subcategory", example = "Accessories")
    private String subCategory;

    @Schema(description = "Unit price", example = "49.99")
    private BigDecimal unitPrice;

    @Schema(description = "Currency code (ISO 4217)", example = "USD")
    private String currency;

    @Schema(description = "Whether the product is active", example = "true")
    private Boolean active;

    @Schema(description = "Primary source identifier")
    private UUID primarySourceId;

    @Schema(description = "Overall quality score (0.0 - 1.0)", example = "0.92")
    private BigDecimal qualityScore;

    @Schema(description = "Whether unresolved conflicts exist", example = "false")
    private Boolean hasConflicts;

    @Schema(description = "Timestamp when the record was created")
    private Instant createdAt;

    @Schema(description = "Timestamp when the record was last updated")
    private Instant updatedAt;
}
