package com.prod.secureestatebackend.util;

import java.math.BigDecimal;

public class AccessFeeUtil {

    public static BigDecimal calculateFee(String type, BigDecimal price, boolean isRental) {
        if (isRental) return new BigDecimal("200");

        if (type == null) return new BigDecimal("500");

        switch (type.toLowerCase()) {
            case "land":
            case "commercial":
                return new BigDecimal("1000");
            case "apartment":
            case "house":
            case "villa":
            default:
                if (price == null) return new BigDecimal("500");
                if (price.compareTo(new BigDecimal("10000000")) < 0)
                    return new BigDecimal("500");
                if (price.compareTo(new BigDecimal("30000000")) <= 0)
                    return new BigDecimal("1000");
                return new BigDecimal("1500");
        }
    }
}