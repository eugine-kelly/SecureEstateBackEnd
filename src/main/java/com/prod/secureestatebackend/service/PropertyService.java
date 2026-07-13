package com.prod.secureestatebackend.service;

import com.prod.secureestatebackend.Entities.Property;
import com.prod.secureestatebackend.Entities.User;
import com.prod.secureestatebackend.dto.PropertyRequest;
import com.prod.secureestatebackend.dto.PropertyResponse;
import com.prod.secureestatebackend.exception.ResourceNotFoundException;
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

    @Transactional(readOnly = true)
    @Cacheable(value = "verifiedProperties")
    public List<PropertyResponse> getAllVerifiedProperties() {
        return propertyRepository.findByArdhisasaVerifiedTrue()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // Single property by ID — used by PropertyDetail page
    @Transactional(readOnly = true)
    public PropertyResponse getPropertyById(Long id) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found with id: " + id));
        return mapToResponse(property);
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
                .ownerEmail(p.getOwner() != null ? p.getOwner().getEmail() : "")
                .build();
    }
}