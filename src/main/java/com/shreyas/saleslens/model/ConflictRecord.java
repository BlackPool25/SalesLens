package com.shreyas.saleslens.model;

import com.shreyas.saleslens.model.enums.ConflictStatus;
import com.shreyas.saleslens.model.enums.ResolutionStrategy;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "conflict_records")
public class ConflictRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_a_id", nullable = false)
    private DataSource sourceA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_b_id", nullable = false)
    private DataSource sourceB;

    @Column(name = "value_a", columnDefinition = "TEXT")
    private String valueA;

    @Column(name = "value_b", columnDefinition = "TEXT")
    private String valueB;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_strategy", nullable = false, length = 50)
    private ResolutionStrategy resolutionStrategy = ResolutionStrategy.FLAGGED_FOR_REVIEW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConflictStatus status = ConflictStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private Users resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

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
