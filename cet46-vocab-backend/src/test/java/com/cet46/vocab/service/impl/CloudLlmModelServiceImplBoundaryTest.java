package com.cet46.vocab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cet46.vocab.config.CloudLlmProperties;
import com.cet46.vocab.entity.CloudLlmModel;
import com.cet46.vocab.entity.CloudLlmProviderCredential;
import com.cet46.vocab.llm.CloudApiKeyCipher;
import com.cet46.vocab.mapper.CloudLlmModelMapper;
import com.cet46.vocab.mapper.CloudLlmProviderCredentialMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudLlmModelServiceImplBoundaryTest {

    @Mock
    private CloudLlmModelMapper cloudLlmModelMapper;

    @Mock
    private CloudLlmProperties cloudLlmProperties;

    @Mock
    private CloudLlmProviderCredentialMapper cloudLlmProviderCredentialMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private CloudApiKeyCipher cloudApiKeyCipher;

    private CloudLlmModelServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CloudLlmModelServiceImpl(cloudLlmModelMapper, cloudLlmProviderCredentialMapper, cloudLlmProperties, jdbcTemplate, cloudApiKeyCipher);
    }

    @Test
    void updatePrivateShouldKeepEnabledWhenEnabledIsNullBoundary() {
        CloudLlmModel existing = CloudLlmModel.builder()
                .id(10L)
                .visibility("user-private")
                .ownerUserId(9L)
                .modelKey("qwen3.5-flash")
                .displayName("old")
                .enabled(false)
                .build();

        when(cloudLlmModelMapper.selectById(10L)).thenReturn(existing);
        when(cloudLlmModelMapper.updateById(any(CloudLlmModel.class))).thenReturn(1);

        CloudLlmModel after = CloudLlmModel.builder()
                .id(10L)
                .visibility("user-private")
                .ownerUserId(9L)
                .modelKey("qwen3.5-122b-a10b")
                .displayName("new")
                .enabled(false)
                .build();
        when(cloudLlmModelMapper.selectById(10L)).thenReturn(existing, after);

        CloudLlmModel result = service.updatePrivate(9L, 10L, null, "qwen3.5-122b-a10b", "new", null, null, null, null, null, null);

        assertEquals(false, result.getEnabled());
        verify(cloudLlmModelMapper).updateById(any(CloudLlmModel.class));
    }

    @Test
    void updatePrivateShouldRejectCrossUserAccessBoundary() {
        CloudLlmModel existing = CloudLlmModel.builder()
                .id(10L)
                .visibility("user-private")
                .ownerUserId(8L)
                .modelKey("qwen3.5-flash")
                .displayName("x")
                .enabled(true)
                .build();
        when(cloudLlmModelMapper.selectById(10L)).thenReturn(existing);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePrivate(9L, 10L, null, "qwen3.5-flash", "new", null, null, null, null, null, true)
        );

        assertEquals("cloud model not found: 10", ex.getMessage());
    }

    @Test
    void setPrivateEnabledShouldRejectNullEnabledBoundary() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.setPrivateEnabled(1L, 1L, null)
        );
        assertEquals("enabled is required", ex.getMessage());
    }

    @Test
    void listEnabledForUserShouldMergeGlobalAndPrivateResultsBoundary() {
        CloudLlmModel global = CloudLlmModel.builder()
                .id(1L)
                .visibility("global")
                .ownerUserId(0L)
                .modelKey("qwen-plus")
                .enabled(true)
                .build();
        CloudLlmModel ownedPrivate = CloudLlmModel.builder()
                .id(2L)
                .visibility("user-private")
                .ownerUserId(9L)
                .modelKey("my-private")
                .enabled(true)
                .build();

        when(cloudLlmModelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(global), List.of(ownedPrivate));

        List<CloudLlmModel> result = service.listEnabledForUser(9L);

        verify(cloudLlmModelMapper, times(2)).selectList(any(LambdaQueryWrapper.class));
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(item -> "qwen-plus".equals(item.getModelKey())));
        assertTrue(result.stream().anyMatch(item -> "my-private".equals(item.getModelKey())));
    }

    @Test
    void updateShouldDeleteLegacyGlobalProviderCredentialWhenApiKeyClearedBoundary() {
        CloudLlmModel existing = CloudLlmModel.builder()
                .id(1L)
                .provider("bailian")
                .visibility("global")
                .ownerUserId(0L)
                .modelKey("qwen-plus")
                .displayName("qwen")
                .enabled(true)
                .isDefault(false)
                .build();
        CloudLlmModel after = CloudLlmModel.builder()
                .id(1L)
                .provider("bailian")
                .visibility("global")
                .ownerUserId(0L)
                .modelKey("qwen-plus")
                .displayName("qwen")
                .enabled(true)
                .isDefault(false)
                .build();

        when(cloudLlmProperties.isSupportedProvider("bailian")).thenReturn(true);
        when(cloudLlmModelMapper.selectById(1L)).thenReturn(existing, after);
        when(cloudLlmModelMapper.updateById(any(CloudLlmModel.class))).thenReturn(1);
        when(cloudLlmModelMapper.selectList(any())).thenReturn(List.of(after));

        service.update(1L, "bailian", "qwen-plus", "qwen", null, null, null, null, true, true, false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<CloudLlmProviderCredential>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(cloudLlmProviderCredentialMapper).delete(captor.capture());
        verify(cloudLlmProviderCredentialMapper, never()).selectOne(any());

        String sqlSegment = captor.getValue().getSqlSegment().toUpperCase(Locale.ROOT);
        assertTrue(sqlSegment.contains("OWNER_USER_ID"));
        assertTrue(sqlSegment.contains("IS NULL"));
    }

    @Test
    void updatePrivateShouldRejectModelKeyTooLongBoundary() {
        String tooLong = "x".repeat(129);

        CloudLlmModel existing = CloudLlmModel.builder()
                .id(1L)
                .visibility("user-private")
                .ownerUserId(1L)
                .modelKey("ok")
                .displayName("ok")
                .enabled(true)
                .build();
        when(cloudLlmModelMapper.selectById(anyLong())).thenReturn(existing);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePrivate(1L, 1L, null, tooLong, "name", null, null, null, null, null, true)
        );

        assertEquals("modelKey length must be <= 128", ex.getMessage());
    }

    @Test
    void updatePrivateShouldDeleteProviderCredentialWhenClearApiKeyTrue() {
        CloudLlmModel existing = CloudLlmModel.builder()
                .id(10L)
                .visibility("user-private")
                .ownerUserId(9L)
                .provider("bailian")
                .modelKey("qwen3.5-flash")
                .displayName("old")
                .enabled(true)
                .build();

        CloudLlmModel after = CloudLlmModel.builder()
                .id(10L)
                .visibility("user-private")
                .ownerUserId(9L)
                .provider("bailian")
                .modelKey("qwen3.5-flash")
                .displayName("old")
                .enabled(true)
                .build();

        when(cloudLlmModelMapper.selectById(10L)).thenReturn(existing, after);
        when(cloudLlmModelMapper.updateById(any(CloudLlmModel.class))).thenReturn(1);
        when(cloudLlmProperties.isSupportedProvider("bailian")).thenReturn(true);

        service.updatePrivate(9L, 10L, "bailian", "qwen3.5-flash", "old", null, null, null, null, true, null);

        verify(cloudLlmProviderCredentialMapper).delete(any());
        verify(cloudLlmProviderCredentialMapper, never()).insert(any(CloudLlmProviderCredential.class));
    }
}


