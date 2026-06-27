package com.shreyas.saleslens.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Quality evaluation scores for a data source")
public class QualityScoreDto {
    @Schema(description = "Unique score identifier")
    private UUID id;

    @Schema(description = "Job identifier that produced these scores")
    private UUID jobId;

    @Schema(description = "Source data source identifier")
    private UUID sourceId;

    @Schema(description = "Completeness score (0.0 - 1.0)", example = "0.95")
    private BigDecimal scoreCompleteness;

    @Schema(description = "Validity score (0.0 - 1.0)", example = "0.88")
    private BigDecimal scoreValidity;

    @Schema(description = "Uniqueness score (0.0 - 1.0)", example = "0.92")
    private BigDecimal scoreUniqueness;

    @Schema(description = "Consistency score (0.0 - 1.0)", example = "0.85")
    private BigDecimal scoreConsistency;

    @Schema(description = "Timeliness score (0.0 - 1.0)", example = "0.90")
    private BigDecimal scoreTimeliness;

    @Schema(description = "Accuracy score (0.0 - 1.0)", example = "0.87")
    private BigDecimal scoreAccuracy;

    @Schema(description = "Overall weighted quality score (0.0 - 1.0)", example = "0.89")
    private BigDecimal scoreOverall;

    @Schema(description = "Letter grade derived from overall score (A-F)", example = "B")
    private String letterGrade;

    @Schema(description = "Timestamp when the score was recorded")
    private Instant createdAt;
}
