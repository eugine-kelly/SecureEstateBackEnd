package com.prod.secureestatebackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MpesaSTKRequest {
    private String phoneNumber;
    private BigDecimal amount;
    private Long propertyId;
    private String buyerEmail;
}