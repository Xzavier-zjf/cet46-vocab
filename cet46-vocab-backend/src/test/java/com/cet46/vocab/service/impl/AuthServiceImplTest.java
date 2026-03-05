package com.cet46.vocab.service.impl;

import com.cet46.vocab.dto.request.LoginRequest;
import com.cet46.vocab.dto.request.RegisterRequest;
import com.cet46.vocab.dto.response.UserInfoResponse;
import com.cet46.vocab.entity.User;
import com.cet46.vocab.mapper.UserMapper;
import com.cet46.vocab.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void 注册成功_应返回用户信息() {
        // 测试意图：用户名不重复时可成功注册并返回基础信息
        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser");
        req.setPassword("123456");
        req.setNickname("tester");

        when(userMapper.selectByUsername("testuser")).thenReturn(null);
        when(passwordEncoder.encode("123456")).thenReturn("encoded-pwd");
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(100L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        UserInfoResponse result = authService.register(req);

        assertNotNull(result);
        assertEquals(100L, result.getUserId());
        assertEquals("testuser", result.getUsername());
        assertEquals("tester", result.getNickname());
        assertEquals("USER", result.getRole());
        assertEquals(20, result.getDailyTarget());
        verify(userMapper).insert(any(User.class));
    }

    @Test
    void 注册用户名重复_应抛出异常() {
        // 测试意图：用户名已存在时注册应抛 IllegalArgumentException
        RegisterRequest req = new RegisterRequest();
        req.setUsername("exists");
        req.setPassword("123456");

        when(userMapper.selectByUsername("exists")).thenReturn(User.builder().id(1L).username("exists").build());

        assertThrows(IllegalArgumentException.class, () -> authService.register(req));
    }

    @Test
    void 登录成功_应返回token并写入redis() {
        // 测试意图：账号密码正确时返回登录信息，并把 token 写入 Redis
        LoginRequest req = new LoginRequest();
        req.setUsername("tom");
        req.setPassword("plain-pwd");

        User user = User.builder()
                .id(10L)
                .username("tom")
                .password("encoded")
                .nickname("Tom")
                .role("USER")
                .llmStyle("story")
                .build();

        when(userMapper.selectByUsername("tom")).thenReturn(user);
        when(passwordEncoder.matches("plain-pwd", "encoded")).thenReturn(true);
        when(jwtUtils.generateToken(10L, "USER")).thenReturn("jwt-token");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        Map<String, Object> result = authService.login(req);

        assertEquals("jwt-token", result.get("token"));
        assertEquals(10L, result.get("userId"));
        assertEquals("Tom", result.get("nickname"));
        verify(valueOperations).set(eq("token:user:10"), eq("jwt-token"), eq(7L), eq(TimeUnit.DAYS));
    }

    @Test
    void 登录密码错误_应抛出异常() {
        // 测试意图：密码错误时应抛 UsernameNotFoundException
        LoginRequest req = new LoginRequest();
        req.setUsername("tom");
        req.setPassword("wrong");

        User user = User.builder().id(10L).username("tom").password("encoded").build();
        when(userMapper.selectByUsername("tom")).thenReturn(user);
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThrows(UsernameNotFoundException.class, () -> authService.login(req));
    }
}
