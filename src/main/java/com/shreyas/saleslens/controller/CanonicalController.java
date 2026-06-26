package com.shreyas.saleslens.controller;

import com.shreyas.saleslens.dto.CanonicalCustomerDto;
import com.shreyas.saleslens.dto.CanonicalOrderDto;
import com.shreyas.saleslens.dto.CanonicalOrderLineItemDto;
import com.shreyas.saleslens.dto.CanonicalProductDto;
import com.shreyas.saleslens.dto.CanonicalRegionDto;
import com.shreyas.saleslens.dto.CanonicalSalespersonDto;
import com.shreyas.saleslens.mapper.CanonicalMapper;
import com.shreyas.saleslens.repository.CanonicalCustomerRepository;
import com.shreyas.saleslens.repository.CanonicalOrderLineItemRepository;
import com.shreyas.saleslens.repository.CanonicalOrderRepository;
import com.shreyas.saleslens.repository.CanonicalProductRepository;
import com.shreyas.saleslens.repository.CanonicalRegionRepository;
import com.shreyas.saleslens.repository.CanonicalSalespersonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/canonical")
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
public class CanonicalController {

    private final CanonicalMapper canonicalMapper;
    private final CanonicalCustomerRepository canonicalCustomerRepository;
    private final CanonicalProductRepository canonicalProductRepository;
    private final CanonicalOrderRepository canonicalOrderRepository;
    private final CanonicalOrderLineItemRepository canonicalOrderLineItemRepository;
    private final CanonicalSalespersonRepository canonicalSalespersonRepository;
    private final CanonicalRegionRepository canonicalRegionRepository;

    @GetMapping("/customers")
    public ResponseEntity<Page<CanonicalCustomerDto>> getCustomers(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        log.info("Fetching canonical customers, page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        Page<CanonicalCustomerDto> result = canonicalCustomerRepository.findAll(pageable)
                .map(canonicalMapper::toDto);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/products")
    public ResponseEntity<Page<CanonicalProductDto>> getProducts(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        log.info("Fetching canonical products, page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        Page<CanonicalProductDto> result = canonicalProductRepository.findAll(pageable)
                .map(canonicalMapper::toDto);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/orders")
    public ResponseEntity<Page<CanonicalOrderDto>> getOrders(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        log.info("Fetching canonical orders, page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        Page<CanonicalOrderDto> result = canonicalOrderRepository.findAll(pageable)
                .map(canonicalMapper::toDto);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/order-line-items")
    public ResponseEntity<Page<CanonicalOrderLineItemDto>> getOrderLineItems(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        log.info("Fetching canonical order line items, page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        Page<CanonicalOrderLineItemDto> result = canonicalOrderLineItemRepository.findAll(pageable)
                .map(canonicalMapper::toDto);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/salespersons")
    public ResponseEntity<Page<CanonicalSalespersonDto>> getSalespersons(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        log.info("Fetching canonical salespersons, page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        Page<CanonicalSalespersonDto> result = canonicalSalespersonRepository.findAll(pageable)
                .map(canonicalMapper::toDto);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/regions")
    public ResponseEntity<Page<CanonicalRegionDto>> getRegions(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        log.info("Fetching canonical regions, page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        Page<CanonicalRegionDto> result = canonicalRegionRepository.findAll(pageable)
                .map(canonicalMapper::toDto);
        return ResponseEntity.ok(result);
    }
}
