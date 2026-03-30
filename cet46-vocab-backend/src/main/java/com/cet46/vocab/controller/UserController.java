package com.cet46.vocab.controller;

import com.cet46.vocab.common.Result;
import com.cet46.vocab.common.ResultCode;
import com.cet46.vocab.dto.request.ChangePasswordRequest;
import com.cet46.vocab.dto.request.UpdatePreferenceRequest;
import com.cet46.vocab.dto.response.CloudLlmHealthResponse;
import com.cet46.vocab.dto.response.LocalModelListResponse;
import com.cet46.vocab.dto.response.LlmLastUsedResponse;
import com.cet46.vocab.dto.response.UserInfoResponse;
import com.cet46.vocab.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    public Result<CloudLlmHealthResponse> checkCloudLlmHealth(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(authentication.getPrincipal().toString());
        return Result.success(userService.checkCloudLlmHealth(userId));
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
}

