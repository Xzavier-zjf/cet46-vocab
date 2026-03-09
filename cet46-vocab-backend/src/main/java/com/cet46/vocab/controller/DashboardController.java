package com.cet46.vocab.controller;

import com.cet46.vocab.common.Result;
import com.cet46.vocab.common.ResultCode;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private static final String DASHBOARD_OVERVIEW_CACHE_PREFIX = "dashboard:overview:";

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    public DashboardController(JdbcTemplate jdbcTemplate, RedisTemplate<String, Object> redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview(Authentication authentication) {
        Long userId = getUserId(authentication);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        Map<String, Object> data = buildOverviewData(userId);
        return Result.success(data);
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats(@RequestParam(value = "days", required = false, defaultValue = "30") Integer days,
                                                Authentication authentication) {
        Long userId = getUserId(authentication);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }

        int windowDays = days == null ? 30 : Math.max(1, Math.min(days, 365));
        Map<String, Object> data = new HashMap<>();
        data.put("dailyCount", queryDailyCount(userId, windowDays));
        data.put("posDistribution", queryPosDistribution(userId));
        data.put("heatmap", queryHeatmap(userId, 365));
        return Result.success(data);
    }

    private Map<String, Object> buildOverviewData(Long userId) {
        Integer learningCount = queryInt(
                "SELECT COUNT(DISTINCT CONCAT(uwp.word_id, '#', uwp.word_type)) " +
                        "FROM user_word_progress uwp " +
                        "WHERE uwp.user_id = ? " +
                        "AND CONCAT(uwp.word_id, '#', uwp.word_type) NOT IN (" +
                        "  SELECT DISTINCT CONCAT(rl.word_id, '#', rl.word_type) " +
                        "  FROM review_log rl " +
                        "  WHERE rl.user_id = ? AND rl.score = 5" +
                        ")",
                userId,
                userId
        );

        List<Map<String, Object>> userRows = jdbcTemplate.queryForList(
                "SELECT daily_target FROM user WHERE id = ? LIMIT 1",
                userId
        );
        int dailyTarget = 20;
        if (!userRows.isEmpty() && userRows.get(0).get("daily_target") != null) {
            dailyTarget = ((Number) userRows.get(0).get("daily_target")).intValue();
        }

        Integer streakDays = queryInt(
                "SELECT COUNT(DISTINCT DATE(reviewed_at)) FROM review_log WHERE user_id = ? AND reviewed_at >= DATE_SUB(CURRENT_DATE, INTERVAL 365 DAY)",
                userId
        );

        Integer masteredCount = queryInt(
                "SELECT COUNT(DISTINCT CONCAT(rl.word_id, '#', rl.word_type)) " +
                        "FROM review_log rl " +
                        "WHERE rl.user_id = ? AND rl.score = 5",
                userId
        );

        int mastered = masteredCount == null ? 0 : masteredCount;
        int learning = learningCount == null ? 0 : learningCount;
        int totalLearned = learning + mastered;

        int due = learning;
        int target = dailyTarget <= 0 ? 20 : dailyTarget;
        int pressureIndex = BigDecimal.valueOf((double) due * 100 / target)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        boolean pressureAlert = pressureIndex > 150;

        Integer weeklyReviewed = queryInt(
                "SELECT COUNT(1) FROM review_log WHERE user_id = ? AND reviewed_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)",
                userId
        );

        Map<String, Object> data = new HashMap<>();
        data.put("todayDue", due);
        data.put("learningCount", learning);
        data.put("dailyTarget", target);
        data.put("pressureIndex", pressureIndex);
        data.put("streakDays", streakDays == null ? 0 : streakDays);
        data.put("totalLearned", totalLearned);
        data.put("masteredCount", mastered);
        int weeklyDone = weeklyReviewed == null ? 0 : weeklyReviewed;
        data.put("weeklyReport", "\u672C\u5468\u4F60\u5DF2\u5B8C\u6210 " + weeklyDone + " \u6B21\u590D\u4E60\uFF0C\u7D2F\u8BA1\u638C\u63E1 " + mastered + " \u4E2A\u5355\u8BCD\u3002\u7EE7\u7EED\u4FDD\u6301\uFF0C\u6309\u8BA1\u5212\u590D\u4E60\u4F1A\u66F4\u7A33\u3002");
        data.put("pressureAlert", pressureAlert);
        return data;
    }

    private List<Map<String, Object>> queryDailyCount(Long userId, int days) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT DATE(reviewed_at) AS d, COUNT(1) AS c FROM review_log " +
                        "WHERE user_id = ? AND reviewed_at >= DATE_SUB(CURRENT_DATE, INTERVAL ? DAY) " +
                        "GROUP BY DATE(reviewed_at) ORDER BY d ASC",
                userId,
                days
        );
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new HashMap<>();
            Date date = (Date) row.get("d");
            item.put("date", date == null ? null : date.toLocalDate().toString());
            item.put("count", row.get("c") == null ? 0 : ((Number) row.get("c")).intValue());
            data.add(item);
        }
        return data;
    }

    private List<Map<String, Object>> queryPosDistribution(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT COALESCE(NULLIF(pos, ''), 'other') AS p, COUNT(1) AS c FROM word_meta wm " +
                        "JOIN user_word_progress uwp ON uwp.word_id = wm.word_id AND uwp.word_type = wm.word_type " +
                        "WHERE uwp.user_id = ? GROUP BY p ORDER BY c DESC",
                userId
        );
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("pos", row.get("p"));
            item.put("count", row.get("c") == null ? 0 : ((Number) row.get("c")).intValue());
            data.add(item);
        }
        return data;
    }

    private List<Map<String, Object>> queryHeatmap(Long userId, int days) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT DATE(reviewed_at) AS d, COUNT(1) AS c FROM review_log " +
                        "WHERE user_id = ? AND reviewed_at >= DATE_SUB(CURRENT_DATE, INTERVAL ? DAY) " +
                        "GROUP BY DATE(reviewed_at)",
                userId,
                days
        );
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new HashMap<>();
            Date date = (Date) row.get("d");
            item.put("date", date == null ? LocalDate.now().toString() : date.toLocalDate().toString());
            item.put("value", row.get("c") == null ? 0 : ((Number) row.get("c")).intValue());
            data.add(item);
        }
        return data;
    }

    private Integer queryInt(String sql, Object... args) {
        Integer val = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return val == null ? 0 : val;
    }

    private Long getUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        return Long.valueOf(authentication.getPrincipal().toString());
    }
}

