package com.cet46.vocab.llm;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;

@Component
public class LlmUsageTracker {

    private static final String KEY_PREFIX = "llm:last-used:";

    private final StringRedisTemplate stringRedisTemplate;

    public LlmUsageTracker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void record(Long userId, String provider, String model, String source) {
        if (userId == null) {
            return;
        }
        String key = KEY_PREFIX + userId;
        stringRedisTemplate.opsForHash().put(key, "provider", normalize(provider));
        stringRedisTemplate.opsForHash().put(key, "model", normalize(model));
        stringRedisTemplate.opsForHash().put(key, "source", normalize(source));
        stringRedisTemplate.opsForHash().put(key, "updatedAt", String.valueOf(System.currentTimeMillis()));
        stringRedisTemplate.expire(key, Duration.ofDays(7));
    }

    public UsageSnapshot get(Long userId) {
        if (userId == null) {
            return new UsageSnapshot("", "", "", null);
        }
        String key = KEY_PREFIX + userId;
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(key);
        if (map == null || map.isEmpty()) {
            return new UsageSnapshot("", "", "", null);
        }
        String provider = asText(map.get("provider"));
        String model = asText(map.get("model"));
        String source = asText(map.get("source"));
        Long updatedAt = asLong(map.get("updatedAt"));
        return new UsageSnapshot(provider, model, source, updatedAt);
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

    public record UsageSnapshot(String provider, String model, String source, Long updatedAt) {
    }
}
