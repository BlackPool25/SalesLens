package com.shreyas.saleslens.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Authentication response with JWT tokens")
public class AuthResponse {
    @Schema(description = "JWT access token for API authorization", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huZG9lIn0.signature")
    String accessToken;

    @Schema(description = "JWT refresh token for obtaining new access tokens", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huZG9lIiwidHlwZSI6InJlZnJlc2gifQ.signature")
    String refreshToken;

    public AuthResponse(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }
}
