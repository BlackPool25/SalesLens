package com.shreyas.saleslens.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Canonical (unified) order line item entity")
public class CanonicalOrderLineItemDto {
    @Schema(description = "Unique line item identifier")
    private UUID id;

    @Schema(description = "Order canonical identifier")
    private UUID orderId;

    @Schema(description = "Product canonical identifier")
    private UUID productId;

    @Schema(description = "Quantity ordered", example = "5")
    private Integer quantity;

    @Schema(description = "Unit price at time of order", example = "49.99")
    private BigDecimal unitPrice;

    @Schema(description = "Discount applied to the line item", example = "0.00")
    private BigDecimal discount;

    @Schema(description = "Line total amount (quantity * unitPrice - discount)", example = "249.95")
    private BigDecimal lineTotal;

    @Schema(description = "Timestamp when the record was created")
    private Instant createdAt;
}
