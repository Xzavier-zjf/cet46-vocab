package com.cet46.vocab.service;

import com.cet46.vocab.dto.response.LlmUsageStatsResponse;

public interface LlmUsageStatsService {

    LlmUsageStatsResponse getUserCloudUsage(Long userId);

    LlmUsageStatsResponse getAdminCloudUsage();
}
