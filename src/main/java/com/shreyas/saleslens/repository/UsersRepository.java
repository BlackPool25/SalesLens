package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsersRepository extends JpaRepository<Users,Long> {
    Optional<Users> findByUsernameOrEmail(String username, String email);
}
