package com.shreyas.saleslens.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CanonicalCustomerDto {
    private UUID id;
    private String externalRefs;
    private String name;
    private String email;
    private String phone;
    private String segment;
    private String region;
    private String country;
    private String city;
    private UUID primarySourceId;
    private BigDecimal qualityScore;
    private Boolean hasConflicts;
    private Instant createdAt;
    private Instant updatedAt;
}
