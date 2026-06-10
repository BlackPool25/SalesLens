package com.shreyas.saleslens.model;

import com.shreyas.saleslens.model.enums.JobStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "ingestion_jobs")
public class IngestionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private DataSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(name = "total_read", nullable = false)
    private Integer totalRead;

    @Column(name = "total_transformed", nullable = false)
    private Integer totalTransformed;

    @Column(name = "total_quality_pass", nullable = false)
    private Integer totalQualityPass;

    @Column(name = "total_quality_fail", nullable = false)
    private Integer totalQualityFail;

    @Column(name = "total_loaded", nullable = false)
    private Integer totalLoaded;

    @Column(name = "total_conflicted", nullable = false)
    private Integer totalConflicted;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
