package com.shreyas.saleslens.controller;

import com.shreyas.saleslens.config.filters.JwtFilter;
import com.shreyas.saleslens.model.SourceSchema;
import com.shreyas.saleslens.model.SourceSchemaField;
import com.shreyas.saleslens.model.enums.InferredType;
import com.shreyas.saleslens.repository.SourceSchemaFieldRepository;
import com.shreyas.saleslens.repository.SourceSchemaRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SchemaController.class)
class SchemaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SourceSchemaRepository sourceSchemaRepository;

    @MockitoBean
    private SourceSchemaFieldRepository sourceSchemaFieldRepository;

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

    private SourceSchema createTestSchema() {
        SourceSchema schema = new SourceSchema();
        schema.setId(UUID.randomUUID());
        schema.setVersion(3);
        schema.setStatus(SourceSchema.STATUS_ACTIVE);
        schema.setCreatedAt(Instant.parse("2026-06-26T10:00:00Z"));
        return schema;
    }

    private SourceSchemaField createTestField() {
        SourceSchemaField field = new SourceSchemaField();
        field.setId(UUID.randomUUID());
        field.setFieldName("email");
        field.setInferredType(InferredType.EMAIL);
        field.setDetectedFormat("email");
        field.setNullable(false);
        field.setSampleValues("[\"user@example.com\"]");
        return field;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getCurrentSchema_returnsSchema() throws Exception {
        SourceSchema schema = createTestSchema();
        SourceSchemaField field = createTestField();
        UUID sourceId = UUID.randomUUID();

        when(sourceSchemaRepository.findBySourceIdAndStatus(sourceId, SourceSchema.STATUS_ACTIVE))
                .thenReturn(Optional.of(schema));
        when(sourceSchemaFieldRepository.findBySchemaId(schema.getId()))
                .thenReturn(List.of(field));

        mockMvc.perform(get("/api/v1/sources/{sourceId}/schema", sourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(schema.getId().toString()))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.fields[0].fieldName").value("email"))
                .andExpect(jsonPath("$.fields[0].inferredType").value("EMAIL"))
                .andExpect(jsonPath("$.fields[0].nullable").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getCurrentSchema_notFound_returns404() throws Exception {
        UUID sourceId = UUID.randomUUID();

        when(sourceSchemaRepository.findBySourceIdAndStatus(sourceId, SourceSchema.STATUS_ACTIVE))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/sources/{sourceId}/schema", sourceId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSchemaHistory_returnsPage() throws Exception {
        SourceSchema schema = createTestSchema();
        SourceSchemaField field = createTestField();
        UUID sourceId = UUID.randomUUID();

        when(sourceSchemaRepository.findBySourceIdOrderByVersionDesc(eq(sourceId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(schema), PageRequest.of(0, 20), 1));
        when(sourceSchemaFieldRepository.findBySchemaId(schema.getId()))
                .thenReturn(List.of(field));

        mockMvc.perform(get("/api/v1/sources/{sourceId}/schema/drift", sourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(schema.getId().toString()))
                .andExpect(jsonPath("$.content[0].version").value(3))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].fields[0].fieldName").value("email"))
                .andExpect(jsonPath("$.content[0].fields[0].inferredType").value("EMAIL"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
