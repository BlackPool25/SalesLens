package com.shreyas.saleslens.controller;

import com.shreyas.saleslens.config.filters.JwtFilter;
import com.shreyas.saleslens.dto.CanonicalCustomerDto;
import com.shreyas.saleslens.dto.CanonicalOrderDto;
import com.shreyas.saleslens.dto.CanonicalProductDto;
import com.shreyas.saleslens.mapper.CanonicalMapper;
import com.shreyas.saleslens.model.CanonicalCustomer;
import com.shreyas.saleslens.model.CanonicalOrder;
import com.shreyas.saleslens.model.CanonicalProduct;
import com.shreyas.saleslens.repository.CanonicalCustomerRepository;
import com.shreyas.saleslens.repository.CanonicalOrderLineItemRepository;
import com.shreyas.saleslens.repository.CanonicalOrderRepository;
import com.shreyas.saleslens.repository.CanonicalProductRepository;
import com.shreyas.saleslens.repository.CanonicalRegionRepository;
import com.shreyas.saleslens.repository.CanonicalSalespersonRepository;
import com.shreyas.saleslens.security.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CanonicalController.class)
class CanonicalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CanonicalMapper canonicalMapper;

    @MockitoBean
    private CanonicalCustomerRepository canonicalCustomerRepository;

    @MockitoBean
    private CanonicalProductRepository canonicalProductRepository;

    @MockitoBean
    private CanonicalOrderRepository canonicalOrderRepository;

    @MockitoBean
    private CanonicalOrderLineItemRepository canonicalOrderLineItemRepository;

    @MockitoBean
    private CanonicalSalespersonRepository canonicalSalespersonRepository;

    @MockitoBean
    private CanonicalRegionRepository canonicalRegionRepository;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JwtFilter jwtFilter;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            HttpServletResponse response = invocation.getArgument(1);
            FilterChain filterChain = invocation.getArgument(2);
            filterChain.doFilter(request, response);
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getCustomers_returnsPage() throws Exception {
        UUID customerId = UUID.randomUUID();
        CanonicalCustomer customer = new CanonicalCustomer();
        customer.setId(customerId);
        customer.setName("John Doe");
        customer.setEmail("john@example.com");
        customer.setCreatedAt(Instant.now());

        CanonicalCustomerDto dto = CanonicalCustomerDto.builder()
                .id(customerId)
                .name("John Doe")
                .email("john@example.com")
                .build();

        when(canonicalCustomerRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(customer), PageRequest.of(0, 20), 1));
        when(canonicalMapper.toDto(customer)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/canonical/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(customerId.toString()))
                .andExpect(jsonPath("$.content[0].name").value("John Doe"))
                .andExpect(jsonPath("$.content[0].email").value("john@example.com"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getProducts_returnsPage() throws Exception {
        UUID productId = UUID.randomUUID();
        CanonicalProduct product = new CanonicalProduct();
        product.setId(productId);
        product.setSku("SKU-001");
        product.setName("Test Product");
        product.setUnitPrice(new BigDecimal("19.99"));

        CanonicalProductDto dto = CanonicalProductDto.builder()
                .id(productId)
                .sku("SKU-001")
                .name("Test Product")
                .unitPrice(new BigDecimal("19.99"))
                .build();

        when(canonicalProductRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product), PageRequest.of(0, 20), 1));
        when(canonicalMapper.toDto(product)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/canonical/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(productId.toString()))
                .andExpect(jsonPath("$.content[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$.content[0].name").value("Test Product"))
                .andExpect(jsonPath("$.content[0].unitPrice").value(19.99))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getOrders_returnsPage() throws Exception {
        UUID orderId = UUID.randomUUID();
        CanonicalOrder order = new CanonicalOrder();
        order.setId(orderId);
        order.setOrderDate(LocalDate.of(2026, 6, 1));
        order.setTotalAmount(new BigDecimal("250.00"));

        CanonicalOrderDto dto = CanonicalOrderDto.builder()
                .id(orderId)
                .orderDate(LocalDate.of(2026, 6, 1))
                .totalAmount(new BigDecimal("250.00"))
                .build();

        when(canonicalOrderRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1));
        when(canonicalMapper.toDto(order)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/canonical/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(orderId.toString()))
                .andExpect(jsonPath("$.content[0].orderDate").value("2026-06-01"))
                .andExpect(jsonPath("$.content[0].totalAmount").value(250.00))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
