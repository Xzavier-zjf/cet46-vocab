package com.cet46.vocab.service;

import com.cet46.vocab.dto.request.ChangePasswordRequest;
import com.cet46.vocab.dto.request.UpdatePreferenceRequest;
import com.cet46.vocab.dto.response.CloudLlmHealthResponse;
import com.cet46.vocab.dto.response.LocalModelListResponse;
import com.cet46.vocab.dto.response.LlmLastUsedResponse;
import com.cet46.vocab.dto.response.UserInfoResponse;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    UserInfoResponse getUserInfo(Long userId);

    void updatePreference(Long userId, UpdatePreferenceRequest req);

    void updateProfile(Long userId, String nickname, MultipartFile avatarFile);

    CloudLlmHealthResponse checkCloudLlmHealth(Long userId);

    CloudLlmHealthResponse checkLocalLlmHealth(Long userId);

    LocalModelListResponse getLocalModels(Long userId);

    LocalModelListResponse getCloudModels(Long userId);

    LlmLastUsedResponse getLastUsedLlm(Long userId);

    void changePassword(Long userId, ChangePasswordRequest req);
}


