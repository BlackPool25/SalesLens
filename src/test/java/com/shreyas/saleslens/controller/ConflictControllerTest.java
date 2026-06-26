package com.shreyas.saleslens.controller;

import com.shreyas.saleslens.config.filters.JwtFilter;
import com.shreyas.saleslens.model.ConflictRecord;
import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.enums.ConflictStatus;
import com.shreyas.saleslens.model.enums.ResolutionStrategy;
import com.shreyas.saleslens.repository.ConflictRecordRepository;
import com.shreyas.saleslens.security.CustomUserDetailsService;
import com.shreyas.saleslens.service.conflict.ConflictResolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConflictController.class)
class ConflictControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConflictResolutionService conflictResolutionService;

    @MockitoBean
    private ConflictRecordRepository conflictRecordRepository;

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

    private ConflictRecord createTestRecord() {
        DataSource sourceA = new DataSource();
        sourceA.setId(UUID.randomUUID());
        DataSource sourceB = new DataSource();
        sourceB.setId(UUID.randomUUID());

        ConflictRecord record = new ConflictRecord();
        record.setId(UUID.randomUUID());
        record.setEntityType("CanonicalCustomer");
        record.setEntityId(UUID.randomUUID());
        record.setFieldName("email");
        record.setSourceA(sourceA);
        record.setSourceB(sourceB);
        record.setValueA("john@example.com");
        record.setValueB("john.doe@example.com");
        record.setResolutionStrategy(ResolutionStrategy.FLAGGED_FOR_REVIEW);
        record.setStatus(ConflictStatus.OPEN);
        record.setCreatedAt(Instant.now());
        return record;
    }

    @Test
    @WithMockUser(roles = "ADMIN")(roles = "ADMIN")
    void testListConflicts() throws Exception {
        ConflictRecord record = createTestRecord();

        when(conflictRecordRepository.findFiltered(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(record), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/conflicts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].entityType").value("CanonicalCustomer"))
                .andExpect(jsonPath("$.content[0].fieldName").value("email"))
                .andExpect(jsonPath("$.content[0].valueA").value("john@example.com"))
                .andExpect(jsonPath("$.content[0].valueB").value("john.doe@example.com"))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetConflictById() throws Exception {
        ConflictRecord record = createTestRecord();

        when(conflictRecordRepository.findById(record.getId()))
                .thenReturn(Optional.of(record));

        mockMvc.perform(get("/api/v1/conflicts/{id}", record.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(record.getId().toString()))
                .andExpect(jsonPath("$.entityType").value("CanonicalCustomer"))
                .andExpect(jsonPath("$.fieldName").value("email"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")(roles = "ADMIN")
    void testGetConflictById_NotFound() throws Exception {
        UUID id = UUID.randomUUID();

        when(conflictRecordRepository.findById(id))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/conflicts/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testResolveConflict() throws Exception {
        ConflictRecord record = createTestRecord();
        String chosenValue = "john@example.com";
        String json = "{\"chosenValue\":\"" + chosenValue + "\"}";

        when(conflictResolutionService.resolveConflict(eq(record.getId()), eq(chosenValue), isNull()))
                .thenReturn(Optional.of(record));

        mockMvc.perform(put("/api/v1/conflicts/{id}/resolve", record.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityType").value("CanonicalCustomer"))
                .andExpect(jsonPath("$.fieldName").value("email"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")(roles = "ADMIN")
    void testResolveConflict_NotFound() throws Exception {
        UUID id = UUID.randomUUID();
        String json = "{\"chosenValue\":\"value\"}";

        when(conflictResolutionService.resolveConflict(eq(id), eq("value"), isNull()))
                .thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/conflicts/{id}/resolve", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testSuppressConflict() throws Exception {
        ConflictRecord record = createTestRecord();

        when(conflictResolutionService.suppressConflict(eq(record.getId()), isNull()))
                .thenReturn(Optional.of(record));

        mockMvc.perform(put("/api/v1/conflicts/{id}/suppress", record.getId())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityType").value("CanonicalCustomer"))
                .andExpect(jsonPath("$.fieldName").value("email"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")(roles = "ADMIN")
    void testSuppressConflict_NotFound() throws Exception {
        UUID id = UUID.randomUUID();

        when(conflictResolutionService.suppressConflict(eq(id), isNull()))
                .thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/conflicts/{id}/suppress", id)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }
}
