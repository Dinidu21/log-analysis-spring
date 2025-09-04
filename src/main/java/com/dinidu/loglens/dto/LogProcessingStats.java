package com.dinidu.loglens.dto;


import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;

@Data
@Builder
public class LogProcessingStats {
    private String fileName;
    private long fileSize;
    private int totalLines;
    private int processedLines;
    private int anomaliesDetected;
    private int errorCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String errorMessage;

    public Duration getProcessingDuration() {
        if (startTime != null && endTime != null) {
            return Duration.between(startTime, endTime);
        }
        return null;
    }

    public void incrementErrorCount() {
        this.errorCount++;
    }

    public double getAnomalyPercentage() {
        return processedLines > 0 ? (double) anomaliesDetected / processedLines * 100 : 0;
    }

    public double getSuccessRate() {
        return totalLines > 0 ? (double) processedLines / totalLines * 100 : 0;
    }
}
