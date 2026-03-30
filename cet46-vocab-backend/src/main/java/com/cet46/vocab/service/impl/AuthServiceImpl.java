package com.cet46.vocab.service.impl;

import com.cet46.vocab.dto.request.LoginRequest;
import com.cet46.vocab.dto.request.RegisterRequest;
import com.cet46.vocab.dto.response.UserInfoResponse;
import com.cet46.vocab.entity.User;
import com.cet46.vocab.llm.LlmProvider;
import com.cet46.vocab.mapper.UserMapper;
import com.cet46.vocab.service.AuthService;
import com.cet46.vocab.utils.JwtUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class AuthServiceImpl implements AuthService {

    private static final int DEFAULT_DAILY_TARGET = 20;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RedisTemplate<String, Object> redisTemplate;

    public AuthServiceImpl(UserMapper userMapper,
                           PasswordEncoder passwordEncoder,
                           JwtUtils jwtUtils,
                           RedisTemplate<String, Object> redisTemplate) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public UserInfoResponse register(RegisterRequest req) {
        User exist = userMapper.selectByUsername(req.getUsername());
        if (exist != null) {
            throw new IllegalArgumentException("username already exists");
        }

        String nickname = StringUtils.hasText(req.getNickname()) ? req.getNickname() : req.getUsername();
        User user = User.builder()
                .username(req.getUsername())
                .password(passwordEncoder.encode(req.getPassword()))
                .nickname(nickname)
                .role("USER")
                .llmProvider(LlmProvider.LOCAL)
                .dailyTarget(DEFAULT_DAILY_TARGET)
                .build();

        userMapper.insert(user);

        return UserInfoResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .llmStyle(user.getLlmStyle())
                .llmProvider(LlmProvider.normalize(user.getLlmProvider()))
                .llmLocalModel(user.getLlmLocalModel())
                .llmCloudModel(user.getLlmCloudModel())
                .dailyTarget(user.getDailyTarget())
                .totalDays(0)
                .streakDays(0)
                .role(user.getRole())
                .build();
    }

    @Override
    public Map<String, Object> login(LoginRequest req) {
        User user = userMapper.selectByUsername(req.getUsername());
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new UsernameNotFoundException("username or password is incorrect");
        }

        String token = jwtUtils.generateToken(user.getId(), user.getRole());
        String redisKey = "token:user:" + user.getId();
        redisTemplate.opsForValue().set(redisKey, token, 7, TimeUnit.DAYS);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("role", user.getRole());
        result.put("llmStyle", user.getLlmStyle());
        result.put("llmProvider", LlmProvider.normalize(user.getLlmProvider()));
        result.put("llmLocalModel", user.getLlmLocalModel());
        result.put("llmCloudModel", user.getLlmCloudModel());
        return result;
    }

    @Override
    public void logout(Long userId) {
        if (userId == null) {
            return;
        }
        redisTemplate.delete("token:user:" + userId);
    }
}


