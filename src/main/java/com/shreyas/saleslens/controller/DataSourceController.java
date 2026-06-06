package com.shreyas.saleslens.controller;

import com.shreyas.saleslens.dto.CreateSourceRequest;
import com.shreyas.saleslens.dto.DataSourceResponse;
import com.shreyas.saleslens.security.UserPrincipal;
import com.shreyas.saleslens.service.DataSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequiredArgsConstructor
@RequestMapping("/datasources")
public class DataSourceController {

    private final DataSourceService dataSourceService;

    @PostMapping("/create-source")
    public String createSource(@RequestBody CreateSourceRequest request) {

        UserPrincipal userPrincipal = (UserPrincipal) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();

        return dataSourceService.createSource(request, userPrincipal.getId());
    }

    @GetMapping("/get-all-sources")
    public List<DataSourceResponse> getAllSources(){
        return dataSourceService.getAllSources();
    }
}
