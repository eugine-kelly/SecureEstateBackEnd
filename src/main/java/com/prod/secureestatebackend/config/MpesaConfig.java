package com.prod.secureestatebackend.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
public class MpesaConfig {

    @Value("${mpesa.consumer.key}")
    private String consumerKey;

    @Value("${mpesa.consumer.secret}")
    private String consumerSecret;

    @Value("${mpesa.shortcode}")
    private String shortcode;

    @Value("${mpesa.passkey}")
    private String passkey;

    @Value("${mpesa.callback.url}")
    private String callbackUrl;

    @Value("${mpesa.c2b.confirmation.url}")
    private String c2bConfirmationUrl;

    @Value("${mpesa.c2b.validation.url}")
    private String c2bValidationUrl;

    @Value("${mpesa.environment:sandbox}")
    private String environment;

    public String getBaseUrl() {
        return environment.equals("production")
                ? "https://api.safaricom.co.ke"
                : "https://sandbox.safaricom.co.ke";
    }

    public boolean isSandbox() {
        return environment.equals("sandbox");
    }
}