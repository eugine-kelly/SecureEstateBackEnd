package com.prod.secureestatebackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MpesaSTKResponse {
    private boolean success;
    private String checkoutRequestId;
    private String message;
}