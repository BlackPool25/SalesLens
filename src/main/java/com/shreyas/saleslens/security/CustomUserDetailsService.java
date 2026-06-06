package com.shreyas.saleslens.security;

import com.shreyas.saleslens.model.Users;
import com.shreyas.saleslens.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UsersRepository usersRepository;

    @Override
    public UserPrincipal loadUserByUsername(String identifier) throws UsernameNotFoundException {
        Users user = usersRepository.findByUsernameOrEmail(identifier, identifier)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials!"));


        return new UserPrincipal(user);
    }
}
