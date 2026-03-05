package com.cet46.vocab.service.impl;

import com.cet46.vocab.dto.request.UpdatePreferenceRequest;
import com.cet46.vocab.dto.response.UserInfoResponse;
import com.cet46.vocab.entity.User;
import com.cet46.vocab.mapper.UserMapper;
import com.cet46.vocab.service.UserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;

    public UserServiceImpl(UserMapper userMapper, JdbcTemplate jdbcTemplate) {
        this.userMapper = userMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserInfoResponse getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("user not found");
        }

        List<LocalDate> reviewDates = queryDistinctReviewDates(userId);
        int totalDays = reviewDates.size();
        int streakDays = calculateStreakDays(reviewDates);

        return UserInfoResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .llmStyle(user.getLlmStyle())
                .dailyTarget(user.getDailyTarget())
                .totalDays(totalDays)
                .streakDays(streakDays)
                .role(user.getRole())
                .build();
    }

    @Override
    public void updatePreference(Long userId, UpdatePreferenceRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("user not found");
        }

        user.setLlmStyle(req.getLlmStyle());
        user.setDailyTarget(req.getDailyTarget());
        userMapper.updateById(user);
    }

    private List<LocalDate> queryDistinctReviewDates(Long userId) {
        String sql = "SELECT DISTINCT DATE(created_at) AS review_date FROM review_log WHERE user_id = ? ORDER BY review_date DESC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Date date = rs.getDate("review_date");
            return date.toLocalDate();
        }, userId);
    }

    private int calculateStreakDays(List<LocalDate> reviewDates) {
        if (reviewDates.isEmpty()) {
            return 0;
        }

        Set<LocalDate> dateSet = new HashSet<>(reviewDates);
        LocalDate current = LocalDate.now();
        int streak = 0;

        while (dateSet.contains(current)) {
            streak++;
            current = current.minusDays(1);
        }
        return streak;
    }
}
