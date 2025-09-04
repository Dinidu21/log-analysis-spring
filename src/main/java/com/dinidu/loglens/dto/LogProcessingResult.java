package com.dinidu.loglens.dto;

import com.dinidu.loglens.model.LogEntry;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LogProcessingResult {
    private LogProcessingStats stats;
    private List<LogEntry> logEntries;
    private boolean success;
    private String errorMessage;
}
