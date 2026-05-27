package com.prod.secureestatebackend.service;

import com.prod.secureestatebackend.Entities.Property;
import com.prod.secureestatebackend.Entities.Role;
import com.prod.secureestatebackend.Entities.User;
import com.prod.secureestatebackend.dto.*;
import com.prod.secureestatebackend.exception.ResourceNotFoundException;
import com.prod.secureestatebackend.repository.PropertyRepository;
import com.prod.secureestatebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // ─── Dashboard Stats ────────────────────────────────────────

    public AdminStatsResponse getStats() {
        long totalUsers = userRepository.count();
        long totalProperties = propertyRepository.count();
        long verifiedProperties = propertyRepository.findByArdhisasaVerifiedTrue().size();
        long buyerCount = userRepository.countByRole(Role.BUYER);
        long sellerCount = userRepository.countByRole(Role.SELLER);
        long agentCount = userRepository.countByRole(Role.AGENT);

        return AdminStatsResponse.builder()
                .totalUsers(totalUsers)
                .totalProperties(totalProperties)
                .verifiedProperties(verifiedProperties)
                .totalRentals(0) // rentals are static for now
                .buyerCount(buyerCount)
                .sellerCount(sellerCount)
                .agentCount(agentCount)
                .build();
    }

    // ─── User Management ────────────────────────────────────────

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserResponse changeUserRole(Long userId, String newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Prevent changing to ADMIN via this endpoint
        Role role = switch (newRole.toUpperCase()) {
            case "SELLER" -> Role.SELLER;
            case "AGENT" -> Role.AGENT;
            default -> Role.BUYER;
        };

        user.setRole(role);
        userRepository.save(user);
        log.info("Admin changed user {} role to {}", user.getEmail(), role);
        return mapToUserResponse(user);
    }

    @Transactional
    public UserResponse toggleUserBan(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
        log.info("Admin {} user: {}", user.isEnabled() ? "unbanned" : "banned", user.getEmail());
        return mapToUserResponse(user);
    }

    // ─── Property Management ────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PropertyResponse> getAllProperties() {
        return propertyRepository.findAll()
                .stream()
                .map(this::mapToPropertyResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "verifiedProperties", allEntries = true)
    public PropertyResponse createProperty(PropertyRequest request, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        Property property = Property.builder()
                .title(request.getTitle())
                .location(request.getLocation())
                .price(request.getPrice())
                .type(request.getType())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .ardhisasaVerified(false)
                .owner(admin)
                .build();

        property = propertyRepository.save(property);
        log.info("Admin created property: {}", property.getTitle());
        return mapToPropertyResponse(property);
    }

    @Transactional
    @CacheEvict(value = "verifiedProperties", allEntries = true)
    public PropertyResponse updateProperty(Long propertyId, PropertyRequest request) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found with id: " + propertyId));

        property.setTitle(request.getTitle());
        property.setLocation(request.getLocation());
        property.setPrice(request.getPrice());
        property.setType(request.getType());
        property.setDescription(request.getDescription());
        if (request.getImageUrl() != null) property.setImageUrl(request.getImageUrl());

        propertyRepository.save(property);
        log.info("Admin updated property: {}", property.getTitle());
        return mapToPropertyResponse(property);
    }

    @Transactional
    @CacheEvict(value = "verifiedProperties", allEntries = true)
    public void deleteProperty(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found with id: " + propertyId));
        propertyRepository.delete(property);
        log.info("Admin deleted property id: {}", propertyId);
    }

    @Transactional
    @CacheEvict(value = "verifiedProperties", allEntries = true)
    public PropertyResponse toggleVerification(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found with id: " + propertyId));

        property.setArdhisasaVerified(!property.isArdhisasaVerified());
        propertyRepository.save(property);
        log.info("Admin {} property: {}", property.isArdhisasaVerified() ? "verified" : "unverified", property.getTitle());
        return mapToPropertyResponse(property);
    }

    // ─── Chat Logs ──────────────────────────────────────────────

    public List<String> getChatSessionKeys() {
        try {
            Set<String> keys = redisTemplate.keys("chat:session:*");
            if (keys == null) return List.of();
            return keys.stream().sorted().collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error fetching chat session keys: {}", e.getMessage());
            return List.of();
        }
    }

    public Object getChatSession(String sessionId) {
        try {
            return redisTemplate.opsForValue().get("chat:session:" + sessionId);
        } catch (Exception e) {
            log.error("Error fetching chat session: {}", e.getMessage());
            return null;
        }
    }

    // ─── Mappers ────────────────────────────────────────────────

    private UserResponse mapToUserResponse(User u) {
        return UserResponse.builder()
                .id(u.getId())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .phone(u.getPhone())
                .role(u.getRole().name())
                .enabled(u.isEnabled())
                .build();
    }

    private PropertyResponse mapToPropertyResponse(Property p) {
        return PropertyResponse.builder()
                .id(p.getId())
                .title(p.getTitle())
                .location(p.getLocation())
                .price(p.getPrice())
                .type(p.getType())
                .ardhisasaVerified(p.isArdhisasaVerified())
                .description(p.getDescription())
                .imageUrl(p.getImageUrl())
                .ownerEmail(p.getOwner().getEmail())
                .build();
    }
}