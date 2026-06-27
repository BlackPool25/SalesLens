package com.shreyas.saleslens.controller;
import com.shreyas.saleslens.config.TestCacheConfig;
import org.springframework.context.annotation.Import;
import com.shreyas.saleslens.config.filters.JwtFilter;
import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.FieldMapping;
import com.shreyas.saleslens.repository.FieldMappingRepository;
import com.shreyas.saleslens.security.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebMvcTest(MappingController.class)
@Import(TestCacheConfig.class)
class MappingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FieldMappingRepository fieldMappingRepository;

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
    void testGetFieldMappings() throws Exception {
        UUID sourceId = UUID.randomUUID();
        DataSource source = new DataSource();
        source.setId(sourceId);

        FieldMapping m = new FieldMapping();
        m.setId(UUID.randomUUID());
        m.setSource(source);
        m.setSourceFieldName("Sales");
        m.setCanonicalEntity("orders");
        m.setCanonicalField("total_amount");
        m.setConfidence(BigDecimal.valueOf(1.00));
        m.setStatus("AUTO_CONFIRMED");

        Page<FieldMapping> page = new PageImpl<>(List.of(m));
        when(fieldMappingRepository.findBySourceId(eq(sourceId), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/sources/{sourceId}/mappings", sourceId)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sourceFieldName").value("Sales"))
                .andExpect(jsonPath("$.content[0].canonicalEntity").value("orders"))
                .andExpect(jsonPath("$.content[0].canonicalField").value("total_amount"))
                .andExpect(jsonPath("$.content[0].status").value("AUTO_CONFIRMED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testConfirmMapping() throws Exception {
        UUID sourceId = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();
        DataSource source = new DataSource();
        source.setId(sourceId);

        FieldMapping m = new FieldMapping();
        m.setId(mappingId);
        m.setSource(source);
        m.setSourceFieldName("Sales");
        m.setCanonicalEntity("orders");
        m.setCanonicalField("total_amount");
        m.setConfidence(BigDecimal.valueOf(0.70));
        m.setStatus("PENDING");

        when(fieldMappingRepository.findById(mappingId)).thenReturn(Optional.of(m));
        when(fieldMappingRepository.save(any(FieldMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/v1/sources/{sourceId}/mappings/{mappingId}/confirm", sourceId, mappingId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTO_CONFIRMED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testOverrideMapping() throws Exception {
        UUID sourceId = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();
        DataSource source = new DataSource();
        source.setId(sourceId);

        FieldMapping m = new FieldMapping();
        m.setId(mappingId);
        m.setSource(source);
        m.setSourceFieldName("Sales");
        m.setCanonicalEntity("orders");
        m.setCanonicalField("total_amount");
        m.setConfidence(BigDecimal.valueOf(0.70));
        m.setStatus("PENDING");

        when(fieldMappingRepository.findById(mappingId)).thenReturn(Optional.of(m));
        when(fieldMappingRepository.save(any(FieldMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/v1/sources/{sourceId}/mappings/{mappingId}/override", sourceId, mappingId)
                        .param("canonicalEntity", "customers")
                        .param("canonicalField", "name")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canonicalEntity").value("customers"))
                .andExpect(jsonPath("$.canonicalField").value("name"))
                .andExpect(jsonPath("$.status").value("AUTO_CONFIRMED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testIgnoreMapping() throws Exception {
        UUID sourceId = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();
        DataSource source = new DataSource();
        source.setId(sourceId);

        FieldMapping m = new FieldMapping();
        m.setId(mappingId);
        m.setSource(source);
        m.setSourceFieldName("Sales");
        m.setCanonicalEntity("orders");
        m.setCanonicalField("total_amount");
        m.setConfidence(BigDecimal.valueOf(0.70));
        m.setStatus("PENDING");

        when(fieldMappingRepository.findById(mappingId)).thenReturn(Optional.of(m));
        when(fieldMappingRepository.save(any(FieldMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/v1/sources/{sourceId}/mappings/{mappingId}/ignore", sourceId, mappingId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IGNORED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testMappingNotFound() throws Exception {
        UUID sourceId = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();

        when(fieldMappingRepository.findById(mappingId)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/sources/{sourceId}/mappings/{mappingId}/confirm", sourceId, mappingId)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }
}
