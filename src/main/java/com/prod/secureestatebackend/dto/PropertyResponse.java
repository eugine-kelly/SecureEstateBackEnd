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
public class PropertyResponse {

    private Long id;
    private String title;
    private String location;
    private BigDecimal price;
    private String type;
    private boolean ardhisasaVerified;
    private String description;
    private String imageUrl;
    private String ownerEmail;

}
