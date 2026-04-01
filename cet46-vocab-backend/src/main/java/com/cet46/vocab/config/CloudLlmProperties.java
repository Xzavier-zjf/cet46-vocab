package com.cet46.vocab.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Data
@Component
@ConfigurationProperties("llm.cloud")
public class CloudLlmProperties {

    private static final List<String> DEFAULT_PROVIDERS = List.of("bailian");
    private static final Map<String, String> DEFAULT_PROVIDER_LABELS = Map.of(
            "bailian", "\u767e\u70bc",
            "openai", "OpenAI",
            "deepseek", "DeepSeek",
            "openrouter", "OpenRouter",
            "groq", "Groq",
            "xai", "xAI",
            "minimax", "MiniMax"
    );

    private Boolean enabled = false;
    private String baseUrl;
    private String path = "/v1/chat/completions";
    private String model;
    private List<String> models = new ArrayList<>();
    private List<String> providers = new ArrayList<>();
    private Map<String, String> providerLabels = new LinkedHashMap<>();
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

    public List<String> resolveProviders() {
        List<String> normalized = new ArrayList<>();
        if (providers != null) {
            for (String item : providers) {
                if (!StringUtils.hasText(item)) {
                    continue;
                }
                String value = item.trim().toLowerCase(Locale.ROOT);
                if (!normalized.contains(value)) {
                    normalized.add(value);
                }
            }
        }
        if (normalized.isEmpty()) {
            normalized.addAll(DEFAULT_PROVIDERS);
        }
        return normalized;
    }

    public boolean isSupportedProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return false;
        }
        String target = provider.trim().toLowerCase(Locale.ROOT);
        return resolveProviders().contains(target);
    }

    public String resolveDefaultProvider() {
        List<String> items = resolveProviders();
        return items.isEmpty() ? "bailian" : items.get(0);
    }

    public Map<String, String> resolveProviderLabels() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (String provider : resolveProviders()) {
            if (StringUtils.hasText(provider)) {
                result.put(provider, provider);
            }
        }
        if (providerLabels != null) {
            providerLabels.forEach((k, v) -> {
                if (!StringUtils.hasText(k) || !StringUtils.hasText(v)) {
                    return;
                }
                String key = k.trim().toLowerCase(Locale.ROOT);
                result.put(key, v.trim());
            });
        }
        DEFAULT_PROVIDER_LABELS.forEach(result::putIfAbsent);
        return result;
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

