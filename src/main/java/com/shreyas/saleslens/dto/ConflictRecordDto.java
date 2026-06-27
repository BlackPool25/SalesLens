package com.shreyas.saleslens.dto;

import com.shreyas.saleslens.model.enums.ConflictStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Conflict record between two sources for a canonical entity field")
public class ConflictRecordDto {
    @Schema(description = "Unique conflict identifier")
    private UUID id;

    @Schema(description = "Canonical entity type involved in the conflict", example = "customers")
    private String entityType;

    @Schema(description = "Canonical entity identifier")
    private UUID entityId;

    @Schema(description = "Field name with conflicting values", example = "email")
    private String fieldName;

    @Schema(description = "First source identifier involved in the conflict")
    private UUID sourceAId;

    @Schema(description = "Second source identifier involved in the conflict")
    private UUID sourceBId;

    @Schema(description = "Value from the first source", example = "john@example.com")
    private String valueA;

    @Schema(description = "Value from the second source", example = "j.doe@example.com")
    private String valueB;

    @Schema(description = "Strategy used to resolve the conflict", example = "TRUST_HIERARCHY")
    private String resolutionStrategy;

    @Schema(description = "Current status of the conflict")
    private ConflictStatus status;

    @Schema(description = "User ID who resolved the conflict")
    private Long resolvedBy;

    @Schema(description = "Timestamp when the conflict was resolved")
    private Instant resolvedAt;

    @Schema(description = "Timestamp when the conflict was created")
    private Instant createdAt;
}
