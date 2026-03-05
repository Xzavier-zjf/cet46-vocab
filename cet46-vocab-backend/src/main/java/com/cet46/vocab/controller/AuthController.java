package com.cet46.vocab.controller;

import com.cet46.vocab.common.Result;
import com.cet46.vocab.common.ResultCode;
import com.cet46.vocab.dto.request.LoginRequest;
import com.cet46.vocab.dto.request.RegisterRequest;
import com.cet46.vocab.dto.response.UserInfoResponse;
import com.cet46.vocab.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<UserInfoResponse> register(@Valid @RequestBody RegisterRequest req) {
        return Result.success(authService.register(req));
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        return Result.success(authService.login(req));
    }

    @PostMapping("/logout")
    public Result<Void> logout(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(authentication.getPrincipal().toString());
        authService.logout(userId);
        return Result.success();
    }
}
