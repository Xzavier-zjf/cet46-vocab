package com.cet46.vocab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cet46.vocab.algorithm.SM2Algorithm;
import com.cet46.vocab.common.WordType;
import com.cet46.vocab.dto.request.ReviewSubmitRequest;
import com.cet46.vocab.dto.response.ReviewCardResponse;
import com.cet46.vocab.entity.Cet4Word;
import com.cet46.vocab.entity.Cet6Word;
import com.cet46.vocab.entity.ReviewLog;
import com.cet46.vocab.entity.User;
import com.cet46.vocab.entity.UserWordProgress;
import com.cet46.vocab.entity.WordMeta;
import com.cet46.vocab.mapper.Cet4WordMapper;
import com.cet46.vocab.mapper.Cet6WordMapper;
import com.cet46.vocab.mapper.ReviewLogMapper;
import com.cet46.vocab.mapper.UserMapper;
import com.cet46.vocab.mapper.UserWordProgressMapper;
import com.cet46.vocab.mapper.WordMetaMapper;
import com.cet46.vocab.service.ReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private static final Logger log = LoggerFactory.getLogger(ReviewServiceImpl.class);
    private static final int IMMEDIATE_REVIEW_LIMIT = 50;

    private final UserWordProgressMapper userWordProgressMapper;
    private final ReviewLogMapper reviewLogMapper;
    private final Cet4WordMapper cet4WordMapper;
    private final Cet6WordMapper cet6WordMapper;
    private final WordMetaMapper wordMetaMapper;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    public ReviewServiceImpl(UserWordProgressMapper userWordProgressMapper,
                             ReviewLogMapper reviewLogMapper,
                             Cet4WordMapper cet4WordMapper,
                             Cet6WordMapper cet6WordMapper,
                             WordMetaMapper wordMetaMapper,
                             UserMapper userMapper,
                             RedisTemplate<String, Object> redisTemplate,
                             JdbcTemplate jdbcTemplate) {
        this.userWordProgressMapper = userWordProgressMapper;
        this.reviewLogMapper = reviewLogMapper;
        this.cet4WordMapper = cet4WordMapper;
        this.cet6WordMapper = cet6WordMapper;
        this.wordMetaMapper = wordMetaMapper;
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ReviewCardResponse> getTodayReviewList(Long userId) {
        List<UserWordProgress> progresses = loadReviewQueue(userId);
        String style = resolveUserStyle(userId);
        List<ReviewCardResponse> list = new ArrayList<>();
        for (UserWordProgress progress : progresses) {
            WordBase wordBase = loadWordBase(progress.getWordId(), progress.getWordType());
            if (wordBase == null) {
                continue;
            }
            WordMeta wordMeta = selectWordMetaSafely(progress.getWordId(), progress.getWordType(), style);
            list.add(ReviewCardResponse.builder()
                    .wordId(progress.getWordId())
                    .wordType(progress.getWordType())
                    .english(wordBase.english)
                    .phonetic(wordBase.phonetic)
                    .chinese(wordBase.chinese)
                    .pos(wordMeta == null ? null : wordMeta.getPos())
                    .sentenceEn(wordMeta == null ? null : wordMeta.getSentenceEn())
                    .sentenceZh(wordMeta == null ? null : wordMeta.getSentenceZh())
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
        jdbcTemplate.update(
                "UPDATE user_word_progress SET easiness = ?, `interval` = ?, repetition = ?, next_review_date = ? WHERE id = ?",
                progress.getEasiness(),
                progress.getInterval(),
                progress.getRepetition(),
                progress.getNextReviewDate(),
                progress.getId()
        );

        ReviewLog reviewLog = ReviewLog.builder()
                .userId(userId)
                .wordId(req.getWordId())
                .wordType(req.getWordType())
                .score(req.getScore())
                .timeSpentMs(req.getTimeSpentMs())
                .reviewedAt(LocalDateTime.now())
                .build();
        reviewLogMapper.insert(reviewLog);
        upsertDailyPlanCacheAfterReview(userId);

        redisTemplate.delete(DASHBOARD_OVERVIEW_CACHE_PREFIX + userId);

        return new SM2UpdateResult(result.interval(), result.easiness(), result.nextReviewDate());
    }

    @Override
    public SessionProgress getSessionProgress(Long userId) {
        int totalToday = loadReviewQueue(userId).size();
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        Long reviewedLong;
        try {
            reviewedLong = reviewLogMapper.selectCount(
                    new LambdaQueryWrapper<ReviewLog>()
                            .eq(ReviewLog::getUserId, userId)
                            .ge(ReviewLog::getReviewedAt, start)
                            .lt(ReviewLog::getReviewedAt, end)
            );
        } catch (DataAccessException ex) {
            log.warn("failed to query reviewed count for user {}", userId, ex);
            return new SessionProgress(totalToday, 0, totalToday);
        }
        int reviewed = reviewedLong == null ? 0 : reviewedLong.intValue();
        int remaining = Math.max(totalToday - reviewed, 0);
        return new SessionProgress(totalToday, reviewed, remaining);
    }

    private WordBase loadWordBase(Long wordId, String wordType) {
        if (!StringUtils.hasText(wordType) || wordId == null) {
            return null;
        }
        WordType type = WordType.from(wordType);
        if (type == null) {
            return null;
        }
        if (type == WordType.CET4) {
            Cet4Word word = cet4WordMapper.selectById(wordId);
            if (word == null) {
                return null;
            }
            return new WordBase(word.getEnglish(), word.getSent(), word.getChinese());
        }
        if (type == WordType.CET6) {
            Cet6Word word = cet6WordMapper.selectById(wordId);
            if (word == null) {
                return null;
            }
            return new WordBase(word.getEnglish(), word.getSent(), word.getChinese());
        }

        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT english, sent, chinese FROM " + type.tableName() + " WHERE id = ? LIMIT 1",
                wordId
        );
        if (rows.isEmpty()) {
            return null;
        }
        java.util.Map<String, Object> row = rows.get(0);
        return new WordBase(
                row.get("english") == null ? "" : String.valueOf(row.get("english")),
                row.get("sent") == null ? "" : String.valueOf(row.get("sent")),
                row.get("chinese") == null ? "" : String.valueOf(row.get("chinese"))
        );
    }
    private List<UserWordProgress> loadReviewQueue(Long userId) {
        try {
            List<UserWordProgress> dueToday = userWordProgressMapper.selectTodayReview(userId);
            if (!dueToday.isEmpty()) {
                return dueToday;
            }
            return userWordProgressMapper.selectImmediateReview(userId, IMMEDIATE_REVIEW_LIMIT);
        } catch (DataAccessException ex) {
            log.warn("failed to load review queue for user {}", userId, ex);
            return List.of();
        }
    }

    private WordMeta selectWordMetaSafely(Long wordId, String wordType, String style) {
        try {
            return wordMetaMapper.selectByWordAndStyle(wordId, wordType, style);
        } catch (DataAccessException ex) {
            log.warn("failed to query word_meta for wordId={}, wordType={}, style={}", wordId, wordType, style, ex);
            return null;
        }
    }

    private String resolveUserStyle(Long userId) {
        try {
            User user = userMapper.selectById(userId);
            if (user == null || !StringUtils.hasText(user.getLlmStyle())) {
                return "story";
            }
            return user.getLlmStyle();
        } catch (DataAccessException ex) {
            log.warn("failed to query user style for userId={}", userId, ex);
            return "story";
        }
    }

    private void upsertDailyPlanCacheAfterReview(Long userId) {
        try {
            LocalDate today = LocalDate.now();
            Integer dueCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM user_word_progress WHERE user_id = ? AND next_review_date <= ?",
                    Integer.class,
                    userId,
                    today
            );
            Integer reviewedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM review_log WHERE user_id = ? AND DATE(reviewed_at) = ?",
                    Integer.class,
                    userId,
                    today
            );
            Integer target = jdbcTemplate.queryForObject(
                    "SELECT daily_target FROM user WHERE id = ? LIMIT 1",
                    Integer.class,
                    userId
            );
            int due = dueCount == null ? 0 : Math.max(dueCount, 0);
            int reviewed = reviewedCount == null ? 0 : Math.max(reviewedCount, 0);
            int dailyTarget = (target == null || target <= 0) ? 20 : target;

            jdbcTemplate.update(
                    "INSERT INTO daily_plan_cache (user_id, plan_date, due_count, reviewed_count, daily_target, completion_rate, generated_at, last_synced_at) " +
                            "VALUES (?, ?, ?, ?, ?, CASE WHEN ? <= 0 THEN 0 ELSE LEAST(100, ROUND(? * 100.0 / ?, 2)) END, NOW(), NOW()) " +
                            "ON DUPLICATE KEY UPDATE " +
                            "reviewed_count = VALUES(reviewed_count), " +
                            "daily_target = VALUES(daily_target), " +
                            "completion_rate = CASE WHEN due_count <= 0 THEN 0 ELSE LEAST(100, ROUND(VALUES(reviewed_count) * 100.0 / due_count, 2)) END, " +
                            "last_synced_at = NOW()",
                    userId,
                    today,
                    due,
                    reviewed,
                    dailyTarget,
                    due,
                    reviewed,
                    due
            );
        } catch (DataAccessException ex) {
            log.warn("upsert daily plan cache failed for user {}", userId, ex);
        }
    }

    private record WordBase(String english, String phonetic, String chinese) {
    }
}


