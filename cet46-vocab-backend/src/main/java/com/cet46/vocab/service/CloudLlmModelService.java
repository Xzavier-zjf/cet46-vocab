package com.cet46.vocab.service;

import com.cet46.vocab.entity.CloudLlmModel;

import java.util.List;

public interface CloudLlmModelService {

    String VISIBILITY_GLOBAL = "global";
    String VISIBILITY_USER_PRIVATE = "user-private";

    List<CloudLlmModel> listAll();

    List<CloudLlmModel> listEnabled();

    List<CloudLlmModel> listEnabledForUser(Long userId);

    List<CloudLlmModel> listPrivateByOwner(Long userId);

    boolean isEnabledModel(String modelKey);

    boolean isEnabledModelForUser(String modelKey, Long userId);

    String resolveDefaultModel();

    String resolveDefaultModelForUser(Long userId);

    String resolveSelectedModel(String savedModel);

    String resolveSelectedModelForUser(String savedModel, Long userId);

    CloudLlmModel create(String provider, String modelKey, String displayName, Boolean enabled, Boolean isDefault);

    CloudLlmModel update(Long id, String provider, String modelKey, String displayName, Boolean enabled, Boolean isDefault);

    CloudLlmModel createPrivate(Long ownerUserId, String modelKey, String displayName, Boolean enabled);

    CloudLlmModel updatePrivate(Long ownerUserId, Long id, String modelKey, String displayName, Boolean enabled);

    CloudLlmModel setPrivateEnabled(Long ownerUserId, Long id, Boolean enabled);

    void deletePrivate(Long ownerUserId, Long id);

    void delete(Long id);

    CloudLlmModel setDefault(Long id);
}
