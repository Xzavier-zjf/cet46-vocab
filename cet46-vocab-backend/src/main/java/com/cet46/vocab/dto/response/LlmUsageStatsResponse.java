package com.cet46.vocab.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmUsageStatsResponse {

    private String viewRole;
    private Summary summary;
    private List<TrendPoint> trend;
    private List<ModelUsageItem> models;
    private List<UserUsageItem> users;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private Long totalCallsToday;
        private Long totalCalls7d;
        private Long totalCalls30d;
        private Long publicCalls30d;
        private Long privateCalls30d;
        private Integer activeModels;
        private Integer activeUsers;
        private String billingNote;
        private String quotaNote;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private String date;
        private Long calls;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelUsageItem {
        private String provider;
        private String modelKey;
        private String displayName;
        private String scope;
        private Long callsToday;
        private Long calls7d;
        private Long calls30d;
        private Long lastUsedAt;
        private String lastSource;
        private String runtimeSource;
        private Boolean freeTier;
        private String costLabel;
        private String quotaLabel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserUsageItem {
        private Long userId;
        private String username;
        private String nickname;
        private String provider;
        private String modelKey;
        private String displayName;
        private Long callsToday;
        private Long calls7d;
        private Long calls30d;
        private Long lastUsedAt;
        private String costLabel;
        private String quotaLabel;
    }
}
