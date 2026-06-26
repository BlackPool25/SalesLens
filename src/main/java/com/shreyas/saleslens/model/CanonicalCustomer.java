package com.shreyas.saleslens.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(schema = "canonical", name = "customers")
public class CanonicalCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "external_refs", columnDefinition = "jsonb")
    private String externalRefs;

    private String name;
    private String email;
    private String phone;
    private String segment;
    private String region;
    private String country;
    private String city;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_source")
    private DataSource primarySource;

    @Column(name = "quality_score", precision = 5, scale = 4)
    private BigDecimal qualityScore;

    @Column(name = "has_conflicts", nullable = false)
    private Boolean hasConflicts = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "additional_attributes", columnDefinition = "jsonb")
    private String additionalAttributes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
