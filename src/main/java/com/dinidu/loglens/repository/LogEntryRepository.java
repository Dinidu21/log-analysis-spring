package com.dinidu.loglens.repository;

import com.dinidu.loglens.model.LogEntry;
import com.dinidu.loglens.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LogEntryRepository extends JpaRepository<LogEntry, Long> {

    List<LogEntry> findByUserOrderByTimestampDesc(User user);

    Page<LogEntry> findByUserOrderByTimestampDesc(User user, Pageable pageable);

    List<LogEntry> findByUserAndIsAnomalyTrueOrderByTimestampDesc(User user);

    Page<LogEntry> findByUserAndIsAnomalyTrueOrderByTimestampDesc(User user, Pageable pageable);

    @Query("SELECT l FROM LogEntry l WHERE l.user = :user AND l.timestamp BETWEEN :startDate AND :endDate ORDER BY l.timestamp DESC")
    List<LogEntry> findByUserAndTimestampBetween(@Param("user") User user,
                                                 @Param("startDate") LocalDateTime startDate,
                                                 @Param("endDate") LocalDateTime endDate);

    // For similarity search with pgvector (this would need pgvector extension enabled)
    @Query(value = "SELECT * FROM log_entries WHERE user_id = :userId AND is_anomaly = false ORDER BY embedding <-> CAST(:targetEmbedding AS vector) LIMIT :limit", nativeQuery = true)
    List<LogEntry> findSimilarLogEntries(@Param("userId") Long userId,
                                         @Param("targetEmbedding") String targetEmbedding,
                                         @Param("limit") int limit);

    long countByUserAndIsAnomalyTrue(User user);

    long countByUser(User user);
}