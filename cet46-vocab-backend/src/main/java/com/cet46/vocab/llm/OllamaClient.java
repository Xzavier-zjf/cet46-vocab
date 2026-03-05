package com.cet46.vocab.llm;

import com.cet46.vocab.config.LlmProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class OllamaClient {

    private final LlmProperties llmProperties;
    private final RestTemplate restTemplate;

    public OllamaClient(LlmProperties llmProperties, RestTemplateBuilder restTemplateBuilder) {
        this.llmProperties = llmProperties;
        int timeout = llmProperties.getTimeoutSeconds() != null ? llmProperties.getTimeoutSeconds() : 30;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(timeout))
                .setReadTimeout(Duration.ofSeconds(timeout))
                .build();
    }

    public String generate(String prompt) {
        String url = llmProperties.getBaseUrl() + "/api/generate";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", llmProperties.getModel());
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        int retries = llmProperties.getMaxRetries() != null ? llmProperties.getMaxRetries() : 2;
        RuntimeException lastException = null;

        for (int i = 0; i <= retries; i++) {
            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
                Map<?, ?> body = response.getBody();
                if (body == null || body.get("response") == null) {
                    throw new LlmCallException("ollama response is empty");
                }
                return String.valueOf(body.get("response"));
            } catch (RuntimeException ex) {
                lastException = ex;
            }
        }

        throw new LlmCallException("ollama call failed", lastException);
    }

    public static class LlmCallException extends RuntimeException {
        public LlmCallException(String message) {
            super(message);
        }

        public LlmCallException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
