package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.CanonicalOrder;
import com.shreyas.saleslens.model.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface CanonicalOrderRepository extends JpaRepository<CanonicalOrder, UUID> {

    @Query(value = "SELECT * FROM canonical.orders WHERE external_refs @> jsonb_build_object(?1, ?2)", nativeQuery = true)
    Optional<CanonicalOrder> findByExternalRefsContaining(String key, String value);

    Optional<CanonicalOrder> findByOrderDateAndTotalAmountAndSource(LocalDate orderDate, BigDecimal totalAmount, DataSource source);
}
