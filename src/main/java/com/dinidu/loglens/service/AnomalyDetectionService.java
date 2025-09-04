package com.dinidu.loglens.service;


import com.dinidu.loglens.model.LogEntry;
import com.dinidu.loglens.model.User;
import com.dinidu.loglens.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectionService {

    private final LogEntryRepository logEntryRepository;

    /**
     * Detects if a log entry is anomalous based on similarity to baseline logs
     */
    public boolean detectAnomaly(List<Float> embedding, User user, double threshold) {
        try {
            Double maxSimilarity = calculateSimilarityScore(embedding, user);

            if (maxSimilarity == null) {
                // No baseline data available, consider as normal
                log.debug("No baseline data available for user {}, treating as normal", user.getId());
                return false;
            }

            boolean isAnomaly = maxSimilarity < threshold;
            log.debug("Similarity score: {}, Threshold: {}, Is anomaly: {}",
                    maxSimilarity, threshold, isAnomaly);

            return isAnomaly;

        } catch (Exception e) {
            log.error("Error during anomaly detection: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Calculates the maximum similarity score with existing baseline logs
     */
    public Double calculateSimilarityScore(List<Float> embedding, User user) {
        try {
            // Convert embedding to string format for the native query
            String embeddingStr = embedding.toString();

            // Find the most similar log entry (only non-anomalous ones for baseline)
            List<LogEntry> similarEntries = logEntryRepository.findSimilarLogEntries(
                    user.getId(), embeddingStr, 1);

            if (similarEntries.isEmpty()) {
                return null; // No baseline data
            }

            // Calculate cosine similarity with the most similar entry
            LogEntry mostSimilar = similarEntries.get(0);
            return calculateCosineSimilarity(embedding, mostSimilar.getEmbedding());

        } catch (Exception e) {
            log.error("Error calculating similarity score: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Calculates cosine similarity between two vectors
     */
    private double calculateCosineSimilarity(List<Float> vector1, List<Float> vector2) {
        if (vector1.size() != vector2.size()) {
            throw new IllegalArgumentException("Vectors must have the same dimension");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vector1.size(); i++) {
            dotProduct += vector1.get(i) * vector2.get(i);
            norm1 += Math.pow(vector1.get(i), 2);
            norm2 += Math.pow(vector2.get(i), 2);
        }

        norm1 = Math.sqrt(norm1);
        norm2 = Math.sqrt(norm2);

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (norm1 * norm2);
    }
}