package com.cet46.vocab.llm;

import com.cet46.vocab.utils.Md5Utils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LlmCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public LlmCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String getCache(String hash) {
        Object value = redisTemplate.opsForValue().get(hash);
        return value == null ? null : String.valueOf(value);
    }

    public void setCache(String hash, String content) {
        redisTemplate.opsForValue().set(hash, content);
    }

    public void deleteCache(String hash) {
        redisTemplate.delete(hash);
    }

    public String buildHash(Long wordId, String wordType, String promptType, String style) {
        String raw = wordId + "_" + wordType + "_" + promptType + "_" + style;
        return Md5Utils.md5(raw);
    }
}
