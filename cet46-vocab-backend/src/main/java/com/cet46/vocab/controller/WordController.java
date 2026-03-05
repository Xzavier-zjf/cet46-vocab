package com.cet46.vocab.controller;

import com.cet46.vocab.common.PageResult;
import com.cet46.vocab.common.Result;
import com.cet46.vocab.common.ResultCode;
import com.cet46.vocab.dto.request.AddWordRequest;
import com.cet46.vocab.dto.request.WordListQuery;
import com.cet46.vocab.dto.response.WordDetailResponse;
import com.cet46.vocab.dto.response.WordListItem;
import com.cet46.vocab.llm.LlmAsyncService;
import com.cet46.vocab.service.WordService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/word")
public class WordController {

    private final WordService wordService;
    private final LlmAsyncService llmAsyncService;

    public WordController(WordService wordService, LlmAsyncService llmAsyncService) {
        this.wordService = wordService;
        this.llmAsyncService = llmAsyncService;
    }

    @GetMapping("/list")
    public Result<PageResult<WordListItem>> getWordList(@RequestParam("type") String type,
                                                        @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                        @RequestParam(value = "size", required = false, defaultValue = "20") Integer size,
                                                        @RequestParam(value = "keyword", required = false) String keyword,
                                                        @RequestParam(value = "pos", required = false) String pos,
                                                        Authentication authentication) {
        Long userId = getUserId(authentication);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }

        WordListQuery query = new WordListQuery();
        query.setType(type);
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
        return Result.success(wordService.getWordDetail(wordId, wordType, userId));
    }

    @PostMapping("/llm/generate")
    public Result<Map<String, Object>> generate(@Valid @RequestBody AddWordRequest req,
                                                Authentication authentication) {
        Long userId = getUserId(authentication);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        String taskId = "llm_task_" + UUID.randomUUID();
        llmAsyncService.generateWordContent(req.getWordId(), req.getWordType(), "story");
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("status", "pending");
        return Result.success(data);
    }

    @PostMapping("/learn/add")
    public Result<Void> addWordToLearn(@Valid @RequestBody AddWordRequest req,
                                       Authentication authentication) {
        Long userId = getUserId(authentication);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        wordService.addWordToLearn(req.getWordId(), req.getWordType(), userId);
        return Result.success();
    }

    private Long getUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        return Long.valueOf(authentication.getPrincipal().toString());
    }
}
