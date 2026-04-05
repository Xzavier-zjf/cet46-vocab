package com.cet46.vocab.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cet46.vocab.config.CloudLlmProperties;
import com.cet46.vocab.entity.CloudLlmModel;
import com.cet46.vocab.entity.CloudLlmProviderCredential;
import com.cet46.vocab.mapper.CloudLlmProviderCredentialMapper;
import com.cet46.vocab.service.CloudLlmModelService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Component
public class CloudLlmRuntimeConfigResolver {

    private final CloudLlmProperties cloudLlmProperties;
    private final CloudLlmModelService cloudLlmModelService;
    private final CloudLlmProviderCredentialMapper cloudLlmProviderCredentialMapper;
    private final CloudApiKeyCipher cloudApiKeyCipher;

    public CloudLlmRuntimeConfigResolver(CloudLlmProperties cloudLlmProperties,
                                         CloudLlmModelService cloudLlmModelService,
                                         CloudLlmProviderCredentialMapper cloudLlmProviderCredentialMapper,
                                         CloudApiKeyCipher cloudApiKeyCipher) {
        this.cloudLlmProperties = cloudLlmProperties;
        this.cloudLlmModelService = cloudLlmModelService;
        this.cloudLlmProviderCredentialMapper = cloudLlmProviderCredentialMapper;
        this.cloudApiKeyCipher = cloudApiKeyCipher;
    }

    public CloudLlmRuntimeConfig resolve(Long userId, String modelOverride) {
        String targetModel = normalize(modelOverride);
        if (!StringUtils.hasText(targetModel)) {
            targetModel = cloudLlmModelService.resolveDefaultModelForUser(userId);
        }

        List<CloudLlmModel> enabledModels = cloudLlmModelService.listEnabledForUser(userId);
        CloudLlmModel privateMatch = findPrivateMatch(enabledModels, userId, targetModel);
        CloudLlmModel globalMatch = findGlobalMatch(enabledModels, targetModel);

        CloudLlmRuntimeConfig privateConfig = toRuntimeConfig(privateMatch, targetModel, userId, "USER_PRIVATE");
        if (isUsable(privateConfig)) {
            return privateConfig;
        }

        CloudLlmRuntimeConfig globalConfig = toRuntimeConfig(globalMatch, targetModel, userId, "GLOBAL_MODEL");
        if (isUsable(globalConfig)) {
            return globalConfig;
        }

        CloudLlmRuntimeConfig systemConfig = buildSystemConfig(targetModel);
        if (isUsable(systemConfig)) {
            return systemConfig;
        }

        throw new IllegalArgumentException("cloud runtime config unavailable");
    }

    public boolean isAvailable(Long userId, String modelOverride) {
        try {
            return isUsable(resolve(userId, modelOverride));
        } catch (Exception ex) {
            return false;
        }
    }

    private CloudLlmRuntimeConfig buildSystemConfig(String targetModel) {
        String model = StringUtils.hasText(targetModel) ? targetModel : cloudLlmProperties.resolveDefaultModel();
        return new CloudLlmRuntimeConfig(
                cloudLlmProperties.resolveDefaultProvider(),
                model,
                normalize(cloudLlmProperties.getBaseUrl()),
                normalizePath(cloudLlmProperties.getPath()),
                normalize(cloudLlmProperties.getApiKey()),
                "openai-compatible",
                "SYSTEM_CONFIG"
        );
    }

    private CloudLlmRuntimeConfig toRuntimeConfig(CloudLlmModel model, String targetModel, Long userId, String source) {
        if (model == null) {
            return null;
        }
        String resolvedModel = StringUtils.hasText(targetModel) ? targetModel : normalize(model.getModelKey());
        String provider = normalize(model.getProvider());
        String apiKey = cloudApiKeyCipher.decrypt(model.getApiKeyCiphertext());
        if (!StringUtils.hasText(apiKey) && "USER_PRIVATE".equals(source)) {
            apiKey = resolveProviderCredentialApiKey(provider, CloudLlmModelService.VISIBILITY_USER_PRIVATE, userId);
        }
        if (!StringUtils.hasText(apiKey)) {
            apiKey = resolveProviderCredentialApiKey(provider, CloudLlmModelService.VISIBILITY_GLOBAL, 0L);
        }
        if (!StringUtils.hasText(apiKey)) {
            apiKey = normalize(cloudLlmProperties.getApiKey());
        }
        return new CloudLlmRuntimeConfig(
                provider,
                resolvedModel,
                firstNonBlank(model.getBaseUrl(), cloudLlmProperties.getBaseUrl()),
                normalizePath(firstNonBlank(model.getPath(), cloudLlmProperties.getPath())),
                apiKey,
                firstNonBlank(model.getProtocol(), "openai-compatible"),
                source
        );
    }

    private boolean isUsable(CloudLlmRuntimeConfig config) {
        if (config == null) {
            return false;
        }
        return Boolean.TRUE.equals(cloudLlmProperties.getEnabled())
                && StringUtils.hasText(config.baseUrl())
                && StringUtils.hasText(config.path())
                && StringUtils.hasText(config.model())
                && StringUtils.hasText(config.apiKey());
    }

    private CloudLlmModel findPrivateMatch(List<CloudLlmModel> models, Long userId, String modelKey) {
        if (models == null || models.isEmpty() || userId == null || !StringUtils.hasText(modelKey)) {
            return null;
        }
        for (CloudLlmModel model : models) {
            if (model == null || !Boolean.TRUE.equals(model.getEnabled())) {
                continue;
            }
            if (!CloudLlmModelService.VISIBILITY_USER_PRIVATE.equals(normalize(model.getVisibility()))) {
                continue;
            }
            if (!userId.equals(model.getOwnerUserId())) {
                continue;
            }
            if (modelKey.equals(normalize(model.getModelKey()))) {
                return model;
            }
        }
        return null;
    }

    private CloudLlmModel findGlobalMatch(List<CloudLlmModel> models, String modelKey) {
        if (models == null || models.isEmpty() || !StringUtils.hasText(modelKey)) {
            return null;
        }
        for (CloudLlmModel model : models) {
            if (model == null || !Boolean.TRUE.equals(model.getEnabled())) {
                continue;
            }
            if (!CloudLlmModelService.VISIBILITY_GLOBAL.equals(normalize(model.getVisibility()))) {
                continue;
            }
            if (modelKey.equals(normalize(model.getModelKey()))) {
                return model;
            }
        }
        return null;
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return normalize(second);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizePath(String value) {
        String path = normalize(value);
        if (!StringUtils.hasText(path)) {
            return "/chat/completions";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private String resolveProviderCredentialApiKey(String provider, String visibility, Long ownerUserId) {
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(visibility) || ownerUserId == null) {
            return null;
        }
        String normalizedProvider = provider.trim().toLowerCase(Locale.ROOT);
        String normalizedVisibility = visibility.trim();
        CloudLlmProviderCredential credential = cloudLlmProviderCredentialMapper.selectOne(
                new LambdaQueryWrapper<CloudLlmProviderCredential>()
                        .eq(CloudLlmProviderCredential::getProvider, normalizedProvider)
                        .eq(CloudLlmProviderCredential::getVisibility, normalizedVisibility)
                        .eq(CloudLlmProviderCredential::getOwnerUserId, ownerUserId)
                        .last("LIMIT 1")
        );
        if (credential == null
                && CloudLlmModelService.VISIBILITY_GLOBAL.equals(normalizedVisibility)
                && ownerUserId == 0L) {
            credential = cloudLlmProviderCredentialMapper.selectOne(
                    new LambdaQueryWrapper<CloudLlmProviderCredential>()
                            .eq(CloudLlmProviderCredential::getProvider, normalizedProvider)
                            .eq(CloudLlmProviderCredential::getVisibility, normalizedVisibility)
                            .isNull(CloudLlmProviderCredential::getOwnerUserId)
                            .last("LIMIT 1")
            );
        }
        if (credential == null) {
            return null;
        }
        return cloudApiKeyCipher.decrypt(credential.getApiKeyCiphertext());
    }
}
