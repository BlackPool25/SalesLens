package com.shreyas.saleslens.controller;
import com.shreyas.saleslens.config.TestCacheConfig;
import org.springframework.context.annotation.Import;
import com.shreyas.saleslens.config.filters.JwtFilter;
import com.shreyas.saleslens.dto.QualityIssueDto;
import com.shreyas.saleslens.dto.QualityScoreDto;
import com.shreyas.saleslens.model.*;
import com.shreyas.saleslens.model.enums.IssueStatus;
import com.shreyas.saleslens.model.enums.QualityDimension;
import com.shreyas.saleslens.model.enums.QualitySeverity;
import com.shreyas.saleslens.repository.QualityIssueRepository;
import com.shreyas.saleslens.repository.QualityScoreRepository;
import com.shreyas.saleslens.security.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QualityController.class)
@Import(TestCacheConfig.class)
class QualityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QualityIssueRepository qualityIssueRepository;

    @MockitoBean
    private QualityScoreRepository qualityScoreRepository;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JwtFilter jwtFilter;

    @BeforeEach
    void setUp() throws Exception {
        // Bypass JwtFilter
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
    void testGetIssues() throws Exception {
        UUID sourceId = UUID.randomUUID();
        QualityRun run = new QualityRun();
        run.setId(UUID.randomUUID());
        DataSource source = new DataSource();
        source.setId(sourceId);
        StagedRecord record = new StagedRecord();
        record.setId(UUID.randomUUID());

        QualityIssue issue = new QualityIssue();
        issue.setId(UUID.randomUUID());
        issue.setRun(run);
        issue.setSource(source);
        issue.setStagedRecord(record);
        issue.setSourceFieldName("total_amount");
        issue.setRuleCode("VALIDITY_NEGATIVE_NUMBER");
        issue.setSeverity(QualitySeverity.HIGH);
        issue.setDimension(QualityDimension.VALIDITY);
        issue.setMessage("Negative amount detected");
        issue.setStatus(IssueStatus.OPEN);

        // Stub findFiltered mapping
        when(qualityIssueRepository.findFiltered(eq(sourceId), eq(QualitySeverity.HIGH), eq(QualityDimension.VALIDITY), eq(IssueStatus.OPEN), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(issue), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/quality/issues")
                        .param("sourceId", sourceId.toString())
                        .param("severity", "HIGH")
                        .param("dimension", "VALIDITY")
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sourceFieldName").value("total_amount"))
                .andExpect(jsonPath("$.content[0].ruleCode").value("VALIDITY_NEGATIVE_NUMBER"))
                .andExpect(jsonPath("$.content[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.content[0].dimension").value("VALIDITY"))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetScores() throws Exception {
        UUID sourceId = UUID.randomUUID();
        IngestionJob job = new IngestionJob();
        job.setId(UUID.randomUUID());
        DataSource source = new DataSource();
        source.setId(sourceId);

        QualityScore score = new QualityScore();
        score.setId(UUID.randomUUID());
        score.setJob(job);
        score.setSource(source);
        score.setScoreCompleteness(BigDecimal.valueOf(1.0000));
        score.setScoreValidity(BigDecimal.valueOf(0.9500));
        score.setScoreUniqueness(BigDecimal.valueOf(1.0000));
        score.setScoreConsistency(BigDecimal.valueOf(0.9000));
        score.setScoreTimeliness(BigDecimal.valueOf(0.9000));
        score.setScoreAccuracy(BigDecimal.valueOf(1.0000));
        score.setScoreOverall(BigDecimal.valueOf(0.9550));
        score.setLetterGrade("A");

        when(qualityScoreRepository.findBySourceIdOrderByCreatedAtDesc(eq(sourceId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(score), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/quality/scores")
                        .param("sourceId", sourceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].scoreOverall").value(0.9550))
                .andExpect(jsonPath("$.content[0].letterGrade").value("A"))
                .andExpect(jsonPath("$.content[0].scoreValidity").value(0.9500))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testAcknowledgeIssue() throws Exception {
        UUID issueId = UUID.randomUUID();
        QualityIssue issue = new QualityIssue();
        issue.setId(issueId);
        issue.setSourceFieldName("total_amount");
        issue.setRuleCode("VALIDITY_NEGATIVE_NUMBER");
        issue.setSeverity(QualitySeverity.HIGH);
        issue.setDimension(QualityDimension.VALIDITY);
        issue.setStatus(IssueStatus.OPEN);

        when(qualityIssueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(qualityIssueRepository.save(any(QualityIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/v1/quality/issues/{issueId}/acknowledge", issueId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
    }
}
