package com.shreyas.saleslens.controller;

import com.shreyas.saleslens.dto.QualityIssueDto;
import com.shreyas.saleslens.dto.QualityScoreDto;
import com.shreyas.saleslens.model.QualityIssue;
import com.shreyas.saleslens.model.QualityScore;
import com.shreyas.saleslens.model.enums.IssueStatus;
import com.shreyas.saleslens.model.enums.QualityDimension;
import com.shreyas.saleslens.model.enums.QualitySeverity;
import com.shreyas.saleslens.repository.QualityIssueRepository;
import com.shreyas.saleslens.repository.QualityScoreRepository;
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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quality")
@RequiredArgsConstructor
@Slf4j
@Transactional
@PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
@Tag(name = "Quality", description = "Endpoints for querying data quality issues, scores, and acknowledging issues")
public class QualityController {

    private final QualityIssueRepository qualityIssueRepository;
    private final QualityScoreRepository qualityScoreRepository;

    @Operation(summary = "Get quality issues", description = "Returns a paginated list of data quality issues with optional filtering by source, severity, dimension, and status")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Issues retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid filter parameters"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN or ANALYST role)")
    })
    @GetMapping("/issues")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<QualityIssueDto>> getIssues(
            @RequestParam(required = false) UUID sourceId,
            @RequestParam(required = false) QualitySeverity severity,
            @RequestParam(required = false) QualityDimension dimension,
            @RequestParam(required = false) IssueStatus status,
            @RequestParam(required = false) UUID jobId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        log.info("Fetching filtered quality issues: sourceId={}, severity={}, dimension={}, status={}, jobId={}",
                sourceId, severity, dimension, status, jobId);

        Page<QualityIssue> issues = qualityIssueRepository.findFiltered(sourceId, severity, dimension, status, jobId, pageable);
        Page<QualityIssueDto> dtoPage = issues.map(this::mapIssueToDto);

        return ResponseEntity.ok(dtoPage);
    }

    @Operation(summary = "Get quality scores", description = "Returns a paginated list of historical quality scores for a given data source")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Scores retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Missing required sourceId parameter"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN or ANALYST role)")
    })
    @GetMapping("/scores")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<QualityScoreDto>> getScores(
            @RequestParam UUID sourceId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        log.info("Fetching historical quality scores for sourceId: {}", sourceId);
        Page<QualityScoreDto> dtoPage = qualityScoreRepository
                .findBySourceIdOrderByCreatedAtDesc(sourceId, pageable)
                .map(this::mapScoreToDto);
        return ResponseEntity.ok(dtoPage);
    }

    @Operation(summary = "Acknowledge a quality issue", description = "Marks a quality issue as acknowledged")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Issue acknowledged successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN or ANALYST role)"),
        @ApiResponse(responseCode = "404", description = "Issue not found")
    })
    @PutMapping("/issues/{issueId}/acknowledge")
    public ResponseEntity<QualityIssueDto> acknowledgeIssue(@PathVariable UUID issueId) {
        log.info("Acknowledging quality issueId: {}", issueId);
        return qualityIssueRepository.findById(issueId)
                .map(issue -> {
                    issue.setStatus(IssueStatus.ACKNOWLEDGED);
                    QualityIssue saved = qualityIssueRepository.save(issue);
                    return ResponseEntity.ok(mapIssueToDto(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private QualityIssueDto mapIssueToDto(QualityIssue issue) {
        return QualityIssueDto.builder()
                .id(issue.getId())
                .runId(issue.getRun() != null ? issue.getRun().getId() : null)
                .sourceId(issue.getSource() != null ? issue.getSource().getId() : null)
                .stagedRecordId(issue.getStagedRecord() != null ? issue.getStagedRecord().getId() : null)
                .sourceFieldName(issue.getSourceFieldName())
                .ruleCode(issue.getRuleCode())
                .severity(issue.getSeverity())
                .dimension(issue.getDimension())
                .message(issue.getMessage())
                .status(issue.getStatus())
                .createdAt(issue.getCreatedAt())
                .build();
    }

    private QualityScoreDto mapScoreToDto(QualityScore score) {
        return QualityScoreDto.builder()
                .id(score.getId())
                .jobId(score.getJob() != null ? score.getJob().getId() : null)
                .sourceId(score.getSource() != null ? score.getSource().getId() : null)
                .scoreCompleteness(score.getScoreCompleteness())
                .scoreValidity(score.getScoreValidity())
                .scoreUniqueness(score.getScoreUniqueness())
                .scoreConsistency(score.getScoreConsistency())
                .scoreTimeliness(score.getScoreTimeliness())
                .scoreAccuracy(score.getScoreAccuracy())
                .scoreOverall(score.getScoreOverall())
                .letterGrade(score.getLetterGrade())
                .createdAt(score.getCreatedAt())
                .build();
    }
}
