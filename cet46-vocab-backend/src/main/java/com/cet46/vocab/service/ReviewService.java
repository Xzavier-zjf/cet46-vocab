package com.cet46.vocab.service;

import com.cet46.vocab.dto.request.ReviewSubmitRequest;
import com.cet46.vocab.dto.response.ReviewCardResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

public interface ReviewService {
    List<ReviewCardResponse> getTodayReviewList(Long userId);

    SM2UpdateResult submitReview(Long userId, ReviewSubmitRequest req);

    SessionProgress getSessionProgress(Long userId);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class SM2UpdateResult {
        private Integer newInterval;
        private Double newEasiness;
        private LocalDate nextReviewDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class SessionProgress {
        private Integer totalToday;
        private Integer reviewed;
        private Integer remaining;
    }
}
