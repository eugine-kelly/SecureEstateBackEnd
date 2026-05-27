package com.prod.secureestatebackend.repository;

import com.prod.secureestatebackend.Entities.Role;
import com.prod.secureestatebackend.Entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    long countByRole(Role role);
}