package com.cet46.vocab.service.impl;

import com.cet46.vocab.dto.request.UpdatePreferenceRequest;
import com.cet46.vocab.dto.request.ChangePasswordRequest;
import com.cet46.vocab.dto.response.CloudLlmHealthResponse;
import com.cet46.vocab.dto.response.UserInfoResponse;
import com.cet46.vocab.entity.User;
import com.cet46.vocab.llm.CloudLlmClient;
import com.cet46.vocab.llm.LlmProvider;
import com.cet46.vocab.llm.OllamaClient;
import com.cet46.vocab.mapper.UserMapper;
import com.cet46.vocab.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final long MAX_AVATAR_SIZE_BYTES = 2 * 1024 * 1024;

    private final UserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;
    private final CloudLlmClient cloudLlmClient;
    private final OllamaClient ollamaClient;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserMapper userMapper,
                           JdbcTemplate jdbcTemplate,
                           CloudLlmClient cloudLlmClient,
                           OllamaClient ollamaClient,
                           PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.cloudLlmClient = cloudLlmClient;
        this.ollamaClient = ollamaClient;
        this.passwordEncoder = passwordEncoder;
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
                .llmProvider(LlmProvider.normalize(user.getLlmProvider()))
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

        if (StringUtils.hasText(req.getLlmStyle())) {
            user.setLlmStyle(req.getLlmStyle());
        }
        if (StringUtils.hasText(req.getLlmProvider())) {
            user.setLlmProvider(LlmProvider.normalize(req.getLlmProvider()));
        } else {
            user.setLlmProvider(LlmProvider.normalize(user.getLlmProvider()));
        }
        user.setDailyTarget(req.getDailyTarget());
        userMapper.updateById(user);
    }

    @Override
    public void updateProfile(Long userId, String nickname, MultipartFile avatarFile) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("user not found");
        }

        if (nickname != null) {
            String normalizedNickname = nickname.trim();
            if (!StringUtils.hasText(normalizedNickname)) {
                throw new IllegalArgumentException("nickname cannot be blank");
            }
            if (normalizedNickname.length() > 50) {
                throw new IllegalArgumentException("nickname length must be <= 50");
            }
            user.setNickname(normalizedNickname);
        }

        if (avatarFile != null && !avatarFile.isEmpty()) {
            validateAvatarFile(avatarFile);
            String oldAvatar = user.getAvatar();
            String avatarPath = saveAvatar(userId, avatarFile);
            user.setAvatar(avatarPath);
            removeOldAvatarIfLocal(oldAvatar, avatarPath);
        }

        userMapper.updateById(user);
    }

    @Override
    public CloudLlmHealthResponse checkCloudLlmHealth(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("user not found");
        }
        CloudLlmHealthResponse response = cloudLlmClient.healthCheck();
        response.setCurrentProvider(LlmProvider.normalize(user.getLlmProvider()));
        return response;
    }

    @Override
    public CloudLlmHealthResponse checkLocalLlmHealth(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("user not found");
        }
        CloudLlmHealthResponse response = ollamaClient.healthCheck();
        response.setCurrentProvider(LlmProvider.normalize(user.getLlmProvider()));
        return response;
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("user not found");
        }
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("\u65E7\u5BC6\u7801\u4E0D\u6B63\u786E");
        }
        if (req.getOldPassword().equals(req.getNewPassword())) {
            throw new IllegalArgumentException("\u65B0\u5BC6\u7801\u4E0D\u80FD\u4E0E\u65E7\u5BC6\u7801\u76F8\u540C");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);
    }

    private void validateAvatarFile(MultipartFile avatarFile) {
        if (avatarFile.getSize() > MAX_AVATAR_SIZE_BYTES) {
            throw new IllegalArgumentException("avatar size must be <= 2MB");
        }
        String contentType = avatarFile.getContentType();
        if (!StringUtils.hasText(contentType) || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("avatar must be an image file");
        }
    }

    private String saveAvatar(Long userId, MultipartFile avatarFile) {
        try {
            String ext = getFileExt(avatarFile.getOriginalFilename());
            Path avatarDir = Paths.get("uploads", "avatars").toAbsolutePath().normalize();
            Files.createDirectories(avatarDir);

            String filename = userId + "_" + System.currentTimeMillis() + ext;
            Path target = avatarDir.resolve(filename);
            Files.copy(avatarFile.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return "/api/uploads/avatars/" + filename;
        } catch (IOException ex) {
            throw new RuntimeException("failed to save avatar", ex);
        }
    }

    private void removeOldAvatarIfLocal(String oldAvatar, String newAvatar) {
        if (!StringUtils.hasText(oldAvatar) || oldAvatar.equals(newAvatar)) {
            return;
        }
        String prefix = "/api/uploads/avatars/";
        if (!oldAvatar.startsWith(prefix)) {
            return;
        }
        String oldFilename = oldAvatar.substring(prefix.length());
        if (!StringUtils.hasText(oldFilename)) {
            return;
        }
        Path oldPath = Paths.get("uploads", "avatars", oldFilename).toAbsolutePath().normalize();
        try {
            Files.deleteIfExists(oldPath);
        } catch (IOException ex) {
            log.warn("failed to delete old avatar file {}", oldPath, ex);
        }
    }

    private String getFileExt(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return ".png";
        }
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return ".png";
        }
        String ext = fileName.substring(idx).toLowerCase();
        if (ext.length() > 10) {
            return ".png";
        }
        return ext;
    }

    private List<LocalDate> queryDistinctReviewDates(Long userId) {
        String sql = "SELECT DISTINCT DATE(reviewed_at) AS review_date FROM review_log WHERE user_id = ? ORDER BY review_date DESC";
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                Date date = rs.getDate("review_date");
                return date == null ? null : date.toLocalDate();
            }, userId).stream().filter(date -> date != null).toList();
        } catch (DataAccessException ex) {
            log.warn("Failed to query review dates for user {}", userId, ex);
            return List.of();
        }
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
