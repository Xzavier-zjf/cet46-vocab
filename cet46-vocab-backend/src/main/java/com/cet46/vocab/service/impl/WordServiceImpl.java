package com.cet46.vocab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cet46.vocab.common.PageResult;
import com.cet46.vocab.dto.request.WordListQuery;
import com.cet46.vocab.dto.response.WordDetailResponse;
import com.cet46.vocab.dto.response.WordListItem;
import com.cet46.vocab.entity.Cet4Word;
import com.cet46.vocab.entity.Cet6Word;
import com.cet46.vocab.entity.User;
import com.cet46.vocab.entity.WordMeta;
import com.cet46.vocab.llm.LlmAsyncService;
import com.cet46.vocab.mapper.Cet4WordMapper;
import com.cet46.vocab.mapper.Cet6WordMapper;
import com.cet46.vocab.mapper.UserMapper;
import com.cet46.vocab.mapper.WordMetaMapper;
import com.cet46.vocab.service.WordService;
import com.cet46.vocab.utils.PosParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WordServiceImpl implements WordService {

    private final Cet4WordMapper cet4WordMapper;
    private final Cet6WordMapper cet6WordMapper;
    private final WordMetaMapper wordMetaMapper;
    private final UserMapper userMapper;
    private final LlmAsyncService llmAsyncService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public WordServiceImpl(Cet4WordMapper cet4WordMapper,
                           Cet6WordMapper cet6WordMapper,
                           WordMetaMapper wordMetaMapper,
                           UserMapper userMapper,
                           LlmAsyncService llmAsyncService,
                           JdbcTemplate jdbcTemplate,
                           ObjectMapper objectMapper) {
        this.cet4WordMapper = cet4WordMapper;
        this.cet6WordMapper = cet6WordMapper;
        this.wordMetaMapper = wordMetaMapper;
        this.userMapper = userMapper;
        this.llmAsyncService = llmAsyncService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
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
        WordBase wordBase = loadWordBase(wordId, wordType);
        if (wordBase == null) {
            return null;
        }

        WordMeta wordMeta = wordMetaMapper.selectByWordAndStyle(wordId, wordType, style);
        if (wordMeta == null || wordMeta.getGenStatus() == null) {
            llmAsyncService.generateWordContent(wordId, wordType, style);
        }

        String pos = wordMeta != null && StringUtils.hasText(wordMeta.getPos())
                ? wordMeta.getPos()
                : PosParser.parse(wordBase.chinese);

        WordDetailResponse.LlmContent llmContent = buildLlmContent(style, wordMeta);
        WordDetailResponse.Progress progress = queryProgress(userId, wordId, wordType);

        return WordDetailResponse.builder()
                .wordId(wordId)
                .wordType(wordType)
                .english(wordBase.english)
                .phonetic(wordBase.sent)
                .chinese(wordBase.chinese)
                .pos(pos)
                .llmContent(llmContent)
                .progress(progress)
                .build();
    }

    @Override
    public void addWordToLearn(Long wordId, String wordType, Long userId) {
        String style = getUserStyle(userId);
        WordBase wordBase = loadWordBase(wordId, wordType);
        if (wordBase == null) {
            return;
        }

        String pos = PosParser.parse(wordBase.chinese);
        WordMeta wordMeta = wordMetaMapper.selectByWordAndStyle(wordId, wordType, style);
        if (wordMeta == null) {
            wordMeta = WordMeta.builder()
                    .wordId(wordId)
                    .wordType(wordType)
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
                "INSERT INTO user_word_progress (user_id, word_id, word_type, easiness, interval, repetition, next_review_date) VALUES (?, ?, ?, ?, ?, ?, ?)",
                userId,
                wordId,
                wordType,
                2.5,
                1,
                0,
                LocalDate.now().plusDays(1)
        );
    }

    private List<WordListItem> toWordListItemsFromCet4(List<Cet4Word> records, String style, String posFilter, Long userId) {
        List<WordListItem> items = new ArrayList<>();
        for (Cet4Word word : records) {
            WordMeta wordMeta = wordMetaMapper.selectByWordAndStyle(Long.valueOf(word.getId()), "cet4", style);
            String pos = wordMeta != null && StringUtils.hasText(wordMeta.getPos()) ? wordMeta.getPos() : PosParser.parse(word.getChinese());
            if (StringUtils.hasText(posFilter) && (pos == null || !containsPos(pos, posFilter))) {
                continue;
            }
            items.add(WordListItem.builder()
                    .wordId(Long.valueOf(word.getId()))
                    .wordType("cet4")
                    .english(word.getEnglish())
                    .phonetic(word.getSent())
                    .chinese(word.getChinese())
                    .pos(pos)
                    .isLearning(isLearning(userId, Long.valueOf(word.getId()), "cet4"))
                    .build());
        }
        return items;
    }

    private List<WordListItem> toWordListItemsFromCet6(List<Cet6Word> records, String style, String posFilter, Long userId) {
        List<WordListItem> items = new ArrayList<>();
        for (Cet6Word word : records) {
            WordMeta wordMeta = wordMetaMapper.selectByWordAndStyle(Long.valueOf(word.getId()), "cet6", style);
            String pos = wordMeta != null && StringUtils.hasText(wordMeta.getPos()) ? wordMeta.getPos() : PosParser.parse(word.getChinese());
            if (StringUtils.hasText(posFilter) && (pos == null || !containsPos(pos, posFilter))) {
                continue;
            }
            items.add(WordListItem.builder()
                    .wordId(Long.valueOf(word.getId()))
                    .wordType("cet6")
                    .english(word.getEnglish())
                    .phonetic(word.getSent())
                    .chinese(word.getChinese())
                    .pos(pos)
                    .isLearning(isLearning(userId, Long.valueOf(word.getId()), "cet6"))
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

    private boolean isLearning(Long userId, Long wordId, String wordType) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM user_word_progress WHERE user_id = ? AND word_id = ? AND word_type = ?",
                Integer.class,
                userId,
                wordId,
                wordType
        );
        return count != null && count > 0;
    }

    private WordDetailResponse.LlmContent buildLlmContent(String style, WordMeta wordMeta) {
        if (wordMeta == null) {
            return WordDetailResponse.LlmContent.builder()
                    .genStatus("pending")
                    .style(style)
                    .sentence(new WordDetailResponse.Sentence(null, null, null))
                    .synonyms(Collections.emptyList())
                    .mnemonic(new WordDetailResponse.Mnemonic(null, null))
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
                .build();
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
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT easiness, interval, repetition, next_review_date FROM user_word_progress WHERE user_id = ? AND word_id = ? AND word_type = ? LIMIT 1",
                userId,
                wordId,
                wordType
        );
        if (rows.isEmpty()) {
            return WordDetailResponse.Progress.builder()
                    .isLearning(false)
                    .easiness(null)
                    .interval(null)
                    .repetition(null)
                    .nextReviewDate(null)
                    .build();
        }
        Map<String, Object> row = rows.get(0);
        Date nextDate = (Date) row.get("next_review_date");
        return WordDetailResponse.Progress.builder()
                .isLearning(true)
                .easiness(row.get("easiness") == null ? null : ((Number) row.get("easiness")).doubleValue())
                .interval(row.get("interval") == null ? null : ((Number) row.get("interval")).intValue())
                .repetition(row.get("repetition") == null ? null : ((Number) row.get("repetition")).intValue())
                .nextReviewDate(nextDate == null ? null : nextDate.toLocalDate())
                .build();
    }

    private String getUserStyle(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getLlmStyle())) {
            return "story";
        }
        return user.getLlmStyle();
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

    private record WordBase(String english, String sent, String chinese) {
    }
}
