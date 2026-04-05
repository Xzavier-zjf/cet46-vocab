package com.cet46.vocab.llm;

import com.cet46.vocab.config.CloudLlmProperties;
import com.cet46.vocab.entity.CloudLlmModel;
import com.cet46.vocab.entity.CloudLlmProviderCredential;
import com.cet46.vocab.mapper.CloudLlmProviderCredentialMapper;
import com.cet46.vocab.service.CloudLlmModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudLlmRuntimeConfigResolverTest {

    @Mock
    private CloudLlmProperties cloudLlmProperties;

    @Mock
    private CloudLlmModelService cloudLlmModelService;

    @Mock
    private CloudLlmProviderCredentialMapper cloudLlmProviderCredentialMapper;

    @Mock
    private CloudApiKeyCipher cloudApiKeyCipher;

    private CloudLlmRuntimeConfigResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CloudLlmRuntimeConfigResolver(
                cloudLlmProperties,
                cloudLlmModelService,
                cloudLlmProviderCredentialMapper,
                cloudApiKeyCipher
        );
    }

    @Test
    void resolveShouldFallbackToGlobalCredentialWithNullOwnerWhenLegacyDataExists() {
        CloudLlmModel globalModel = CloudLlmModel.builder()
                .id(1L)
                .provider("QWEN")
                .modelKey("qwen-max")
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .path("/chat/completions")
                .protocol("openai-compatible")
                .enabled(true)
                .visibility(CloudLlmModelService.VISIBILITY_GLOBAL)
                .ownerUserId(0L)
                .build();

        CloudLlmProviderCredential legacyCredential = CloudLlmProviderCredential.builder()
                .id(2L)
                .provider("qwen")
                .visibility(CloudLlmModelService.VISIBILITY_GLOBAL)
                .ownerUserId(null)
                .apiKeyCiphertext("cipher")
                .build();

        when(cloudLlmModelService.resolveDefaultModelForUser(100L)).thenReturn("qwen-max");
        when(cloudLlmModelService.listEnabledForUser(100L)).thenReturn(List.of(globalModel));
        when(cloudLlmProperties.getEnabled()).thenReturn(true);
        when(cloudLlmProviderCredentialMapper.selectOne(any())).thenReturn(null, legacyCredential);
        when(cloudApiKeyCipher.decrypt("cipher")).thenReturn("legacy-api-key");

        CloudLlmRuntimeConfig config = resolver.resolve(100L, null);

        assertEquals("legacy-api-key", config.apiKey());
        assertEquals("GLOBAL_MODEL", config.source());
        assertEquals("QWEN", config.provider());
    }
}
