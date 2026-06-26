package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.CanonicalOrderLineItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CanonicalOrderLineItemRepository extends JpaRepository<CanonicalOrderLineItem, UUID> {

    List<CanonicalOrderLineItem> findByOrderId(UUID orderId);
}
