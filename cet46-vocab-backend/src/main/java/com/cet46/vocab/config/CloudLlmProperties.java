package com.cet46.vocab.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties("llm.cloud")
public class CloudLlmProperties {

    private Boolean enabled = false;
    private String baseUrl;
    private String path = "/v1/chat/completions";
    private String model;
    private List<String> models = new ArrayList<>();
    private String apiKey;
    private Duration timeout = Duration.ofSeconds(60);
    private Integer maxRetries = 1;
    private Double temperature = 0.3;
    private Integer maxTokens = 600;

    public String getApiKey() {
        if (StringUtils.hasText(apiKey)) {
            return apiKey.trim();
        }
        String fallback = firstNonBlank(
                System.getProperty("LLM_CLOUD_API_KEY"),
                System.getenv("LLM_CLOUD_API_KEY"),
                System.getProperty("DASHSCOPE_API_KEY"),
                System.getenv("DASHSCOPE_API_KEY"),
                System.getProperty("ALIYUN_DASHSCOPE_API_KEY"),
                System.getenv("ALIYUN_DASHSCOPE_API_KEY")
        );
        return fallback == null ? null : fallback.trim();
    }

    public List<String> resolveModels() {
        List<String> normalized = new ArrayList<>();
        if (models != null) {
            for (String item : models) {
                if (StringUtils.hasText(item)) {
                    normalized.add(item.trim());
                }
            }
        }
        if (normalized.isEmpty() && StringUtils.hasText(model)) {
            normalized.add(model.trim());
        }
        return normalized;
    }

    public String resolveDefaultModel() {
        if (StringUtils.hasText(model)) {
            return model.trim();
        }
        List<String> normalized = resolveModels();
        return normalized.isEmpty() ? null : normalized.get(0);
    }

    public boolean isSupportedModel(String targetModel) {
        if (!StringUtils.hasText(targetModel)) {
            return false;
        }
        String target = targetModel.trim();
        for (String item : resolveModels()) {
            if (target.equals(item)) {
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
