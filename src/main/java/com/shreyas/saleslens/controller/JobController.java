package com.shreyas.saleslens.controller;

import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.repository.IngestionJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
public class JobController {

    private final IngestionJobRepository ingestionJobRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> getAllJobs(@PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ingestionJobRepository.findAll(pageable).map(JobController::toResponse);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getJob(@PathVariable UUID id) {
        return ingestionJobRepository.findById(id)
                .map(JobController::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private static Map<String, Object> toResponse(IngestionJob job) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", job.getId());
        map.put("sourceId", job.getSource().getId());
        map.put("status", job.getStatus());
        map.put("totalRead", job.getTotalRead());
        map.put("totalTransformed", job.getTotalTransformed());
        map.put("totalQualityPass", job.getTotalQualityPass());
        map.put("totalQualityFail", job.getTotalQualityFail());
        map.put("totalLoaded", job.getTotalLoaded());
        map.put("totalConflicted", job.getTotalConflicted());
        map.put("errorMessage", job.getErrorMessage());
        map.put("startedAt", job.getStartedAt());
        map.put("completedAt", job.getCompletedAt());
        map.put("createdAt", job.getCreatedAt());
        return map;
    }
}
