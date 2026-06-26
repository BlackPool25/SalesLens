package com.shreyas.saleslens.dto;

import com.shreyas.saleslens.model.enums.IssueStatus;
import com.shreyas.saleslens.model.enums.QualityDimension;
import com.shreyas.saleslens.model.enums.QualitySeverity;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class QualityIssueDto {
    private UUID id;
    private UUID runId;
    private UUID sourceId;
    private UUID stagedRecordId;
    private String sourceFieldName;
    private String ruleCode;
    private QualitySeverity severity;
    private QualityDimension dimension;
    private String message;
    private IssueStatus status;
    private Instant createdAt;
}
