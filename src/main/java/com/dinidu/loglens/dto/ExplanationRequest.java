package com.dinidu.loglens.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@Builder
public class ExplanationRequest {

    @NotBlank(message = "Anomalous log is required")
    @Size(max = 10000, message = "Anomalous log must not exceed 10000 characters")
    @JsonProperty("anomalous_log")
    private String anomalousLog;

    @JsonProperty("similar_logs")
    private List<String> similarLogs;
}
