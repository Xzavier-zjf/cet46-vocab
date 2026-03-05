package com.cet46.vocab.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DailyPlanScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyPlanScheduler.class);
    private static final int BATCH_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public DailyPlanScheduler(JdbcTemplate jdbcTemplate,
                              RedisTemplate<String, Object> redisTemplate,
                              ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void generateDailyPlan() {
        LocalDate today = LocalDate.now();
        LocalDateTime generatedAt = today.atStartOfDay();
        log.info("daily plan scheduler started, date={}", today);

        try {
            List<Long> userIds = jdbcTemplate.query(
                    "SELECT DISTINCT user_id FROM user_word_progress",
                    (rs, rowNum) -> rs.getLong("user_id")
            );

            if (userIds.isEmpty()) {
                log.info("daily plan scheduler finished, no users with learning records");
                return;
            }

            for (int i = 0; i < userIds.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, userIds.size());
                List<Long> batch = userIds.subList(i, end);
                log.info("processing batch {}/{} (size={})",
                        (i / BATCH_SIZE) + 1,
                        (int) Math.ceil(userIds.size() / (double) BATCH_SIZE),
                        batch.size());

                for (Long userId : batch) {
                    try {
                        Long dueCount = jdbcTemplate.queryForObject(
                                "SELECT COUNT(1) FROM user_word_progress WHERE user_id = ? AND next_review_date <= ?",
                                Long.class,
                                userId,
                                today
                        );
                        long count = dueCount == null ? 0L : dueCount;

                        String key = "plan:daily:" + userId + ":" + today;
                        String value = buildValue(count, generatedAt);
                        redisTemplate.opsForValue().set(key, value, Duration.ofDays(1));
                    } catch (Exception ex) {
                        log.error("generate daily plan failed for userId={}", userId, ex);
                    }
                }
            }

            log.info("daily plan scheduler finished, total users={}", userIds.size());
        } catch (Exception ex) {
            log.error("daily plan scheduler failed", ex);
        }
    }

    private String buildValue(long dueCount, LocalDateTime generatedAt) throws JsonProcessingException {
        Map<String, Object> value = new HashMap<>();
        value.put("dueCount", dueCount);
        value.put("generatedAt", generatedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return objectMapper.writeValueAsString(value);
    }
}
