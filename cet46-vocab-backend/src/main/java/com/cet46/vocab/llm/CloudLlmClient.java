package com.cet46.vocab.llm;

import com.cet46.vocab.config.CloudLlmProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CloudLlmClient {

    private final CloudLlmProperties cloudLlmProperties;
    private final RestTemplate restTemplate;

    public CloudLlmClient(CloudLlmProperties cloudLlmProperties, RestTemplateBuilder restTemplateBuilder) {
        this.cloudLlmProperties = cloudLlmProperties;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(cloudLlmProperties.getTimeout())
                .setReadTimeout(cloudLlmProperties.getTimeout())
                .build();
    }

    public String generate(String prompt) {
        if (!Boolean.TRUE.equals(cloudLlmProperties.getEnabled())) {
            throw new OllamaClient.LlmCallException("cloud llm is disabled");
        }
        if (!StringUtils.hasText(cloudLlmProperties.getBaseUrl()) || !StringUtils.hasText(cloudLlmProperties.getModel())) {
            throw new OllamaClient.LlmCallException("cloud llm base-url/model is not configured");
        }

        String url = joinUrl(cloudLlmProperties.getBaseUrl(), cloudLlmProperties.getPath());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(cloudLlmProperties.getApiKey())) {
            headers.setBearerAuth(cloudLlmProperties.getApiKey().trim());
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", cloudLlmProperties.getModel());
        requestBody.put("temperature", cloudLlmProperties.getTemperature());
        requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        int retries = cloudLlmProperties.getMaxRetries() == null ? 1 : Math.max(cloudLlmProperties.getMaxRetries(), 0);
        RuntimeException lastException = null;
        for (int i = 0; i <= retries; i++) {
            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
                String content = extractContent(response.getBody());
                if (!StringUtils.hasText(content)) {
                    throw new OllamaClient.LlmCallException("cloud llm response content is empty");
                }
                return content;
            } catch (RuntimeException ex) {
                lastException = ex;
            }
        }

        throw new OllamaClient.LlmCallException("cloud llm call failed", lastException);
    }

    private String joinUrl(String baseUrl, String path) {
        String base = baseUrl.trim();
        String suffix = StringUtils.hasText(path) ? path.trim() : "/v1/chat/completions";
        if (base.endsWith("/") && suffix.startsWith("/")) {
            return base.substring(0, base.length() - 1) + suffix;
        }
        if (!base.endsWith("/") && !suffix.startsWith("/")) {
            return base + "/" + suffix;
        }
        return base + suffix;
    }

    private String extractContent(Map<?, ?> body) {
        if (body == null) {
            return null;
        }
        Object responseField = body.get("response");
        if (responseField instanceof String responseText && StringUtils.hasText(responseText)) {
            return responseText;
        }
        Object choices = body.get("choices");
        if (!(choices instanceof List<?> choiceList) || choiceList.isEmpty()) {
            return null;
        }
        Object firstChoice = choiceList.get(0);
        if (!(firstChoice instanceof Map<?, ?> firstChoiceMap)) {
            return null;
        }
        Object message = firstChoiceMap.get("message");
        if (!(message instanceof Map<?, ?> messageMap)) {
            return null;
        }
        Object content = messageMap.get("content");
        return content == null ? null : String.valueOf(content);
    }
}

