package com.cet46.vocab.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties("llm.cloud")
public class CloudLlmProperties {

    private Boolean enabled = false;
    private String baseUrl;
    private String path = "/v1/chat/completions";
    private String model;
    private String apiKey;
    private Duration timeout = Duration.ofSeconds(60);
    private Integer maxRetries = 1;
    private Double temperature = 0.3;
}

