package com.shreyas.saleslens.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CanonicalRegionDto {
    private UUID id;
    private String name;
    private String country;
    private String zone;
    private Instant createdAt;
}
