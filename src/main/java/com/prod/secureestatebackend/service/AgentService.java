package com.prod.secureestatebackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prod.secureestatebackend.dto.AgentRequest;
import com.prod.secureestatebackend.dto.AgentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${groq.api.key}")
    private String groqApiKey;

    // Groq uses OpenAI-compatible API format
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    // Best free model on Groq for chat
    private static final String GROQ_MODEL = "llama-3.3-70b-versatile";

    private static final String CHAT_SESSION_PREFIX = "chat:session:";
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    private static final String SYSTEM_PROMPT = """
            You are a helpful and knowledgeable real estate agent for SecureEstate, Kenya's most secure property marketplace.

            You help customers with:
            - Finding properties to buy or rent in Kenya (Nairobi, Mombasa, and beyond)
            - Explaining the Ardhisasa title verification process
            - Guiding customers through secure M-Pesa payment processes
            - Answering questions about cybersecurity features (MFA, RBAC, end-to-end encryption)
            - Providing Kenya real estate market insights
            - Explaining legal requirements for buying/renting property in Kenya

            Current listings on SecureEstate include:
            FOR SALE:
            - 3 Bed Apartment, Westlands, Nairobi — KES 12,500,000 (Ardhisasa Verified)
            - 4 Bed Villa, Karen, Nairobi — KES 45,000,000 (Ardhisasa Verified)
            - Commercial Plot, Mombasa Island — KES 28,000,000
            - 2 Bed Apartment, Kilimani, Nairobi — KES 8,900,000 (Ardhisasa Verified)

            FOR RENT:
            - 1 Bed Studio, Kilimani, Nairobi — KES 45,000/mo (Verified)
            - 3 Bed Apartment, Westlands, Nairobi — KES 95,000/mo (Verified)
            - 4 Bed Townhouse, Karen, Nairobi — KES 180,000/mo (Verified)
            - 2 Bed Apartment, Lavington, Nairobi — KES 75,000/mo
            - Office Space, Upper Hill, Nairobi — KES 120,000/mo (Verified)
            - Beachfront Villa, Nyali, Mombasa — KES 250,000/mo (Verified)

            Be friendly, professional, and always emphasize security features. Keep responses concise and helpful.
            If asked about specific legal or financial advice, recommend they consult a qualified lawyer or financial advisor.
            """;

    public AgentResponse chat(AgentRequest request) {
        String sessionId = (request.getSessionId() != null && !request.getSessionId().isEmpty())
                ? request.getSessionId()
                : UUID.randomUUID().toString();

        String redisKey = CHAT_SESSION_PREFIX + sessionId;
        List<AgentRequest.ChatMessage> history = getSessionHistory(redisKey);
        history.add(new AgentRequest.ChatMessage("user", request.getMessage()));

        String reply = callGroqApi(history);

        // Only save to history if it's a real reply, not an error message
        if (!reply.startsWith("I'm having trouble") && !reply.startsWith("I'm receiving")) {
            history.add(new AgentRequest.ChatMessage("assistant", reply));
            saveSessionHistory(redisKey, history);
        }

        return AgentResponse.builder()
                .reply(reply)
                .sessionId(sessionId)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<AgentRequest.ChatMessage> getSessionHistory(String redisKey) {
        try {
            Object raw = redisTemplate.opsForValue().get(redisKey);
            if (raw != null) {
                return objectMapper.convertValue(raw,
                        objectMapper.getTypeFactory().constructCollectionType(
                                List.class, AgentRequest.ChatMessage.class));
            }
        } catch (Exception e) {
            log.warn("Could not load chat history from Redis: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    private void saveSessionHistory(String redisKey, List<AgentRequest.ChatMessage> history) {
        try {
            redisTemplate.opsForValue().set(redisKey, history, SESSION_TTL);
        } catch (Exception e) {
            log.warn("Could not save chat history to Redis: {}", e.getMessage());
        }
    }

    private String callGroqApi(List<AgentRequest.ChatMessage> history) {
        try {
            // Groq uses OpenAI-compatible format
            // Build messages array with system prompt first
            ArrayNode messages = objectMapper.createArrayNode();

            // System message
            ObjectNode systemMsg = objectMapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content", SYSTEM_PROMPT);
            messages.add(systemMsg);

            // Conversation history
            for (AgentRequest.ChatMessage msg : history) {
                ObjectNode msgNode = objectMapper.createObjectNode();
                msgNode.put("role", msg.getRole()); // "user" or "assistant" — Groq uses same names
                msgNode.put("content", msg.getContent());
                messages.add(msgNode);
            }

            // Build request body
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", GROQ_MODEL);
            requestBody.set("messages", messages);
            requestBody.put("max_tokens", 1024);
            requestBody.put("temperature", 0.7);

            log.info("Calling Groq API with model: {}", GROQ_MODEL);

            // Call Groq API
            String responseBody = webClientBuilder.build()
                    .post()
                    .uri(GROQ_API_URL)
                    .header("Authorization", "Bearer " + groqApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Parse OpenAI-compatible response
            JsonNode responseJson = objectMapper.readTree(responseBody);
            String reply = responseJson
                    .path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText("Sorry, I could not generate a response. Please try again.");

            log.info("Groq responded successfully");
            return reply;

        } catch (WebClientResponseException e) {
            log.error("Groq API HTTP {} error: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return "I'm having trouble connecting right now. Please try again shortly.";
        } catch (Exception e) {
            log.error("Groq API error: {} — {}", e.getClass().getSimpleName(), e.getMessage());
            return "I'm having trouble connecting right now. Please try again shortly.";
        }
    }
}