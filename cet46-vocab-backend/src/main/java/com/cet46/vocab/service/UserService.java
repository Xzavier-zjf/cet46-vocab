package com.cet46.vocab.service;

import com.cet46.vocab.dto.request.ChangePasswordRequest;
import com.cet46.vocab.dto.request.UpdatePreferenceRequest;
import com.cet46.vocab.dto.response.CloudLlmHealthResponse;
import com.cet46.vocab.dto.response.LocalModelListResponse;
import com.cet46.vocab.dto.response.LlmLastUsedResponse;
import com.cet46.vocab.dto.response.UserInfoResponse;
import com.cet46.vocab.entity.CloudLlmModel;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {

    UserInfoResponse getUserInfo(Long userId);

    void updatePreference(Long userId, UpdatePreferenceRequest req);

    void updateProfile(Long userId, String nickname, MultipartFile avatarFile);

    CloudLlmHealthResponse checkCloudLlmHealth(Long userId);

    CloudLlmHealthResponse checkCloudLlmHealth(Long userId, String modelOverride);


    CloudLlmHealthResponse previewCloudLlmHealth(Long userId,
                                           String provider,
                                           String modelKey,
                                           String baseUrl,
                                           String path,
                                           String protocol,
                                           String apiKey);

    CloudLlmHealthResponse checkLocalLlmHealth(Long userId);

    LocalModelListResponse getLocalModels(Long userId);

    LocalModelListResponse getCloudModels(Long userId);

    List<CloudLlmModel> listPrivateCloudModels(Long userId);

    CloudLlmModel createPrivateCloudModel(Long userId,
                                          String provider,
                                          String modelKey,
                                          String displayName,
                                          String baseUrl,
                                          String path,
                                          String protocol,
                                          String apiKey,
                                          Boolean clearApiKey,
                                          Boolean enabled);

    CloudLlmModel updatePrivateCloudModel(Long userId,
                                          Long id,
                                          String provider,
                                          String modelKey,
                                          String displayName,
                                          String baseUrl,
                                          String path,
                                          String protocol,
                                          String apiKey,
                                          Boolean clearApiKey,
                                          Boolean enabled);

    CloudLlmModel setPrivateCloudModelEnabled(Long userId, Long id, Boolean enabled);

    void deletePrivateCloudModel(Long userId, Long id);

    LlmLastUsedResponse getLastUsedLlm(Long userId);

    void changePassword(Long userId, ChangePasswordRequest req);
}

