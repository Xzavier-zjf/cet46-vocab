package com.cet46.vocab.llm;

import com.cet46.vocab.config.CloudLlmProperties;
import com.cet46.vocab.dto.response.CloudLlmHealthResponse;
import org.springframework.http.HttpStatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Component
public class CloudLlmClient {

    private static final Logger log = LoggerFactory.getLogger(CloudLlmClient.class);
    private static final int RAW_LOG_LIMIT = 1200;

    private final CloudLlmProperties cloudLlmProperties;
    private final RestTemplate restTemplate;

    public CloudLlmClient(CloudLlmProperties cloudLlmProperties, RestTemplateBuilder restTemplateBuilder) {
        this.cloudLlmProperties = cloudLlmProperties;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(cloudLlmProperties.getTimeout())
                .setReadTimeout(cloudLlmProperties.getTimeout())
                .build();
    }

    @PostConstruct
    public void logCloudConfig() {
        log.info("cloud llm config enabled={}, baseUrl={}, path={}, model={}, timeout={}, apiKeyConfigured={}",
                cloudLlmProperties.getEnabled(),
                cloudLlmProperties.getBaseUrl(),
                cloudLlmProperties.getPath(),
                cloudLlmProperties.getModel(),
                cloudLlmProperties.getTimeout(),
                StringUtils.hasText(cloudLlmProperties.getApiKey()));
        String baseUrl = cloudLlmProperties.getBaseUrl();
        if (StringUtils.hasText(baseUrl) && !baseUrl.contains("dashscope.aliyuncs.com")) {
            log.warn("cloud llm baseUrl is not DashScope: {}", baseUrl);
        }
    }

    public String generate(String prompt) {
        return generate(prompt, null, cloudLlmProperties.getMaxTokens());
    }

    public String generate(String prompt, Integer maxTokens) {
        return generate(prompt, null, maxTokens);
    }

    public String generate(String prompt, String modelOverride) {
        return generate(prompt, modelOverride, cloudLlmProperties.getMaxTokens());
    }

    public String generate(String prompt, String modelOverride, Integer maxTokens) {
        return generateWithMeta(prompt, modelOverride, maxTokens).content();
    }

    public GenerationResult generateWithMeta(String prompt, Integer maxTokens) {
        return generateWithMeta(prompt, null, maxTokens);
    }

    public GenerationResult generateWithMeta(String prompt, String modelOverride, Integer maxTokens) {
        String requestModel = resolveRequestModel(modelOverride);
        if (!Boolean.TRUE.equals(cloudLlmProperties.getEnabled())) {
            throw new OllamaClient.LlmCallException("cloud llm is disabled");
        }
        if (!StringUtils.hasText(cloudLlmProperties.getBaseUrl()) || !StringUtils.hasText(requestModel)) {
            throw new OllamaClient.LlmCallException("cloud llm base-url/model is not configured");
        }
        if (!StringUtils.hasText(cloudLlmProperties.getApiKey())) {
            throw new OllamaClient.LlmCallException("cloud llm api-key is not configured");
        }

        String url = joinUrl(cloudLlmProperties.getBaseUrl(), cloudLlmProperties.getPath());
        log.info("cloud llm request url={}, model={}", url, requestModel);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(cloudLlmProperties.getApiKey().trim());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", requestModel);
        requestBody.put("temperature", cloudLlmProperties.getTemperature());
        if (maxTokens != null && maxTokens > 0) {
            requestBody.put("max_tokens", maxTokens);
        }
        requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        int retries = cloudLlmProperties.getMaxRetries() == null ? 1 : Math.max(cloudLlmProperties.getMaxRetries(), 0);
        RuntimeException lastException = null;
        for (int i = 0; i <= retries; i++) {
            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
                Map<?, ?> body = response.getBody();
                String content = extractContent(body);
                if (!StringUtils.hasText(content)) {
                    throw new OllamaClient.LlmCallException("cloud llm response content is empty");
                }
                boolean truncated = isLengthTruncated(body, maxTokens);
                log.info("cloud llm response model={}, chars={}, preview={}",
                        requestModel,
                        content.length(),
                        preview(content));
                return new GenerationResult(content, truncated);
            } catch (RuntimeException ex) {
                lastException = ex;
            }
        }

        throw new OllamaClient.LlmCallException("cloud llm call failed", lastException);
    }
    public CloudLlmHealthResponse healthCheck() {
        return healthCheck(null);
    }

    public CloudLlmHealthResponse healthCheck(String modelOverride) {
        List<String> details = new ArrayList<>();
        String baseUrl = cloudLlmProperties.getBaseUrl();
        String model = resolveRequestModel(modelOverride);
        boolean configured = Boolean.TRUE.equals(cloudLlmProperties.getEnabled())
                && StringUtils.hasText(baseUrl)
                && StringUtils.hasText(model)
                && StringUtils.hasText(cloudLlmProperties.getApiKey());
        if (!Boolean.TRUE.equals(cloudLlmProperties.getEnabled())) {
            details.add("\u4E91\u7AEF\u80FD\u529B\u672A\u542F\u7528\uFF08llm.cloud.enabled=false\uFF09");
        }
        if (!StringUtils.hasText(baseUrl)) {
            details.add("\u7F3A\u5C11 llm.cloud.base-url");
        }
        if (!StringUtils.hasText(model)) {
            details.add("\u7F3A\u5C11 llm.cloud.model");
        }
        if (!StringUtils.hasText(cloudLlmProperties.getApiKey())) {
            details.add("\u7F3A\u5C11\u4E91\u7AEF API Key");
        }

        String host = null;
        boolean dnsOk = false;
        if (StringUtils.hasText(baseUrl)) {
            try {
                URI uri = URI.create(baseUrl.trim());
                host = uri.getHost();
                if (!StringUtils.hasText(host)) {
                    details.add("base-url \u672A\u5305\u542B\u53EF\u89E3\u6790 host");
                } else {
                    InetAddress.getByName(host);
                    dnsOk = true;
                }
            } catch (Exception ex) {
                details.add("DNS \u89E3\u6790\u5931\u8D25: " + ex.getMessage());
            }
        }

        boolean authOk = false;
        boolean modelOk = false;
        Long latencyMs = null;
        if (configured && dnsOk) {
            long startMs = System.currentTimeMillis();
            try {
                String content = callHealthProbe(model);
                latencyMs = System.currentTimeMillis() - startMs;
                authOk = true;
                modelOk = StringUtils.hasText(content);
                if (!modelOk) {
                    details.add("\u9274\u6743\u901A\u8FC7\uFF0C\u4F46\u6A21\u578B\u8FD4\u56DE\u7A7A\u5185\u5BB9");
                }
            } catch (HttpStatusCodeException ex) {
                latencyMs = System.currentTimeMillis() - startMs;
                HttpStatusCode code = ex.getStatusCode();
                int status = code.value();
                if (status == 401 || status == 403) {
                    details.add("\u9274\u6743\u5931\u8D25\uFF08HTTP " + status + "\uFF09");
                } else {
                    authOk = true;
                    details.add("\u6A21\u578B\u8C03\u7528\u5931\u8D25\uFF08HTTP " + status + "\uFF09");
                    String body = ex.getResponseBodyAsString();
                    if (StringUtils.hasText(body)) {
                        details.add("\u9519\u8BEF\u6458\u8981: " + preview(body));
                    }
                }
            } catch (ResourceAccessException ex) {
                latencyMs = System.currentTimeMillis() - startMs;
                details.add("\u7F51\u7EDC\u8FDE\u63A5\u5931\u8D25: " + ex.getMessage());
            } catch (Exception ex) {
                latencyMs = System.currentTimeMillis() - startMs;
                details.add("\u8C03\u7528\u5F02\u5E38: " + ex.getMessage());
            }
        }

        String message;
        if (!configured) {
            message = "\u4E91\u7AEF\u914D\u7F6E\u4E0D\u5B8C\u6574";
        } else if (!dnsOk) {
            message = "DNS \u89E3\u6790\u5931\u8D25";
        } else if (!authOk) {
            message = "\u9274\u6743\u5931\u8D25";
        } else if (!modelOk) {
            message = "\u6A21\u578B\u4E0D\u53EF\u7528";
        } else {
            message = "\u4E91\u7AEF\u8FDE\u901A\u6B63\u5E38";
        }

        return CloudLlmHealthResponse.builder()
                .currentProvider("cloud")
                .baseUrl(baseUrl)
                .model(model)
                .configured(configured)
                .dnsOk(dnsOk)
                .authOk(authOk)
                .modelOk(modelOk)
                .latencyMs(latencyMs)
                .message(message)
                .details(details)
                .build();
    }

    private String callHealthProbe(String model) {
        String url = joinUrl(cloudLlmProperties.getBaseUrl(), cloudLlmProperties.getPath());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(cloudLlmProperties.getApiKey().trim());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", 0);
        requestBody.put("messages", List.of(Map.of("role", "user", "content", "Reply with OK")));

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
        return extractContent(response.getBody());
    }
    private String resolveRequestModel(String modelOverride) {
        if (StringUtils.hasText(modelOverride)) {
            return modelOverride.trim();
        }
        return cloudLlmProperties.resolveDefaultModel();
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

    private boolean isLengthTruncated(Map<?, ?> body, Integer requestMaxTokens) {
        if (body == null) {
            return false;
        }
        Object choices = body.get("choices");
        if (choices instanceof List<?> choiceList && !choiceList.isEmpty()) {
            Object firstChoice = choiceList.get(0);
            if (firstChoice instanceof Map<?, ?> firstChoiceMap) {
                Object finishReason = firstChoiceMap.get("finish_reason");
                if (finishReason != null) {
                    String value = String.valueOf(finishReason).trim().toLowerCase();
                    if ("length".equals(value) || "max_tokens".equals(value)) {
                        return true;
                    }
                }
            }
        }

        if (requestMaxTokens == null || requestMaxTokens <= 0) {
            return false;
        }
        Object usageObj = body.get("usage");
        if (!(usageObj instanceof Map<?, ?> usageMap)) {
            return false;
        }
        Object completionTokensObj = usageMap.get("completion_tokens");
        if (!(completionTokensObj instanceof Number completionTokens)) {
            return false;
        }
        // Some gateways may not return finish_reason=length consistently.
        return completionTokens.intValue() >= Math.max(1, requestMaxTokens - 8);
    }

    private String preview(String text) {
        String normalized = text == null ? "" : text.replace("\r", "\\r").replace("\n", "\\n");
        if (normalized.length() <= RAW_LOG_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, RAW_LOG_LIMIT) + "...";
    }

    public record GenerationResult(String content, boolean truncated) {
    }
}



