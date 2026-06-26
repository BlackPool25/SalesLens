package com.shreyas.saleslens.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(schema = "canonical", name = "orders")
public class CanonicalOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "external_refs", columnDefinition = "jsonb")
    private String externalRefs;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @Column(name = "ship_date")
    private LocalDate shipDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private CanonicalCustomer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salesperson_id")
    private CanonicalSalesperson salesperson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private CanonicalRegion region;

    @Column(name = "ship_mode")
    private String shipMode;

    @Column(name = "shipping_cost", precision = 12, scale = 4)
    private BigDecimal shippingCost;

    @Column(name = "total_amount", precision = 12, scale = 4)
    private BigDecimal totalAmount;

    @Column(length = 3)
    private String currency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "additional_attributes", columnDefinition = "jsonb")
    private String additionalAttributes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private DataSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private IngestionJob job;

    @Column(name = "quality_score", precision = 5, scale = 4)
    private BigDecimal qualityScore;

    @Column(name = "has_conflicts", nullable = false)
    private Boolean hasConflicts = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
