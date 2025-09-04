package com.dinidu.loglens.service;


import com.dinidu.loglens.dto.EmbeddingRequest;
import com.dinidu.loglens.dto.EmbeddingResponse;
import com.dinidu.loglens.dto.ExplanationRequest;
import com.dinidu.loglens.dto.ExplanationResponse;
import com.dinidu.loglens.exception.AIServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIService {

    private final RestTemplate restTemplate;

    @Value("${ai.service.base-url:http://localhost:8001}")
    private String aiServiceBaseUrl;

    @Value("${ai.service.timeout:30000}")
    private int timeoutMs;

    /**
     * Generates vector embedding for a log message using the Python AI microservice
     *
     * @param logMessage The raw log message to generate embedding for
     * @return List of Float values representing the vector embedding
     * @throws AIServiceException if the AI service is unavailable or returns an error
     */
    @Retryable(
            value = {ResourceAccessException.class, RestClientException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public List<Float> generateEmbedding(String logMessage) {
        log.debug("Generating embedding for log message: {}", logMessage);

        if (logMessage == null || logMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("Log message cannot be null or empty");
        }

        try {
            String url = aiServiceBaseUrl + "/api/v1/embeddings";

            // Create request payload
            EmbeddingRequest request = EmbeddingRequest.builder()
                    .logMessage(logMessage.trim())
                    .build();

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("User-Agent", "LogAnalysis-Backend/1.0");

            HttpEntity<EmbeddingRequest> entity = new HttpEntity<>(request, headers);

            // Make the request
            ResponseEntity<EmbeddingResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    EmbeddingResponse.class
            );

            if (response.getStatusCode() != HttpStatus.OK) {
                throw new AIServiceException("AI service returned status: " + response.getStatusCode());
            }

            EmbeddingResponse embeddingResponse = response.getBody();
            if (embeddingResponse == null || embeddingResponse.getEmbedding() == null) {
                throw new AIServiceException("AI service returned null or empty embedding");
            }

            List<Float> embedding = embeddingResponse.getEmbedding();
            log.debug("Successfully generated embedding with {} dimensions", embedding.size());

            return embedding;

        } catch (RestClientException e) {
            log.error("Error calling AI service for embedding generation: {}", e.getMessage());
            throw new AIServiceException("Failed to generate embedding: " + e.getMessage(), e);
        }
    }

    /**
     * Gets AI-generated explanation for an anomalous log using similar logs as context
     *
     * @param anomalousLog The anomalous log message that needs explanation
     * @param similarLogs List of similar log messages to provide context
     * @return Human-readable explanation of the potential issue
     * @throws AIServiceException if the AI service is unavailable or returns an error
     */
    @Retryable(
            value = {ResourceAccessException.class, RestClientException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String getExplanation(String anomalousLog, List<String> similarLogs) {
        log.debug("Getting explanation for anomalous log with {} similar logs",
                similarLogs != null ? similarLogs.size() : 0);

        if (anomalousLog == null || anomalousLog.trim().isEmpty()) {
            throw new IllegalArgumentException("Anomalous log cannot be null or empty");
        }

        try {
            String url = aiServiceBaseUrl + "/api/v1/explain";

            // Create request payload
            ExplanationRequest request = ExplanationRequest.builder()
                    .anomalousLog(anomalousLog.trim())
                    .similarLogs(similarLogs != null ? similarLogs : List.of())
                    .build();

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("User-Agent", "LogAnalysis-Backend/1.0");

            HttpEntity<ExplanationRequest> entity = new HttpEntity<>(request, headers);

            // Make the request
            ResponseEntity<ExplanationResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ExplanationResponse.class
            );

            if (response.getStatusCode() != HttpStatus.OK) {
                throw new AIServiceException("AI service returned status: " + response.getStatusCode());
            }

            ExplanationResponse explanationResponse = response.getBody();
            if (explanationResponse == null || explanationResponse.getExplanation() == null) {
                throw new AIServiceException("AI service returned null explanation");
            }

            String explanation = explanationResponse.getExplanation();
            log.debug("Successfully generated explanation with {} characters", explanation.length());

            return explanation;

        } catch (RestClientException e) {
            log.error("Error calling AI service for explanation generation: {}", e.getMessage());
            throw new AIServiceException("Failed to generate explanation: " + e.getMessage(), e);
        }
    }

    /**
     * Health check method to verify AI service connectivity
     *
     * @return true if AI service is available, false otherwise
     */
    public boolean isAIServiceHealthy() {
        try {
            String url = aiServiceBaseUrl + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.warn("AI service health check failed: {}", e.getMessage());
            return false;
        }
    }
}
