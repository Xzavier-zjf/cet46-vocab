package com.cet46.vocab.controller;

import com.cet46.vocab.common.Result;
import com.cet46.vocab.dto.request.ReviewSubmitRequest;
import com.cet46.vocab.dto.response.ReviewCardResponse;
import com.cet46.vocab.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/review")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/today")
    public Result<Map<String, Object>> getTodayReview(Authentication authentication) {
        Long userId = getUserId(authentication);
        List<ReviewCardResponse> list = reviewService.getTodayReviewList(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("total", list.size());
        data.put("list", list);
        return Result.success(data);
    }

    @PostMapping("/submit")
    public Result<ReviewService.SM2UpdateResult> submitReview(@Valid @RequestBody ReviewSubmitRequest req,
                                                              Authentication authentication) {
        Long userId = getUserId(authentication);
        return Result.success(reviewService.submitReview(userId, req));
    }

    @GetMapping("/session/progress")
    public Result<ReviewService.SessionProgress> getSessionProgress(Authentication authentication) {
        Long userId = getUserId(authentication);
        return Result.success(reviewService.getSessionProgress(userId));
    }

    private Long getUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("\u672A\u767B\u5F55");
        }
        return Long.valueOf(authentication.getPrincipal().toString());
    }
}
