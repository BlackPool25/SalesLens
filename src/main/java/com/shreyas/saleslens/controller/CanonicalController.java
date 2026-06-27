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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Canonical Data", description = "Endpoints for querying canonical (master) entity data")
public class CanonicalController {

    private final CanonicalMapper canonicalMapper;
    private final CanonicalCustomerRepository canonicalCustomerRepository;
    private final CanonicalProductRepository canonicalProductRepository;
    private final CanonicalOrderRepository canonicalOrderRepository;
    private final CanonicalOrderLineItemRepository canonicalOrderLineItemRepository;
    private final CanonicalSalespersonRepository canonicalSalespersonRepository;
    private final CanonicalRegionRepository canonicalRegionRepository;

    @Operation(summary = "Get canonical customers", description = "Returns a paginated list of canonical customer records")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Customers retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN or ANALYST role)")
    })
    @GetMapping("/customers")
    public ResponseEntity<Page<CanonicalCustomerDto>> getCustomers(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        log.info("Fetching canonical customers, page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        Page<CanonicalCustomerDto> result = canonicalCustomerRepository.findAll(pageable)
                .map(canonicalMapper::toDto);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get canonical products", description = "Returns a paginated list of canonical product records")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Products retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN or ANALYST role)")
    })
    @GetMapping("/products")
    public ResponseEntity<Page<CanonicalProductDto>> getProducts(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        log.info("Fetching canonical products, page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        Page<CanonicalProductDto> result = canonicalProductRepository.findAll(pageable)
                .map(canonicalMapper::toDto);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get canonical orders", description = "Returns a paginated list of canonical order records")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Orders retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN or ANALYST role)")
    })
    @GetMapping("/orders")
    public ResponseEntity<Page<CanonicalOrderDto>> getOrders(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        log.info("Fetching canonical orders, page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        Page<CanonicalOrderDto> result = canonicalOrderRepository.findAll(pageable)
                .map(canonicalMapper::toDto);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get canonical order line items", description = "Returns a paginated list of canonical order line item records")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order line items retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN or ANALYST role)")
    })
    @GetMapping("/order-line-items")
    public ResponseEntity<Page<CanonicalOrderLineItemDto>> getOrderLineItems(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        log.info("Fetching canonical order line items, page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        Page<CanonicalOrderLineItemDto> result = canonicalOrderLineItemRepository.findAll(pageable)
                .map(canonicalMapper::toDto);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get canonical salespersons", description = "Returns a paginated list of canonical salesperson records")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Salespersons retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN or ANALYST role)")
    })
    @GetMapping("/salespersons")
    public ResponseEntity<Page<CanonicalSalespersonDto>> getSalespersons(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        log.info("Fetching canonical salespersons, page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        Page<CanonicalSalespersonDto> result = canonicalSalespersonRepository.findAll(pageable)
                .map(canonicalMapper::toDto);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get canonical regions", description = "Returns a paginated list of canonical region records")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Regions retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN or ANALYST role)")
    })
    @GetMapping("/regions")
    public ResponseEntity<Page<CanonicalRegionDto>> getRegions(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        log.info("Fetching canonical regions, page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        Page<CanonicalRegionDto> result = canonicalRegionRepository.findAll(pageable)
                .map(canonicalMapper::toDto);
        return ResponseEntity.ok(result);
    }
}
