package com.cet46.vocab.service.impl;

import com.cet46.vocab.dto.response.LlmUsageStatsResponse;
import com.cet46.vocab.entity.CloudLlmModel;
import com.cet46.vocab.entity.User;
import com.cet46.vocab.llm.LlmUsageTracker;
import com.cet46.vocab.mapper.UserMapper;
import com.cet46.vocab.service.CloudLlmModelService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmUsageStatsServiceImplTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    @Mock
    private LlmUsageTracker llmUsageTracker;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CloudLlmModelService cloudLlmModelService;

    @Test
    void getUserCloudUsage_shouldSplitPublicAndPrivateUsage() {
        Long userId = 1001L;
        LocalDate today = LocalDate.now();
        User user = User.builder().id(userId).username("alice").nickname("Alice").build();
        when(userMapper.selectById(userId)).thenReturn(user);
        when(llmUsageTracker.listUserCloudUsage(userId)).thenReturn(List.of(
                new LlmUsageTracker.UsageAggregate(today.format(DATE_FORMATTER), userId, "public", "bailian", "qwen-plus", "assistant.chat", "GLOBAL_MODEL", 2L, 100L),
                new LlmUsageTracker.UsageAggregate(today.minusDays(2).format(DATE_FORMATTER), userId, "private", "bailian", "my-free-model", "word.detail.generate", "USER_PRIVATE", 1L, 90L),
                new LlmUsageTracker.UsageAggregate(today.minusDays(10).format(DATE_FORMATTER), userId, "public", "bailian", "qwen-max", "assistant.chat", "GLOBAL_MODEL", 5L, 80L)
        ));
        when(cloudLlmModelService.listEnabledForUser(userId)).thenReturn(List.of(
                CloudLlmModel.builder().provider("bailian").modelKey("qwen-plus").displayName("Qwen Plus").visibility("global").build(),
                CloudLlmModel.builder().provider("bailian").modelKey("my-free-model").displayName("My Free Model").visibility("user-private").build(),
                CloudLlmModel.builder().provider("bailian").modelKey("qwen-max").displayName("Qwen Max").visibility("global").build()
        ));

        LlmUsageStatsServiceImpl service = new LlmUsageStatsServiceImpl(llmUsageTracker, userMapper, cloudLlmModelService);
        LlmUsageStatsResponse result = service.getUserCloudUsage(userId);

        assertEquals("USER", result.getViewRole());
        assertEquals(2L, result.getSummary().getTotalCallsToday());
        assertEquals(3L, result.getSummary().getTotalCalls7d());
        assertEquals(8L, result.getSummary().getTotalCalls30d());
        assertEquals(7L, result.getSummary().getPublicCalls30d());
        assertEquals(1L, result.getSummary().getPrivateCalls30d());
        assertEquals(3, result.getModels().size());
        assertTrue(result.getModels().stream().anyMatch(item -> Boolean.TRUE.equals(item.getFreeTier()) && "my-free-model".equals(item.getModelKey())));
    }

    @Test
    void getAdminCloudUsage_shouldAggregatePublicUsageByUser() {
        LocalDate today = LocalDate.now();
        when(llmUsageTracker.listAllPublicCloudUsage()).thenReturn(List.of(
                new LlmUsageTracker.UsageAggregate(today.format(DATE_FORMATTER), 1L, "public", "bailian", "qwen-plus", "assistant.chat", "GLOBAL_MODEL", 4L, 100L),
                new LlmUsageTracker.UsageAggregate(today.minusDays(1).format(DATE_FORMATTER), 2L, "public", "bailian", "qwen-plus", "word.detail.generate", "GLOBAL_MODEL", 3L, 90L)
        ));
        when(userMapper.selectBatchIds(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(
                User.builder().id(1L).username("alice").nickname("Alice").build(),
                User.builder().id(2L).username("bob").nickname("Bob").build()
        ));
        when(cloudLlmModelService.listAll()).thenReturn(List.of(
                CloudLlmModel.builder().provider("bailian").modelKey("qwen-plus").displayName("Qwen Plus").visibility("global").build()
        ));

        LlmUsageStatsServiceImpl service = new LlmUsageStatsServiceImpl(llmUsageTracker, userMapper, cloudLlmModelService);
        LlmUsageStatsResponse result = service.getAdminCloudUsage();

        assertEquals("ADMIN", result.getViewRole());
        assertEquals(7L, result.getSummary().getTotalCalls30d());
        assertEquals(2, result.getSummary().getActiveUsers());
        assertEquals(1, result.getModels().size());
        assertEquals(2, result.getUsers().size());
        assertFalse(result.getUsers().stream().anyMatch(item -> item.getUserId() == null));
    }
}
