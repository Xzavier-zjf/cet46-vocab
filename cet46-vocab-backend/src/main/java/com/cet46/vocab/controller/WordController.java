package com.cet46.vocab.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cet46.vocab.common.PageResult;
import com.cet46.vocab.common.Result;
import com.cet46.vocab.common.ResultCode;
import com.cet46.vocab.common.WordType;
import com.cet46.vocab.config.CloudLlmProperties;
import com.cet46.vocab.dto.request.AddWordRequest;
import com.cet46.vocab.dto.request.WordListQuery;
import com.cet46.vocab.dto.response.WordDetailResponse;
import com.cet46.vocab.dto.response.WordListItem;
import com.cet46.vocab.dto.response.WordProgressStatusResponse;
import com.cet46.vocab.entity.User;
import com.cet46.vocab.entity.WordMeta;
import com.cet46.vocab.llm.CloudLlmRuntimeConfig;
import com.cet46.vocab.llm.LlmAsyncService;
import com.cet46.vocab.llm.LlmProvider;
import com.cet46.vocab.llm.CloudLlmRuntimeConfigResolver;
import com.cet46.vocab.mapper.UserMapper;
import com.cet46.vocab.mapper.WordMetaMapper;
import com.cet46.vocab.service.CloudLlmModelService;
import com.cet46.vocab.service.WordService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/word")
public class WordController {

    private final WordService wordService;
    private final LlmAsyncService llmAsyncService;
    private final UserMapper userMapper;
    private final WordMetaMapper wordMetaMapper;
    private final CloudLlmProperties cloudLlmProperties;
    private final CloudLlmModelService cloudLlmModelService;
    private final CloudLlmRuntimeConfigResolver cloudLlmRuntimeConfigResolver;

    public WordController(WordService wordService,
                          LlmAsyncService llmAsyncService,
                          UserMapper userMapper,
                          WordMetaMapper wordMetaMapper,
                          CloudLlmProperties cloudLlmProperties,
                          CloudLlmModelService cloudLlmModelService,
                          CloudLlmRuntimeConfigResolver cloudLlmRuntimeConfigResolver) {
        this.wordService = wordService;
        this.llmAsyncService = llmAsyncService;
        this.userMapper = userMapper;
        this.wordMetaMapper = wordMetaMapper;
        this.cloudLlmProperties = cloudLlmProperties;        this.cloudLlmModelService = cloudLlmModelService;
        this.cloudLlmRuntimeConfigResolver = cloudLlmRuntimeConfigResolver;
    }

    @GetMapping("/list")
    public Result<PageResult<WordListItem>> getWordList(@RequestParam("type") String type,
                                                         @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                         @RequestParam(value = "size", required = false, defaultValue = "10") Integer size,
                                                         @RequestParam(value = "keyword", required = false) String keyword,
                                                         @RequestParam(value = "pos", required = false) String pos,
                                                         Authentication authentication) {
        Long userId = getUserId(authentication);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }

        WordType wordType = WordType.from(type);
        if (wordType == null) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), WordType.supportedHint());
        }

        WordListQuery query = new WordListQuery();
        query.setType(wordType.code());
        query.setPage(page);
        query.setSize(size);
        query.setKeyword(keyword);
        query.setPos(pos);
        return Result.success(wordService.getWordList(query, userId));
    }

    @GetMapping("/detail")
    public Result<WordDetailResponse> getWordDetail(@RequestParam("wordId") Long wordId,
                                                     @RequestParam("wordType") String wordType,
                                                     Authentication authentication) {
        Long userId = getUserId(authentication);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        WordType normalized = WordType.from(wordType);
        if (normalized == null) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), WordType.supportedHint());
        }
        return Result.success(wordService.getWordDetail(wordId, normalized.code(), userId));
    }

    @PostMapping("/llm/generate")
    public Result<Map<String, Object>> generate(@Valid @RequestBody AddWordRequest req,
                                                 Authentication authentication) {
        Long userId = getUserId(authentication);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        WordType wordType = WordType.from(req.getWordType());
        if (wordType == null) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), WordType.supportedHint());
        }
        String taskId = "llm_task_" + UUID.randomUUID();
        String style = resolveUserStyle(userId);
        String provider = resolveUserProvider(userId);
        String localModel = resolveUserLocalModel(userId);
        String cloudModel = resolveUserCloudModel(userId);
        if (isCloudUnavailable(userId, provider, cloudModel)) {
            return Result.fail(ResultCode.LLM_ERROR.getCode(), "\u4e91\u7aef API \u6682\u4e0d\u53ef\u7528\uff0c\u8bf7\u914d\u7f6e llm.cloud.api-key");
        }
        llmAsyncService.markWordContentPending(req.getWordId(), wordType.code(), style, true);
        wordService.invalidateWordDetailCache(userId, req.getWordId(), wordType.code());        llmAsyncService.regenerateWordContent(req.getWordId(), wordType.code(), style, provider, localModel, cloudModel, userId);
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("status", "pending");
        data.put("style", style);
        data.put("provider", provider);
        return Result.success(data);
    }

    @PostMapping("/llm/generate-explain")
    public Result<Map<String, Object>> generateExplain(@Valid @RequestBody AddWordRequest req,
                                                        Authentication authentication) {
        Long userId = getUserId(authentication);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        WordType wordType = WordType.from(req.getWordType());
        if (wordType == null) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), WordType.supportedHint());
        }
        String style = resolveUserStyle(userId);
        String provider = resolveUserProvider(userId);
        String localModel = resolveUserLocalModel(userId);
        String cloudModel = resolveUserCloudModel(userId);
        if (isCloudUnavailable(userId, provider, cloudModel)) {
            return Result.fail(ResultCode.LLM_ERROR.getCode(), "\u4e91\u7aef API \u6682\u4e0d\u53ef\u7528\uff0c\u8bf7\u914d\u7f6e llm.cloud.api-key");
        }
        llmAsyncService.markWordExplainPending(req.getWordId(), wordType.code(), style, true);
        wordService.invalidateWordDetailCache(userId, req.getWordId(), wordType.code());        llmAsyncService.regenerateWordExplainContent(req.getWordId(), wordType.code(), style, provider, localModel, cloudModel, userId);
        Map<String, Object> data = new HashMap<>();
        data.put("status", "pending");
        data.put("style", style);
        data.put("provider", provider);
        return Result.success(data);
    }

    @PostMapping("/llm/retry-pending")
    public Result<Map<String, Object>> retryPending(@RequestParam("wordType") String wordType,
                                                     @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit,
                                                     Authentication authentication) {
        Long userId = getUserId(authentication);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        WordType normalizedWordType = WordType.from(wordType);
        if (normalizedWordType == null) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), WordType.supportedHint());
        }

        int batchLimit = limit == null ? 20 : Math.min(Math.max(limit, 1), 200);
        String style = resolveUserStyle(userId);
        String provider = resolveUserProvider(userId);
        String localModel = resolveUserLocalModel(userId);
        String cloudModel = resolveUserCloudModel(userId);
        if (isCloudUnavailable(userId, provider, cloudModel)) {
            return Result.fail(ResultCode.LLM_ERROR.getCode(), "\u4e91\u7aef API \u6682\u4e0d\u53ef\u7528\uff0c\u8bf7\u914d\u7f6e llm.cloud.api-key");
        }

        LambdaQueryWrapper<WordMeta> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WordMeta::getStyle, style)
                .eq(WordMeta::getWordType, normalizedWordType.code())
                .and(w -> w.eq(WordMeta::getGenStatus, "pending")
                        .or()
                        .eq(WordMeta::getGenStatus, "partial")
                        .or()
                        .eq(WordMeta::getGenStatus, "fallback")
                        .or()
                        .eq(WordMeta::getAiExplainStatus, "pending")
                        .or()
                        .eq(WordMeta::getAiExplainStatus, "fallback"))
                .orderByAsc(WordMeta::getUpdatedAt)
                .last("LIMIT " + batchLimit);
        List<WordMeta> pendingMetas = wordMetaMapper.selectList(wrapper);
        Set<Long> pendingWordIds = new LinkedHashSet<>();
        if (pendingMetas != null) {
            for (WordMeta pendingMeta : pendingMetas) {
                if (pendingMeta != null && pendingMeta.getWordId() != null) {
                    pendingWordIds.add(pendingMeta.getWordId());
                }
            }
        }
        for (Long pendingWordId : pendingWordIds) {
            llmAsyncService.markWordContentPending(pendingWordId, normalizedWordType.code(), style, false);
            llmAsyncService.markWordExplainPending(pendingWordId, normalizedWordType.code(), style, false);
            wordService.invalidateWordDetailCache(userId, pendingWordId, normalizedWordType.code());            llmAsyncService.regenerateWordContent(pendingWordId, normalizedWordType.code(), style, provider, localModel, cloudModel, userId);
            llmAsyncService.regenerateWordExplainContent(pendingWordId, normalizedWordType.code(), style, provider, localModel, cloudModel, userId);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("queued", pendingWordIds.size());
        data.put("wordType", normalizedWordType.code());
        data.put("style", style);
        data.put("provider", provider);
        return Result.success(data);
    }

    @PostMapping("/learn/add")
    public Result<Void> addWordToLearn(@Valid @RequestBody AddWordRequest req,
                                        Authentication authentication) {
        Long userId = getUserId(authentication);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        WordType wordType = WordType.from(req.getWordType());
        if (wordType == null) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), WordType.supportedHint());
        }
        wordService.addWordToLearn(req.getWordId(), wordType.code(), userId);
        return Result.success();
    }

    @GetMapping("/progress/status")
    public Result<WordProgressStatusResponse> getProgressStatus(@RequestParam("wordId") Long wordId,
                                                                 @RequestParam("wordType") String wordType,
                                                                 Authentication authentication) {
        Long userId = getUserId(authentication);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        WordType normalized = WordType.from(wordType);
        if (normalized == null) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), WordType.supportedHint());
        }
        return Result.success(wordService.getProgressStatus(wordId, normalized.code(), userId));
    }

    private Long getUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        try {
            return Long.valueOf(authentication.getPrincipal().toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveUserStyle(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getLlmStyle())) {
            return "story";
        }
        return user.getLlmStyle();
    }

    private String resolveUserProvider(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return LlmProvider.LOCAL;
        }
        return LlmProvider.normalize(user.getLlmProvider());
    }

    private String resolveUserLocalModel(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getLlmLocalModel())) {
            return null;
        }
        return user.getLlmLocalModel().trim();
    }
    private String resolveUserCloudModel(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return cloudLlmModelService.resolveDefaultModelForUser(userId);
        }
        return cloudLlmModelService.resolveSelectedModelForUser(user.getLlmCloudModel(), userId);
    }

    private boolean isCloudUnavailable(Long userId, String provider, String cloudModel) {
        if (!LlmProvider.CLOUD.equals(LlmProvider.normalize(provider))) {
            return false;
        }
        return !cloudLlmRuntimeConfigResolver.isAvailable(userId, cloudModel);
    }
}








