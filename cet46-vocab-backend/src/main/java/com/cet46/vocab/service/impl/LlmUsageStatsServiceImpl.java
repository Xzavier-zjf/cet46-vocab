package com.cet46.vocab.service.impl;

import com.cet46.vocab.dto.response.LlmUsageStatsResponse;
import com.cet46.vocab.entity.CloudLlmModel;
import com.cet46.vocab.entity.User;
import com.cet46.vocab.llm.LlmUsageTracker;
import com.cet46.vocab.mapper.UserMapper;
import com.cet46.vocab.service.CloudLlmModelService;
import com.cet46.vocab.service.LlmUsageStatsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class LlmUsageStatsServiceImpl implements LlmUsageStatsService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final LlmUsageTracker llmUsageTracker;
    private final UserMapper userMapper;
    private final CloudLlmModelService cloudLlmModelService;

    public LlmUsageStatsServiceImpl(LlmUsageTracker llmUsageTracker,
                                    UserMapper userMapper,
                                    CloudLlmModelService cloudLlmModelService) {
        this.llmUsageTracker = llmUsageTracker;
        this.userMapper = userMapper;
        this.cloudLlmModelService = cloudLlmModelService;
    }

    @Override
    public LlmUsageStatsResponse getUserCloudUsage(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("user not found");
        }
        List<LlmUsageTracker.UsageAggregate> records = llmUsageTracker.listUserCloudUsage(userId);
        Map<String, String> modelNames = buildUserModelNameMap(userId);
        return LlmUsageStatsResponse.builder()
                .viewRole("USER")
                .summary(buildSummary(records, false))
                .trend(buildTrend(records))
                .models(buildModelItems(records, modelNames))
                .users(List.of())
                .build();
    }

    @Override
    public LlmUsageStatsResponse getAdminCloudUsage() {
        List<LlmUsageTracker.UsageAggregate> records = llmUsageTracker.listAllPublicCloudUsage();
        Map<String, User> users = buildUserMap(records);
        Map<String, String> modelNames = buildAdminModelNameMap();
        return LlmUsageStatsResponse.builder()
                .viewRole("ADMIN")
                .summary(buildSummary(records, true))
                .trend(buildTrend(records))
                .models(buildModelItems(records, modelNames))
                .users(buildUserItems(records, users, modelNames))
                .build();
    }

    private Map<String, String> buildUserModelNameMap(Long userId) {
        Map<String, String> map = new HashMap<>();
        List<CloudLlmModel> models = cloudLlmModelService.listEnabledForUser(userId);
        for (CloudLlmModel model : models) {
            if (model == null) {
                continue;
            }
            String scope = normalizeScope(model.getVisibility());
            map.put(modelKey(scope, model.getProvider(), model.getModelKey()),
                    StringUtils.hasText(model.getDisplayName()) ? model.getDisplayName().trim() : normalizeText(model.getModelKey()));
        }
        return map;
    }

    private Map<String, String> buildAdminModelNameMap() {
        Map<String, String> map = new HashMap<>();
        for (CloudLlmModel model : cloudLlmModelService.listAll()) {
            if (model == null) {
                continue;
            }
            map.put(modelKey("public", model.getProvider(), model.getModelKey()),
                    StringUtils.hasText(model.getDisplayName()) ? model.getDisplayName().trim() : normalizeText(model.getModelKey()));
        }
        return map;
    }

    private Map<String, User> buildUserMap(List<LlmUsageTracker.UsageAggregate> records) {
        Set<Long> userIds = new LinkedHashSet<>();
        for (LlmUsageTracker.UsageAggregate record : records) {
            if (record != null && record.userId() != null) {
                userIds.add(record.userId());
            }
        }
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<String, User> map = new HashMap<>();
        if (users != null) {
            for (User user : users) {
                if (user != null && user.getId() != null) {
                    map.put(String.valueOf(user.getId()), user);
                }
            }
        }
        return map;
    }

    private LlmUsageStatsResponse.Summary buildSummary(List<LlmUsageTracker.UsageAggregate> records, boolean adminView) {
        long todayCalls = 0L;
        long last7dCalls = 0L;
        long last30dCalls = 0L;
        long publicCalls = 0L;
        long privateCalls = 0L;
        Set<String> models = new LinkedHashSet<>();
        Set<Long> users = new LinkedHashSet<>();
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(6);
        LocalDate thirtyDaysAgo = today.minusDays(29);
        for (LlmUsageTracker.UsageAggregate record : records) {
            LocalDate date = parseDate(record == null ? null : record.date());
            if (date == null) {
                continue;
            }
            long calls = record.safeCalls();
            if (!date.isBefore(thirtyDaysAgo)) {
                last30dCalls += calls;
                if ("private".equalsIgnoreCase(record.scope())) {
                    privateCalls += calls;
                } else {
                    publicCalls += calls;
                }
                models.add(modelKey(record.scope(), record.provider(), record.model()));
                if (record.userId() != null) {
                    users.add(record.userId());
                }
            }
            if (!date.isBefore(sevenDaysAgo)) {
                last7dCalls += calls;
            }
            if (today.equals(date)) {
                todayCalls += calls;
            }
        }
        return LlmUsageStatsResponse.Summary.builder()
                .totalCallsToday(todayCalls)
                .totalCalls7d(last7dCalls)
                .totalCalls30d(last30dCalls)
                .publicCalls30d(publicCalls)
                .privateCalls30d(privateCalls)
                .activeModels(models.size())
                .activeUsers(adminView ? users.size() : null)
                .billingNote("成本统计为简化版：免费模型会标注，其他模型按平台实际计费，本站暂未接入单价。")
                .quotaNote("免费模型的可用额度请以对应云平台控制台为准。")
                .build();
    }

    private List<LlmUsageStatsResponse.TrendPoint> buildTrend(List<LlmUsageTracker.UsageAggregate> records) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);
        Map<String, Long> buckets = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = start.plusDays(i);
            buckets.put(date.format(DATE_FORMATTER), 0L);
        }
        for (LlmUsageTracker.UsageAggregate record : records) {
            String date = record == null ? null : record.date();
            if (!buckets.containsKey(date)) {
                continue;
            }
            buckets.put(date, buckets.get(date) + record.safeCalls());
        }
        List<LlmUsageStatsResponse.TrendPoint> trend = new ArrayList<>();
        for (Map.Entry<String, Long> entry : buckets.entrySet()) {
            trend.add(LlmUsageStatsResponse.TrendPoint.builder()
                    .date(entry.getKey())
                    .calls(entry.getValue())
                    .build());
        }
        return trend;
    }

    private List<LlmUsageStatsResponse.ModelUsageItem> buildModelItems(List<LlmUsageTracker.UsageAggregate> records,
                                                                       Map<String, String> modelNames) {
        Map<String, ModelAccumulator> grouped = new HashMap<>();
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(29);
        for (LlmUsageTracker.UsageAggregate record : records) {
            LocalDate date = parseDate(record == null ? null : record.date());
            if (date == null || date.isBefore(thirtyDaysAgo)) {
                continue;
            }
            String key = modelKey(record.scope(), record.provider(), record.model());
            ModelAccumulator acc = grouped.computeIfAbsent(key, unused -> new ModelAccumulator(record.scope(), record.provider(), record.model()));
            acc.accept(record, date);
        }
        List<LlmUsageStatsResponse.ModelUsageItem> items = new ArrayList<>();
        for (ModelAccumulator acc : grouped.values()) {
            BillingInfo billing = resolveBilling(acc.modelKey);
            items.add(LlmUsageStatsResponse.ModelUsageItem.builder()
                    .provider(acc.provider)
                    .modelKey(acc.modelKey)
                    .displayName(modelNames.getOrDefault(modelKey(acc.scope, acc.provider, acc.modelKey), acc.modelKey))
                    .scope(acc.scope)
                    .callsToday(acc.callsToday)
                    .calls7d(acc.calls7d)
                    .calls30d(acc.calls30d)
                    .lastUsedAt(acc.lastUsedAt)
                    .lastSource(acc.lastSource)
                    .runtimeSource(acc.runtimeSource)
                    .freeTier(billing.freeTier)
                    .costLabel(billing.costLabel)
                    .quotaLabel(billing.quotaLabel)
                    .build());
        }
        items.sort(Comparator.comparing(LlmUsageStatsResponse.ModelUsageItem::getCalls30d, Comparator.nullsFirst(Long::compareTo)).reversed()
                .thenComparing(LlmUsageStatsResponse.ModelUsageItem::getLastUsedAt, Comparator.nullsFirst(Long::compareTo)).reversed());
        return items;
    }

    private List<LlmUsageStatsResponse.UserUsageItem> buildUserItems(List<LlmUsageTracker.UsageAggregate> records,
                                                                     Map<String, User> users,
                                                                     Map<String, String> modelNames) {
        Map<String, UserAccumulator> grouped = new HashMap<>();
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(29);
        for (LlmUsageTracker.UsageAggregate record : records) {
            LocalDate date = parseDate(record == null ? null : record.date());
            if (date == null || date.isBefore(thirtyDaysAgo) || record.userId() == null) {
                continue;
            }
            String key = record.userId() + "|" + modelKey(record.scope(), record.provider(), record.model());
            UserAccumulator acc = grouped.computeIfAbsent(key, unused -> new UserAccumulator(record.userId(), record.provider(), record.model(), record.scope()));
            acc.accept(record, date);
        }
        List<LlmUsageStatsResponse.UserUsageItem> items = new ArrayList<>();
        for (UserAccumulator acc : grouped.values()) {
            User user = users.get(String.valueOf(acc.userId));
            BillingInfo billing = resolveBilling(acc.modelKey);
            items.add(LlmUsageStatsResponse.UserUsageItem.builder()
                    .userId(acc.userId)
                    .username(user == null ? "" : user.getUsername())
                    .nickname(user == null ? "" : user.getNickname())
                    .provider(acc.provider)
                    .modelKey(acc.modelKey)
                    .displayName(modelNames.getOrDefault(modelKey(acc.scope, acc.provider, acc.modelKey), acc.modelKey))
                    .callsToday(acc.callsToday)
                    .calls7d(acc.calls7d)
                    .calls30d(acc.calls30d)
                    .lastUsedAt(acc.lastUsedAt)
                    .costLabel(billing.costLabel)
                    .quotaLabel(billing.quotaLabel)
                    .build());
        }
        items.sort(Comparator.comparing(LlmUsageStatsResponse.UserUsageItem::getCalls30d, Comparator.nullsFirst(Long::compareTo)).reversed()
                .thenComparing(LlmUsageStatsResponse.UserUsageItem::getLastUsedAt, Comparator.nullsFirst(Long::compareTo)).reversed());
        return items;
    }

    private BillingInfo resolveBilling(String modelKey) {
        String normalized = normalizeText(modelKey).toLowerCase(Locale.ROOT);
        if (normalized.contains("free") || normalized.contains("免费")) {
            return new BillingInfo(true, "免费模型", "可用额度请以平台控制台为准");
        }
        return new BillingInfo(false, "按平台实际计费，本站暂未接入单价", "额度信息暂未接入");
    }

    private String modelKey(String scope, String provider, String model) {
        return normalizeText(scope) + "|" + normalizeText(provider).toLowerCase(Locale.ROOT) + "|" + normalizeText(model);
    }

    private String normalizeScope(String visibility) {
        return "user-private".equalsIgnoreCase(normalizeText(visibility)) || "private".equalsIgnoreCase(normalizeText(visibility))
                ? "private"
                : "public";
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (Exception ex) {
            return null;
        }
    }

    private static class ModelAccumulator {
        private final String scope;
        private final String provider;
        private final String modelKey;
        private long callsToday;
        private long calls7d;
        private long calls30d;
        private Long lastUsedAt;
        private String lastSource = "";
        private String runtimeSource = "";

        private ModelAccumulator(String scope, String provider, String modelKey) {
            this.scope = scope;
            this.provider = provider;
            this.modelKey = modelKey;
        }

        private void accept(LlmUsageTracker.UsageAggregate record, LocalDate date) {
            long calls = record.safeCalls();
            LocalDate today = LocalDate.now();
            if (!date.isBefore(today.minusDays(29))) {
                calls30d += calls;
            }
            if (!date.isBefore(today.minusDays(6))) {
                calls7d += calls;
            }
            if (today.equals(date)) {
                callsToday += calls;
            }
            if (lastUsedAt == null || (record.lastUsedAt() != null && record.lastUsedAt() > lastUsedAt)) {
                lastUsedAt = record.lastUsedAt();
                lastSource = record.source();
                runtimeSource = record.runtimeSource();
            }
        }
    }

    private static class UserAccumulator {
        private final Long userId;
        private final String provider;
        private final String modelKey;
        private final String scope;
        private long callsToday;
        private long calls7d;
        private long calls30d;
        private Long lastUsedAt;

        private UserAccumulator(Long userId, String provider, String modelKey, String scope) {
            this.userId = userId;
            this.provider = provider;
            this.modelKey = modelKey;
            this.scope = scope;
        }

        private void accept(LlmUsageTracker.UsageAggregate record, LocalDate date) {
            long calls = record.safeCalls();
            LocalDate today = LocalDate.now();
            if (!date.isBefore(today.minusDays(29))) {
                calls30d += calls;
            }
            if (!date.isBefore(today.minusDays(6))) {
                calls7d += calls;
            }
            if (today.equals(date)) {
                callsToday += calls;
            }
            if (lastUsedAt == null || (record.lastUsedAt() != null && record.lastUsedAt() > lastUsedAt)) {
                lastUsedAt = record.lastUsedAt();
            }
        }
    }

    private record BillingInfo(boolean freeTier, String costLabel, String quotaLabel) {
    }
}


