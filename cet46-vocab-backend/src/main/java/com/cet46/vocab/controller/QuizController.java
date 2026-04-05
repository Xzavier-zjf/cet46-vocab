package com.cet46.vocab.controller;

import com.cet46.vocab.common.Result;
import com.cet46.vocab.common.ResultCode;
import com.cet46.vocab.dto.request.QuizGenerateRequest;
import com.cet46.vocab.dto.request.QuizSubmitRequest;
import com.cet46.vocab.service.QuizService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/quiz")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@Valid @RequestBody QuizGenerateRequest req,
                                                Authentication authentication) {
        Long userId = getUserId(authentication);
        return Result.success(quizService.generateQuiz(userId, req));
    }

    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@Valid @RequestBody QuizSubmitRequest req,
                                              Authentication authentication) {
        Long userId = getUserId(authentication);
        return Result.success(quizService.submitQuiz(userId, req));
    }

    @GetMapping("/history")
    public Result<List<Map<String, Object>>> history(@RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit,
                                                     Authentication authentication) {
        Long userId = getUserId(authentication);
        return Result.success(quizService.listQuizHistory(userId, limit));
    }

    @GetMapping("/history/{id}")
    public Result<Map<String, Object>> historyDetail(@PathVariable("id") Long id,
                                                     Authentication authentication) {
        Long userId = getUserId(authentication);
        return Result.success(quizService.getQuizHistoryDetail(userId, id));
    }

    private Long getUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalArgumentException(ResultCode.UNAUTHORIZED.getMessage());
        }
        return Long.valueOf(authentication.getPrincipal().toString());
    }
}
