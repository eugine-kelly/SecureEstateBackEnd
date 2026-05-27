package com.prod.secureestatebackend.security;

import com.prod.secureestatebackend.Entities.Role;
import com.prod.secureestatebackend.Entities.User;
import com.prod.secureestatebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        // Auto-create user in DB if first time logging in with Google
        userRepository.findByEmail(email).orElseGet(() -> {
            log.info("Creating new Google OAuth user: {}", email);
            User newUser = User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .fullName(name != null ? name : email)
                    .role(Role.BUYER)
                    .build();
            return userRepository.save(newUser);
        });

        return oAuth2User;
    }
}