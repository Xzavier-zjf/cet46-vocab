package com.cet46.vocab.llm;

import com.cet46.vocab.config.LlmProperties;
import com.cet46.vocab.dto.response.CloudLlmHealthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);
    private static final Pattern MODEL_SIZE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)b", Pattern.CASE_INSENSITIVE);

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

    public String getBaseUrl() {
        return llmProperties.getBaseUrl();
    }

    public String getDefaultModel() {
        return llmProperties.getModel();
    }

    public String generate(String prompt) {
        return generate(prompt, null);
    }

    public String generate(String prompt, String modelOverride) {
        return generateWithOptions(prompt, 220, true, modelOverride);
    }

    public String generatePlainText(String prompt, int numPredict) {
        return generatePlainText(prompt, numPredict, null);
    }

    public String generatePlainText(String prompt, int numPredict, String modelOverride) {
        int safeNumPredict = Math.max(64, numPredict);
        return generateWithOptionsMeta(prompt, safeNumPredict, false, modelOverride).content();
    }

    public GenerationResult generatePlainTextWithMeta(String prompt, int numPredict) {
        return generatePlainTextWithMeta(prompt, numPredict, null);
    }

    public GenerationResult generatePlainTextWithMeta(String prompt, int numPredict, String modelOverride) {
        int safeNumPredict = Math.max(64, numPredict);
        return generateWithOptionsMeta(prompt, safeNumPredict, false, modelOverride);
    }

    public List<LocalModelInfo> listModels() {
        return listModelsInternal();
    }

    public boolean isModelInstalled(String modelName) {
        if (!StringUtils.hasText(modelName)) {
            return false;
        }
        String target = normalizeModelName(modelName);
        for (LocalModelInfo item : listModelsInternal()) {
            if (modelMatches(target, item.name())) {
                return true;
            }
        }
        return false;
    }

    private String generateWithOptions(String prompt, int numPredict, boolean includeFormat, String modelOverride) {
        return generateWithOptionsMeta(prompt, numPredict, includeFormat, modelOverride).content();
    }

    private GenerationResult generateWithOptionsMeta(String prompt, int numPredict, boolean includeFormat, String modelOverride) {
        String url = llmProperties.getBaseUrl() + "/api/generate";

        Map<String, Object> requestBody = new HashMap<>();
        String requestModel = resolveRequestModel(modelOverride);
        int adjustedNumPredict = adjustNumPredictByModel(numPredict, requestModel);
        requestBody.put("model", requestModel);
        log.debug("ollama generate request: model={}, numPredict={}=>{}, think={}", requestModel, numPredict, adjustedNumPredict, false);
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false);
        requestBody.put("think", false);
        requestBody.put("options", Map.of(
                "num_predict", adjustedNumPredict,
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
        return healthCheck(null);
    }

    public CloudLlmHealthResponse healthCheck(String modelOverride) {
        List<String> details = new ArrayList<>();
        String baseUrl = llmProperties.getBaseUrl();
        String model = resolveRequestModel(modelOverride);
        boolean configured = StringUtils.hasText(baseUrl) && StringUtils.hasText(model);

        if (!StringUtils.hasText(baseUrl)) {
            details.add("\u7f3a\u5c11 llm.ollama.base-url");
        }
        if (!StringUtils.hasText(model)) {
            details.add("\u7f3a\u5c11 llm.ollama.model");
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
                    details.add("base-url \u672a\u5305\u542b\u53ef\u89e3\u6790 host");
                }
            } catch (Exception ex) {
                details.add("DNS \u89e3\u6790\u5931\u8d25: " + ex.getMessage());
            }
        }

        boolean modelOk = false;
        Long latencyMs = null;
        if (configured && dnsOk) {
            long start = System.currentTimeMillis();
            try {
                String probe = probeModelByTags(model);
                latencyMs = System.currentTimeMillis() - start;
                modelOk = "ok".equalsIgnoreCase(probe);
                if (!modelOk) {
                    details.add("\u672c\u5730\u6a21\u578b\u672a\u5b89\u88c5\u6216\u6a21\u578b\u540d\u4e0d\u5339\u914d");
                }
            } catch (Exception ex) {
                latencyMs = System.currentTimeMillis() - start;
                details.add("\u672c\u5730\u6a21\u578b\u8c03\u7528\u5931\u8d25: " + ex.getMessage());
            }
        }

        String message;
        if (!configured) {
            message = "\u672c\u5730\u6a21\u578b\u914d\u7f6e\u4e0d\u5b8c\u6574";
        } else if (!dnsOk) {
            message = "\u672c\u5730\u670d\u52a1\u8fde\u63a5\u5931\u8d25";
        } else if (!modelOk) {
            message = "\u672c\u5730\u6a21\u578b\u4e0d\u53ef\u7528";
        } else {
            message = "\u672c\u5730\u6a21\u578b\u8fde\u901a\u6b63\u5e38";
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

    private String probeModelByTags(String targetModel) {
        if (!StringUtils.hasText(targetModel)) {
            return null;
        }
        String target = normalizeModelName(targetModel);
        for (LocalModelInfo item : listModelsInternal()) {
            if (modelMatches(target, item.name())) {
                return "ok";
            }
        }
        return null;
    }

    private List<LocalModelInfo> listModelsInternal() {
        String url = llmProperties.getBaseUrl() + "/api/tags";
        try {
            log.info("ollama list models request: {}", url);
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<?, ?> body = response.getBody();
            if (body == null) {
                log.warn("ollama tags response body is null, url={}", url);
                return List.of();
            }
            Object modelsObj = body.get("models");
            if (!(modelsObj instanceof List<?> models) || models.isEmpty()) {
                log.warn("ollama tags response models is empty, url={}", url);
                return List.of();
            }

            List<LocalModelInfo> result = new ArrayList<>(models.size());
            for (Object item : models) {
                if (!(item instanceof Map<?, ?> modelMap)) {
                    continue;
                }
                Object nameObj = modelMap.get("name");
                if (nameObj == null) {
                    continue;
                }
                String name = String.valueOf(nameObj).trim();
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                Long sizeBytes = toLong(modelMap.get("size"));
                String modifiedAt = toText(modelMap.get("modified_at"));
                String digest = toText(modelMap.get("digest"));
                result.add(new LocalModelInfo(name, sizeBytes, modifiedAt, digest));
            }
            log.info("ollama list models result: count={}, names={}", result.size(), result.stream().map(LocalModelInfo::name).toList());
            return result;
        } catch (Exception ex) {
            log.error("ollama list models failed, url={}", url, ex);
            throw ex;
        }
    }

    
    private int adjustNumPredictByModel(int requested, String modelName) {
        int safeRequested = Math.max(64, requested);
        double sizeB = extractModelSizeB(modelName);
        if (sizeB > 0 && sizeB <= 1.0d) {
            return Math.min(safeRequested, 96);
        }
        if (sizeB > 1.0d && sizeB <= 2.0d) {
            return Math.min(safeRequested, 140);
        }
        if (sizeB > 2.0d && sizeB <= 4.0d) {
            return Math.min(safeRequested, 220);
        }
        return safeRequested;
    }

    private double extractModelSizeB(String modelName) {
        if (!StringUtils.hasText(modelName)) {
            return -1;
        }
        Matcher matcher = MODEL_SIZE_PATTERN.matcher(modelName);
        double result = -1;
        while (matcher.find()) {
            String value = matcher.group(1);
            try {
                result = Double.parseDouble(value);
            } catch (Exception ignore) {
                // Ignore invalid capture and continue with next candidate.
            }
        }
        return result;
    }
    private Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private String toText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private boolean modelMatches(String normalizedTarget, String candidateName) {
        String name = normalizeModelName(candidateName);
        return name.equals(normalizedTarget)
                || name.startsWith(normalizedTarget + ":")
                || normalizedTarget.startsWith(name + ":");
    }

    private String resolveRequestModel(String modelOverride) {
        if (StringUtils.hasText(modelOverride)) {
            return modelOverride.trim();
        }
        return llmProperties.getModel();
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

    public record LocalModelInfo(String name, Long sizeBytes, String modifiedAt, String digest) {
    }
}
