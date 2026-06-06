package com.shreyas.saleslens.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
public class Users {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    @NotNull
    private String username;
    private String firstName;
    private String lastName;

    @Column(unique = true)
    @NotNull
    private String email;

    @NotNull
    private String password;

    private String role;

}
