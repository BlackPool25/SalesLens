package com.shreyas.saleslens.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.config.filters.JwtFilter;
import com.shreyas.saleslens.dto.SchemaDemoteRequest;
import com.shreyas.saleslens.dto.SchemaPromoteRequest;
import com.shreyas.saleslens.security.CustomUserDetailsService;
import com.shreyas.saleslens.service.SchemaManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SchemaManagementController.class)
class SchemaManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private SchemaManagementService schemaManagementService;

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
    void testPromoteAttribute_Success() throws Exception {
        SchemaPromoteRequest request = new SchemaPromoteRequest();
        request.setEntityName("orders");
        request.setAttributeKey("discount");
        request.setDataType("DECIMAL");

        mockMvc.perform(post("/api/v1/schema/promote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(schemaManagementService).promoteAttribute("orders", "discount", "DECIMAL");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testPromoteAttribute_InvalidEntityName() throws Exception {
        SchemaPromoteRequest request = new SchemaPromoteRequest();
        request.setEntityName("orders; DROP TABLE canonical.orders; --");
        request.setAttributeKey("discount");
        request.setDataType("DECIMAL");

        mockMvc.perform(post("/api/v1/schema/promote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(schemaManagementService, never()).promoteAttribute(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDemoteColumn_Success() throws Exception {
        SchemaDemoteRequest request = new SchemaDemoteRequest();
        request.setEntityName("orders");
        request.setColumnName("discount");

        mockMvc.perform(post("/api/v1/schema/demote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(schemaManagementService).demoteColumn("orders", "discount");
    }

    @Test
    void testPromoteAttribute_withoutAdminRole_returns403() throws Exception {
        SchemaPromoteRequest request = new SchemaPromoteRequest();
        request.setEntityName("orders");
        request.setAttributeKey("discount");
        request.setDataType("DECIMAL");

        mockMvc.perform(post("/api/v1/schema/promote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(schemaManagementService, never()).promoteAttribute(any(), any(), any());
    }
}
