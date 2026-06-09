package com.prod.secureestatebackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prod.secureestatebackend.dto.PropertyRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${groq.api.key}")
    private String groqApiKey;

    private static final String GROQ_API_URL =
            "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.3-70b-versatile";

    public FraudDetectionResult scanListing(PropertyRequest request) {
        try {
            String prompt = buildFraudPrompt(request);

            ArrayNode messages = objectMapper.createArrayNode();

            ObjectNode systemMsg = objectMapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content",
                    "You are an AI fraud detection system for SecureEstate, " +
                            "Kenya's real estate marketplace. Analyze property listings for " +
                            "fraud signals. Respond ONLY with valid JSON in this exact format: " +
                            "{\"fraudScore\": 0-100, \"riskLevel\": \"LOW|MEDIUM|HIGH\", " +
                            "\"approved\": true|false, \"reasons\": [\"reason1\", \"reason2\"], " +
                            "\"recommendation\": \"brief recommendation\"}. " +
                            "fraudScore 0=clean, 100=definite fraud. " +
                            "Approve if fraudScore < 60."
            );
            messages.add(systemMsg);

            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", GROQ_MODEL);
            requestBody.set("messages", messages);
            requestBody.put("max_tokens", 500);
            requestBody.put("temperature", 0.1); // Low temp for consistent analysis

            String responseBody = webClientBuilder.build()
                    .post()
                    .uri(GROQ_API_URL)
                    .header("Authorization", "Bearer " + groqApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode responseJson = objectMapper.readTree(responseBody);
            String aiResponse = responseJson
                    .path("choices").get(0)
                    .path("message")
                    .path("content")
                    .asText();

            log.info("Groq fraud scan response: {}", aiResponse);

            // Parse AI JSON response
            JsonNode result = objectMapper.readTree(aiResponse.trim());
            int fraudScore = result.path("fraudScore").asInt(0);
            String riskLevel = result.path("riskLevel").asText("LOW");
            boolean approved = result.path("approved").asBoolean(true);
            String recommendation = result.path("recommendation").asText("");

            // Build reasons list
            StringBuilder reasons = new StringBuilder();
            result.path("reasons").forEach(r -> reasons.append(r.asText()).append("; "));

            log.info("Fraud scan for '{}': score={}, risk={}, approved={}",
                    request.getTitle(), fraudScore, riskLevel, approved);

            return FraudDetectionResult.builder()
                    .fraudScore(fraudScore)
                    .riskLevel(riskLevel)
                    .approved(approved)
                    .reasons(reasons.toString())
                    .recommendation(recommendation)
                    .build();

        } catch (Exception e) {
            log.error("Fraud detection error: {}", e.getMessage());
            // On error, approve with warning (don't block listings due to AI error)
            return FraudDetectionResult.builder()
                    .fraudScore(0)
                    .riskLevel("UNKNOWN")
                    .approved(true)
                    .reasons("AI scan unavailable — manual review recommended")
                    .recommendation("Manual review required")
                    .build();
        }
    }

    private String buildFraudPrompt(PropertyRequest request) {
        BigDecimal price = request.getPrice();
        String location = request.getLocation();
        String type = request.getType();
        String title = request.getTitle();
        String description = request.getDescription();

        // Calculate price per sqm benchmark for Kenya
        String priceContext = "";
        if (price != null) {
            long priceVal = price.longValue();
            if (priceVal < 100000 && type != null &&
                    (type.equals("House") || type.equals("Villa"))) {
                priceContext = "WARNING: Price suspiciously low for a house/villa.";
            }
            if (priceVal > 500000000) {
                priceContext = "WARNING: Price extremely high, possible error or fraud.";
            }
        }

        return String.format("""
            Analyze this Kenya real estate listing for fraud signals:
            
            Title: %s
            Location: %s
            Price: KES %s
            Type: %s
            Description: %s
            %s
            
            Kenya fraud signals to check:
            - Unrealistically low prices (e.g. 3-bed Nairobi apartment for under KES 500,000)
            - Vague or copied descriptions
            - Suspicious locations that don't match property type
            - Missing or suspicious details
            - Prices that seem too good to be true for the location
            - Commercial property listed as residential or vice versa
            
            Respond with JSON only.
            """,
                title, location, price, type, description, priceContext
        );
    }

    // Inner result class
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FraudDetectionResult {
        private int fraudScore;
        private String riskLevel;
        private boolean approved;
        private String reasons;
        private String recommendation;
    }
}