package com.cet46.vocab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cet46.vocab.algorithm.SM2Algorithm;
import com.cet46.vocab.dto.request.ReviewSubmitRequest;
import com.cet46.vocab.dto.response.ReviewCardResponse;
import com.cet46.vocab.entity.Cet4Word;
import com.cet46.vocab.entity.Cet6Word;
import com.cet46.vocab.entity.ReviewLog;
import com.cet46.vocab.entity.UserWordProgress;
import com.cet46.vocab.mapper.Cet4WordMapper;
import com.cet46.vocab.mapper.Cet6WordMapper;
import com.cet46.vocab.mapper.ReviewLogMapper;
import com.cet46.vocab.mapper.UserWordProgressMapper;
import com.cet46.vocab.service.ReviewService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReviewServiceImpl implements ReviewService {

    private static final String DASHBOARD_OVERVIEW_CACHE_PREFIX = "dashboard:overview:";

    private final UserWordProgressMapper userWordProgressMapper;
    private final ReviewLogMapper reviewLogMapper;
    private final Cet4WordMapper cet4WordMapper;
    private final Cet6WordMapper cet6WordMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public ReviewServiceImpl(UserWordProgressMapper userWordProgressMapper,
                             ReviewLogMapper reviewLogMapper,
                             Cet4WordMapper cet4WordMapper,
                             Cet6WordMapper cet6WordMapper,
                             RedisTemplate<String, Object> redisTemplate) {
        this.userWordProgressMapper = userWordProgressMapper;
        this.reviewLogMapper = reviewLogMapper;
        this.cet4WordMapper = cet4WordMapper;
        this.cet6WordMapper = cet6WordMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<ReviewCardResponse> getTodayReviewList(Long userId) {
        List<UserWordProgress> progresses = userWordProgressMapper.selectTodayReview(userId);
        List<ReviewCardResponse> list = new ArrayList<>();
        for (UserWordProgress progress : progresses) {
            WordBase wordBase = loadWordBase(progress.getWordId(), progress.getWordType());
            if (wordBase == null) {
                continue;
            }
            list.add(ReviewCardResponse.builder()
                    .wordId(progress.getWordId())
                    .wordType(progress.getWordType())
                    .english(wordBase.english)
                    .phonetic(wordBase.phonetic)
                    .chinese(wordBase.chinese)
                    .easiness(progress.getEasiness())
                    .interval(progress.getInterval())
                    .repetition(progress.getRepetition())
                    .build());
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SM2UpdateResult submitReview(Long userId, ReviewSubmitRequest req) {
        UserWordProgress progress = userWordProgressMapper.selectOne(
                new LambdaQueryWrapper<UserWordProgress>()
                        .eq(UserWordProgress::getUserId, userId)
                        .eq(UserWordProgress::getWordId, req.getWordId())
                        .eq(UserWordProgress::getWordType, req.getWordType())
                        .last("LIMIT 1")
        );
        if (progress == null) {
            throw new RuntimeException("learning progress not found");
        }

        double easiness = progress.getEasiness() == null ? 2.5 : progress.getEasiness();
        int interval = progress.getInterval() == null ? 1 : progress.getInterval();
        int repetition = progress.getRepetition() == null ? 0 : progress.getRepetition();

        SM2Algorithm.SM2Result result = SM2Algorithm.calculate(req.getScore(), easiness, interval, repetition);

        progress.setEasiness(result.easiness());
        progress.setInterval(result.interval());
        progress.setRepetition(result.repetition());
        progress.setNextReviewDate(result.nextReviewDate());
        userWordProgressMapper.updateById(progress);

        ReviewLog reviewLog = ReviewLog.builder()
                .userId(userId)
                .wordId(req.getWordId())
                .wordType(req.getWordType())
                .score(req.getScore())
                .timeSpentMs(req.getTimeSpentMs())
                .reviewedAt(LocalDateTime.now())
                .build();
        reviewLogMapper.insert(reviewLog);

        redisTemplate.delete(DASHBOARD_OVERVIEW_CACHE_PREFIX + userId);

        return new SM2UpdateResult(result.interval(), result.easiness(), result.nextReviewDate());
    }

    @Override
    public SessionProgress getSessionProgress(Long userId) {
        int totalToday = userWordProgressMapper.selectTodayReview(userId).size();
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        Long reviewedLong = reviewLogMapper.selectCount(
                new LambdaQueryWrapper<ReviewLog>()
                        .eq(ReviewLog::getUserId, userId)
                        .ge(ReviewLog::getReviewedAt, start)
                        .lt(ReviewLog::getReviewedAt, end)
        );
        int reviewed = reviewedLong == null ? 0 : reviewedLong.intValue();
        int remaining = Math.max(totalToday - reviewed, 0);
        return new SessionProgress(totalToday, reviewed, remaining);
    }

    private WordBase loadWordBase(Long wordId, String wordType) {
        if (!StringUtils.hasText(wordType) || wordId == null) {
            return null;
        }
        if ("cet4".equalsIgnoreCase(wordType)) {
            Cet4Word word = cet4WordMapper.selectById(wordId);
            if (word == null) {
                return null;
            }
            return new WordBase(word.getEnglish(), word.getSent(), word.getChinese());
        }
        if ("cet6".equalsIgnoreCase(wordType)) {
            Cet6Word word = cet6WordMapper.selectById(wordId);
            if (word == null) {
                return null;
            }
            return new WordBase(word.getEnglish(), word.getSent(), word.getChinese());
        }
        return null;
    }

    private record WordBase(String english, String phonetic, String chinese) {
    }
}
