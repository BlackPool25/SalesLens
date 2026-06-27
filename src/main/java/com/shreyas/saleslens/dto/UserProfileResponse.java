package com.shreyas.saleslens.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "User profile response with user details and roles")
public record UserProfileResponse(
        @Schema(description = "User ID", example = "1")
        Long id,

        @Schema(description = "Username", example = "johndoe")
        String username,

        @Schema(description = "Email address", example = "johndoe@example.com")
        String email,

        @Schema(description = "List of assigned roles", example = "[\"ROLE_USER\"]")
        List<String> roles
) {}
