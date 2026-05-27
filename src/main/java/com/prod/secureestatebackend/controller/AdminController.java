package com.prod.secureestatebackend.controller;

import com.prod.secureestatebackend.dto.*;
import com.prod.secureestatebackend.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ─── Dashboard ──────────────────────────────────────────────
    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    // ─── Users ──────────────────────────────────────────────────
    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<UserResponse> changeRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(adminService.changeUserRole(id, body.get("role")));
    }

    @PutMapping("/users/{id}/toggle-ban")
    public ResponseEntity<UserResponse> toggleBan(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.toggleUserBan(id));
    }

    // ─── Properties ─────────────────────────────────────────────
    @GetMapping("/properties")
    public ResponseEntity<List<PropertyResponse>> getAllProperties() {
        return ResponseEntity.ok(adminService.getAllProperties());
    }

    @PostMapping("/properties")
    public ResponseEntity<PropertyResponse> createProperty(
            @RequestBody PropertyRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(adminService.createProperty(request, userDetails.getUsername()));
    }

    @PutMapping("/properties/{id}")
    public ResponseEntity<PropertyResponse> updateProperty(
            @PathVariable Long id,
            @RequestBody PropertyRequest request) {
        return ResponseEntity.ok(adminService.updateProperty(id, request));
    }

    @DeleteMapping("/properties/{id}")
    public ResponseEntity<Map<String, String>> deleteProperty(@PathVariable Long id) {
        adminService.deleteProperty(id);
        return ResponseEntity.ok(Map.of("message", "Property deleted successfully"));
    }

    @PutMapping("/properties/{id}/toggle-verify")
    public ResponseEntity<PropertyResponse> toggleVerification(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.toggleVerification(id));
    }

    // ─── Chat Logs ──────────────────────────────────────────────
    @GetMapping("/chat-logs")
    public ResponseEntity<List<String>> getChatLogs() {
        return ResponseEntity.ok(adminService.getChatSessionKeys());
    }

    @GetMapping("/chat-logs/{sessionId}")
    public ResponseEntity<Object> getChatSession(@PathVariable String sessionId) {
        return ResponseEntity.ok(adminService.getChatSession(sessionId));
    }
}