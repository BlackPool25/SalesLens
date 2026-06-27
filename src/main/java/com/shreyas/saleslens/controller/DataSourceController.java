package com.shreyas.saleslens.controller;

import com.shreyas.saleslens.dto.CreateSourceRequest;
import com.shreyas.saleslens.dto.DataSourceResponse;
import com.shreyas.saleslens.security.UserPrincipal;
import com.shreyas.saleslens.service.DataSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Data Sources", description = "Endpoints for managing data source configurations")
public class DataSourceController {

    private final DataSourceService dataSourceService;

    @Operation(summary = "Create a data source", description = "Registers a new data source configuration (CSV_FILE, JDBC_POSTGRES, JDBC_MYSQL, etc.)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Data source created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request body / validation error"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN or ANALYST role)")
    })
    @PostMapping("/create-source")
    public String createSource(@Valid @RequestBody CreateSourceRequest request) {

        UserPrincipal userPrincipal = (UserPrincipal) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();

        return dataSourceService.createSource(request, userPrincipal.getId());
    }

    @Operation(summary = "Get all data sources", description = "Returns a paginated list of all registered data sources")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Data sources retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN or ANALYST role)")
    })
    @GetMapping("/get-all-sources")
    public Page<DataSourceResponse> getAllSources(@PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return dataSourceService.getAllSources(pageable);
    }

    @Operation(summary = "Get data source by ID", description = "Returns a single data source by its UUID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Data source retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN or ANALYST role)")
    })
    @GetMapping("/get-by-id")
    public DataSourceResponse getBySourceId(@RequestParam UUID id) {
        return dataSourceService.getBySourceId(id);
    }

    @Operation(summary = "Get data sources by user", description = "Returns a paginated list of data sources created by the specified user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Data sources retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (requires ADMIN or ANALYST role)")
    })
    @GetMapping("/get-by-user")
    public Page<DataSourceResponse> getByUser(@RequestParam Long id, @PageableDefault(size = 20) Pageable pageable) {
        return dataSourceService.getByUser(id, pageable);
    }
}
