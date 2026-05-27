package com.prod.secureestatebackend.service;


import com.prod.secureestatebackend.Entities.Role;
import com.prod.secureestatebackend.Entities.User;
import com.prod.secureestatebackend.dto.AuthResponse;
import com.prod.secureestatebackend.dto.LoginRequest;
import com.prod.secureestatebackend.dto.RegisterRequest;
import com.prod.secureestatebackend.repository.UserRepository;
import com.prod.secureestatebackend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {

        // Fix 1: Block ADMIN self-registration + handle null role safely
        Role role;
        if (request.getRole() == null) {
            role = Role.BUYER; // default to BUYER if no role provided
        } else {
            role = switch (request.getRole().toUpperCase()) {
                case "SELLER" -> Role.SELLER;
                case "AGENT"  -> Role.AGENT;
                default       -> Role.BUYER; // anything else (including ADMIN) defaults to BUYER
            };
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(role)
                .build();

        userRepository.save(user);

        String token = jwtService.generateToken(user);

        return AuthResponse.builder()
                .accessToken(token)
                .role(user.getRole().name())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail()).orElseThrow();
        String token = jwtService.generateToken(user);

        return AuthResponse.builder()
                .accessToken(token)
                .role(user.getRole().name())
                .build();
    }
}