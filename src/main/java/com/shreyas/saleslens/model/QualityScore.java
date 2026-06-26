package com.shreyas.saleslens.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "quality_scores")
public class QualityScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false, unique = true)
    private IngestionJob job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private DataSource source;

    @Column(name = "score_completeness", nullable = false, precision = 5, scale = 4)
    private BigDecimal scoreCompleteness;

    @Column(name = "score_validity", nullable = false, precision = 5, scale = 4)
    private BigDecimal scoreValidity;

    @Column(name = "score_uniqueness", nullable = false, precision = 5, scale = 4)
    private BigDecimal scoreUniqueness;

    @Column(name = "score_consistency", nullable = false, precision = 5, scale = 4)
    private BigDecimal scoreConsistency;

    @Column(name = "score_timeliness", nullable = false, precision = 5, scale = 4)
    private BigDecimal scoreTimeliness;

    @Column(name = "score_accuracy", nullable = false, precision = 5, scale = 4)
    private BigDecimal scoreAccuracy;

    @Column(name = "score_overall", nullable = false, precision = 5, scale = 4)
    private BigDecimal scoreOverall;

    @Column(name = "letter_grade", nullable = false, columnDefinition = "VARCHAR(1)")
    private String letterGrade;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
