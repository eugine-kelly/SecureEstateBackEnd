package com.prod.secureestatebackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prod.secureestatebackend.Entities.EscrowTransaction;
import com.prod.secureestatebackend.config.MpesaConfig;
import com.prod.secureestatebackend.repository.PropertyRepository;
import com.prod.secureestatebackend.util.AccessFeeUtil;
import com.prod.secureestatebackend.dto.MpesaSTKRequest;
import com.prod.secureestatebackend.dto.MpesaSTKResponse;
import com.prod.secureestatebackend.repository.EscrowTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MpesaService {

    private final MpesaConfig mpesaConfig;
    private final EscrowTransactionRepository escrowRepo;
    private final PropertyRepository propertyRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    // ─── Expose repo for controller ──────────────────────────────
    public EscrowTransactionRepository getEscrowRepo() {
        return escrowRepo;
    }

    // ─── Auth Token ──────────────────────────────────────────────

    private String getAccessToken() {
        try {
            String credentials = mpesaConfig.getConsumerKey() + ":" + mpesaConfig.getConsumerSecret();
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());

            log.info("Getting M-Pesa access token from: {}", mpesaConfig.getBaseUrl());
            log.info("Using consumer key prefix: {}...",
                    mpesaConfig.getConsumerKey().length() > 6 ? mpesaConfig.getConsumerKey().substring(0, 6) : "SHORT");

            String response = webClientBuilder.build()
                    .get()
                    .uri(mpesaConfig.getBaseUrl() + "/oauth/v1/generate?grant_type=client_credentials")
                    .header("Authorization", "Basic " + encoded)
                    .header("Cache-Control", "no-cache")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Auth response: {}", response);
            JsonNode json = objectMapper.readTree(response);
            String token = json.path("access_token").asText();
            if (token == null || token.isEmpty()) {
                throw new RuntimeException("Empty access token received: " + response);
            }
            return token;
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("M-Pesa auth HTTP {} error: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to authenticate with M-Pesa: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Failed to get M-Pesa access token: {}", e.getMessage());
            throw new RuntimeException("Failed to authenticate with M-Pesa");
        }
    }

    // ─── STK Push ────────────────────────────────────────────────

    @Transactional
    public MpesaSTKResponse initiateSTKPush(MpesaSTKRequest request) {
        try {
            String token = getAccessToken();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String password = Base64.getEncoder().encodeToString(
                    (mpesaConfig.getShortcode() + mpesaConfig.getPasskey() + timestamp).getBytes()
            );

            // Format phone: 0712345678 → 254712345678
            String phone = formatPhone(request.getPhoneNumber());

            ObjectNode body = objectMapper.createObjectNode();
            body.put("BusinessShortCode", mpesaConfig.getShortcode());
            body.put("Password", password);
            body.put("Timestamp", timestamp);
            body.put("TransactionType", "CustomerPayBillOnline");
            body.put("Amount", request.getAmount().intValue());
            body.put("PartyA", phone);
            body.put("PartyB", mpesaConfig.getShortcode());
            body.put("PhoneNumber", phone);
            body.put("CallBackURL", mpesaConfig.getCallbackUrl());
            body.put("AccountReference", "SecureEstate-" + request.getPropertyId());
            body.put("TransactionDesc", "Property payment - SecureEstate Escrow");

            String response = webClientBuilder.build()
                    .post()
                    .uri(mpesaConfig.getBaseUrl() + "/mpesa/stkpush/v1/processrequest")
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .bodyValue(body.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode json = objectMapper.readTree(response);
            String checkoutRequestId = json.path("CheckoutRequestID").asText();
            String merchantRequestId = json.path("MerchantRequestID").asText();
            String responseCode = json.path("ResponseCode").asText();

            if ("0".equals(responseCode)) {
                // Save pending transaction
                String buyerEmail = request.getBuyerEmail() != null
                        ? request.getBuyerEmail() : "";

                EscrowTransaction transaction = EscrowTransaction.builder()
                        .checkoutRequestId(checkoutRequestId)
                        .merchantRequestId(merchantRequestId)
                        .phoneNumber(phone)
                        .buyerEmail(buyerEmail)
                        .propertyId(request.getPropertyId())
                        .amount(request.getAmount())
                        .status(EscrowTransaction.EscrowStatus.PENDING)
                        .paymentType("STK_PUSH")
                        .initiatedAt(LocalDateTime.now())
                        .build();
                escrowRepo.save(transaction);

                log.info("STK Push initiated for property {} — CheckoutRequestID: {} — buyerEmail: {}",
                        request.getPropertyId(), checkoutRequestId, buyerEmail);

                return MpesaSTKResponse.builder()
                        .success(true)
                        .checkoutRequestId(checkoutRequestId)
                        .message("STK Push sent to " + request.getPhoneNumber() + ". Enter your M-Pesa PIN to complete payment.")
                        .build();
            } else {
                String errorMessage = json.path("errorMessage").asText("Payment initiation failed");
                log.error("STK Push failed: {}", errorMessage);
                return MpesaSTKResponse.builder()
                        .success(false)
                        .message(errorMessage)
                        .build();
            }

        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            String rawError = e.getResponseBodyAsString();
            log.error("STK Push HTTP {} error. Safaricom response: {}", e.getStatusCode(), rawError);

            // Parse a clean message for the frontend
            String cleanMessage = "Payment initiation failed. Please check your phone number and try again.";
            try {
                JsonNode errJson = objectMapper.readTree(rawError);
                String errMsg = errJson.path("errorMessage").asText("");
                if (errMsg.contains("PhoneNumber")) cleanMessage = "Invalid phone number. Use format: 0712345678";
                else if (errMsg.contains("insufficient")) cleanMessage = "Insufficient M-Pesa balance.";
                else if (errMsg.contains("wrong PIN") || errMsg.contains("PIN")) cleanMessage = "Wrong M-Pesa PIN entered.";
                else if (!errMsg.isEmpty()) cleanMessage = errMsg;
            } catch (Exception ignored) {}

            return MpesaSTKResponse.builder()
                    .success(false)
                    .message(cleanMessage)
                    .build();
        } catch (Exception e) {
            log.error("STK Push unexpected error: {} — {}", e.getClass().getSimpleName(), e.getMessage());
            return MpesaSTKResponse.builder()
                    .success(false)
                    .message("Failed to initiate payment. Please try again.")
                    .build();
        }
    }

    // ─── STK Query (manual status check via Daraja) ─────────────

    public void queryAndUpdateSTKStatus(String checkoutRequestId) {
        try {
            String token = getAccessToken();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String password = Base64.getEncoder().encodeToString(
                    (mpesaConfig.getShortcode() + mpesaConfig.getPasskey() + timestamp).getBytes()
            );

            ObjectNode body = objectMapper.createObjectNode();
            body.put("BusinessShortCode", mpesaConfig.getShortcode());
            body.put("Password", password);
            body.put("Timestamp", timestamp);
            body.put("CheckoutRequestID", checkoutRequestId);

            String response = webClientBuilder.build()
                    .post()
                    .uri(mpesaConfig.getBaseUrl() + "/mpesa/stkpushquery/v1/query")
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .bodyValue(body.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode json = objectMapper.readTree(response);
            String resultCode = json.path("ResultCode").asText("");

            EscrowTransaction transaction = escrowRepo.findByCheckoutRequestId(checkoutRequestId).orElse(null);
            if (transaction == null) return;

            if ("0".equals(resultCode)) {
                transaction.setStatus(EscrowTransaction.EscrowStatus.IN_ESCROW);
                transaction.setCompletedAt(LocalDateTime.now());
                escrowRepo.save(transaction);
                log.info("STK Query: Payment confirmed for {}", checkoutRequestId);
            } else if ("1032".equals(resultCode) || "1".equals(resultCode)) {
                transaction.setStatus(EscrowTransaction.EscrowStatus.FAILED);
                transaction.setFailureReason("Cancelled or failed");
                escrowRepo.save(transaction);
                log.info("STK Query: Payment failed/cancelled for {}", checkoutRequestId);
            }
        } catch (Exception e) {
            log.warn("STK Query error: {}", e.getMessage());
        }
    }

    // ─── STK Callback ────────────────────────────────────────────

    @Transactional
    public void handleSTKCallback(String callbackBody) {
        try {
            JsonNode json = objectMapper.readTree(callbackBody);
            JsonNode body = json.path("Body").path("stkCallback");

            String checkoutRequestId = body.path("CheckoutRequestID").asText();
            int resultCode = body.path("ResultCode").asInt();

            EscrowTransaction transaction = escrowRepo.findByCheckoutRequestId(checkoutRequestId)
                    .orElse(null);

            if (transaction == null) {
                log.warn("No transaction found for CheckoutRequestID: {}", checkoutRequestId);
                return;
            }

            if (resultCode == 0) {
                // Payment successful
                JsonNode metadata = body.path("CallbackMetadata").path("Item");
                String receiptNumber = "";
                for (JsonNode item : metadata) {
                    if ("MpesaReceiptNumber".equals(item.path("Name").asText())) {
                        receiptNumber = item.path("Value").asText();
                    }
                }

                transaction.setStatus(EscrowTransaction.EscrowStatus.IN_ESCROW);
                transaction.setMpesaReceiptNumber(receiptNumber);
                transaction.setCompletedAt(LocalDateTime.now());
                escrowRepo.save(transaction);

                log.info("Payment successful! Receipt: {} — Now in escrow for property {}", receiptNumber, transaction.getPropertyId());
            } else {
                // Payment failed
                String reason = body.path("ResultDesc").asText("Payment cancelled or failed");
                transaction.setStatus(EscrowTransaction.EscrowStatus.FAILED);
                transaction.setFailureReason(reason);
                escrowRepo.save(transaction);
                log.warn("STK Push payment failed: {}", reason);
            }

        } catch (Exception e) {
            log.error("Error handling STK callback: {}", e.getMessage());
        }
    }

    // ─── C2B Registration ────────────────────────────────────────

    public void registerC2BUrls() {
        try {
            String token = getAccessToken();

            ObjectNode body = objectMapper.createObjectNode();
            body.put("ShortCode", mpesaConfig.getShortcode());
            body.put("ResponseType", "Completed");
            body.put("ConfirmationURL", mpesaConfig.getC2bConfirmationUrl());
            body.put("ValidationURL", mpesaConfig.getC2bValidationUrl());

            String response = webClientBuilder.build()
                    .post()
                    .uri(mpesaConfig.getBaseUrl() + "/mpesa/c2b/v1/registerurl")
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .bodyValue(body.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("C2B URL registration response: {}", response);
        } catch (Exception e) {
            log.error("C2B URL registration failed: {}", e.getMessage());
        }
    }

    // ─── C2B Confirmation ────────────────────────────────────────

    @Transactional
    public void handleC2BConfirmation(String confirmationBody) {
        try {
            JsonNode json = objectMapper.readTree(confirmationBody);

            String receiptNumber = json.path("TransID").asText();
            String phone = json.path("MSISDN").asText();
            BigDecimal amount = new BigDecimal(json.path("TransAmount").asText("0"));
            String accountRef = json.path("BillRefNumber").asText();

            // Extract property ID from account reference (SecureEstate-{propertyId})
            Long propertyId = null;
            if (accountRef.startsWith("SecureEstate-")) {
                try {
                    propertyId = Long.parseLong(accountRef.replace("SecureEstate-", ""));
                } catch (NumberFormatException ignored) {}
            }

            // Save C2B transaction directly as IN_ESCROW
            EscrowTransaction transaction = EscrowTransaction.builder()
                    .mpesaReceiptNumber(receiptNumber)
                    .phoneNumber(phone)
                    .propertyId(propertyId)
                    .amount(amount)
                    .status(EscrowTransaction.EscrowStatus.IN_ESCROW)
                    .paymentType("C2B")
                    .initiatedAt(LocalDateTime.now())
                    .completedAt(LocalDateTime.now())
                    .build();

            escrowRepo.save(transaction);
            log.info("C2B payment confirmed! Receipt: {} Amount: KES {} for property {}", receiptNumber, amount, propertyId);

        } catch (Exception e) {
            log.error("Error handling C2B confirmation: {}", e.getMessage());
        }
    }

    // ─── Admin — Release Escrow ───────────────────────────────────

    @Transactional
    public EscrowTransaction releaseEscrow(Long transactionId) {
        EscrowTransaction transaction = escrowRepo.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        if (transaction.getStatus() != EscrowTransaction.EscrowStatus.IN_ESCROW) {
            throw new RuntimeException("Transaction is not in escrow status");
        }

        transaction.setStatus(EscrowTransaction.EscrowStatus.RELEASED);
        transaction.setCompletedAt(LocalDateTime.now());
        escrowRepo.save(transaction);

        log.info("Admin released escrow for transaction {} — Receipt: {}", transactionId, transaction.getMpesaReceiptNumber());
        return transaction;
    }

    // ─── Admin — Refund ──────────────────────────────────────────

    @Transactional
    public EscrowTransaction refundTransaction(Long transactionId) {
        EscrowTransaction transaction = escrowRepo.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        transaction.setStatus(EscrowTransaction.EscrowStatus.REFUNDED);
        escrowRepo.save(transaction);

        log.info("Admin refunded transaction {} — Receipt: {}", transactionId, transaction.getMpesaReceiptNumber());
        return transaction;
    }

    // ─── Query Payment Status ─────────────────────────────────────

    public EscrowTransaction.EscrowStatus checkPaymentStatus(String checkoutRequestId) {
        return escrowRepo.findByCheckoutRequestId(checkoutRequestId)
                .map(EscrowTransaction::getStatus)
                .orElse(EscrowTransaction.EscrowStatus.FAILED);
    }

    // ─── Get All Escrow Transactions ─────────────────────────────

    public List<EscrowTransaction> getAllTransactions() {
        return escrowRepo.findAll();
    }

    public List<EscrowTransaction> getTransactionsByStatus(EscrowTransaction.EscrowStatus status) {
        return escrowRepo.findByStatus(status);
    }

    // ─── Helper ──────────────────────────────────────────────────

    private String formatPhone(String phone) {
        // Remove all spaces, dashes and non-numeric chars except +
        phone = phone.trim().replaceAll("[\\s\\-()]", "");

        // Remove leading + if present
        if (phone.startsWith("+")) phone = phone.substring(1);

        // Convert 07XXXXXXXX → 2547XXXXXXXX
        if (phone.startsWith("0") && phone.length() == 10) {
            phone = "254" + phone.substring(1);
        }

        // Convert 7XXXXXXXX (9 digits) → 2547XXXXXXXX
        if (phone.length() == 9 && (phone.startsWith("7") || phone.startsWith("1"))) {
            phone = "254" + phone;
        }

        // Already in 254 format
        if (phone.startsWith("254") && phone.length() == 12) {
            return phone;
        }

        log.warn("Phone number may be invalid: {}", phone);
        return phone;
    }
}