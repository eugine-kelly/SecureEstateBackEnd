package com.prod.secureestatebackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyAccessResponse {
    // Already visible (before payment)
    private Long propertyId;
    private String title;
    private String location;
    private BigDecimal price;
    private String type;
    private boolean ardhisasaVerified;
    private String imageUrl;
    private BigDecimal accessFee;
    private boolean accessUnlocked;

    // Unlocked after payment
    private String description;
    private String agentFullName;
    private String agentEmail;
    private String agentPhone;
    private String agentWhatsApp;
    private String ownerEmail;
    private Integer beds;
    private Integer baths;
    private String mpesaReceiptNumber; // proof of payment
}