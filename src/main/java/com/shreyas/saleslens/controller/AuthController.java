package com.shreyas.saleslens.controller;


import com.shreyas.saleslens.dto.AuthResponse;
import com.shreyas.saleslens.dto.LoginRequest;
import com.shreyas.saleslens.dto.RegisterRequest;
import com.shreyas.saleslens.dto.UserProfileResponse;
import com.shreyas.saleslens.security.UserPrincipal;
import com.shreyas.saleslens.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Endpoints for user registration and login")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a new user", description = "Creates a new user account with the provided credentials")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User registered successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request body / validation error")
    })
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> registerUser(@Validated @RequestBody RegisterRequest registerRequest) {
        authService.registerUser(registerRequest);
        return ResponseEntity.ok(Map.of("message", "User registered successfully"));
    }

    @Operation(summary = "Login a user", description = "Authenticates a user and returns JWT tokens")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful, tokens returned"),
        @ApiResponse(responseCode = "400", description = "Invalid request body / validation error"),
        @ApiResponse(responseCode = "401", description = "Bad credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> loginUser(@Validated @RequestBody LoginRequest request) {
        try {
            var authResponse = authService.loginUser(request);
            var refreshCookie = ResponseCookie.from("refreshToken", authResponse.getRefreshToken())
                    .httpOnly(true)
                    .secure(false)
                    .sameSite("Lax")
                    .path("/auth/refresh")
                    .maxAge(604800)
                    .build();
            var body = new AuthResponse(authResponse.getAccessToken(), null);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                    .body(body);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @Operation(summary = "Refresh access token", description = "Uses a refresh token from httpOnly cookie to issue a new access token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "New access token issued"),
        @ApiResponse(responseCode = "401", description = "Invalid or missing refresh token")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshAccessToken(
            @CookieValue(name = "refreshToken", required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            var authResponse = authService.refreshAccessToken(refreshToken);
            return ResponseEntity.ok(authResponse);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @Operation(summary = "Get current user profile", description = "Returns the authenticated user's profile information")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User profile retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token")
    })
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var roles = principal.getAuthorities().stream()
                .map(Object::toString)
                .toList();
        var profile = new UserProfileResponse(
                principal.getId(),
                principal.getUsername(),
                principal.getEmail(),
                roles
        );
        return ResponseEntity.ok(profile);
    }
}
