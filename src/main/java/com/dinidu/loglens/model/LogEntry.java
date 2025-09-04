package com.dinidu.loglens.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "log_entries", indexes = {
        @Index(name = "idx_log_entries_user_id", columnList = "user_id"),
        @Index(name = "idx_log_entries_is_anomaly", columnList = "is_anomaly"),
        @Index(name = "idx_log_entries_timestamp", columnList = "timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "log_message", columnDefinition = "TEXT", nullable = false)
    private String logMessage;

    // Using List<Float> for pgvector compatibility
    // Note: You may need to add pgvector dependency and custom converter
    @Column(name = "embedding", columnDefinition = "vector(384)")
    @Convert(converter = VectorConverter.class)
    private List<Float> embedding;

    @Column(name = "is_anomaly", nullable = false)
    private Boolean isAnomaly = false;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "similarity_score")
    private Double similarityScore;

    @Column(name = "log_level")
    private String logLevel;

    @Column(name = "source_file")
    private String sourceFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}