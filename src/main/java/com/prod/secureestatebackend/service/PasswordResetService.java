package com.prod.secureestatebackend.service;

import com.prod.secureestatebackend.Entities.PasswordResetToken;
import com.prod.secureestatebackend.repository.PasswordResetTokenRepository;
import com.prod.secureestatebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Transactional
    public void sendResetLink(String email) {
        // Check if user exists — always respond success to prevent email enumeration
        boolean userExists = userRepository.findByEmail(email).isPresent();

        if (userExists) {
            // Delete any existing tokens for this email
            tokenRepository.deleteByEmail(email);

            // Generate secure token
            String token = UUID.randomUUID().toString();

            // Save token (expires in 30 minutes)
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(token)
                    .email(email)
                    .expiresAt(LocalDateTime.now().plusMinutes(30))
                    .used(false)
                    .build();
            tokenRepository.save(resetToken);

            // Send email
            String resetLink = frontendUrl + "/reset-password?token=" + token;
            sendResetEmail(email, resetLink);
        }
        // Always log but never expose whether email exists
        log.info("Password reset requested for: {}", email);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset link."));

        if (resetToken.isExpired()) {
            throw new RuntimeException("Reset link has expired. Please request a new one.");
        }

        if (resetToken.isUsed()) {
            throw new RuntimeException("Reset link has already been used.");
        }

        // Update password
        userRepository.findByEmail(resetToken.getEmail()).ifPresent(user -> {
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
        });

        // Mark token as used
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
    }

    private void sendResetEmail(String toEmail, String resetLink) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("SecureEstate — Password Reset Request");
            message.setText(
                    "Hello,\n\n" +
                            "You requested a password reset for your SecureEstate account.\n\n" +
                            "Click the link below to reset your password:\n" +
                            resetLink + "\n\n" +
                            "This link expires in 30 minutes.\n\n" +
                            "If you did not request this, please ignore this email.\n\n" +
                            "— SecureEstate Security Team\n" +
                            "Kenya's Most Secure Property Marketplace"
            );
            mailSender.send(message);
            log.info("Password reset email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send reset email: {}", e.getMessage());
        }
    }
}