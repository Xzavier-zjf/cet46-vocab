package com.cet46.vocab.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private String llmStyle;
    private String llmProvider;
    private String llmLocalModel;
    private String llmCloudModel;
    private Integer dailyTarget;
    private Integer totalDays;
    private Integer streakDays;
    private String role;
}

