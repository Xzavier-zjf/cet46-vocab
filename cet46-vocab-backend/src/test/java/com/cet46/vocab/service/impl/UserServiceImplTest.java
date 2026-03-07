package com.cet46.vocab.service.impl;

import com.cet46.vocab.dto.response.UserInfoResponse;
import com.cet46.vocab.entity.User;
import com.cet46.vocab.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void getUserInfoShouldFallbackToZeroStatsWhenReviewLogQueryFails() {
        User user = User.builder()
                .id(1L)
                .username("alice")
                .nickname("Alice")
                .role("USER")
                .dailyTarget(20)
                .build();
        when(userMapper.selectById(1L)).thenReturn(user);
        when(jdbcTemplate.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq(1L)))
                .thenThrow(new InvalidDataAccessResourceUsageException("review_log not ready"));

        UserInfoResponse result = userService.getUserInfo(1L);

        assertNotNull(result);
        assertEquals(1L, result.getUserId());
        assertEquals("alice", result.getUsername());
        assertEquals(0, result.getTotalDays());
        assertEquals(0, result.getStreakDays());
    }
}
