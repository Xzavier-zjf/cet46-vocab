package com.cet46.vocab.service.impl;

import com.cet46.vocab.config.CloudLlmProperties;
import com.cet46.vocab.dto.request.ChangePasswordRequest;
import com.cet46.vocab.dto.request.UpdatePreferenceRequest;
import com.cet46.vocab.dto.response.CloudLlmHealthResponse;
import com.cet46.vocab.dto.response.LocalModelItemResponse;
import com.cet46.vocab.dto.response.LocalModelListResponse;
import com.cet46.vocab.dto.response.LlmLastUsedResponse;
import com.cet46.vocab.dto.response.UserInfoResponse;
import com.cet46.vocab.entity.CloudLlmModel;
import com.cet46.vocab.entity.User;
import com.cet46.vocab.llm.CloudLlmClient;
import com.cet46.vocab.llm.LlmProvider;
import com.cet46.vocab.llm.OllamaClient;
import com.cet46.vocab.llm.LlmUsageTracker;
import com.cet46.vocab.mapper.UserMapper;
import com.cet46.vocab.service.CloudLlmModelService;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final long MAX_AVATAR_SIZE_BYTES = 2 * 1024 * 1024;

    private final UserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;
    private final CloudLlmClient cloudLlmClient;
    private final CloudLlmProperties cloudLlmProperties;
    private final OllamaClient ollamaClient;
    private final PasswordEncoder passwordEncoder;
    private final LlmUsageTracker llmUsageTracker;
    private final CloudLlmModelService cloudLlmModelService;

    public UserServiceImpl(UserMapper userMapper,
                           JdbcTemplate jdbcTemplate,
                           CloudLlmClient cloudLlmClient,
                           CloudLlmProperties cloudLlmProperties,
                           OllamaClient ollamaClient,
                           PasswordEncoder passwordEncoder,
                           LlmUsageTracker llmUsageTracker,
                           CloudLlmModelService cloudLlmModelService) {
        this.userMapper = userMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.cloudLlmClient = cloudLlmClient;
        this.cloudLlmProperties = cloudLlmProperties;
        this.ollamaClient = ollamaClient;
        this.passwordEncoder = passwordEncoder;
        this.llmUsageTracker = llmUsageTracker;
        this.cloudLlmModelService = cloudLlmModelService;
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
                .llmLocalModel(normalizeModelName(user.getLlmLocalModel()))
                .llmCloudModel(cloudLlmModelService.resolveSelectedModelForUser(user.getLlmCloudModel(), userId))
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

        String provider = StringUtils.hasText(req.getLlmProvider())
                ? LlmProvider.normalize(req.getLlmProvider())
                : LlmProvider.normalize(user.getLlmProvider());
        user.setLlmProvider(provider);

        if (req.getLlmLocalModel() != null) {
            String localModel = normalizeModelName(req.getLlmLocalModel());
            if (StringUtils.hasText(localModel) && !ollamaClient.isModelInstalled(localModel)) {
                throw new IllegalArgumentException("llmLocalModel is not installed: " + localModel);
            }
            user.setLlmLocalModel(localModel);
        }

        if (req.getLlmCloudModel() != null) {
            String cloudModel = normalizeModelName(req.getLlmCloudModel());
            if (StringUtils.hasText(cloudModel) && !cloudLlmModelService.isEnabledModelForUser(cloudModel, userId)) {
                throw new IllegalArgumentException("llmCloudModel is not supported: " + cloudModel);
            }
            user.setLlmCloudModel(cloudModel);
        }

        if (LlmProvider.LOCAL.equals(provider)
                && StringUtils.hasText(user.getLlmLocalModel())
                && !ollamaClient.isModelInstalled(user.getLlmLocalModel())) {
            throw new IllegalArgumentException("llmLocalModel is not installed: " + user.getLlmLocalModel());
        }

        if (LlmProvider.CLOUD.equals(provider) && StringUtils.hasText(user.getLlmCloudModel())) {
            user.setLlmCloudModel(cloudLlmModelService.resolveSelectedModelForUser(user.getLlmCloudModel(), userId));
            if (!StringUtils.hasText(user.getLlmCloudModel())) {
                throw new IllegalArgumentException("llmCloudModel is not supported");
            }
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
        return checkCloudLlmHealth(userId, null);
    }

    @Override
    public CloudLlmHealthResponse checkCloudLlmHealth(Long userId, String modelOverride) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("user not found");
        }
        String normalizedOverride = normalizeModelName(modelOverride);
        if (StringUtils.hasText(normalizedOverride)
                && !cloudLlmModelService.isEnabledModelForUser(normalizedOverride, userId)) {
            throw new IllegalArgumentException("llmCloudModel is not supported: " + normalizedOverride);
        }
        String targetModel = StringUtils.hasText(normalizedOverride)
                ? normalizedOverride
                : resolveUserCloudModel(user);
        CloudLlmHealthResponse response = cloudLlmClient.healthCheck(targetModel);
        response.setCurrentProvider(LlmProvider.normalize(user.getLlmProvider()));
        return response;
    }
    @Override
    public CloudLlmHealthResponse checkLocalLlmHealth(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("user not found");
        }
        String selectedModel = StringUtils.hasText(user.getLlmLocalModel())
                ? user.getLlmLocalModel().trim()
                : null;
        CloudLlmHealthResponse response = ollamaClient.healthCheck(selectedModel);
        response.setCurrentProvider(LlmProvider.normalize(user.getLlmProvider()));
        return response;
    }

    @Override
    public LocalModelListResponse getLocalModels(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("user not found");
        }

        try {
            List<OllamaClient.LocalModelInfo> models = ollamaClient.listModels();
            List<LocalModelItemResponse> items = models.stream()
                    .map(item -> LocalModelItemResponse.builder()
                            .name(item.name())
                            .sizeBytes(item.sizeBytes())
                            .modifiedAt(item.modifiedAt())
                            .digest(item.digest())
                            .build())
                    .toList();
            String selectedModel = resolveSelectedModel(user.getLlmLocalModel(), items);

            return LocalModelListResponse.builder()
                    .serviceUp(true)
                    .baseUrl(ollamaClient.getBaseUrl())
                    .count(items.size())
                    .selectedModel(selectedModel)
                    .defaultModel(ollamaClient.getDefaultModel())
                    .models(items)
                    .message("\u672c\u5730\u6a21\u578b\u5217\u8868\u83b7\u53d6\u6210\u529f")
                    .build();
        } catch (Exception ex) {
            log.warn("failed to list local ollama models", ex);
            String selectedModel = StringUtils.hasText(user.getLlmLocalModel())
                    ? user.getLlmLocalModel().trim()
                    : "";
            return LocalModelListResponse.builder()
                    .serviceUp(false)
                    .baseUrl(ollamaClient.getBaseUrl())
                    .count(0)
                    .selectedModel(selectedModel)
                    .defaultModel(ollamaClient.getDefaultModel())
                    .models(List.of())
                    .message("\u65e0\u6cd5\u8fde\u63a5\u672c\u5730\u6a21\u578b\u670d\u52a1")
                    .build();
        }
    }


    @Override
    public LocalModelListResponse getCloudModels(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("user not found");
        }

        List<CloudLlmModel> models = cloudLlmModelService.listEnabledForUser(userId);
        List<LocalModelItemResponse> items = models.stream()
                .map(item -> LocalModelItemResponse.builder()
                        .name(item.getModelKey())
                        .displayName(item.getDisplayName())
                        .build())
                .toList();
        String selectedModel = cloudLlmModelService.resolveSelectedModelForUser(user.getLlmCloudModel(), userId);
        String defaultModel = cloudLlmModelService.resolveDefaultModelForUser(userId);

        return LocalModelListResponse.builder()
                .serviceUp(Boolean.TRUE.equals(cloudLlmProperties.getEnabled()))
                .baseUrl(cloudLlmProperties.getBaseUrl())
                .count(items.size())
                .selectedModel(selectedModel == null ? "" : selectedModel)
                .defaultModel(defaultModel == null ? "" : defaultModel)
                .models(items)
                .providers(cloudLlmProperties.resolveProviders())
                .providerLabels(cloudLlmProperties.resolveProviderLabels())
                .message("\u4e91\u7aef\u6a21\u578b\u5217\u8868\u83b7\u53d6\u6210\u529f")
                .build();
    }
    @Override
    public List<CloudLlmModel> listPrivateCloudModels(Long userId) {
        return cloudLlmModelService.listPrivateByOwner(userId);
    }

    @Override
    public CloudLlmModel createPrivateCloudModel(Long userId, String modelKey, String displayName, Boolean enabled) {
        return cloudLlmModelService.createPrivate(userId, modelKey, displayName, enabled);
    }

    @Override
    public CloudLlmModel updatePrivateCloudModel(Long userId, Long id, String modelKey, String displayName, Boolean enabled) {
        return cloudLlmModelService.updatePrivate(userId, id, modelKey, displayName, enabled);
    }

    @Override
    public CloudLlmModel setPrivateCloudModelEnabled(Long userId, Long id, Boolean enabled) {
        return cloudLlmModelService.setPrivateEnabled(userId, id, enabled);
    }
    @Override
    public void deletePrivateCloudModel(Long userId, Long id) {
        cloudLlmModelService.deletePrivate(userId, id);
    }

    private String resolveSelectedModel(String savedModel, List<LocalModelItemResponse> items) {
        if (StringUtils.hasText(savedModel) && containsModel(items, savedModel.trim())) {
            return savedModel.trim();
        }
        String defaultModel = ollamaClient.getDefaultModel();
        if (StringUtils.hasText(defaultModel) && containsModel(items, defaultModel.trim())) {
            return defaultModel.trim();
        }
        if (items != null && !items.isEmpty() && StringUtils.hasText(items.get(0).getName())) {
            return items.get(0).getName().trim();
        }
        return "";
    }

    private boolean containsModel(List<LocalModelItemResponse> items, String model) {
        if (items == null || items.isEmpty() || !StringUtils.hasText(model)) {
            return false;
        }
        for (LocalModelItemResponse item : items) {
            if (item != null && StringUtils.hasText(item.getName()) && model.equals(item.getName().trim())) {
                return true;
            }
        }
        return false;
    }



    @Override
    public LlmLastUsedResponse getLastUsedLlm(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("user not found");
        }
        LlmUsageTracker.UsageSnapshot snapshot = llmUsageTracker.get(userId);
        return LlmLastUsedResponse.builder()
                .provider(snapshot.provider())
                .model(snapshot.model())
                .source(snapshot.source())
                .updatedAt(snapshot.updatedAt())
                .build();
    }
    @Override
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("user not found");
        }
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("\u65e7\u5bc6\u7801\u4e0d\u6b63\u786e");
        }
        if (req.getOldPassword().equals(req.getNewPassword())) {
            throw new IllegalArgumentException("\u65b0\u5bc6\u7801\u4e0d\u80fd\u4e0e\u65e7\u5bc6\u7801\u76f8\u540c");
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

    private String normalizeModelName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }


    private String resolveUserCloudModel(User user) {
        String userModel = normalizeModelName(user.getLlmCloudModel());
        return cloudLlmModelService.resolveSelectedModelForUser(userModel, user.getId());
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























