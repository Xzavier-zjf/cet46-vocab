package com.cet46.vocab.service;

import com.cet46.vocab.dto.request.LoginRequest;
import com.cet46.vocab.dto.request.RegisterRequest;
import com.cet46.vocab.dto.response.UserInfoResponse;

import java.util.Map;

public interface AuthService {

    UserInfoResponse register(RegisterRequest req);

    Map<String, Object> login(LoginRequest req);

    void logout(Long userId);
}
