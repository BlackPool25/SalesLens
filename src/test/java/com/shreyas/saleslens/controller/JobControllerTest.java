package com.shreyas.saleslens.controller;

import com.shreyas.saleslens.config.filters.JwtFilter;
import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.enums.JobStatus;
import com.shreyas.saleslens.repository.IngestionJobRepository;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestionJobRepository ingestionJobRepository;

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

    private IngestionJob createTestJob() {
        DataSource source = new DataSource();
        source.setId(UUID.randomUUID());

        IngestionJob job = new IngestionJob();
        job.setId(UUID.randomUUID());
        job.setSource(source);
        job.setStatus(JobStatus.COMPLETED);
        job.setTotalRead(100);
        job.setTotalTransformed(95);
        job.setTotalQualityPass(90);
        job.setTotalQualityFail(5);
        job.setTotalLoaded(85);
        job.setTotalConflicted(2);
        job.setCreatedAt(Instant.now());
        return job;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllJobs_returnsPage() throws Exception {
        IngestionJob job = createTestJob();

        when(ingestionJobRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(job), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(job.getId().toString()))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getJob_returnsJob() throws Exception {
        IngestionJob job = createTestJob();

        when(ingestionJobRepository.findById(job.getId()))
                .thenReturn(Optional.of(job));

        mockMvc.perform(get("/api/v1/jobs/{id}", job.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(job.getId().toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalRead").value(100));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getJob_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();

        when(ingestionJobRepository.findById(id))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/jobs/{id}", id))
                .andExpect(status().isNotFound());
    }
}
