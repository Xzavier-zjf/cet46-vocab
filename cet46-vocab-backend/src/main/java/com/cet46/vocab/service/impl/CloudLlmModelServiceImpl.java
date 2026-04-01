package com.cet46.vocab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cet46.vocab.config.CloudLlmProperties;
import com.cet46.vocab.entity.CloudLlmModel;
import com.cet46.vocab.mapper.CloudLlmModelMapper;
import com.cet46.vocab.service.CloudLlmModelService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CloudLlmModelServiceImpl implements CloudLlmModelService {

    private static final String DEFAULT_PROVIDER = "bailian";

    private final CloudLlmModelMapper cloudLlmModelMapper;
    private final CloudLlmProperties cloudLlmProperties;
    private final JdbcTemplate jdbcTemplate;

    public CloudLlmModelServiceImpl(CloudLlmModelMapper cloudLlmModelMapper,
                                    CloudLlmProperties cloudLlmProperties,
                                    JdbcTemplate jdbcTemplate) {
        this.cloudLlmModelMapper = cloudLlmModelMapper;
        this.cloudLlmProperties = cloudLlmProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<CloudLlmModel> listAll() {
        return cloudLlmModelMapper.selectList(new LambdaQueryWrapper<CloudLlmModel>()
                .and(wrapper -> wrapper
                        .isNull(CloudLlmModel::getVisibility)
                        .or().eq(CloudLlmModel::getVisibility, VISIBILITY_GLOBAL))
                .orderByDesc(CloudLlmModel::getIsDefault)
                .orderByDesc(CloudLlmModel::getEnabled)
                .orderByAsc(CloudLlmModel::getId));
    }

    @Override
    public List<CloudLlmModel> listEnabled() {
        return cloudLlmModelMapper.selectList(new LambdaQueryWrapper<CloudLlmModel>()
                .eq(CloudLlmModel::getEnabled, true)
                .and(wrapper -> wrapper
                        .isNull(CloudLlmModel::getVisibility)
                        .or().eq(CloudLlmModel::getVisibility, VISIBILITY_GLOBAL))
                .orderByDesc(CloudLlmModel::getIsDefault)
                .orderByAsc(CloudLlmModel::getId));
    }

    @Override
    public List<CloudLlmModel> listEnabledForUser(Long userId) {
        List<CloudLlmModel> global = listEnabled();
        if (userId == null) {
            return global;
        }

        List<CloudLlmModel> privateEnabled = cloudLlmModelMapper.selectList(new LambdaQueryWrapper<CloudLlmModel>()
                .eq(CloudLlmModel::getEnabled, true)
                .eq(CloudLlmModel::getVisibility, VISIBILITY_USER_PRIVATE)
                .eq(CloudLlmModel::getOwnerUserId, userId)
                .orderByAsc(CloudLlmModel::getId));

        if (privateEnabled.isEmpty()) {
            return global;
        }
        List<CloudLlmModel> merged = new ArrayList<>(global.size() + privateEnabled.size());
        merged.addAll(global);
        merged.addAll(privateEnabled);
        return merged;
    }

    @Override
    public List<CloudLlmModel> listPrivateByOwner(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return cloudLlmModelMapper.selectList(new LambdaQueryWrapper<CloudLlmModel>()
                .eq(CloudLlmModel::getVisibility, VISIBILITY_USER_PRIVATE)
                .eq(CloudLlmModel::getOwnerUserId, userId)
                .orderByDesc(CloudLlmModel::getEnabled)
                .orderByAsc(CloudLlmModel::getId));
    }

    @Override
    public boolean isEnabledModel(String modelKey) {
        return isEnabledModelForUser(modelKey, null);
    }

    @Override
    public boolean isEnabledModelForUser(String modelKey, Long userId) {
        String key = normalizeModelKey(modelKey);
        if (!StringUtils.hasText(key)) {
            return false;
        }
        List<CloudLlmModel> enabled = listEnabledForUser(userId);
        if (enabled.isEmpty()) {
            return cloudLlmProperties.isSupportedModel(key);
        }
        return enabled.stream().anyMatch(item -> key.equals(item.getModelKey()));
    }

    @Override
    public String resolveDefaultModel() {
        return resolveDefaultModelForUser(null);
    }

    @Override
    public String resolveDefaultModelForUser(Long userId) {
        List<CloudLlmModel> enabled = listEnabledForUser(userId);
        if (!enabled.isEmpty()) {
            for (CloudLlmModel item : enabled) {
                if (Boolean.TRUE.equals(item.getIsDefault()) && isGlobal(item) && StringUtils.hasText(item.getModelKey())) {
                    return item.getModelKey().trim();
                }
            }
            for (CloudLlmModel item : enabled) {
                if (Boolean.TRUE.equals(item.getIsDefault()) && StringUtils.hasText(item.getModelKey())) {
                    return item.getModelKey().trim();
                }
            }
            for (CloudLlmModel item : enabled) {
                if (isGlobal(item) && StringUtils.hasText(item.getModelKey())) {
                    return item.getModelKey().trim();
                }
            }
            for (CloudLlmModel item : enabled) {
                if (StringUtils.hasText(item.getModelKey())) {
                    return item.getModelKey().trim();
                }
            }
        }
        return cloudLlmProperties.resolveDefaultModel();
    }

    @Override
    public String resolveSelectedModel(String savedModel) {
        return resolveSelectedModelForUser(savedModel, null);
    }

    @Override
    public String resolveSelectedModelForUser(String savedModel, Long userId) {
        String normalized = normalizeModelKey(savedModel);
        if (StringUtils.hasText(normalized) && isEnabledModelForUser(normalized, userId)) {
            return normalized;
        }
        return resolveDefaultModelForUser(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CloudLlmModel create(String provider, String modelKey, String displayName, Boolean enabled, Boolean isDefault) {
        String normalizedModelKey = requireModelKey(modelKey);
        String normalizedDisplayName = normalizeDisplayName(displayName, normalizedModelKey);
        boolean enabledFlag = enabled == null || enabled;
        boolean defaultFlag = isDefault != null && isDefault;

        CloudLlmModel entity = CloudLlmModel.builder()
                .provider(normalizeProvider(provider))
                .modelKey(normalizedModelKey)
                .displayName(normalizedDisplayName)
                .enabled(enabledFlag)
                .isDefault(defaultFlag)
                .visibility(VISIBILITY_GLOBAL)
                .ownerUserId(0L)
                .tenantId(null)
                .build();

        try {
            cloudLlmModelMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("modelKey already exists: " + normalizedModelKey);
        }

        if (defaultFlag) {
            clearDefaultExcept(entity.getId());
        } else {
            ensureOneDefaultExists();
        }

        return cloudLlmModelMapper.selectById(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CloudLlmModel update(Long id, String provider, String modelKey, String displayName, Boolean enabled, Boolean isDefault) {
        CloudLlmModel existing = requireById(id);
        if (!isGlobal(existing)) {
            throw new IllegalArgumentException("only global model can be updated by admin api");
        }

        String normalizedModelKey = requireModelKey(modelKey);
        String normalizedDisplayName = normalizeDisplayName(displayName, normalizedModelKey);
        boolean enabledFlag = enabled == null ? Boolean.TRUE.equals(existing.getEnabled()) : enabled;
        boolean defaultFlag = isDefault == null ? Boolean.TRUE.equals(existing.getIsDefault()) : isDefault;

        existing.setModelKey(normalizedModelKey);
        existing.setDisplayName(normalizedDisplayName);
        existing.setEnabled(enabledFlag);
        existing.setIsDefault(defaultFlag);
        existing.setProvider(normalizeProvider(provider));
        existing.setVisibility(VISIBILITY_GLOBAL);
        existing.setOwnerUserId(0L);

        try {
            cloudLlmModelMapper.updateById(existing);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("modelKey already exists: " + normalizedModelKey);
        }

        if (defaultFlag) {
            clearDefaultExcept(existing.getId());
        }

        if (!enabledFlag && defaultFlag) {
            existing.setIsDefault(false);
            cloudLlmModelMapper.updateById(existing);
        }

        ensureOneDefaultExists();
        return cloudLlmModelMapper.selectById(existing.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CloudLlmModel createPrivate(Long ownerUserId, String modelKey, String displayName, Boolean enabled) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("ownerUserId is required");
        }
        String normalizedModelKey = requireModelKey(modelKey);
        String normalizedDisplayName = normalizeDisplayName(displayName, normalizedModelKey);
        boolean enabledFlag = enabled == null || enabled;

        CloudLlmModel entity = CloudLlmModel.builder()
                .provider(DEFAULT_PROVIDER)
                .modelKey(normalizedModelKey)
                .displayName(normalizedDisplayName)
                .enabled(enabledFlag)
                .isDefault(false)
                .visibility(VISIBILITY_USER_PRIVATE)
                .ownerUserId(ownerUserId)
                .tenantId(null)
                .build();

        try {
            cloudLlmModelMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("modelKey already exists: " + normalizedModelKey);
        }

        return cloudLlmModelMapper.selectById(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CloudLlmModel updatePrivate(Long ownerUserId, Long id, String modelKey, String displayName, Boolean enabled) {
        CloudLlmModel existing = requireOwnedPrivate(ownerUserId, id);
        String normalizedModelKey = requireModelKey(modelKey);
        String normalizedDisplayName = normalizeDisplayName(displayName, normalizedModelKey);

        existing.setModelKey(normalizedModelKey);
        existing.setDisplayName(normalizedDisplayName);
        if (enabled != null) {
            existing.setEnabled(enabled);
        }
        existing.setIsDefault(false);
        existing.setVisibility(VISIBILITY_USER_PRIVATE);
        existing.setOwnerUserId(ownerUserId);

        try {
            cloudLlmModelMapper.updateById(existing);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("modelKey already exists: " + normalizedModelKey);
        }

        return cloudLlmModelMapper.selectById(existing.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CloudLlmModel setPrivateEnabled(Long ownerUserId, Long id, Boolean enabled) {
        if (enabled == null) {
            throw new IllegalArgumentException("enabled is required");
        }
        CloudLlmModel existing = requireOwnedPrivate(ownerUserId, id);
        existing.setEnabled(enabled);
        existing.setIsDefault(false);
        cloudLlmModelMapper.updateById(existing);
        return cloudLlmModelMapper.selectById(existing.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePrivate(Long ownerUserId, Long id) {
        CloudLlmModel existing = requireOwnedPrivate(ownerUserId, id);

        String fallback = resolveDefaultModelForUser(ownerUserId);
        String deletedKey = existing.getModelKey();
        if (StringUtils.hasText(deletedKey) && StringUtils.hasText(fallback) && !fallback.equals(deletedKey)) {
            jdbcTemplate.update(
                    "UPDATE user SET llm_cloud_model = ? WHERE id = ? AND llm_cloud_model = ?",
                    fallback,
                    ownerUserId,
                    deletedKey
            );
        }

        cloudLlmModelMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CloudLlmModel existing = requireById(id);
        if (!isGlobal(existing)) {
            throw new IllegalArgumentException("private model cannot be deleted by admin api");
        }
        if (Boolean.TRUE.equals(existing.getIsDefault())) {
            throw new IllegalArgumentException("default model cannot be deleted");
        }

        String fallback = resolveDefaultModel();
        String deletedKey = existing.getModelKey();
        if (StringUtils.hasText(deletedKey) && StringUtils.hasText(fallback) && !fallback.equals(deletedKey)) {
            jdbcTemplate.update(
                    "UPDATE user SET llm_cloud_model = ? WHERE llm_cloud_model = ?",
                    fallback,
                    deletedKey
            );
        }

        cloudLlmModelMapper.deleteById(id);
        ensureOneDefaultExists();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CloudLlmModel setDefault(Long id) {
        CloudLlmModel existing = requireById(id);
        if (!isGlobal(existing)) {
            throw new IllegalArgumentException("only global model can be set as default");
        }
        if (!Boolean.TRUE.equals(existing.getEnabled())) {
            throw new IllegalArgumentException("disabled model cannot be set as default");
        }
        existing.setIsDefault(true);
        cloudLlmModelMapper.updateById(existing);
        clearDefaultExcept(id);
        return cloudLlmModelMapper.selectById(id);
    }

    private CloudLlmModel requireById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        CloudLlmModel existing = cloudLlmModelMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("cloud model not found: " + id);
        }
        return existing;
    }

    private CloudLlmModel requireOwnedPrivate(Long ownerUserId, Long id) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("ownerUserId is required");
        }
        CloudLlmModel existing = requireById(id);
        if (!VISIBILITY_USER_PRIVATE.equals(normalizeVisibility(existing.getVisibility()))
                || !ownerUserId.equals(existing.getOwnerUserId())) {
            throw new IllegalArgumentException("cloud model not found: " + id);
        }
        return existing;
    }

    private void clearDefaultExcept(Long keepId) {
        List<CloudLlmModel> all = listEnabled();
        for (CloudLlmModel item : all) {
            if (item == null || item.getId() == null) {
                continue;
            }
            if (item.getId().equals(keepId)) {
                continue;
            }
            if (Boolean.TRUE.equals(item.getIsDefault())) {
                item.setIsDefault(false);
                cloudLlmModelMapper.updateById(item);
            }
        }
    }

    private void ensureOneDefaultExists() {
        List<CloudLlmModel> enabled = listEnabled();
        if (enabled.isEmpty()) {
            return;
        }
        boolean hasDefault = enabled.stream().anyMatch(item -> Boolean.TRUE.equals(item.getIsDefault()));
        if (hasDefault) {
            return;
        }
        CloudLlmModel first = enabled.get(0);
        first.setIsDefault(true);
        cloudLlmModelMapper.updateById(first);
    }

    private boolean isGlobal(CloudLlmModel item) {
        return VISIBILITY_GLOBAL.equals(normalizeVisibility(item == null ? null : item.getVisibility()));
    }

    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return cloudLlmProperties.resolveDefaultProvider();
        }
        String value = provider.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 32) {
            throw new IllegalArgumentException("provider length must be <= 32");
        }
        if (!cloudLlmProperties.isSupportedProvider(value)) {
            throw new IllegalArgumentException("provider is not allowed: " + value);
        }
        return value;
    }
    private String normalizeVisibility(String visibility) {
        if (!StringUtils.hasText(visibility)) {
            return VISIBILITY_GLOBAL;
        }
        String value = visibility.trim();
        if (VISIBILITY_USER_PRIVATE.equals(value)) {
            return VISIBILITY_USER_PRIVATE;
        }
        return VISIBILITY_GLOBAL;
    }

    private String normalizeModelKey(String modelKey) {
        if (!StringUtils.hasText(modelKey)) {
            return "";
        }
        return modelKey.trim();
    }

    private String requireModelKey(String modelKey) {
        String normalized = normalizeModelKey(modelKey);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("modelKey is required");
        }
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("modelKey length must be <= 128");
        }
        return normalized;
    }

    private String normalizeDisplayName(String displayName, String modelKey) {
        String value = StringUtils.hasText(displayName) ? displayName.trim() : modelKey;
        if (value.length() > 128) {
            throw new IllegalArgumentException("displayName length must be <= 128");
        }
        return value;
    }
}




