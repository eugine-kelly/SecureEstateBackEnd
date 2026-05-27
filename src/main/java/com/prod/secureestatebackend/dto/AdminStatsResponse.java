package com.prod.secureestatebackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsResponse {
    private long totalUsers;
    private long totalProperties;
    private long verifiedProperties;
    private long totalRentals;
    private long buyerCount;
    private long sellerCount;
    private long agentCount;
}