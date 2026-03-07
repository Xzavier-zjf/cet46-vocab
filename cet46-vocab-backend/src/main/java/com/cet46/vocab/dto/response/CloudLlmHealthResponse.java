package com.cet46.vocab.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CloudLlmHealthResponse {

    private String currentProvider;
    private String baseUrl;
    private String model;
    private Boolean configured;
    private Boolean dnsOk;
    private Boolean authOk;
    private Boolean modelOk;
    private Long latencyMs;
    private String message;
    private List<String> details;
}

