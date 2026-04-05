package com.cet46.vocab.service.impl;

import com.cet46.vocab.config.CloudLlmProperties;
import com.cet46.vocab.dto.response.CloudLlmHealthResponse;
import com.cet46.vocab.dto.response.UserInfoResponse;
import com.cet46.vocab.entity.User;
import com.cet46.vocab.mapper.UserMapper;
import com.cet46.vocab.llm.CloudLlmClient;
import com.cet46.vocab.llm.CloudLlmRuntimeConfig;
import com.cet46.vocab.service.CloudLlmModelService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private CloudLlmClient cloudLlmClient;

    @Mock
    private CloudLlmProperties cloudLlmProperties;

    @Mock
    private CloudLlmModelService cloudLlmModelService;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void getUserInfoShouldFallbackToZeroStatsWhenReviewLogQueryFails() {
        User user = User.builder()
                .id(1L)
                .username("alice")
                .nickname("Alice")
                .role("USER")
                .dailyTarget(20)
                .build();
        when(userMapper.selectById(1L)).thenReturn(user);
        when(cloudLlmModelService.resolveSelectedModelForUser(null, 1L)).thenReturn(null);
        when(jdbcTemplate.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq(1L)))
                .thenThrow(new InvalidDataAccessResourceUsageException("review_log not ready"));

        UserInfoResponse result = userService.getUserInfo(1L);

        assertNotNull(result);
        assertEquals(1L, result.getUserId());
        assertEquals("alice", result.getUsername());
        assertEquals(0, result.getTotalDays());
        assertEquals(0, result.getStreakDays());
    }
    @Test
    void previewCloudLlmHealthShouldUseManualPayload() {
        User user = User.builder().id(1L).username("alice").build();
        when(userMapper.selectById(1L)).thenReturn(user);

        CloudLlmHealthResponse health = CloudLlmHealthResponse.builder()
                .currentProvider("bailian")
                .runtimeSource("MANUAL_PRECHECK")
                .model("qwen-max")
                .message("云端连通正常")
                .build();
        when(cloudLlmClient.healthCheck(any(CloudLlmRuntimeConfig.class))).thenReturn(health);

        CloudLlmHealthResponse result = userService.previewCloudLlmHealth(
                1L,
                "  bailian  ",
                " qwen-max ",
                " https://dashscope.aliyuncs.com/compatible-mode/v1 ",
                " /chat/completions ",
                " openai-compatible ",
                " sk-123 "
        );

        assertNotNull(result);
        assertEquals("qwen-max", result.getModel());
        assertEquals("MANUAL_PRECHECK", result.getRuntimeSource());
        assertEquals("bailian", result.getCurrentProvider());

        org.mockito.Mockito.verify(cloudLlmClient).healthCheck(argThat((CloudLlmRuntimeConfig config) ->
                config != null
                        && "bailian".equals(config.provider())
                        && "qwen-max".equals(config.model())
                        && "https://dashscope.aliyuncs.com/compatible-mode/v1".equals(config.baseUrl())
                        && "/chat/completions".equals(config.path())
                        && "openai-compatible".equals(config.protocol())
                        && "sk-123".equals(config.apiKey())
                        && "MANUAL_PRECHECK".equals(config.source())
        ));
    }
}

