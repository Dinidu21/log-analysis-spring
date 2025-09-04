package com.dinidu.loglens.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class EmbeddingResponse {

    @JsonProperty("embedding")
    private List<Float> embedding;

    @JsonProperty("model_name")
    private String modelName;

    @JsonProperty("dimension")
    private Integer dimension;
}
