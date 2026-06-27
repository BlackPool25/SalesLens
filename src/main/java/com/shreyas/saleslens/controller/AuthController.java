package com.shreyas.saleslens.controller;


import com.shreyas.saleslens.dto.AuthResponse;
import com.shreyas.saleslens.dto.LoginRequest;
import com.shreyas.saleslens.dto.RegisterRequest;
import com.shreyas.saleslens.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public String registerUser(@Validated @RequestBody RegisterRequest registerRequest) {
        return authService.registerUser(registerRequest);
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
            return ResponseEntity.ok(authService.loginUser(request));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
    }
}
