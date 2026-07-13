package com.prod.secureestatebackend.controller;

import com.prod.secureestatebackend.dto.PropertyRequest;
import com.prod.secureestatebackend.dto.PropertyResponse;
import com.prod.secureestatebackend.service.PropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;

    @PostMapping
    @PreAuthorize("hasRole('SELLER') or hasRole('AGENT')")
    public ResponseEntity<PropertyResponse> create(@RequestBody PropertyRequest request) {
        return ResponseEntity.ok(propertyService.createProperty(request));
    }

    @GetMapping("/verified")
    public ResponseEntity<List<PropertyResponse>> getVerified() {
        return ResponseEntity.ok(propertyService.getAllVerifiedProperties());
    }

    // Single property by ID — needed for PropertyDetail page
    @GetMapping("/{id}")
    public ResponseEntity<PropertyResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(propertyService.getPropertyById(id));
    }
}