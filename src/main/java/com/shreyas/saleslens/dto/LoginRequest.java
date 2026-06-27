package com.shreyas.saleslens.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Login credentials for authentication")
public class LoginRequest {
    @Schema(description = "Username or email address", example = "johndoe")
    private String identifier;

    @Schema(description = "User password", example = "SecurePassword123!")
    private String password;
}
