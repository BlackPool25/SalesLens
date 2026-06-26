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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/quality")
@RequiredArgsConstructor
@Slf4j
@Transactional
public class QualityController {

    private final QualityIssueRepository qualityIssueRepository;
    private final QualityScoreRepository qualityScoreRepository;

    @GetMapping("/issues")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<QualityIssueDto>> getIssues(
            @RequestParam(required = false) UUID sourceId,
            @RequestParam(required = false) QualitySeverity severity,
            @RequestParam(required = false) QualityDimension dimension,
            @RequestParam(required = false) IssueStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        log.info("Fetching filtered quality issues: sourceId={}, severity={}, dimension={}, status={}",
                sourceId, severity, dimension, status);

        Page<QualityIssue> issues = qualityIssueRepository.findFiltered(sourceId, severity, dimension, status, pageable);
        Page<QualityIssueDto> dtoPage = issues.map(this::mapIssueToDto);

        return ResponseEntity.ok(dtoPage);
    }

    @GetMapping("/scores")
    @Transactional(readOnly = true)
    public ResponseEntity<List<QualityScoreDto>> getScores(@RequestParam UUID sourceId) {
        log.info("Fetching historical quality scores for sourceId: {}", sourceId);
        List<QualityScore> scores = qualityScoreRepository.findBySourceIdOrderByCreatedAtDesc(sourceId);
        List<QualityScoreDto> dtos = scores.stream()
                .map(this::mapScoreToDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

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
