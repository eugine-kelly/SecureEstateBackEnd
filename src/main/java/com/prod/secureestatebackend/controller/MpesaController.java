package com.prod.secureestatebackend.controller;

import com.prod.secureestatebackend.Entities.EscrowTransaction;
import com.prod.secureestatebackend.dto.MpesaSTKRequest;
import com.prod.secureestatebackend.dto.MpesaSTKResponse;
import com.prod.secureestatebackend.service.MpesaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/mpesa")
@RequiredArgsConstructor
public class MpesaController {

    private final MpesaService mpesaService;

    // Safaricom requires exactly this JSON format
    private static final String SUCCESS_RESPONSE =
            "{\"ResultCode\":\"0\",\"ResultDesc\":\"Accepted\"}";

    // ─── STK Push ────────────────────────────────────────────────
    @PostMapping("/stk-push")
    public ResponseEntity<MpesaSTKResponse> initiateSTKPush(
            @RequestBody MpesaSTKRequest request) {
        return ResponseEntity.ok(mpesaService.initiateSTKPush(request));
    }

    // ─── Check payment status ────────────────────────────────────
    @GetMapping("/status/{checkoutRequestId}")
    public ResponseEntity<Map<String, String>> checkStatus(
            @PathVariable String checkoutRequestId) {
        EscrowTransaction.EscrowStatus status =
                mpesaService.checkPaymentStatus(checkoutRequestId);
        return ResponseEntity.ok(Map.of("status", status.name()));
    }

    // ─── Manual Daraja query ─────────────────────────────────────
    @PostMapping("/query/{checkoutRequestId}")
    public ResponseEntity<Map<String, String>> queryStatus(
            @PathVariable String checkoutRequestId) {
        mpesaService.queryAndUpdateSTKStatus(checkoutRequestId);
        EscrowTransaction.EscrowStatus status =
                mpesaService.checkPaymentStatus(checkoutRequestId);
        return ResponseEntity.ok(Map.of("status", status.name()));
    }

    // ─── STK Callback (Safaricom calls this) ─────────────────────
    @PostMapping(
            value = "/callback/stk",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.ALL_VALUE},
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<String> stkCallback(
            @RequestBody(required = false) String body) {
        log.info("STK callback received. Body length: {}",
                body != null ? body.length() : 0);
        try {
            if (body != null && !body.isBlank()) {
                mpesaService.handleSTKCallback(body);
            }
        } catch (Exception e) {
            log.error("STK callback error: {}", e.getMessage());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(SUCCESS_RESPONSE);
    }

    // ─── C2B Validation ──────────────────────────────────────────
    @PostMapping(
            value = "/callback/c2b/validation",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.ALL_VALUE},
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<String> c2bValidation(
            @RequestBody(required = false) String body) {
        log.info("C2B validation received");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(SUCCESS_RESPONSE);
    }

    // ─── C2B Confirmation ─────────────────────────────────────────
    @PostMapping(
            value = "/callback/c2b/confirmation",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.ALL_VALUE},
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<String> c2bConfirmation(
            @RequestBody(required = false) String body) {
        log.info("C2B confirmation received. Body length: {}",
                body != null ? body.length() : 0);
        try {
            if (body != null && !body.isBlank()) {
                mpesaService.handleC2BConfirmation(body);
            }
        } catch (Exception e) {
            log.error("C2B confirmation error: {}", e.getMessage());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(SUCCESS_RESPONSE);
    }

    // ─── Admin endpoints ─────────────────────────────────────────
    @GetMapping("/admin/transactions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<EscrowTransaction>> getAllTransactions() {
        return ResponseEntity.ok(mpesaService.getAllTransactions());
    }

    @GetMapping("/admin/transactions/escrow")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<EscrowTransaction>> getEscrowTransactions() {
        return ResponseEntity.ok(mpesaService.getTransactionsByStatus(
                EscrowTransaction.EscrowStatus.IN_ESCROW));
    }

    @PostMapping("/admin/release/{transactionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EscrowTransaction> releaseEscrow(
            @PathVariable Long transactionId) {
        return ResponseEntity.ok(mpesaService.releaseEscrow(transactionId));
    }

    @PostMapping("/admin/refund/{transactionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EscrowTransaction> refundTransaction(
            @PathVariable Long transactionId) {
        return ResponseEntity.ok(mpesaService.refundTransaction(transactionId));
    }

    @PostMapping("/admin/register-c2b")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> registerC2B() {
        mpesaService.registerC2BUrls();
        return ResponseEntity.ok(
                Map.of("message", "C2B URLs registered successfully"));
    }
}