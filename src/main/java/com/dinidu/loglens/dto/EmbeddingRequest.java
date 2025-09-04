package com.dinidu.loglens.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Builder
public class EmbeddingRequest {

    @NotBlank(message = "Log message is required")
    @Size(max = 10000, message = "Log message must not exceed 10000 characters")
    @JsonProperty("log_message")
    private String logMessage;
}