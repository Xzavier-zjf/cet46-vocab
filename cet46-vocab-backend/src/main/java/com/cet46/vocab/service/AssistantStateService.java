package com.cet46.vocab.service;

import com.cet46.vocab.dto.request.AssistantStateSyncRequest;
import com.cet46.vocab.dto.response.AssistantStateResponse;

public interface AssistantStateService {
    AssistantStateResponse loadState(Long userId);

    AssistantStateResponse syncState(Long userId, AssistantStateSyncRequest request);
}
