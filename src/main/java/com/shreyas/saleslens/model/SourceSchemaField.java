package com.shreyas.saleslens.model;

import com.shreyas.saleslens.model.enums.InferredType;
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
@Table(name = "source_schema_fields")
public class SourceSchemaField {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schema_id", nullable = false)
    private SourceSchema schema;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Enumerated(EnumType.STRING)
    @Column(name = "inferred_type", length = 50)
    private InferredType inferredType;

    @Column(name = "detected_format", length = 50)
    private String detectedFormat;

    @Column(nullable = false)
    private boolean nullable;

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
