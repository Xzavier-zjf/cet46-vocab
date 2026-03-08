package com.cet46.vocab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cet46.vocab.common.PageResult;
import com.cet46.vocab.config.CloudLlmProperties;
import com.cet46.vocab.dto.request.WordListQuery;
import com.cet46.vocab.dto.response.WordDetailResponse;
import com.cet46.vocab.dto.response.WordListItem;
import com.cet46.vocab.dto.response.WordProgressStatusResponse;
import com.cet46.vocab.entity.Cet4Word;
import com.cet46.vocab.entity.Cet6Word;
import com.cet46.vocab.entity.User;
import com.cet46.vocab.entity.WordMeta;
import com.cet46.vocab.llm.LlmAsyncService;
import com.cet46.vocab.llm.LlmProvider;
import com.cet46.vocab.mapper.Cet4WordMapper;
import com.cet46.vocab.mapper.Cet6WordMapper;
import com.cet46.vocab.mapper.UserMapper;
import com.cet46.vocab.mapper.WordMetaMapper;
import com.cet46.vocab.service.WordService;
import com.cet46.vocab.utils.PosParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Date;
import java.time.LocalDate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

@Service
public class WordServiceImpl implements WordService {

    private static final Logger log = LoggerFactory.getLogger(WordServiceImpl.class);
    private static final String WORD_DETAIL_CACHE_PREFIX = "word:detail:";
    private static final int IN_BATCH_SIZE = 500;
    private static final String STATUS_NOT_LEARNING = "NOT_LEARNING";
    private static final String STATUS_LEARNING = "LEARNING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final Duration PENDING_RETRY_INTERVAL = Duration.ofSeconds(8);

    private final Cet4WordMapper cet4WordMapper;
    private final Cet6WordMapper cet6WordMapper;
    private final WordMetaMapper wordMetaMapper;
    private final UserMapper userMapper;
    private final LlmAsyncService llmAsyncService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CloudLlmProperties cloudLlmProperties;

    public WordServiceImpl(Cet4WordMapper cet4WordMapper,
                           Cet6WordMapper cet6WordMapper,
                           WordMetaMapper wordMetaMapper,
                           UserMapper userMapper,
                           LlmAsyncService llmAsyncService,
                           JdbcTemplate jdbcTemplate,
                           ObjectMapper objectMapper,
                           RedisTemplate<String, Object> redisTemplate,
                           CloudLlmProperties cloudLlmProperties) {
        this.cet4WordMapper = cet4WordMapper;
        this.cet6WordMapper = cet6WordMapper;
        this.wordMetaMapper = wordMetaMapper;
        this.userMapper = userMapper;
        this.llmAsyncService = llmAsyncService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.cloudLlmProperties = cloudLlmProperties;
    }

    @Override
    public PageResult<WordListItem> getWordList(WordListQuery query, Long userId) {
        int pageNo = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int pageSize = query.getSize() == null ? 20 : Math.min(Math.max(query.getSize(), 1), 100);
        String style = getUserStyle(userId);

        if ("cet4".equalsIgnoreCase(query.getType())) {
            Page<Cet4Word> page = new Page<>(pageNo, pageSize);
            LambdaQueryWrapper<Cet4Word> wrapper = new LambdaQueryWrapper<>();
            if (StringUtils.hasText(query.getKeyword())) {
                wrapper.like(Cet4Word::getEnglish, query.getKeyword().trim());
            }
            Page<Cet4Word> result = cet4WordMapper.selectPage(page, wrapper);
            List<WordListItem> items = toWordListItemsFromCet4(result.getRecords(), style, query.getPos(), userId);
            Page<WordListItem> out = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
            out.setRecords(items);
            return PageResult.of(out);
        }

        if ("cet6".equalsIgnoreCase(query.getType())) {
            Page<Cet6Word> page = new Page<>(pageNo, pageSize);
            LambdaQueryWrapper<Cet6Word> wrapper = new LambdaQueryWrapper<>();
            if (StringUtils.hasText(query.getKeyword())) {
                wrapper.like(Cet6Word::getEnglish, query.getKeyword().trim());
            }
            Page<Cet6Word> result = cet6WordMapper.selectPage(page, wrapper);
            List<WordListItem> items = toWordListItemsFromCet6(result.getRecords(), style, query.getPos(), userId);
            Page<WordListItem> out = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
            out.setRecords(items);
            return PageResult.of(out);
        }

        Page<WordListItem> emptyPage = new Page<>(pageNo, pageSize, 0);
        emptyPage.setRecords(Collections.emptyList());
        return PageResult.of(emptyPage);
    }

    @Override
    public WordDetailResponse getWordDetail(Long wordId, String wordType, Long userId) {
        String style = getUserStyle(userId);
        String provider = getUserProvider(userId);
        String cacheKey = buildWordDetailCacheKey(userId, style, wordType, wordId);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                WordDetailResponse cachedResponse = parseCachedWordDetail(cached);
                String cachedStatus = cachedResponse != null && cachedResponse.getLlmContent() != null
                        ? cachedResponse.getLlmContent().getGenStatus()
                        : null;
                if (isStableGeneratedStatus(cachedStatus)) {
                    return cachedResponse;
                }
            } catch (IllegalArgumentException ex) {
                log.warn("failed to parse word detail cache, fallback to db, key={}", cacheKey, ex);
            }
        }

        WordBase wordBase = loadWordBase(wordId, wordType);
        if (wordBase == null) {
            return null;
        }

        WordMeta wordMeta = selectWordMetaSafely(wordId, wordType, style);
        boolean cloudUnavailable = isCloudUnavailable(provider);
        if (cloudUnavailable) {
            wordMeta = normalizePendingWhenCloudUnavailable(wordMeta);
        }
        wordMeta = fixStuckPendingMeta(wordMeta);
        wordMeta = fixInconsistentGeneratedStatus(wordMeta);
        if (shouldTriggerGeneration(wordMeta, provider)) {
            llmAsyncService.generateWordContent(wordId, wordType, style, provider);
        }

        String pos = wordMeta != null && StringUtils.hasText(wordMeta.getPos())
                ? wordMeta.getPos()
                : PosParser.parse(wordBase.chinese);

        WordDetailResponse.LlmContent llmContent = buildLlmContent(style, wordMeta, cloudUnavailable, pos);
        WordDetailResponse.Progress progress = queryProgress(userId, wordId, wordType);

        WordDetailResponse response = WordDetailResponse.builder()
                .wordId(wordId)
                .wordType(wordType)
                .english(wordBase.english)
                .phonetic(wordBase.sent)
                .chinese(wordBase.chinese)
                .pos(pos)
                .llmContent(llmContent)
                .progress(progress)
                .build();
        // Only cache stable generated statuses, otherwise retry/polling can read stale fallback/pending.
        if (isStableGeneratedStatus(llmContent.getGenStatus())) {
            try {
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), 1, TimeUnit.HOURS);
            } catch (RuntimeException ex) {
                // Cache write failure should not break the detail API response path.
                log.warn("failed to cache word detail, key={}", cacheKey, ex);
            } catch (Exception ex) {
                log.warn("failed to serialize word detail cache, key={}", cacheKey, ex);
            }
        }
        return response;
    }

    @Override
    public void invalidateWordDetailCache(Long userId, Long wordId, String wordType) {
        if (userId == null || wordId == null || !StringUtils.hasText(wordType)) {
            return;
        }
        String style = getUserStyle(userId);
        String cacheKey = buildWordDetailCacheKey(userId, style, wordType, wordId);
        try {
            redisTemplate.delete(cacheKey);
        } catch (RuntimeException ex) {
            log.warn("failed to evict word detail cache, key={}", cacheKey, ex);
        }
    }

    @Override
    public void addWordToLearn(Long wordId, String wordType, Long userId) {
        String style = getUserStyle(userId);
        WordBase wordBase = loadWordBase(wordId, wordType);
        if (wordBase == null) {
            return;
        }

        String pos = PosParser.parse(wordBase.chinese);
        WordMeta wordMeta = selectWordMetaSafely(wordId, wordType, style);
        if (wordMeta == null) {
            wordMeta = WordMeta.builder()
                    .wordId(wordId)
                    .wordType(wordType)
                    .word(wordBase.english)
                    .style(style)
                    .pos(pos)
                    .genStatus("pending")
                    .promptHash(UUID.randomUUID().toString().replace("-", ""))
                    .build();
            wordMetaMapper.insert(wordMeta);
        } else if (!StringUtils.hasText(wordMeta.getPos()) && StringUtils.hasText(pos)) {
            wordMeta.setPos(pos);
            wordMetaMapper.updateById(wordMeta);
        }

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM user_word_progress WHERE user_id = ? AND word_id = ? AND word_type = ?",
                Integer.class,
                userId,
                wordId,
                wordType
        );
        if (count != null && count > 0) {
            return;
        }

        jdbcTemplate.update(
                "INSERT INTO user_word_progress (user_id, word_id, word_type, easiness, `interval`, repetition, next_review_date) VALUES (?, ?, ?, ?, ?, ?, ?)",
                userId,
                wordId,
                wordType,
                2.5,
                1,
                0,
                LocalDate.now()
        );
    }

    @Override
    public WordProgressStatusResponse getProgressStatus(Long wordId, String wordType, Long userId) {
        String normalizedType = wordType == null ? "" : wordType.toLowerCase();
        boolean learning = hasLearningProgress(userId, wordId, normalizedType);
        boolean completed = hasCompletedReview(userId, wordId, normalizedType);
        String status = resolveProgressStatus(learning, completed);
        return WordProgressStatusResponse.builder()
                .wordId(wordId)
                .wordType(normalizedType)
                .status(status)
                .isLearning(STATUS_LEARNING.equals(status))
                .isCompleted(STATUS_COMPLETED.equals(status))
                .build();
    }

    private List<WordListItem> toWordListItemsFromCet4(List<Cet4Word> records, String style, String posFilter, Long userId) {
        List<Long> wordIds = new ArrayList<>();
        for (Cet4Word word : records) {
            wordIds.add(Long.valueOf(word.getId()));
        }
        Map<Long, String> posMap = loadPosMap(wordIds, "cet4", style);
        Set<Long> learningIds = loadLearningWordIdSet(userId, wordIds, "cet4");
        Set<Long> completedIds = loadCompletedWordIdSet(userId, wordIds, "cet4");

        List<WordListItem> items = new ArrayList<>();
        for (Cet4Word word : records) {
            Long wordId = Long.valueOf(word.getId());
            String pos = StringUtils.hasText(posMap.get(wordId))
                    ? posMap.get(wordId)
                    : PosParser.parse(word.getChinese());
            if (StringUtils.hasText(posFilter) && (pos == null || !containsPos(pos, posFilter))) {
                continue;
            }
            String progressStatus = resolveProgressStatus(learningIds.contains(wordId), completedIds.contains(wordId));
            items.add(WordListItem.builder()
                    .wordId(wordId)
                    .wordType("cet4")
                    .english(word.getEnglish())
                    .phonetic(word.getSent())
                    .chinese(word.getChinese())
                    .pos(pos)
                    .isLearning(STATUS_LEARNING.equals(progressStatus))
                    .progressStatus(progressStatus)
                    .build());
        }
        return items;
    }

    private List<WordListItem> toWordListItemsFromCet6(List<Cet6Word> records, String style, String posFilter, Long userId) {
        List<Long> wordIds = new ArrayList<>();
        for (Cet6Word word : records) {
            wordIds.add(Long.valueOf(word.getId()));
        }
        Map<Long, String> posMap = loadPosMap(wordIds, "cet6", style);
        Set<Long> learningIds = loadLearningWordIdSet(userId, wordIds, "cet6");
        Set<Long> completedIds = loadCompletedWordIdSet(userId, wordIds, "cet6");

        List<WordListItem> items = new ArrayList<>();
        for (Cet6Word word : records) {
            Long wordId = Long.valueOf(word.getId());
            String pos = StringUtils.hasText(posMap.get(wordId))
                    ? posMap.get(wordId)
                    : PosParser.parse(word.getChinese());
            if (StringUtils.hasText(posFilter) && (pos == null || !containsPos(pos, posFilter))) {
                continue;
            }
            String progressStatus = resolveProgressStatus(learningIds.contains(wordId), completedIds.contains(wordId));
            items.add(WordListItem.builder()
                    .wordId(wordId)
                    .wordType("cet6")
                    .english(word.getEnglish())
                    .phonetic(word.getSent())
                    .chinese(word.getChinese())
                    .pos(pos)
                    .isLearning(STATUS_LEARNING.equals(progressStatus))
                    .progressStatus(progressStatus)
                    .build());
        }
        return items;
    }

    private boolean containsPos(String pos, String filter) {
        String[] parts = pos.split(",");
        for (String part : parts) {
            if (part.trim().equalsIgnoreCase(filter.trim())) {
                return true;
            }
        }
        return false;
    }

    private Map<Long, String> loadPosMap(List<Long> wordIds, String wordType, String style) {
        if (wordIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> posMap = new HashMap<>();
        for (int start = 0; start < wordIds.size(); start += IN_BATCH_SIZE) {
            int end = Math.min(start + IN_BATCH_SIZE, wordIds.size());
            List<Long> batchIds = wordIds.subList(start, end);
            String placeholders = String.join(",", Collections.nCopies(batchIds.size(), "?"));
            String sql = "SELECT word_id, pos FROM word_meta WHERE word_type = ? AND style = ? AND word_id IN (" + placeholders + ")";

            List<Object> params = new ArrayList<>();
            params.add(wordType);
            params.add(style);
            params.addAll(batchIds);

            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
                for (Map<String, Object> row : rows) {
                    Object rawWordId = row.get("word_id");
                    Object rawPos = row.get("pos");
                    if (rawWordId instanceof Number && rawPos instanceof String pos && StringUtils.hasText(pos)) {
                        posMap.put(((Number) rawWordId).longValue(), pos);
                    }
                }
            } catch (DataAccessException ex) {
                log.warn("failed to batch query word_meta, wordType={}, style={}, batch=[{}, {})", wordType, style, start, end, ex);
            }
        }
        return posMap;
    }

    private Set<Long> loadLearningWordIdSet(Long userId, List<Long> wordIds, String wordType) {
        if (wordIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> learningIds = new HashSet<>();
        for (int start = 0; start < wordIds.size(); start += IN_BATCH_SIZE) {
            int end = Math.min(start + IN_BATCH_SIZE, wordIds.size());
            List<Long> batchIds = wordIds.subList(start, end);
            String placeholders = String.join(",", Collections.nCopies(batchIds.size(), "?"));
            String sql = "SELECT word_id FROM user_word_progress WHERE user_id = ? AND word_type = ? AND word_id IN (" + placeholders + ")";

            List<Object> params = new ArrayList<>();
            params.add(userId);
            params.add(wordType);
            params.addAll(batchIds);

            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
                for (Map<String, Object> row : rows) {
                    Object rawWordId = row.get("word_id");
                    if (rawWordId instanceof Number) {
                        learningIds.add(((Number) rawWordId).longValue());
                    }
                }
            } catch (DataAccessException ex) {
                log.warn("failed to batch query learning status for user={}, wordType={}, batch=[{}, {})", userId, wordType, start, end, ex);
            }
        }
        return learningIds;
    }

    private Set<Long> loadCompletedWordIdSet(Long userId, List<Long> wordIds, String wordType) {
        if (wordIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> completedIds = new HashSet<>();
        for (int start = 0; start < wordIds.size(); start += IN_BATCH_SIZE) {
            int end = Math.min(start + IN_BATCH_SIZE, wordIds.size());
            List<Long> batchIds = wordIds.subList(start, end);
            String placeholders = String.join(",", Collections.nCopies(batchIds.size(), "?"));
            String sql = "SELECT DISTINCT word_id FROM review_log WHERE user_id = ? AND word_type = ? AND word_id IN (" + placeholders + ")";

            List<Object> params = new ArrayList<>();
            params.add(userId);
            params.add(wordType);
            params.addAll(batchIds);

            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
                for (Map<String, Object> row : rows) {
                    Object rawWordId = row.get("word_id");
                    if (rawWordId instanceof Number) {
                        completedIds.add(((Number) rawWordId).longValue());
                    }
                }
            } catch (DataAccessException ex) {
                log.warn("failed to batch query completed status for user={}, wordType={}, batch=[{}, {})", userId, wordType, start, end, ex);
            }
        }
        return completedIds;
    }

    private boolean hasLearningProgress(Long userId, Long wordId, String wordType) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM user_word_progress WHERE user_id = ? AND word_id = ? AND word_type = ?",
                    Integer.class,
                    userId,
                    wordId,
                    wordType
            );
            return count != null && count > 0;
        } catch (DataAccessException ex) {
            log.warn("failed to query learning progress for user={}, wordId={}, wordType={}", userId, wordId, wordType, ex);
            return false;
        }
    }

    private boolean hasCompletedReview(Long userId, Long wordId, String wordType) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM review_log WHERE user_id = ? AND word_id = ? AND word_type = ?",
                    Integer.class,
                    userId,
                    wordId,
                    wordType
            );
            return count != null && count > 0;
        } catch (DataAccessException ex) {
            log.warn("failed to query completed review for user={}, wordId={}, wordType={}", userId, wordId, wordType, ex);
            return false;
        }
    }

    private String resolveProgressStatus(boolean learning, boolean completed) {
        if (completed) {
            return STATUS_COMPLETED;
        }
        if (learning) {
            return STATUS_LEARNING;
        }
        return STATUS_NOT_LEARNING;
    }

    private WordDetailResponse.LlmContent buildLlmContent(String style, WordMeta wordMeta, boolean cloudUnavailable, String pos) {
        if (wordMeta == null) {
            if (cloudUnavailable) {
                return cloudUnavailableContent(style);
            }
            return WordDetailResponse.LlmContent.builder()
                    .genStatus("pending")
                    .style(style)
                    .sentence(new WordDetailResponse.Sentence(null, null, null))
                    .synonyms(Collections.emptyList())
                    .mnemonic(new WordDetailResponse.Mnemonic(null, null))
                    .smartExplain(null)
                    .grammarUsage(null)
                    .explainStatus("pending")
                    .build();
        }

        return WordDetailResponse.LlmContent.builder()
                .genStatus(wordMeta.getGenStatus())
                .style(style)
                .sentence(new WordDetailResponse.Sentence(
                        wordMeta.getSentenceEn(),
                        wordMeta.getSentenceZh(),
                        wordMeta.getSentenceDifficulty()))
                .synonyms(parseSynonyms(wordMeta.getSynonymsJson()))
                .mnemonic(new WordDetailResponse.Mnemonic(
                        wordMeta.getMnemonic(),
                        wordMeta.getRootAnalysis()))
                .smartExplain(wordMeta.getAiExplain())
                .grammarUsage(resolveGrammarUsage(wordMeta.getAiExplain(), pos))
                .explainStatus(resolveExplainStatus(wordMeta))
                .build();
    }

    private String resolveGrammarUsage(String explain, String pos) {
        if (!StringUtils.hasText(explain)) {
            return fallbackGrammarUsage(pos);
        }
        String[] lines = explain.split("\\R");
        for (String line : lines) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("语法用法：")) {
                return trimmed.substring("语法用法：".length()).trim();
            }
        }
        return fallbackGrammarUsage(pos);
    }

    private String fallbackGrammarUsage(String pos) {
        if (!StringUtils.hasText(pos)) {
            return null;
        }
        String normalized = pos.toLowerCase();
        if (normalized.contains("n")) {
            return "注意可数/不可数、单复数及冠词搭配。";
        }
        if (normalized.contains("v")) {
            return "关注时态变化及常见动词结构（及物/不及物、to do 或 doing）。";
        }
        if (normalized.contains("adj")) {
            return "常作定语/表语，注意比较级和常见介词搭配。";
        }
        if (normalized.contains("adv")) {
            return "通常修饰动词或形容词，注意句中位置。";
        }
        return "关注该词在句中成分位置与固定搭配。";
    }

    private List<WordDetailResponse.SynonymItem> parseSynonyms(String synonymsJson) {
        if (!StringUtils.hasText(synonymsJson)) {
            return Collections.emptyList();
        }
        try {
            JavaType type = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, WordDetailResponse.SynonymItem.class);
            return objectMapper.readValue(synonymsJson, type);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private WordDetailResponse.Progress queryProgress(Long userId, Long wordId, String wordType) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT easiness, `interval` AS interval_days, repetition, next_review_date FROM user_word_progress WHERE user_id = ? AND word_id = ? AND word_type = ? LIMIT 1",
                    userId,
                    wordId,
                    wordType
            );
        } catch (DataAccessException ex) {
            log.warn("failed to query progress for user={}, wordId={}, wordType={}", userId, wordId, wordType, ex);
            return WordDetailResponse.Progress.builder()
                    .isLearning(false)
                    .status(STATUS_NOT_LEARNING)
                    .easiness(null)
                    .interval(null)
                    .repetition(null)
                    .nextReviewDate(null)
                    .build();
        }
        boolean completed = hasCompletedReview(userId, wordId, wordType);
        if (rows.isEmpty()) {
            String status = resolveProgressStatus(false, completed);
            return WordDetailResponse.Progress.builder()
                    .isLearning(STATUS_LEARNING.equals(status))
                    .status(status)
                    .easiness(null)
                    .interval(null)
                    .repetition(null)
                    .nextReviewDate(null)
                    .build();
        }
        Map<String, Object> row = rows.get(0);
        LocalDate nextDate = toLocalDate(row.get("next_review_date"));
        String status = resolveProgressStatus(true, completed);
        return WordDetailResponse.Progress.builder()
                .isLearning(STATUS_LEARNING.equals(status))
                .status(status)
                .easiness(row.get("easiness") == null ? null : ((Number) row.get("easiness")).doubleValue())
                .interval(row.get("interval_days") == null ? null : ((Number) row.get("interval_days")).intValue())
                .repetition(row.get("repetition") == null ? null : ((Number) row.get("repetition")).intValue())
                .nextReviewDate(nextDate)
                .build();
    }

    private LocalDate toLocalDate(Object rawDate) {
        if (rawDate == null) {
            return null;
        }
        if (rawDate instanceof LocalDate localDate) {
            return localDate;
        }
        if (rawDate instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (rawDate instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (rawDate instanceof java.util.Date utilDate) {
            return new Date(utilDate.getTime()).toLocalDate();
        }
        if (rawDate instanceof CharSequence text) {
            try {
                return LocalDate.parse(text.toString());
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }

    private String getUserStyle(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getLlmStyle())) {
            return "story";
        }
        return user.getLlmStyle();
    }

    private String getUserProvider(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return LlmProvider.LOCAL;
        }
        return LlmProvider.normalize(user.getLlmProvider());
    }

    private WordBase loadWordBase(Long wordId, String wordType) {
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

    private WordMeta selectWordMetaSafely(Long wordId, String wordType, String style) {
        try {
            return wordMetaMapper.selectByWordAndStyle(wordId, wordType, style);
        } catch (DataAccessException ex) {
            log.warn("failed to query word_meta for wordId={}, wordType={}, style={}", wordId, wordType, style, ex);
            return null;
        }
    }

    private boolean shouldTriggerGeneration(WordMeta wordMeta, String provider) {
        if (isCloudUnavailable(provider)) {
            return false;
        }
        if (wordMeta == null || !StringUtils.hasText(wordMeta.getGenStatus())) {
            return true;
        }
        String status = wordMeta.getGenStatus().toLowerCase();
        if ("pending".equals(status)) {
            // Pending should be retried when stale even if sentence has been staged.
            return isPendingTimedOut(wordMeta);
        }
        if ("partial".equals(status) || "fallback".equals(status)) {
            // Continue background completion for missing parts to improve consistency across words.
            return hasMissingLlmParts(wordMeta) && isPendingTimedOut(wordMeta);
        }
        return false;
    }

    private WordMeta fixStuckPendingMeta(WordMeta wordMeta) {
        if (wordMeta == null || !"pending".equalsIgnoreCase(wordMeta.getGenStatus())) {
            return wordMeta;
        }
        if (!isPendingTimedOut(wordMeta)) {
            return wordMeta;
        }
        String resolvedStatus = resolveExistingContentStatus(wordMeta);
        if (!StringUtils.hasText(resolvedStatus)) {
            return wordMeta;
        }
        try {
            wordMeta.setGenStatus(resolvedStatus);
            wordMetaMapper.updateById(wordMeta);
            return wordMeta;
        } catch (Exception ex) {
            log.warn("failed to fix stuck pending status for wordMetaId={}", wordMeta.getId(), ex);
            return wordMeta;
        }
    }

    private WordMeta fixInconsistentGeneratedStatus(WordMeta wordMeta) {
        if (wordMeta == null) {
            return null;
        }
        String resolvedStatus = resolveExistingContentStatus(wordMeta);
        if (!StringUtils.hasText(resolvedStatus)) {
            return wordMeta;
        }
        if (resolvedStatus.equalsIgnoreCase(wordMeta.getGenStatus())) {
            return wordMeta;
        }
        try {
            wordMeta.setGenStatus(resolvedStatus);
            wordMetaMapper.updateById(wordMeta);
            return wordMeta;
        } catch (Exception ex) {
            log.warn("failed to fix inconsistent gen status for wordMetaId={}", wordMeta.getId(), ex);
            return wordMeta;
        }
    }

    private boolean isPendingTimedOut(WordMeta wordMeta) {
        LocalDateTime updatedAt = wordMeta.getUpdatedAt();
        if (updatedAt == null) {
            return true;
        }
        return updatedAt.isBefore(LocalDateTime.now().minus(PENDING_RETRY_INTERVAL));
    }

    private String resolveExistingContentStatus(WordMeta wordMeta) {
        boolean sentenceOk = StringUtils.hasText(wordMeta.getSentenceEn()) || StringUtils.hasText(wordMeta.getSentenceZh());
        boolean synonymOk = hasSynonymContent(wordMeta.getSynonymsJson());
        boolean mnemonicOk = StringUtils.hasText(wordMeta.getMnemonic()) || StringUtils.hasText(wordMeta.getRootAnalysis());

        if (sentenceOk && synonymOk && mnemonicOk) {
            return "full";
        }
        if (sentenceOk || synonymOk || mnemonicOk) {
            return "partial";
        }
        return null;
    }

    private boolean hasMissingLlmParts(WordMeta wordMeta) {
        boolean sentenceOk = StringUtils.hasText(wordMeta.getSentenceEn()) || StringUtils.hasText(wordMeta.getSentenceZh());
        boolean synonymOk = hasSynonymContent(wordMeta.getSynonymsJson());
        boolean mnemonicOk = StringUtils.hasText(wordMeta.getMnemonic()) || StringUtils.hasText(wordMeta.getRootAnalysis());
        return !(sentenceOk && synonymOk && mnemonicOk);
    }

    private boolean hasSynonymContent(String synonymsJson) {
        if (!StringUtils.hasText(synonymsJson)) {
            return false;
        }
        String trimmed = synonymsJson.trim();
        return !"[]".equals(trimmed) && !"null".equalsIgnoreCase(trimmed);
    }

    private WordMeta normalizePendingWhenCloudUnavailable(WordMeta wordMeta) {
        if (wordMeta == null || !"pending".equalsIgnoreCase(wordMeta.getGenStatus())) {
            return wordMeta;
        }
        boolean hasAnyContent = StringUtils.hasText(wordMeta.getSentenceEn())
                || StringUtils.hasText(wordMeta.getSentenceZh())
                || hasSynonymContent(wordMeta.getSynonymsJson())
                || StringUtils.hasText(wordMeta.getMnemonic())
                || StringUtils.hasText(wordMeta.getRootAnalysis());
        if (hasAnyContent) {
            return wordMeta;
        }
        try {
            wordMeta.setGenStatus("fallback");
            wordMeta.setSentenceZh("云端API未配置完成，请在“我的资料”完成配置后点击“重试AI生成”");
            wordMetaMapper.updateById(wordMeta);
        } catch (Exception ex) {
            log.warn("failed to normalize pending when cloud unavailable, wordMetaId={}", wordMeta.getId(), ex);
        }
        return wordMeta;
    }

    private record WordBase(String english, String sent, String chinese) {
    }

    private String buildWordDetailCacheKey(Long userId, String style, String wordType, Long wordId) {
        return WORD_DETAIL_CACHE_PREFIX + userId + ":" + style + ":" + wordType + ":" + wordId;
    }

    private boolean isStableGeneratedStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        return "full".equalsIgnoreCase(status);
    }

    private boolean isCloudUnavailable(String provider) {
        if (!LlmProvider.CLOUD.equals(LlmProvider.normalize(provider))) {
            return false;
        }
        return !Boolean.TRUE.equals(cloudLlmProperties.getEnabled())
                || !StringUtils.hasText(cloudLlmProperties.getBaseUrl())
                || !StringUtils.hasText(cloudLlmProperties.getModel())
                || !StringUtils.hasText(cloudLlmProperties.getApiKey());
    }

    private WordDetailResponse.LlmContent cloudUnavailableContent(String style) {
        return WordDetailResponse.LlmContent.builder()
                .genStatus("fallback")
                .style(style)
                .sentence(new WordDetailResponse.Sentence(
                        null,
                        "云端API未配置完成，请在“我的资料”完成配置后点击“重试AI生成”",
                        null))
                .synonyms(Collections.emptyList())
                .mnemonic(new WordDetailResponse.Mnemonic(null, null))
                .smartExplain(null)
                .grammarUsage(null)
                .explainStatus("fallback")
                .build();
    }

    private WordDetailResponse parseCachedWordDetail(Object cached) {
        if (cached == null) {
            return null;
        }
        if (cached instanceof String cacheJson) {
            try {
                return objectMapper.readValue(cacheJson, WordDetailResponse.class);
            } catch (Exception ex) {
                throw new IllegalArgumentException("failed to deserialize cached word detail json", ex);
            }
        }
        return objectMapper.convertValue(cached, WordDetailResponse.class);
    }

    private String resolveExplainStatus(WordMeta wordMeta) {
        if (wordMeta == null) {
            return "pending";
        }
        if (StringUtils.hasText(wordMeta.getAiExplainStatus())) {
            return wordMeta.getAiExplainStatus();
        }
        return StringUtils.hasText(wordMeta.getAiExplain()) ? "full" : "fallback";
    }
}
