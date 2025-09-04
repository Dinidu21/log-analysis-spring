package com.dinidu.loglens.controller;


import com.dinidu.loglens.dto.LogProcessingResult;
import com.dinidu.loglens.model.LogEntry;
import com.dinidu.loglens.model.User;
import com.dinidu.loglens.repository.LogEntryRepository;
import com.dinidu.loglens.security.CustomOAuth2User;
import com.dinidu.loglens.service.LogProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Slf4j
public class LogController {

    private final LogProcessingService logProcessingService;
    private final LogEntryRepository logEntryRepository;

    /**
     * Upload and process log file
     */
    @PostMapping("/upload")
    public ResponseEntity<LogProcessingResult> uploadLogFile(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomOAuth2User oauth2User) {

        log.info("Received log file upload request from user: {}", oauth2User.getUser().getEmail());

        LogProcessingResult result = logProcessingService.processLogFile(file, oauth2User.getUser());

        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Get all log entries for the authenticated user with pagination
     */
    @GetMapping
    public ResponseEntity<Page<LogEntry>> getAllLogs(
            @AuthenticationPrincipal CustomOAuth2User oauth2User,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<LogEntry> logEntries = logEntryRepository.findByUserOrderByTimestampDesc(
                oauth2User.getUser(), pageable);

        return ResponseEntity.ok(logEntries);
    }

    /**
     * Get only anomalous log entries for the authenticated user
     */
    @GetMapping("/anomalies")
    public ResponseEntity<Page<LogEntry>> getAnomalies(
            @AuthenticationPrincipal CustomOAuth2User oauth2User,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<LogEntry> anomalies = logEntryRepository.findByUserAndIsAnomalyTrueOrderByTimestampDesc(
                oauth2User.getUser(), pageable);

        return ResponseEntity.ok(anomalies);
    }

    /**
     * Get log entry by ID (must belong to authenticated user)
     */
    @GetMapping("/{id}")
    public ResponseEntity<LogEntry> getLogEntry(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomOAuth2User oauth2User) {

        return logEntryRepository.findById(id)
                .filter(entry -> entry.getUser().getId().equals(oauth2User.getUser().getId()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get user's log statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getLogStats(
            @AuthenticationPrincipal CustomOAuth2User oauth2User) {

        User user = oauth2User.getUser();
        long totalLogs = logEntryRepository.countByUser(user);
        long totalAnomalies = logEntryRepository.countByUserAndIsAnomalyTrue(user);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalLogs", totalLogs);
        stats.put("totalAnomalies", totalAnomalies);
        stats.put("anomalyPercentage", totalLogs > 0 ? (double) totalAnomalies / totalLogs * 100 : 0);

        return ResponseEntity.ok(stats);
    }

    /**
     * Delete a log entry (must belong to authenticated user)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLogEntry(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomOAuth2User oauth2User) {

        return logEntryRepository.findById(id)
                .filter(entry -> entry.getUser().getId().equals(oauth2User.getUser().getId()))
                .map(entry -> {
                    logEntryRepository.delete(entry);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete all log entries for authenticated user
     */
    @DeleteMapping("/all")
    public ResponseEntity<Map<String, Object>> deleteAllLogs(
            @AuthenticationPrincipal CustomOAuth2User oauth2User) {

        List<LogEntry> userLogs = logEntryRepository.findByUserOrderByTimestampDesc(oauth2User.getUser());
        long deletedCount = userLogs.size();

        logEntryRepository.deleteAll(userLogs);

        Map<String, Object> response = new HashMap<>();
        response.put("deletedCount", deletedCount);
        response.put("message", "All logs deleted successfully");

        return ResponseEntity.ok(response);
    }
}