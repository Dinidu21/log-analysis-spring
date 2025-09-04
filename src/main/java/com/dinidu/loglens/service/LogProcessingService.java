package com.dinidu.loglens.service;


import com.dinidu.loglens.dto.LogProcessingResult;
import com.dinidu.loglens.dto.LogProcessingStats;
import com.dinidu.loglens.exception.LogProcessingException;
import com.dinidu.loglens.model.LogEntry;
import com.dinidu.loglens.model.User;
import com.dinidu.loglens.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogProcessingService {

    private final AIService aiService;
    private final LogEntryRepository logEntryRepository;
    private final AnomalyDetectionService anomalyDetectionService;

    @Value("${log.processing.anomaly-threshold:0.2}")
    private double anomalyThreshold;

    @Value("${log.processing.batch-size:50}")
    private int batchSize;

    @Value("${log.processing.max-similar-logs:5}")
    private int maxSimilarLogs;

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    // Common log patterns
    private static final List<Pattern> LOG_PATTERNS = Arrays.asList(
            // Standard format: 2024-01-15 10:30:45 [INFO] Message
            Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})\\s*\\[?(\\w+)\\]?\\s+(.*)$"),
            // ISO format: 2024-01-15T10:30:45Z [INFO] Message
            Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z?)\\s*\\[?(\\w+)\\]?\\s+(.*)$"),
            // Syslog format: Jan 15 10:30:45 [INFO] Message
            Pattern.compile("^(\\w{3}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2})\\s*\\[?(\\w+)\\]?\\s+(.*)$"),
            // Simple format: [INFO] Message (no timestamp)
            Pattern.compile("^\\[?(\\w+)\\]?\\s+(.*)$")
    );

    /**
     * Processes an uploaded log file and detects anomalies
     */
    @Transactional
    public LogProcessingResult  processLogFile(MultipartFile file, User user) {
        log.info("Starting log file processing for user: {} with file: {}",
                user.getEmail(), file.getOriginalFilename());

        validateFile(file);

        LogProcessingStats stats = LogProcessingStats.builder()
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .startTime(LocalDateTime.now())
                .build();

        List<LogEntry> processedEntries = new ArrayList<>();
        List<CompletableFuture<LogEntry>> futures = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            List<String> logLines = reader.lines()
                    .filter(line -> !line.trim().isEmpty())
                    .collect(Collectors.toList());

            stats.setTotalLines(logLines.size());
            log.info("Processing {} log lines", logLines.size());

            // Process logs in batches to avoid overwhelming the AI service
            List<List<String>> batches = createBatches(logLines, batchSize);

            for (List<String> batch : batches) {
                List<CompletableFuture<LogEntry>> batchFutures = batch.stream()
                        .map(logLine -> processLogLineAsync(logLine, user))
                        .collect(Collectors.toList());

                futures.addAll(batchFutures);

                // Wait for batch to complete before starting next batch
                CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();

                // Collect completed entries
                for (CompletableFuture<LogEntry> future : batchFutures) {
                    try {
                        LogEntry entry = future.get();
                        if (entry != null) {
                            processedEntries.add(entry);
                        }
                    } catch (Exception e) {
                        log.error("Error processing log entry: {}", e.getMessage());
                        stats.incrementErrorCount();
                    }
                }

                log.info("Processed batch of {} entries. Total processed: {}",
                        batch.size(), processedEntries.size());
            }

            // Save all processed entries
            List<LogEntry> savedEntries = logEntryRepository.saveAll(processedEntries);

            // Update statistics
            stats.setProcessedLines(savedEntries.size());
            stats.setAnomaliesDetected((int) savedEntries.stream().filter(LogEntry::getIsAnomaly).count());
            stats.setEndTime(LocalDateTime.now());

            log.info("Log processing completed. Processed: {}, Anomalies: {}",
                    stats.getProcessedLines(), stats.getAnomaliesDetected());

            return LogProcessingResult.builder()
                    .stats(stats)
                    .logEntries(savedEntries)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Error processing log file: {}", e.getMessage(), e);
            stats.setEndTime(LocalDateTime.now());
            stats.setErrorMessage(e.getMessage());

            return LogProcessingResult.builder()
                    .stats(stats)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * Processes a single log line asynchronously
     */
    private CompletableFuture<LogEntry> processLogLineAsync(String logLine, User user) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return processLogLine(logLine, user);
            } catch (Exception e) {
                log.error("Error processing log line '{}': {}", logLine, e.getMessage());
                return null;
            }
        }, executorService);
    }

    /**
     * Processes a single log line
     */
    private LogEntry processLogLine(String logLine, User user) {
        log.debug("Processing log line: {}", logLine);

        // Parse log entry
        ParsedLogEntry parsed = parseLogLine(logLine);

        // Generate embedding
        List<Float> embedding = aiService.generateEmbedding(parsed.getMessage());

        // Create initial log entry
        LogEntry logEntry = LogEntry.builder()
                .timestamp(parsed.getTimestamp())
                .logMessage(parsed.getMessage())
                .logLevel(parsed.getLevel())
                .embedding(embedding)
                .user(user)
                .isAnomaly(false)
                .build();

        // Perform anomaly detection
        boolean isAnomaly = anomalyDetectionService.detectAnomaly(embedding, user, anomalyThreshold);
        logEntry.setIsAnomaly(isAnomaly);

        // Generate explanation if anomaly detected
        if (isAnomaly) {
            try {
                List<String> similarLogs = findSimilarLogs(embedding, user);
                String explanation = aiService.getExplanation(parsed.getMessage(), similarLogs);
                logEntry.setExplanation(explanation);

                // Calculate similarity score for the most similar log
                Double similarityScore = anomalyDetectionService.calculateSimilarityScore(embedding, user);
                logEntry.setSimilarityScore(similarityScore);

                log.info("Anomaly detected in log: {} (similarity: {})",
                        parsed.getMessage(), similarityScore);
            } catch (Exception e) {
                log.warn("Failed to generate explanation for anomaly: {}", e.getMessage());
                logEntry.setExplanation("Anomaly detected but explanation generation failed: " + e.getMessage());
            }
        }

        return logEntry;
    }

    /**
     * Parses a log line to extract timestamp, level, and message
     */
    private ParsedLogEntry parseLogLine(String logLine) {
        for (Pattern pattern : LOG_PATTERNS) {
            Matcher matcher = pattern.matcher(logLine.trim());
            if (matcher.matches()) {
                if (matcher.groupCount() == 3) {
                    // Pattern with timestamp
                    String timestampStr = matcher.group(1);
                    String level = matcher.group(2).toUpperCase();
                    String message = matcher.group(3);

                    LocalDateTime timestamp = parseTimestamp(timestampStr);
                    return new ParsedLogEntry(timestamp, level, message);
                } else if (matcher.groupCount() == 2) {
                    // Pattern without timestamp
                    String level = matcher.group(1).toUpperCase();
                    String message = matcher.group(2);

                    return new ParsedLogEntry(LocalDateTime.now(), level, message);
                }
            }
        }

        // If no pattern matches, treat entire line as message
        return new ParsedLogEntry(LocalDateTime.now(), "UNKNOWN", logLine.trim());
    }

    /**
     * Parses timestamp from various formats
     */
    private LocalDateTime parseTimestamp(String timestampStr) {
        List<DateTimeFormatter> formatters = Arrays.asList(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
                DateTimeFormatter.ofPattern("MMM dd HH:mm:ss")
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                if (formatter.toString().contains("MMM")) {
                    // For syslog format, assume current year
                    String currentYear = String.valueOf(LocalDateTime.now().getYear());
                    return LocalDateTime.parse(currentYear + " " + timestampStr,
                            DateTimeFormatter.ofPattern("yyyy MMM dd HH:mm:ss"));
                } else {
                    return LocalDateTime.parse(timestampStr, formatter);
                }
            } catch (DateTimeParseException ignored) {
                // Try next format
            }
        }

        // If all parsing fails, use current time
        log.warn("Could not parse timestamp: {}, using current time", timestampStr);
        return LocalDateTime.now();
    }

    /**
     * Finds similar logs for context in anomaly explanation
     */
    private List<String> findSimilarLogs(List<Float> embedding, User user) {
        try {
            // Convert embedding to string for native query
            String embeddingStr = embedding.toString();

            List<LogEntry> similarEntries = logEntryRepository.findSimilarLogEntries(
                    user.getId(), embeddingStr, maxSimilarLogs);

            return similarEntries.stream()
                    .map(LogEntry::getLogMessage)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Error finding similar logs: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Creates batches from a list of log lines
     */
    private <T> List<List<T>> createBatches(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches;
    }

    /**
     * Validates uploaded file
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new LogProcessingException("Uploaded file is empty");
        }

        if (file.getSize() > 50 * 1024 * 1024) { // 50MB limit
            throw new LogProcessingException("File size exceeds maximum limit of 50MB");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.startsWith("text/") &&
                !contentType.equals("application/octet-stream")) {
            throw new LogProcessingException("Invalid file type. Only text files are supported.");
        }
    }

    /**
     * Helper class for parsed log entry
     */
    private static class ParsedLogEntry {
        private final LocalDateTime timestamp;
        private final String level;
        private final String message;

        public ParsedLogEntry(LocalDateTime timestamp, String level, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.message = message;
        }

        public LocalDateTime getTimestamp() { return timestamp; }
        public String getLevel() { return level; }
        public String getMessage() { return message; }
    }
}
