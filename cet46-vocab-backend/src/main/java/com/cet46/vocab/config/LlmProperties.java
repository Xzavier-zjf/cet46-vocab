package com.cet46.vocab.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties("llm.ollama")
public class LlmProperties {

    private String baseUrl;
    private String model;
    // Prefer duration style config: e.g. llm.ollama.timeout: 60s
    private Duration timeout = Duration.ofSeconds(60);
    // Backward compatibility: llm.ollama.timeout-seconds
    private Integer timeoutSeconds;
    private Integer maxRetries = 2;
    // Qwen3.5 supports thinking mode; set false to avoid extra thinking text.
    private Boolean think = false;
    // Ask Ollama to force JSON output for reliable parser handling.
    private String format = "json";
    private CacheProperties cache = new CacheProperties();

    public Duration resolveTimeout() {
        if (timeout != null) {
            return timeout;
        }
        if (timeoutSeconds != null && timeoutSeconds > 0) {
            return Duration.ofSeconds(timeoutSeconds);
        }
        return Duration.ofSeconds(60);
    }

    @Data
    public static class CacheProperties {
        private Boolean enabled = true;
        private String prefix = "llm:content:";
    }
}
