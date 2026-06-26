package com.shreyas.saleslens.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class QualityScoreDto {
    private UUID id;
    private UUID jobId;
    private UUID sourceId;
    private BigDecimal scoreCompleteness;
    private BigDecimal scoreValidity;
    private BigDecimal scoreUniqueness;
    private BigDecimal scoreConsistency;
    private BigDecimal scoreTimeliness;
    private BigDecimal scoreAccuracy;
    private BigDecimal scoreOverall;
    private String letterGrade;
    private Instant createdAt;
}
