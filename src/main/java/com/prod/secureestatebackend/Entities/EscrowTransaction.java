package com.prod.secureestatebackend.Entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "escrow_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscrowTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String checkoutRequestId;   // STK Push request ID
    private String mpesaReceiptNumber;  // M-Pesa receipt after payment
    private String merchantRequestId;

    private String phoneNumber;         // Buyer's phone
    private String buyerEmail;          // Buyer's email
    private Long propertyId;            // Property being purchased

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EscrowStatus status = EscrowStatus.PENDING;

    private String paymentType;         // STK_PUSH or C2B

    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;
    private String failureReason;

    public enum EscrowStatus {
        PENDING,        // Payment initiated, waiting for user
        PAID,           // M-Pesa confirmed payment received
        IN_ESCROW,      // Funds held, pending admin release
        RELEASED,       // Admin released funds to seller
        REFUNDED,       // Payment refunded to buyer
        FAILED          // Payment failed
    }
}