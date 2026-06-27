package com.shreyas.saleslens.dto;

import com.shreyas.saleslens.model.enums.IssueStatus;
import com.shreyas.saleslens.model.enums.QualityDimension;
import com.shreyas.saleslens.model.enums.QualitySeverity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Quality issue found during data quality evaluation")
public class QualityIssueDto {
    @Schema(description = "Unique issue identifier")
    private UUID id;

    @Schema(description = "Quality run identifier")
    private UUID runId;

    @Schema(description = "Source data source identifier")
    private UUID sourceId;

    @Schema(description = "Staged record identifier where the issue was found")
    private UUID stagedRecordId;

    @Schema(description = "Field name where the issue was detected", example = "email")
    private String sourceFieldName;

    @Schema(description = "Quality rule that was violated", example = "VALIDITY_EMAIL")
    private String ruleCode;

    @Schema(description = "Severity level of the issue")
    private QualitySeverity severity;

    @Schema(description = "Quality dimension that was evaluated")
    private QualityDimension dimension;

    @Schema(description = "Human-readable description of the issue", example = "Invalid email format")
    private String message;

    @Schema(description = "Current status of the issue")
    private IssueStatus status;

    @Schema(description = "Timestamp when the issue was created")
    private Instant createdAt;
}
