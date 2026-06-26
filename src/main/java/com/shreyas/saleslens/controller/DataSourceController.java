package com.shreyas.saleslens.controller;

import com.shreyas.saleslens.dto.CreateSourceRequest;
import com.shreyas.saleslens.dto.DataSourceResponse;
import com.shreyas.saleslens.security.UserPrincipal;
import com.shreyas.saleslens.service.DataSourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/datasources")
@PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
public class DataSourceController {

    private final DataSourceService dataSourceService;

    @PostMapping("/create-source")
    public String createSource(@Valid @RequestBody CreateSourceRequest request) {

        UserPrincipal userPrincipal = (UserPrincipal) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();

        return dataSourceService.createSource(request, userPrincipal.getId());
    }

    @GetMapping("/get-all-sources")
    public Page<DataSourceResponse> getAllSources(@PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return dataSourceService.getAllSources(pageable);
    }

    @GetMapping("/get-by-id")
    public DataSourceResponse getBySourceId(@RequestParam UUID id) {
        return dataSourceService.getBySourceId(id);
    }

    @GetMapping("/get-by-user")
    public Page<DataSourceResponse> getByUser(@RequestParam Long id, @PageableDefault(size = 20) Pageable pageable) {
        return dataSourceService.getByUser(id, pageable);
    }
}
