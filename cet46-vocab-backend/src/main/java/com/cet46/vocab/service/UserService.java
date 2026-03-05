package com.cet46.vocab.service;

import com.cet46.vocab.dto.request.UpdatePreferenceRequest;
import com.cet46.vocab.dto.response.UserInfoResponse;

public interface UserService {

    UserInfoResponse getUserInfo(Long userId);

    void updatePreference(Long userId, UpdatePreferenceRequest req);
}
