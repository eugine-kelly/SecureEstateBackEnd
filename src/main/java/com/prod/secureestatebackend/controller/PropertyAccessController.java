package com.prod.secureestatebackend.controller;

import com.prod.secureestatebackend.dto.PropertyAccessResponse;
import com.prod.secureestatebackend.service.PropertyAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/property-access")
@RequiredArgsConstructor
public class PropertyAccessController {

    private final PropertyAccessService propertyAccessService;

    // Get property with access status
    @GetMapping("/{propertyId}")
    public ResponseEntity<PropertyAccessResponse> getPropertyAccess(
            @PathVariable Long propertyId,
            @RequestParam(defaultValue = "false") boolean isRental,
            @AuthenticationPrincipal UserDetails userDetails) {
        String email = userDetails.getUsername();
        return ResponseEntity.ok(propertyAccessService.getPropertyAccess(propertyId, email, isRental));
    }

    // Check if user has access
    @GetMapping("/{propertyId}/check")
    public ResponseEntity<Map<String, Object>> checkAccess(
            @PathVariable Long propertyId,
            @AuthenticationPrincipal UserDetails userDetails) {
        boolean hasAccess = propertyAccessService.hasAccess(propertyId, userDetails.getUsername());
        return ResponseEntity.ok(Map.of("hasAccess", hasAccess));
    }

    // Get access fee for a property
    @GetMapping("/{propertyId}/fee")
    public ResponseEntity<Map<String, Object>> getAccessFee(
            @PathVariable Long propertyId,
            @RequestParam(defaultValue = "false") boolean isRental) {
        BigDecimal fee = propertyAccessService.getAccessFee(propertyId, isRental);
        return ResponseEntity.ok(Map.of(
                "accessFee", fee,
                "formattedFee", "KES " + fee.toPlainString()
        ));
    }
}