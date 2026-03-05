package com.cet46.vocab.controller;

import com.cet46.vocab.common.Result;
import com.cet46.vocab.common.ResultCode;
import com.cet46.vocab.dto.request.UpdatePreferenceRequest;
import com.cet46.vocab.dto.response.UserInfoResponse;
import com.cet46.vocab.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
