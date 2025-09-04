package com.dinidu.loglens;

import com.dinidu.loglens.model.User;
import com.dinidu.loglens.repository.LogEntryRepository;
import com.dinidu.loglens.service.AIService;
import com.dinidu.loglens.service.AnomalyDetectionService;
import com.dinidu.loglens.service.LogProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogProcessingServiceTest {

    @Mock
    private AIService aiService;

    @Mock
    private LogEntryRepository logEntryRepository;

    @Mock
    private AnomalyDetectionService anomalyDetectionService;

    @InjectMocks
    private LogProcessingService logProcessingService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .name("Test User")
                .provider(User.Provider.GOOGLE)
                .providerId("123456")
                .build();
    }

    @Test
    void testProcessLogFile_Success() {
        // Given
        String logContent = """
            2024-01-15 10:30:45 [INFO] Application started successfully
            2024-01-15 10:30:46 [ERROR] Database connection failed
            2024-01-15 10:30:47 [WARN] Retrying database connection
            """;

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.log",
                "text/plain",
                logContent.getBytes()
        );

        // Mock AI service responses
        when(aiService.generateEmbedding(anyString()))
                .thenReturn(Arrays.asList(0.1f, 0.2f, 0.3f, 0.4f));
        when(aiService.getExplanation(anyString(), anyList()))
                .thenReturn("This error indicates a database connectivity issue");

        // Mock anomaly detection
        when(anomalyDetectionService.detectAnomaly(anyList(), eq(testUser), anyDouble()))
                .thenReturn(false, true, false); // Second log is anomaly
        when(anomalyDetectionService.calculateSimilarityScore(anyList(), eq(testUser)))
                .thenReturn(0.15); // Below threshold

        // Mock repository
        when(logEntryRepository.findSimilarLogEntries(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of());
        when(logEntryRepository.saveAll(anyList()))
                .thenReturn(List.of());

        // When & Then - should not throw exception
        // LogProcessingResult result = logProcessingService.processLogFile(file, testUser);

        // Verify interactions
        verify(aiService, times(3)).generateEmbedding(anyString());
        verify(anomalyDetectionService, times(3)).detectAnomaly(anyList(), eq(testUser), anyDouble());
    }
}