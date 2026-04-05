package com.cet46.vocab.controller;

import com.cet46.vocab.common.Result;
import com.cet46.vocab.common.ResultCode;
import com.cet46.vocab.dto.request.ChangePasswordRequest;
import com.cet46.vocab.dto.request.UpdatePreferenceRequest;
import com.cet46.vocab.dto.response.CloudLlmHealthResponse;
import com.cet46.vocab.dto.response.LocalModelListResponse;
import com.cet46.vocab.dto.response.LlmLastUsedResponse;
import com.cet46.vocab.dto.response.UserInfoResponse;
import com.cet46.vocab.entity.CloudLlmModel;
import com.cet46.vocab.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/info")
    public Result<UserInfoResponse> getUserInfo(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(authentication.getPrincipal().toString());
        return Result.success(userService.getUserInfo(userId));
    }

    @PutMapping("/preference")
    public Result<Void> updatePreference(Authentication authentication,
                                         @Valid @RequestBody UpdatePreferenceRequest req) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(authentication.getPrincipal().toString());
        userService.updatePreference(userId, req);
        return Result.success();
    }

    @PutMapping("/profile")
    public Result<Void> updateProfile(Authentication authentication,
                                      @RequestParam(value = "nickname", required = false) String nickname,
                                      @RequestParam(value = "avatar", required = false) MultipartFile avatar) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(authentication.getPrincipal().toString());
        userService.updateProfile(userId, nickname, avatar);
        return Result.success();
    }

    @PostMapping("/profile")
    public Result<Void> updateProfileByPost(Authentication authentication,
                                            @RequestParam(value = "nickname", required = false) String nickname,
                                            @RequestParam(value = "avatar", required = false) MultipartFile avatar) {
        return updateProfile(authentication, nickname, avatar);
    }

    @GetMapping("/llm/cloud-health")
    public Result<CloudLlmHealthResponse> checkCloudLlmHealth(Authentication authentication,
                                                              @RequestParam(value = "model", required = false) String model) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(authentication.getPrincipal().toString());
        return Result.success(userService.checkCloudLlmHealth(userId, model));
    }

    @GetMapping("/llm/local-health")
    public Result<CloudLlmHealthResponse> checkLocalLlmHealth(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(authentication.getPrincipal().toString());
        return Result.success(userService.checkLocalLlmHealth(userId));
    }

    @GetMapping("/llm/local-models")
    public Result<LocalModelListResponse> getLocalModels(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(authentication.getPrincipal().toString());
        return Result.success(userService.getLocalModels(userId));
    }

    @GetMapping("/llm/cloud-models")
    public Result<LocalModelListResponse> getCloudModels(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(authentication.getPrincipal().toString());
        return Result.success(userService.getCloudModels(userId));
    }


    @PreAuthorize("hasRole('ADMIN') or hasAnyAuthority('PRIVATE_CLOUD_MODEL_CREATE', 'PRIVATE_CLOUD_MODEL_EDIT')")
    @PostMapping("/llm/cloud-models/preview-health")
    public Result<CloudLlmHealthResponse> previewCloudModelHealth(Authentication authentication,
                                                                  @Valid @RequestBody CloudModelPreviewRequest req) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(authentication.getPrincipal().toString());
        return Result.success(userService.previewCloudLlmHealth(
                userId,
                req.getProvider(),
                req.getModelKey(),
                req.getBaseUrl(),
                req.getPath(),
                req.getProtocol(),
                req.getApiKey()
        ));
    }

    @GetMapping("/llm/cloud-models/private")
    public Result<List<CloudModelItem>> listPrivateCloudModels(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(authentication.getPrincipal().toString());
        List<CloudModelItem> items = userService.listPrivateCloudModels(userId).stream()
                .map(this::toCloudModelItem)
                .toList();
        return Result.success(items);
    }

    @PreAuthorize("hasAuthority('PRIVATE_CLOUD_MODEL_CREATE')")
    @PostMapping("/llm/cloud-models/private")
    public Result<CloudModelItem> createPrivateCloudModel(Authentication authentication,
                                                          @Valid @RequestBody CloudModelSaveRequest req) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(authentication.getPrincipal().toString());
        CloudLlmModel saved = userService.createPrivateCloudModel(userId, req.getProvider(), req.getModelKey(), req.getDisplayName(), req.getBaseUrl(), req.getPath(), req.getProtocol(), req.getApiKey(), req.getClearApiKey(), req.getEnabled());
        return Result.success(toCloudModelItem(saved));
    }

    @PreAuthorize("hasAuthority('PRIVATE_CLOUD_MODEL_EDIT')")
    @PutMapping("/llm/cloud-models/private/{id}")
    public Result<CloudModelItem> updatePrivateCloudModel(Authentication authentication,
                                                          @PathVariable("id") Long id,
                                                          @Valid @RequestBody CloudModelUpdateRequest req) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(authentication.getPrincipal().toString());
        CloudLlmModel saved = userService.updatePrivateCloudModel(userId, id, req.getProvider(), req.getModelKey(), req.getDisplayName(), req.getBaseUrl(), req.getPath(), req.getProtocol(), req.getApiKey(), req.getClearApiKey(), null);
        return Result.success(toCloudModelItem(saved));
    }

    @PreAuthorize("hasAuthority('PRIVATE_CLOUD_MODEL_EDIT')")
    @PutMapping("/llm/cloud-models/private/{id}/full")
    public Result<CloudModelItem> updatePrivateCloudModelFull(Authentication authentication,
                                                              @PathVariable("id") Long id,
                                                              @Valid @RequestBody CloudModelFullUpdateRequest req) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        if (req.getEnabled() != null && !hasAuthority(authentication, "PRIVATE_CLOUD_MODEL_TOGGLE")) {
            return Result.fail(ResultCode.FORBIDDEN.getCode(), "forbidden");
        }
        Long userId = Long.valueOf(authentication.getPrincipal().toString());
        CloudLlmModel saved = userService.updatePrivateCloudModel(userId, id, req.getProvider(), req.getModelKey(), req.getDisplayName(), req.getBaseUrl(), req.getPath(), req.getProtocol(), req.getApiKey(), req.getClearApiKey(), req.getEnabled());
        return Result.success(toCloudModelItem(saved));
    }

    @PreAuthorize("hasAuthority('PRIVATE_CLOUD_MODEL_TOGGLE')")
    @PutMapping("/llm/cloud-models/private/{id}/enabled")
    public Result<CloudModelItem> setPrivateCloudModelEnabled(Authentication authentication,
                                                              @PathVariable("id") Long id,
                                                              @Valid @RequestBody CloudModelToggleRequest req) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(authentication.getPrincipal().toString());
        CloudLlmModel saved = userService.setPrivateCloudModelEnabled(userId, id, req.getEnabled());
        return Result.success(toCloudModelItem(saved));
    }

    @PreAuthorize("hasAuthority('PRIVATE_CLOUD_MODEL_DELETE')")
    @DeleteMapping("/llm/cloud-models/private/{id}")
    public Result<Void> deletePrivateCloudModel(Authentication authentication,
                                                @PathVariable("id") Long id) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(authentication.getPrincipal().toString());
        userService.deletePrivateCloudModel(userId, id);
        return Result.success();
    }

    @GetMapping("/llm/last-used")
    public Result<LlmLastUsedResponse> getLastUsedLlm(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(authentication.getPrincipal().toString());
        return Result.success(userService.getLastUsedLlm(userId));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(Authentication authentication,
                                       @Valid @RequestBody ChangePasswordRequest req) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(authentication.getPrincipal().toString());
        userService.changePassword(userId, req);
        return Result.success();
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority item : authentication.getAuthorities()) {
            if (item != null && authority.equals(item.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private CloudModelItem toCloudModelItem(CloudLlmModel model) {
        CloudModelItem item = new CloudModelItem();
        item.setId(model.getId());
        item.setProvider(model.getProvider());
        item.setModelKey(model.getModelKey());
        item.setDisplayName(model.getDisplayName());
        item.setBaseUrl(model.getBaseUrl());
        item.setPath(model.getPath());
        item.setProtocol(model.getProtocol());
        item.setHasApiKey(org.springframework.util.StringUtils.hasText(model.getApiKeyCiphertext()));
        item.setApiKeyMask(model.getApiKeyMask());
        item.setEnabled(Boolean.TRUE.equals(model.getEnabled()));
        item.setIsDefault(Boolean.TRUE.equals(model.getIsDefault()));
        item.setVisibility(model.getVisibility());
        item.setOwnerUserId(model.getOwnerUserId());
        item.setCreatedAt(model.getCreatedAt());
        item.setUpdatedAt(model.getUpdatedAt());
        return item;
    }

    @Data
    private static class CloudModelPreviewRequest {
        private String provider;
        @NotBlank
        private String modelKey;
        private String baseUrl;
        private String path;
        private String protocol;
        private String apiKey;
    }

    @Data
    private static class CloudModelSaveRequest {
        private String provider;
        @NotBlank
        private String modelKey;
        private String displayName;
        private String baseUrl;
        private String path;
        private String protocol;
        private String apiKey;
        private Boolean clearApiKey;
        private Boolean enabled;
    }

    @Data
    private static class CloudModelUpdateRequest {
        private String provider;
        @NotBlank
        private String modelKey;
        private String displayName;
        private String baseUrl;
        private String path;
        private String protocol;
        private String apiKey;
        private Boolean clearApiKey;
    }

    @Data
    private static class CloudModelFullUpdateRequest {
        private String provider;
        @NotBlank
        private String modelKey;
        private String displayName;
        private String baseUrl;
        private String path;
        private String protocol;
        private String apiKey;
        private Boolean clearApiKey;
        private Boolean enabled;
    }

    @Data
    private static class CloudModelToggleRequest {
        @NotNull
        private Boolean enabled;
    }

    @Data
    private static class CloudModelItem {
        private Long id;
        private String provider;
        private String modelKey;
        private String displayName;
        private String baseUrl;
        private String path;
        private String protocol;
        private Boolean hasApiKey;
        private String apiKeyMask;
        private Boolean enabled;
        private Boolean isDefault;
        private String visibility;
        private Long ownerUserId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}

