package com.cet46.vocab.llm;

import com.cet46.vocab.config.LlmProperties;
import com.cet46.vocab.dto.response.CloudLlmHealthResponse;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Component
public class OllamaClient {

    private final LlmProperties llmProperties;
    private final RestTemplate restTemplate;

    public OllamaClient(LlmProperties llmProperties, RestTemplateBuilder restTemplateBuilder) {
        this.llmProperties = llmProperties;
        Duration timeout = llmProperties.resolveTimeout();
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(timeout)
                .setReadTimeout(timeout)
                .build();
    }

    public String generate(String prompt) {
        return generateWithOptions(prompt, 220, true);
    }

    public String generatePlainText(String prompt, int numPredict) {
        int safeNumPredict = Math.max(64, numPredict);
        return generateWithOptionsMeta(prompt, safeNumPredict, false).content();
    }

    public GenerationResult generatePlainTextWithMeta(String prompt, int numPredict) {
        int safeNumPredict = Math.max(64, numPredict);
        return generateWithOptionsMeta(prompt, safeNumPredict, false);
    }

    private String generateWithOptions(String prompt, int numPredict, boolean includeFormat) {
        return generateWithOptionsMeta(prompt, numPredict, includeFormat).content();
    }

    private GenerationResult generateWithOptionsMeta(String prompt, int numPredict, boolean includeFormat) {
        String url = llmProperties.getBaseUrl() + "/api/generate";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", llmProperties.getModel());
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false);
        requestBody.put("think", Boolean.TRUE.equals(llmProperties.getThink()));
        requestBody.put("options", Map.of(
                "num_predict", numPredict,
                "temperature", 0.3
        ));
        if (includeFormat && StringUtils.hasText(llmProperties.getFormat())) {
            requestBody.put("format", llmProperties.getFormat().trim());
        }

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
                String content = String.valueOf(body.get("response"));
                if (!StringUtils.hasText(content)) {
                    throw new LlmCallException("ollama response content is blank");
                }
                return new GenerationResult(content, isLengthTruncated(body));
            } catch (RuntimeException ex) {
                lastException = ex;
            }
        }

        throw new LlmCallException("ollama call failed", lastException);
    }

    private boolean isLengthTruncated(Map<?, ?> body) {
        Object doneReason = body.get("done_reason");
        if (doneReason == null) {
            return false;
        }
        String value = String.valueOf(doneReason).trim().toLowerCase(Locale.ROOT);
        return "length".equals(value) || "max_tokens".equals(value);
    }

    public CloudLlmHealthResponse healthCheck() {
        List<String> details = new ArrayList<>();
        String baseUrl = llmProperties.getBaseUrl();
        String model = llmProperties.getModel();
        boolean configured = StringUtils.hasText(baseUrl) && StringUtils.hasText(model);
        if (!StringUtils.hasText(baseUrl)) {
            details.add("\u7F3A\u5C11 llm.ollama.base-url");
        }
        if (!StringUtils.hasText(model)) {
            details.add("\u7F3A\u5C11 llm.ollama.model");
        }

        boolean dnsOk = false;
        if (StringUtils.hasText(baseUrl)) {
            try {
                URI uri = URI.create(baseUrl.trim());
                String host = uri.getHost();
                if (StringUtils.hasText(host)) {
                    InetAddress.getByName(host);
                    dnsOk = true;
                } else {
                    details.add("base-url \u672A\u5305\u542B\u53EF\u89E3\u6790 host");
                }
            } catch (Exception ex) {
                details.add("DNS \u89E3\u6790\u5931\u8D25: " + ex.getMessage());
            }
        }

        boolean modelOk = false;
        Long latencyMs = null;
        if (configured && dnsOk) {
            long start = System.currentTimeMillis();
            try {
                String probe = probeModelByTags();
                latencyMs = System.currentTimeMillis() - start;
                modelOk = "ok".equalsIgnoreCase(probe);
                if (!modelOk) {
                    details.add("\u672C\u5730\u6A21\u578B\u672A\u5B89\u88C5\u6216\u6A21\u578B\u540D\u4E0D\u5339\u914D");
                }
            } catch (Exception ex) {
                latencyMs = System.currentTimeMillis() - start;
                details.add("\u672C\u5730\u6A21\u578B\u8C03\u7528\u5931\u8D25: " + ex.getMessage());
            }
        }

        String message;
        if (!configured) {
            message = "\u672C\u5730\u6A21\u578B\u914D\u7F6E\u4E0D\u5B8C\u6574";
        } else if (!dnsOk) {
            message = "\u672C\u5730\u670D\u52A1\u8FDE\u63A5\u5931\u8D25";
        } else if (!modelOk) {
            message = "\u672C\u5730\u6A21\u578B\u4E0D\u53EF\u7528";
        } else {
            message = "\u672C\u5730\u6A21\u578B\u8FDE\u901A\u6B63\u5E38";
        }

        return CloudLlmHealthResponse.builder()
                .currentProvider(LlmProvider.LOCAL)
                .baseUrl(baseUrl)
                .model(model)
                .configured(configured)
                .dnsOk(dnsOk)
                .authOk(true)
                .modelOk(modelOk)
                .latencyMs(latencyMs)
                .message(message)
                .details(details)
                .build();
    }

    private String probeModelByTags() {
        String url = llmProperties.getBaseUrl() + "/api/tags";
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        Map<?, ?> body = response.getBody();
        if (body == null) {
            return null;
        }
        Object modelsObj = body.get("models");
        if (!(modelsObj instanceof List<?> models) || models.isEmpty()) {
            return null;
        }
        String target = normalizeModelName(llmProperties.getModel());
        for (Object item : models) {
            if (!(item instanceof Map<?, ?> modelMap)) {
                continue;
            }
            Object nameObj = modelMap.get("name");
            if (nameObj == null) {
                continue;
            }
            String name = normalizeModelName(String.valueOf(nameObj));
            if (name.equals(target) || name.startsWith(target + ":") || target.startsWith(name + ":")) {
                return "ok";
            }
        }
        return null;
    }

    private String normalizeModelName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static class LlmCallException extends RuntimeException {
        public LlmCallException(String message) {
            super(message);
        }

        public LlmCallException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public record GenerationResult(String content, boolean truncated) {
    }
}
