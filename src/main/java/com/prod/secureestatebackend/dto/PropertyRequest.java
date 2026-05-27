package com.prod.secureestatebackend.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String location;

    @Positive
    private BigDecimal price;

    @NotBlank
    private String type;

    private String description;

    private String imageUrl;

}
