package com.cet46.vocab.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cet46.vocab.common.PageResult;
import com.cet46.vocab.common.Result;
import com.cet46.vocab.common.ResultCode;
import com.cet46.vocab.entity.Cet4Word;
import com.cet46.vocab.entity.Cet6Word;
import com.cet46.vocab.entity.CloudLlmModel;
import com.cet46.vocab.entity.WordMeta;
import com.cet46.vocab.llm.LlmAsyncService;
import com.cet46.vocab.llm.LlmCacheService;
import com.cet46.vocab.llm.LlmProvider;
import com.cet46.vocab.mapper.Cet4WordMapper;
import com.cet46.vocab.mapper.Cet6WordMapper;
import com.cet46.vocab.mapper.WordMetaMapper;
import com.cet46.vocab.service.CloudLlmModelService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/llm")
public class AdminController {

    private static final String CACHE_PREFIX = "llm:content:";

    private final LlmAsyncService llmAsyncService;
    private final LlmCacheService llmCacheService;
    private final WordMetaMapper wordMetaMapper;
    private final Cet4WordMapper cet4WordMapper;
    private final Cet6WordMapper cet6WordMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CloudLlmModelService cloudLlmModelService;

    public AdminController(LlmAsyncService llmAsyncService,
                           LlmCacheService llmCacheService,
                           WordMetaMapper wordMetaMapper,
                           Cet4WordMapper cet4WordMapper,
                           Cet6WordMapper cet6WordMapper,
                           RedisTemplate<String, Object> redisTemplate,
                           CloudLlmModelService cloudLlmModelService) {
        this.llmAsyncService = llmAsyncService;
        this.llmCacheService = llmCacheService;
        this.wordMetaMapper = wordMetaMapper;
        this.cet4WordMapper = cet4WordMapper;
        this.cet6WordMapper = cet6WordMapper;
        this.redisTemplate = redisTemplate;
        this.cloudLlmModelService = cloudLlmModelService;
    }

    @PostMapping("/batch-generate")
    public Result<Map<String, Object>> batchGenerate(@Valid @RequestBody BatchGenerateRequest req) {
        String wordType = normalizeWordType(req.getWordType());
        if (wordType == null) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), "wordType must be cet4 or cet6");
        }

        String style = normalizeStyle(req.getStyle());
        if (style == null) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), "style must be academic/story/sarcastic");
        }

        int limit = req.getLimit() == null ? 100 : Math.max(1, req.getLimit());
        List<Long> candidateWordIds = findCandidateWordIds(wordType, style, limit);

        for (Long wordId : candidateWordIds) {
            llmAsyncService.generateWordContent(wordId, wordType, style, LlmProvider.LOCAL, null, null);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", "batch_task_" + UUID.randomUUID());
        data.put("queued", candidateWordIds.size());
        return Result.success(data);
    }

    @GetMapping("/review")
    public Result<PageResult<AdminReviewItem>> review(@RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                       @RequestParam(value = "size", required = false, defaultValue = "20") Integer size,
                                                       @RequestParam(value = "status", required = false) String status) {
        int pageNo = page == null || page < 1 ? 1 : page;
        int pageSize = size == null ? 20 : Math.min(Math.max(size, 1), 100);

        Page<WordMeta> p = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<WordMeta> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(WordMeta::getGenStatus, status.trim());
        }
        wrapper.orderByDesc(WordMeta::getUpdatedAt).orderByDesc(WordMeta::getId);

        Page<WordMeta> metaPage = wordMetaMapper.selectPage(p, wrapper);
        List<AdminReviewItem> list = new ArrayList<>();
        for (WordMeta meta : metaPage.getRecords()) {
            AdminReviewItem item = new AdminReviewItem();
            item.setWordId(meta.getWordId());
            item.setWordType(meta.getWordType());
            item.setStyle(meta.getStyle());
            item.setEnglish(loadEnglish(meta.getWordId(), meta.getWordType()));
            item.setGenStatus(meta.getGenStatus());
            item.setSentence(meta.getSentenceEn());
            item.setMnemonic(meta.getMnemonic());
            list.add(item);
        }

        Page<AdminReviewItem> out = new Page<>(metaPage.getCurrent(), metaPage.getSize(), metaPage.getTotal());
        out.setRecords(list);
        return Result.success(PageResult.of(out));
    }

    @PostMapping("/regenerate")
    public Result<Map<String, Object>> regenerate(@Valid @RequestBody RegenerateRequest req) {
        String wordType = normalizeWordType(req.getWordType());
        if (wordType == null) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), "wordType must be cet4 or cet6");
        }

        String style = normalizeStyle(req.getStyle());
        if (style == null) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), "style must be academic/story/sarcastic");
        }

        String promptType = normalizePromptType(req.getPromptType());
        if (promptType == null) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), "promptType must be sentence/synonym/mnemonic/all");
        }

        clearCache(req.getWordId(), wordType, style, promptType);
        llmAsyncService.regenerateWordContent(req.getWordId(), wordType, style, LlmProvider.LOCAL, null, null);

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", "regenerate_task_" + UUID.randomUUID());
        return Result.success(data);
    }


    @GetMapping("/cloud-models")
    public Result<List<CloudModelItem>> listCloudModels() {
        List<CloudModelItem> items = cloudLlmModelService.listAll().stream()
                .map(this::toCloudModelItem)
                .collect(Collectors.toList());
        return Result.success(items);
    }

    @PostMapping("/cloud-models")
    public Result<CloudModelItem> createCloudModel(@Valid @RequestBody CloudModelSaveRequest req) {
        CloudLlmModel saved = cloudLlmModelService.create(
                req.getProvider(),
                req.getModelKey(),
                req.getDisplayName(),
                req.getBaseUrl(),
                req.getPath(),
                req.getProtocol(),
                req.getApiKey(),
                req.getClearApiKey(),
                req.getEnabled(),
                req.getIsDefault()
        );
        return Result.success(toCloudModelItem(saved));
    }

    @PutMapping("/cloud-models/{id}")
    public Result<CloudModelItem> updateCloudModel(@PathVariable("id") Long id,
                                                    @Valid @RequestBody CloudModelSaveRequest req) {
        CloudLlmModel saved = cloudLlmModelService.update(
                id,
                req.getProvider(),
                req.getModelKey(),
                req.getDisplayName(),
                req.getBaseUrl(),
                req.getPath(),
                req.getProtocol(),
                req.getApiKey(),
                req.getClearApiKey(),
                req.getEnabled(),
                req.getIsDefault()
        );
        return Result.success(toCloudModelItem(saved));
    }

    @PutMapping("/cloud-models/{id}/default")
    public Result<CloudModelItem> setDefaultCloudModel(@PathVariable("id") Long id) {
        CloudLlmModel saved = cloudLlmModelService.setDefault(id);
        return Result.success(toCloudModelItem(saved));
    }

    @DeleteMapping("/cloud-models/{id}")
    public Result<Void> deleteCloudModel(@PathVariable("id") Long id) {
        cloudLlmModelService.delete(id);
        return Result.success();
    }

    private CloudModelItem toCloudModelItem(CloudLlmModel item) {
        CloudModelItem out = new CloudModelItem();
        out.setId(item.getId());
        out.setProvider(item.getProvider());
        out.setModelKey(item.getModelKey());
        out.setDisplayName(item.getDisplayName());
        out.setBaseUrl(item.getBaseUrl());
        out.setPath(item.getPath());
        out.setProtocol(item.getProtocol());
        out.setHasApiKey(org.springframework.util.StringUtils.hasText(item.getApiKeyCiphertext()));
        out.setApiKeyMask(item.getApiKeyMask());
        out.setEnabled(Boolean.TRUE.equals(item.getEnabled()));
        out.setIsDefault(Boolean.TRUE.equals(item.getIsDefault()));
        out.setCreatedAt(item.getCreatedAt());
        out.setUpdatedAt(item.getUpdatedAt());
        return out;
    }
    private List<Long> findCandidateWordIds(String wordType, String style, int limit) {
        List<Long> candidates = new ArrayList<>();
        int fetchBatchSize = Math.max(limit * 3, 200);
        int pageNo = 1;

        while (candidates.size() < limit) {
            if ("cet4".equals(wordType)) {
                Page<Cet4Word> page = cet4WordMapper.selectPage(new Page<>(pageNo, fetchBatchSize),
                        new LambdaQueryWrapper<Cet4Word>().orderByAsc(Cet4Word::getId));
                if (page.getRecords().isEmpty()) {
                    break;
                }
                for (Cet4Word word : page.getRecords()) {
                    if (shouldQueue(Long.valueOf(word.getId()), wordType, style)) {
                        candidates.add(Long.valueOf(word.getId()));
                        if (candidates.size() >= limit) {
                            break;
                        }
                    }
                }
                if (page.getCurrent() >= page.getPages()) {
                    break;
                }
            } else {
                Page<Cet6Word> page = cet6WordMapper.selectPage(new Page<>(pageNo, fetchBatchSize),
                        new LambdaQueryWrapper<Cet6Word>().orderByAsc(Cet6Word::getId));
                if (page.getRecords().isEmpty()) {
                    break;
                }
                for (Cet6Word word : page.getRecords()) {
                    if (shouldQueue(Long.valueOf(word.getId()), wordType, style)) {
                        candidates.add(Long.valueOf(word.getId()));
                        if (candidates.size() >= limit) {
                            break;
                        }
                    }
                }
                if (page.getCurrent() >= page.getPages()) {
                    break;
                }
            }
            pageNo++;
        }
        return candidates;
    }

    private boolean shouldQueue(Long wordId, String wordType, String style) {
        WordMeta wordMeta = wordMetaMapper.selectByWordAndStyle(wordId, wordType, style);
        return wordMeta == null || "fallback".equalsIgnoreCase(wordMeta.getGenStatus());
    }

    private String loadEnglish(Long wordId, String wordType) {
        if ("cet4".equalsIgnoreCase(wordType)) {
            Cet4Word word = cet4WordMapper.selectById(wordId);
            return word == null ? null : word.getEnglish();
        }
        if ("cet6".equalsIgnoreCase(wordType)) {
            Cet6Word word = cet6WordMapper.selectById(wordId);
            return word == null ? null : word.getEnglish();
        }
        return null;
    }

    private void clearCache(Long wordId, String wordType, String style, String promptType) {
        if ("all".equals(promptType)) {
            redisTemplate.delete(buildCacheKey(wordId, wordType, "sentence", style));
            redisTemplate.delete(buildCacheKey(wordId, wordType, "synonym", style));
            redisTemplate.delete(buildCacheKey(wordId, wordType, "mnemonic", style));
            return;
        }
        redisTemplate.delete(buildCacheKey(wordId, wordType, promptType, style));
    }

    private String buildCacheKey(Long wordId, String wordType, String promptType, String style) {
        return CACHE_PREFIX + llmCacheService.buildHash(wordId, wordType, promptType, style);
    }

    private String normalizeWordType(String wordType) {
        if (!StringUtils.hasText(wordType)) {
            return null;
        }
        String value = wordType.trim().toLowerCase(Locale.ROOT);
        return ("cet4".equals(value) || "cet6".equals(value)) ? value : null;
    }

    private String normalizeStyle(String style) {
        if (!StringUtils.hasText(style)) {
            return null;
        }
        String value = style.trim().toLowerCase(Locale.ROOT);
        if ("academic".equals(value) || "story".equals(value) || "sarcastic".equals(value)) {
            return value;
        }
        return null;
    }

    private String normalizePromptType(String promptType) {
        if (!StringUtils.hasText(promptType)) {
            return null;
        }
        String value = promptType.trim().toLowerCase(Locale.ROOT);
        if ("sentence".equals(value) || "synonym".equals(value) || "mnemonic".equals(value) || "all".equals(value)) {
            return value;
        }
        return null;
    }

    @Data
    public static class BatchGenerateRequest {
        @NotBlank
        private String wordType;
        @NotBlank
        private String style;
        @Min(1)
        @Max(500)
        private Integer limit;
    }

    @Data
    public static class RegenerateRequest {
        @Min(1)
        private Long wordId;
        @NotBlank
        private String wordType;
        @NotBlank
        private String style;
        @NotBlank
        private String promptType;
    }

    @Data
    public static class CloudModelSaveRequest {
        private String provider;
        @NotBlank
        private String modelKey;
        private String displayName;
        private String baseUrl;
        private String path;
        private String protocol;
        private String apiKey;
        private Boolean clearApiKey;
        private Boolean enabled;
        private Boolean isDefault;
    }

    @Data
    public static class CloudModelItem {
        private Long id;
        private String provider;
        private String modelKey;
        private String displayName;
        private String baseUrl;
        private String path;
        private String protocol;
        private Boolean hasApiKey;
        private String apiKeyMask;
        private Boolean enabled;
        private Boolean isDefault;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
    }
    @Data
    public static class AdminReviewItem {
        private Long wordId;
        private String wordType;
        private String style;
        private String english;
        private String genStatus;
        private String sentence;
        private String mnemonic;
    }
}










