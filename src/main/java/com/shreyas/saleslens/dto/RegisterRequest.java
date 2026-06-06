package com.shreyas.saleslens.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class RegisterRequest {
    String firstName;
    String lastName;
    String email;
    String username;
    String password;
}
