package com.shreyas.saleslens.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CanonicalSalespersonDto {
    private UUID id;
    private String externalRefs;
    private String name;
    private String email;
    private String team;
    private String territory;
    private String region;
    private Boolean active;
    private UUID primarySourceId;
    private Instant createdAt;
    private Instant updatedAt;
}
