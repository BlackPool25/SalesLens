package com.shreyas.saleslens.service;

import com.shreyas.saleslens.config.filters.TokenType;
import com.shreyas.saleslens.dto.AuthResponse;
import com.shreyas.saleslens.dto.LoginRequest;
import com.shreyas.saleslens.dto.RegisterRequest;
import com.shreyas.saleslens.model.Users;
import com.shreyas.saleslens.repository.UsersRepository;
import com.shreyas.saleslens.security.CustomUserDetailsService;
import com.shreyas.saleslens.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService customUserDetailsService;

    public boolean checkUserExists(String username, String email) {
        return usersRepository.findByUsernameOrEmail(username, email).isPresent();
    }

    public String registerUser(RegisterRequest registerRequest) {

        if (checkUserExists(registerRequest.getUsername(), registerRequest.getEmail())) {
            return "User already exists";
        }

        Users user = new Users();
        user.setUsername(registerRequest.getUsername());
        user.setEmail(registerRequest.getEmail());
        user.setFirstName(registerRequest.getFirstName());
        user.setLastName(registerRequest.getLastName());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setRole("USER");
        usersRepository.save(user);

        return "User: " + user.getUsername() + " registered successfully!";
    }

    public AuthResponse loginUser(LoginRequest loginRequest) {

        Users user = usersRepository
                .findByUsernameOrEmail(loginRequest.getIdentifier(), loginRequest.getIdentifier())
                .orElseThrow(() -> new RuntimeException("Invalid credentials!"));

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getIdentifier(),
                        loginRequest.getPassword()
                )
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        assert userDetails != null;
        return new AuthResponse(jwtUtil.generateAccessToken(userDetails), jwtUtil.generateRefreshToken(userDetails));
    }

    public AuthResponse refreshAccessToken(String refreshToken) {
        var username = jwtUtil.getUsernameFromToken(refreshToken);
        var userDetails = customUserDetailsService.loadUserByUsername(username);

        if (jwtUtil.isTokenValid(refreshToken, userDetails, TokenType.REFRESH)) {
            return new AuthResponse(jwtUtil.generateAccessToken(userDetails), null);
        }

        throw new BadCredentialsException("Invalid refresh token");
    }
}
