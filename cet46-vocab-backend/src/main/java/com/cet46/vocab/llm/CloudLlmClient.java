package com.cet46.vocab.llm;

import com.cet46.vocab.config.CloudLlmProperties;
import com.cet46.vocab.dto.response.CloudLlmHealthResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    }

    public String generate(String prompt) {
        return generate(prompt, (String) null, cloudLlmProperties.getMaxTokens());
    }

    public String generate(String prompt, String modelOverride) {
        return generate(prompt, modelOverride, cloudLlmProperties.getMaxTokens());
    }

    public String generate(String prompt, String modelOverride, Integer maxTokens) {
        return generateWithMeta(prompt, modelOverride, maxTokens).content();
    }

    public String generate(String prompt, CloudLlmRuntimeConfig runtimeConfig, Integer maxTokens) {
        return generateWithMeta(prompt, runtimeConfig, maxTokens).content();
    }

    public GenerationResult generateWithMeta(String prompt, Integer maxTokens) {
        return generateWithMeta(prompt, (String) null, maxTokens);
    }

    public GenerationResult generateWithMeta(String prompt, String modelOverride, Integer maxTokens) {
        CloudLlmRuntimeConfig runtime = new CloudLlmRuntimeConfig(
                cloudLlmProperties.resolveDefaultProvider(),
                resolveRequestModel(modelOverride),
                cloudLlmProperties.getBaseUrl(),
                cloudLlmProperties.getPath(),
                cloudLlmProperties.getApiKey(),
                "openai-compatible",
                "SYSTEM_CONFIG"
        );
        return generateWithMeta(prompt, runtime, maxTokens);
    }

    public GenerationResult generateWithMeta(String prompt, CloudLlmRuntimeConfig runtimeConfig, Integer maxTokens) {
        if (!Boolean.TRUE.equals(cloudLlmProperties.getEnabled())) {
            throw new OllamaClient.LlmCallException("cloud llm is disabled");
        }
        CloudLlmRuntimeConfig runtime = requireUsableRuntime(runtimeConfig);

        String url = joinUrl(runtime.baseUrl(), runtime.path());
        log.info("cloud llm request url={}, provider={}, model={}, source={}",
                url,
                runtime.provider(),
                runtime.model(),
                runtime.source());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(runtime.apiKey().trim());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", runtime.model());
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
                log.info("cloud llm response model={}, chars={}, preview={}", runtime.model(), content.length(), preview(content));
                return new GenerationResult(content, truncated);
            } catch (RuntimeException ex) {
                lastException = ex;
            }
        }

        throw new OllamaClient.LlmCallException("cloud llm call failed", lastException);
    }

    public CloudLlmHealthResponse healthCheck() {
        return healthCheck((CloudLlmRuntimeConfig) null);
    }

    public CloudLlmHealthResponse healthCheck(String modelOverride) {
        CloudLlmRuntimeConfig runtime = new CloudLlmRuntimeConfig(
                cloudLlmProperties.resolveDefaultProvider(),
                resolveRequestModel(modelOverride),
                cloudLlmProperties.getBaseUrl(),
                cloudLlmProperties.getPath(),
                cloudLlmProperties.getApiKey(),
                "openai-compatible",
                "SYSTEM_CONFIG"
        );
        return healthCheck(runtime);
    }

    public CloudLlmHealthResponse healthCheck(CloudLlmRuntimeConfig runtimeConfig) {
        List<String> details = new ArrayList<>();
        CloudLlmRuntimeConfig runtime = normalizeRuntime(runtimeConfig);

        boolean configured = Boolean.TRUE.equals(cloudLlmProperties.getEnabled())
                && StringUtils.hasText(runtime.baseUrl())
                && StringUtils.hasText(runtime.model())
                && StringUtils.hasText(runtime.apiKey());

        if (!Boolean.TRUE.equals(cloudLlmProperties.getEnabled())) {
            details.add("云端能力未启用（llm.cloud.enabled=false）");
        }
        if (!StringUtils.hasText(runtime.baseUrl())) {
            details.add("缺少 base-url");
        }
        if (!StringUtils.hasText(runtime.model())) {
            details.add("缺少 model");
        }
        if (!StringUtils.hasText(runtime.apiKey())) {
            details.add("缺少云端 API Key");
        }

        String host = null;
        boolean dnsOk = false;
        if (StringUtils.hasText(runtime.baseUrl())) {
            try {
                URI uri = URI.create(runtime.baseUrl().trim());
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
            long startMs = System.currentTimeMillis();
            try {
                String content = callHealthProbe(runtime);
                latencyMs = System.currentTimeMillis() - startMs;
                authOk = true;
                modelOk = StringUtils.hasText(content);
                if (!modelOk) {
                    details.add("鉴权通过，但模型返回空内容");
                }
            } catch (HttpStatusCodeException ex) {
                latencyMs = System.currentTimeMillis() - startMs;
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
                latencyMs = System.currentTimeMillis() - startMs;
                details.add("网络连接失败: " + ex.getMessage());
            } catch (Exception ex) {
                latencyMs = System.currentTimeMillis() - startMs;
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
                .currentProvider(StringUtils.hasText(runtime.provider()) ? runtime.provider() : "cloud")
                .runtimeSource(runtime.source())
                .baseUrl(runtime.baseUrl())
                .model(runtime.model())
                .configured(configured)
                .dnsOk(dnsOk)
                .authOk(authOk)
                .modelOk(modelOk)
                .latencyMs(latencyMs)
                .message(message)
                .details(details)
                .build();
    }

    private String callHealthProbe(CloudLlmRuntimeConfig runtime) {
        String url = joinUrl(runtime.baseUrl(), runtime.path());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(runtime.apiKey().trim());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", runtime.model());
        requestBody.put("temperature", 0);
        requestBody.put("messages", List.of(Map.of("role", "user", "content", "Reply with OK")));

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
        return extractContent(response.getBody());
    }

    private CloudLlmRuntimeConfig requireUsableRuntime(CloudLlmRuntimeConfig runtimeConfig) {
        CloudLlmRuntimeConfig runtime = normalizeRuntime(runtimeConfig);
        if (!StringUtils.hasText(runtime.baseUrl()) || !StringUtils.hasText(runtime.model())) {
            throw new OllamaClient.LlmCallException("cloud llm base-url/model is not configured");
        }
        if (!StringUtils.hasText(runtime.apiKey())) {
            throw new OllamaClient.LlmCallException("cloud llm api-key is not configured");
        }
        return runtime;
    }

    private CloudLlmRuntimeConfig normalizeRuntime(CloudLlmRuntimeConfig runtimeConfig) {
        if (runtimeConfig != null) {
            return runtimeConfig;
        }
        return new CloudLlmRuntimeConfig(
                cloudLlmProperties.resolveDefaultProvider(),
                cloudLlmProperties.resolveDefaultModel(),
                cloudLlmProperties.getBaseUrl(),
                cloudLlmProperties.getPath(),
                cloudLlmProperties.getApiKey(),
                "openai-compatible",
                "SYSTEM_CONFIG"
        );
    }

    private String resolveRequestModel(String modelOverride) {
        if (StringUtils.hasText(modelOverride)) {
            return modelOverride.trim();
        }
        return cloudLlmProperties.resolveDefaultModel();
    }

    private String joinUrl(String baseUrl, String path) {
        String base = baseUrl.trim();
        String suffix = StringUtils.hasText(path) ? path.trim() : "/chat/completions";
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

