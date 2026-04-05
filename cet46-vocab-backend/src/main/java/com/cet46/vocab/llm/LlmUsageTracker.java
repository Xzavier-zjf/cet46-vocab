package com.cet46.vocab.llm;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class LlmUsageTracker {

    private static final String KEY_PREFIX = "llm:last-used:";
    private static final String CLOUD_USAGE_PREFIX = "llm:usage:day:";
    private static final String SCOPE_PUBLIC = "public";
    private static final String SCOPE_PRIVATE = "private";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate stringRedisTemplate;
    private final JdbcTemplate jdbcTemplate;

    public LlmUsageTracker(StringRedisTemplate stringRedisTemplate,
                           JdbcTemplate jdbcTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(Long userId, String provider, String model, String source) {
        record(userId, provider, model, source, null);
    }

    public void record(Long userId, String provider, String model, String source, String runtimeSource) {
        if (userId == null) {
            return;
        }
        String normalizedProvider = normalize(provider);
        String normalizedModel = normalize(model);
        String normalizedSource = normalize(source);
        String normalizedRuntimeSource = normalize(runtimeSource);

        String key = KEY_PREFIX + userId;
        stringRedisTemplate.opsForHash().put(key, "provider", normalizedProvider);
        stringRedisTemplate.opsForHash().put(key, "model", normalizedModel);
        stringRedisTemplate.opsForHash().put(key, "source", normalizedSource);
        stringRedisTemplate.opsForHash().put(key, "runtimeSource", normalizedRuntimeSource);
        stringRedisTemplate.opsForHash().put(key, "updatedAt", String.valueOf(System.currentTimeMillis()));
        stringRedisTemplate.expire(key, Duration.ofDays(7));

        if (!LlmProvider.CLOUD.equals(normalizedProvider) || !StringUtils.hasText(normalizedModel)) {
            return;
        }

        long now = System.currentTimeMillis();
        String scope = resolveScope(normalizedRuntimeSource);
        String day = LocalDate.now(ZoneId.systemDefault()).format(DATE_FORMATTER);
        String usageKey = CLOUD_USAGE_PREFIX + day
                + ":user:" + userId
                + ":scope:" + scope
                + ":provider:" + encodeKeyPart(normalizedProvider)
                + ":model:" + encodeKeyPart(normalizedModel);
        stringRedisTemplate.opsForHash().increment(usageKey, "calls", 1L);
        stringRedisTemplate.opsForHash().put(usageKey, "date", day);
        stringRedisTemplate.opsForHash().put(usageKey, "userId", String.valueOf(userId));
        stringRedisTemplate.opsForHash().put(usageKey, "scope", scope);
        stringRedisTemplate.opsForHash().put(usageKey, "provider", normalizedProvider);
        stringRedisTemplate.opsForHash().put(usageKey, "model", normalizedModel);
        stringRedisTemplate.opsForHash().put(usageKey, "source", normalizedSource);
        stringRedisTemplate.opsForHash().put(usageKey, "runtimeSource", normalizedRuntimeSource);
        stringRedisTemplate.opsForHash().put(usageKey, "lastUsedAt", String.valueOf(now));
        stringRedisTemplate.expire(usageKey, Duration.ofDays(90));
        persistDailyUsage(day, userId, scope, normalizedProvider, normalizedModel, normalizedSource, normalizedRuntimeSource, now);
    }

    public UsageSnapshot get(Long userId) {
        if (userId == null) {
            return new UsageSnapshot("", "", "", "", null);
        }
        String key = KEY_PREFIX + userId;
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(key);
        if (map == null || map.isEmpty()) {
            return new UsageSnapshot("", "", "", "", null);
        }
        String provider = asText(map.get("provider"));
        String model = asText(map.get("model"));
        String source = asText(map.get("source"));
        String runtimeSource = asText(map.get("runtimeSource"));
        Long updatedAt = asLong(map.get("updatedAt"));
        return new UsageSnapshot(provider, model, source, runtimeSource, updatedAt);
    }

    public List<UsageAggregate> listUserCloudUsage(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<UsageAggregate> fromDb = queryUsageFromDb(userId, null);
        if (!fromDb.isEmpty()) {
            return fromDb;
        }
        return loadUsage("*", String.valueOf(userId), "*");
    }

    public List<UsageAggregate> listAllPublicCloudUsage() {
        List<UsageAggregate> fromDb = queryUsageFromDb(null, SCOPE_PUBLIC);
        if (!fromDb.isEmpty()) {
            return fromDb;
        }
        return loadUsage("*", "*", SCOPE_PUBLIC);
    }

    private void persistDailyUsage(String day,
                                   Long userId,
                                   String scope,
                                   String provider,
                                   String modelKey,
                                   String source,
                                   String runtimeSource,
                                   long now) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO llm_usage_daily (date_key, user_id, scope, provider, model_key, source, runtime_source, calls, last_used_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?) " +
                            "ON DUPLICATE KEY UPDATE " +
                            "calls = calls + 1, " +
                            "last_used_at = GREATEST(COALESCE(last_used_at, 0), VALUES(last_used_at)), " +
                            "source = VALUES(source), " +
                            "runtime_source = VALUES(runtime_source)",
                    day,
                    userId,
                    scope,
                    provider,
                    modelKey,
                    source,
                    runtimeSource,
                    now
            );
        } catch (Exception ignored) {
            // DB persistence is best-effort; Redis remains the realtime source fallback.
        }
    }

    private List<UsageAggregate> queryUsageFromDb(Long userId, String scope) {
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT date_key, user_id, scope, provider, model_key, source, runtime_source, calls, last_used_at " +
                            "FROM llm_usage_daily WHERE 1=1"
            );
            List<Object> args = new ArrayList<>();
            if (userId != null) {
                sql.append(" AND user_id = ?");
                args.add(userId);
            }
            if (StringUtils.hasText(scope)) {
                sql.append(" AND scope = ?");
                args.add(scope.trim());
            }
            sql.append(" ORDER BY date_key DESC");
            return jdbcTemplate.query(
                    sql.toString(),
                    (rs, rowNum) -> new UsageAggregate(
                            asText(rs.getString("date_key")),
                            rs.getLong("user_id"),
                            asText(rs.getString("scope")),
                            asText(rs.getString("provider")),
                            asText(rs.getString("model_key")),
                            asText(rs.getString("source")),
                            asText(rs.getString("runtime_source")),
                            rs.getLong("calls"),
                            rs.getLong("last_used_at")
                    ),
                    args.toArray()
            );
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<UsageAggregate> loadUsage(String day, String userId, String scope) {
        String pattern = CLOUD_USAGE_PREFIX + day + ":user:" + userId + ":scope:" + scope + ":provider:*:model:*";
        Set<String> keys = scanKeys(pattern);
        if (keys.isEmpty()) {
            return List.of();
        }
        List<UsageAggregate> result = new ArrayList<>();
        for (String key : keys) {
            Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(key);
            if (map == null || map.isEmpty()) {
                continue;
            }
            result.add(new UsageAggregate(
                    asText(map.get("date")),
                    asLong(map.get("userId")),
                    asText(map.get("scope")),
                    asText(map.get("provider")),
                    asText(map.get("model")),
                    asText(map.get("source")),
                    asText(map.get("runtimeSource")),
                    asLong(map.get("calls")),
                    asLong(map.get("lastUsedAt"))
            ));
        }
        return result;
    }

    private Set<String> scanKeys(String pattern) {
        return stringRedisTemplate.execute((RedisConnection connection) -> {
            if (connection == null) {
                return Set.of();
            }
            Set<String> keys = new LinkedHashSet<>();
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(500).build();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    byte[] raw = cursor.next();
                    if (raw != null && raw.length > 0) {
                        keys.add(new String(raw, StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception ex) {
                return Set.of();
            }
            return keys;
        });
    }

    private String resolveScope(String runtimeSource) {
        return "USER_PRIVATE".equalsIgnoreCase(runtimeSource) ? SCOPE_PRIVATE : SCOPE_PUBLIC;
    }

    private String encodeKeyPart(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    public record UsageSnapshot(String provider, String model, String source, String runtimeSource, Long updatedAt) {
    }

    public record UsageAggregate(String date,
                                 Long userId,
                                 String scope,
                                 String provider,
                                 String model,
                                 String source,
                                 String runtimeSource,
                                 Long calls,
                                 Long lastUsedAt) {
        public long safeCalls() {
            return calls == null ? 0L : calls;
        }
    }
}



