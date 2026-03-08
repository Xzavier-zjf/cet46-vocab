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
        return generate(prompt, cloudLlmProperties.getMaxTokens());
    }

    public String generate(String prompt, Integer maxTokens) {
        if (!Boolean.TRUE.equals(cloudLlmProperties.getEnabled())) {
            throw new OllamaClient.LlmCallException("cloud llm is disabled");
        }
        if (!StringUtils.hasText(cloudLlmProperties.getBaseUrl()) || !StringUtils.hasText(cloudLlmProperties.getModel())) {
            throw new OllamaClient.LlmCallException("cloud llm base-url/model is not configured");
        }
        if (!StringUtils.hasText(cloudLlmProperties.getApiKey())) {
            throw new OllamaClient.LlmCallException("cloud llm api-key is not configured");
        }

        String url = joinUrl(cloudLlmProperties.getBaseUrl(), cloudLlmProperties.getPath());
        log.info("cloud llm request url={}, model={}", url, cloudLlmProperties.getModel());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(cloudLlmProperties.getApiKey().trim());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", cloudLlmProperties.getModel());
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
                String content = extractContent(response.getBody());
                if (!StringUtils.hasText(content)) {
                    throw new OllamaClient.LlmCallException("cloud llm response content is empty");
                }
                log.info("cloud llm response model={}, chars={}, preview={}",
                        cloudLlmProperties.getModel(),
                        content.length(),
                        preview(content));
                return content;
            } catch (RuntimeException ex) {
                lastException = ex;
            }
        }

        throw new OllamaClient.LlmCallException("cloud llm call failed", lastException);
    }

    public CloudLlmHealthResponse healthCheck() {
        List<String> details = new ArrayList<>();
        String baseUrl = cloudLlmProperties.getBaseUrl();
        String model = cloudLlmProperties.getModel();
        boolean configured = Boolean.TRUE.equals(cloudLlmProperties.getEnabled())
                && StringUtils.hasText(baseUrl)
                && StringUtils.hasText(model)
                && StringUtils.hasText(cloudLlmProperties.getApiKey());
        if (!Boolean.TRUE.equals(cloudLlmProperties.getEnabled())) {
            details.add("云端能力未启用（llm.cloud.enabled=false）");
        }
        if (!StringUtils.hasText(baseUrl)) {
            details.add("缺少 llm.cloud.base-url");
        }
        if (!StringUtils.hasText(model)) {
            details.add("缺少 llm.cloud.model");
        }
        if (!StringUtils.hasText(cloudLlmProperties.getApiKey())) {
            details.add("缺少云端 API Key");
        }

        String host = null;
        boolean dnsOk = false;
        if (StringUtils.hasText(baseUrl)) {
            try {
                URI uri = URI.create(baseUrl.trim());
                host = uri.getHost();
                if (!StringUtils.hasText(host)) {
                    details.add("base-url 未包含可解析 host");
                } else {
                    InetAddress.getByName(host);
                    dnsOk = true;
                }
            } catch (Exception ex) {
                details.add("DNS 解析失败: " + ex.getMessage());
            }
        }

        boolean authOk = false;
        boolean modelOk = false;
        Long latencyMs = null;
        if (configured && dnsOk) {
            long start = System.currentTimeMillis();
            try {
                String content = callHealthProbe();
                latencyMs = System.currentTimeMillis() - start;
                authOk = true;
                modelOk = StringUtils.hasText(content);
                if (!modelOk) {
                    details.add("鉴权通过，但模型返回空内容");
                }
            } catch (HttpStatusCodeException ex) {
                latencyMs = System.currentTimeMillis() - start;
                HttpStatusCode code = ex.getStatusCode();
                int status = code.value();
                if (status == 401 || status == 403) {
                    details.add("鉴权失败（HTTP " + status + "）");
                } else {
                    authOk = true;
                    details.add("模型调用失败（HTTP " + status + "）");
                    String body = ex.getResponseBodyAsString();
                    if (StringUtils.hasText(body)) {
                        details.add("错误摘要: " + preview(body));
                    }
                }
            } catch (ResourceAccessException ex) {
                latencyMs = System.currentTimeMillis() - start;
                details.add("网络连接失败: " + ex.getMessage());
            } catch (Exception ex) {
                latencyMs = System.currentTimeMillis() - start;
                details.add("调用异常: " + ex.getMessage());
            }
        }

        String message;
        if (!configured) {
            message = "云端配置不完整";
        } else if (!dnsOk) {
            message = "DNS 解析失败";
        } else if (!authOk) {
            message = "鉴权失败";
        } else if (!modelOk) {
            message = "模型不可用";
        } else {
            message = "云端连通正常";
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

    private String callHealthProbe() {
        String url = joinUrl(cloudLlmProperties.getBaseUrl(), cloudLlmProperties.getPath());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(cloudLlmProperties.getApiKey().trim());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", cloudLlmProperties.getModel());
        requestBody.put("temperature", 0);
        requestBody.put("messages", List.of(Map.of("role", "user", "content", "Reply with OK")));

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
        return extractContent(response.getBody());
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

    private String preview(String text) {
        String normalized = text == null ? "" : text.replace("\r", "\\r").replace("\n", "\\n");
        if (normalized.length() <= RAW_LOG_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, RAW_LOG_LIMIT) + "...";
    }
}
