package com.shreyas.saleslens.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(schema = "canonical", name = "salespersons")
public class CanonicalSalesperson {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "external_refs", columnDefinition = "jsonb")
    private String externalRefs;

    private String name;
    private String email;
    private String team;
    private String territory;
    private String region;

    @Column(nullable = false)
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_source")
    private DataSource primarySource;

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
