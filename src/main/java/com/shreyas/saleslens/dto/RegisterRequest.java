package com.shreyas.saleslens.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Setter;
import lombok.Getter;

@Setter
@Getter
@Schema(description = "User registration request")
public class RegisterRequest {
    @Schema(description = "User's first name", example = "John")
    String firstName;

    @Schema(description = "User's last name", example = "Doe")
    String lastName;

    @Schema(description = "User email address", example = "johndoe@example.com")
    String email;

    @Schema(description = "Desired username", example = "johndoe")
    String username;

    @Schema(description = "Desired password", example = "SecurePassword123!")
    String password;
}
