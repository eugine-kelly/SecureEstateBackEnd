package com.prod.secureestatebackend.service;

import com.prod.secureestatebackend.Entities.Property;
import com.prod.secureestatebackend.Entities.User;
import com.prod.secureestatebackend.dto.PropertyRequest;
import com.prod.secureestatebackend.dto.PropertyResponse;
import com.prod.secureestatebackend.repository.PropertyRepository;
import com.prod.secureestatebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;

    // Evict cache when a new property is created
    @Transactional
    @CacheEvict(value = "verifiedProperties", allEntries = true)
    public PropertyResponse createProperty(PropertyRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User owner = userRepository.findByEmail(email).orElseThrow();

        Property property = Property.builder()
                .title(request.getTitle())
                .location(request.getLocation())
                .price(request.getPrice())
                .type(request.getType())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .ardhisasaVerified(false)
                .owner(owner)
                .build();

        property = propertyRepository.save(property);
        return mapToResponse(property);
    }

    // Cache results in Redis for 10 minutes (configured in RedisConfig)
    @Transactional(readOnly = true)
    @Cacheable(value = "verifiedProperties")
    public List<PropertyResponse> getAllVerifiedProperties() {
        return propertyRepository.findByArdhisasaVerifiedTrue()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private PropertyResponse mapToResponse(Property p) {
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