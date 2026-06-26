package com.shreyas.saleslens.dto;

import com.shreyas.saleslens.model.enums.ConflictStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ConflictRecordDto {
    private UUID id;
    private String entityType;
    private UUID entityId;
    private String fieldName;
    private UUID sourceAId;
    private UUID sourceBId;
    private String valueA;
    private String valueB;
    private String resolutionStrategy;
    private ConflictStatus status;
    private Long resolvedBy;
    private Instant resolvedAt;
    private Instant createdAt;
}
