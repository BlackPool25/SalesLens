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
@Table(name = "field_profiles")
public class FieldProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private DataProfile profile;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "null_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal nullRate;

    @Column(name = "unique_count", nullable = false)
    private int uniqueCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "top_values", columnDefinition = "jsonb")
    private String topValues;

    @Column(name = "min_value")
    private String minValue;

    @Column(name = "max_value")
    private String maxValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sample_values", columnDefinition = "jsonb")
    private String sampleValues;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
