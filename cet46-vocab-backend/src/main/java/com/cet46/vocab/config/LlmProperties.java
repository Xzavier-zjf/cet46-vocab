package com.cet46.vocab.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("llm.ollama")
public class LlmProperties {

    private String baseUrl;
    private String model;
    private Integer timeoutSeconds = 30;
    private Integer maxRetries = 2;
    private CacheProperties cache = new CacheProperties();

    @Data
    public static class CacheProperties {
        private Boolean enabled = true;
        private String prefix = "llm:content:";
    }
}
