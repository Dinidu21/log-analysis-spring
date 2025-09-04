package com.dinidu.loglens.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ExplanationResponse {

    @JsonProperty("explanation")
    private String explanation;

    @JsonProperty("confidence_score")
    private Double confidenceScore;

    @JsonProperty("model_used")
    private String modelUsed;
}
