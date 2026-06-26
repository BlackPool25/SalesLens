package com.shreyas.saleslens.controller;

import com.shreyas.saleslens.config.filters.JwtFilter;
import com.shreyas.saleslens.security.CustomUserDetailsService;
import com.shreyas.saleslens.service.ingestion.IngestionOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestionController.class)
class IngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestionOrchestrator ingestionOrchestrator;

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
    @WithMockUser
    void uploadExcel_validFile_returns202() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy,data\n1,2".getBytes()
        );

        when(ingestionOrchestrator.ingestExcel(any(), any())).thenReturn(jobId);

        mockMvc.perform(multipart("/api/v1/ingest/excel")
                        .file(file)
                        .param("sourceId", sourceId.toString())
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.message").value("Excel ingestion job accepted; poll /api/v1/jobs/" + jobId + " for status"));
    }

    @Test
    @WithMockUser
    void uploadExcel_csvFile_returns400() throws Exception {
        UUID sourceId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "data.csv",
                "text/csv",
                "dummy,data\n1,2".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/ingest/excel")
                        .file(file)
                        .param("sourceId", sourceId.toString())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Only .xlsx files are accepted"));
    }

    @Test
    @WithMockUser
    void uploadExcel_noFile_returns400() throws Exception {
        UUID sourceId = UUID.randomUUID();

        mockMvc.perform(multipart("/api/v1/ingest/excel")
                        .param("sourceId", sourceId.toString())
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void uploadExcel_nonExistentSource_returns500() {
        UUID sourceId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy,data\n1,2".getBytes()
        );

        when(ingestionOrchestrator.ingestExcel(any(), any()))
                .thenThrow(new IllegalArgumentException("Source not found: " + sourceId));

        ServletException ex = assertThrows(ServletException.class, () ->
                mockMvc.perform(multipart("/api/v1/ingest/excel")
                        .file(file)
                        .param("sourceId", sourceId.toString())
                        .with(csrf()))
        );
        assertInstanceOf(IllegalArgumentException.class, ex.getRootCause());
    }

    @Test
    @WithMockUser
    void triggerJdbc_validSource_returns202() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();

        when(ingestionOrchestrator.triggerJdbcIngestion(sourceId)).thenReturn(jobId);

        mockMvc.perform(post("/api/v1/ingest/jdbc/{sourceId}", sourceId)
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.message").value("JDBC ingestion job accepted; poll /api/v1/jobs/" + jobId + " for status"));
    }

    @Test
    @WithMockUser
    void triggerJdbc_orchestratorThrows_throwsServletException() {
        UUID sourceId = UUID.randomUUID();

        when(ingestionOrchestrator.triggerJdbcIngestion(sourceId))
                .thenThrow(new IllegalArgumentException("Source not found: " + sourceId));

        ServletException ex = assertThrows(ServletException.class, () ->
                mockMvc.perform(post("/api/v1/ingest/jdbc/{sourceId}", sourceId)
                        .with(csrf()))
        );
        assertInstanceOf(IllegalArgumentException.class, ex.getRootCause());
    }

    @Test
    @WithMockUser
    void triggerJdbc_inactiveSource_returns500() {
        UUID sourceId = UUID.randomUUID();

        when(ingestionOrchestrator.triggerJdbcIngestion(sourceId))
                .thenThrow(new IllegalArgumentException("Source " + sourceId + " is not active"));

        ServletException ex = assertThrows(ServletException.class, () ->
                mockMvc.perform(post("/api/v1/ingest/jdbc/{sourceId}", sourceId)
                        .with(csrf()))
        );
        assertInstanceOf(IllegalArgumentException.class, ex.getRootCause());
    }

    @Test
    @WithMockUser
    void triggerJdbc_nonJdbcSource_returns500() {
        UUID sourceId = UUID.randomUUID();

        when(ingestionOrchestrator.triggerJdbcIngestion(sourceId))
                .thenThrow(new IllegalArgumentException("Source " + sourceId + " is not a JDBC source"));

        ServletException ex = assertThrows(ServletException.class, () ->
                mockMvc.perform(post("/api/v1/ingest/jdbc/{sourceId}", sourceId)
                        .with(csrf()))
        );
        assertInstanceOf(IllegalArgumentException.class, ex.getRootCause());
    }
}
