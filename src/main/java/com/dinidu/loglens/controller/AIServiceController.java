package com.dinidu.loglens.controller;

import com.dinidu.loglens.service.AIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('USER')")
public class AIServiceController {

    private final AIService aiService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> checkAIServiceHealth() {
        Map<String, Object> response = new HashMap<>();
        boolean isHealthy = aiService.isAIServiceHealthy();

        response.put("ai_service_healthy", isHealthy);
        response.put("status", isHealthy ? "UP" : "DOWN");

        return ResponseEntity.ok(response);
    }
}
