package com.cet46.vocab.service.impl;

import com.cet46.vocab.dto.request.QuizGenerateRequest;
import com.cet46.vocab.dto.request.QuizSubmitRequest;
import com.cet46.vocab.service.QuizService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class QuizServiceImpl implements QuizService {

    private static final int DEFAULT_QUIZ_COUNT = 10;
    private static final int MAX_QUIZ_COUNT = 50;
    private static final int MAX_HISTORY_LIMIT = 100;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private final Map<String, QuizSession> sessionStore = new ConcurrentHashMap<>();

    public QuizServiceImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> generateQuiz(Long userId, QuizGenerateRequest req) {
        int requested = req == null || req.getCount() == null ? DEFAULT_QUIZ_COUNT : req.getCount();
        int count = Math.min(Math.max(requested, 1), MAX_QUIZ_COUNT);
        String wordType = req != null && StringUtils.hasText(req.getWordType()) ? req.getWordType().trim().toLowerCase(Locale.ROOT) : "cet4";
        String mode = req != null && StringUtils.hasText(req.getMode()) ? req.getMode().trim().toLowerCase(Locale.ROOT) : "choice";
        boolean fillMode = "fill".equals(mode);

        List<WordRow> words = loadWords(wordType, count);
        List<Map<String, Object>> questions = new ArrayList<>();
        Map<String, String> answerMap = new HashMap<>();
        Map<String, String> englishMap = new HashMap<>();
        Map<String, Long> wordIdMap = new HashMap<>();
        Map<String, String> wordTypeMap = new HashMap<>();
        Map<String, String> correctTextMap = new HashMap<>();

        for (int i = 0; i < words.size(); i++) {
            WordRow word = words.get(i);
            String questionId = "q_" + (i + 1);
            Map<String, Object> question = new LinkedHashMap<>();
            question.put("questionId", questionId);
            question.put("english", word.english());
            question.put("phonetic", word.phonetic());
            question.put("mode", fillMode ? "fill" : "choice");
            question.put("wordId", word.id());
            question.put("wordType", toWordTypeCode(word.sourceTable()));

            if (fillMode) {
                answerMap.put(questionId, word.english());
                correctTextMap.put(questionId, word.english());
            } else {
                List<String> options = buildOptions(word);
                List<Map<String, Object>> optionItems = new ArrayList<>();
                String[] ids = new String[]{"A", "B", "C", "D"};
                String correctId = "A";
                for (int idx = 0; idx < ids.length && idx < options.size(); idx++) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", ids[idx]);
                    item.put("text", options.get(idx));
                    optionItems.add(item);
                    if (word.chinese().equals(options.get(idx))) {
                        correctId = ids[idx];
                    }
                }
                question.put("options", optionItems);
                question.put("correctId", correctId);
                answerMap.put(questionId, correctId);
                correctTextMap.put(questionId, word.chinese());
            }

            englishMap.put(questionId, word.english());
            wordIdMap.put(questionId, word.id());
            wordTypeMap.put(questionId, toWordTypeCode(word.sourceTable()));
            questions.add(question);
        }

        String quizId = UUID.randomUUID().toString().replace("-", "");
        sessionStore.put(quizId, new QuizSession(userId, mode, answerMap, englishMap, wordIdMap, wordTypeMap, correctTextMap));

        Map<String, Object> data = new HashMap<>();
        data.put("quizId", quizId);
        data.put("questions", questions);
        return data;
    }

    @Override
    public Map<String, Object> submitQuiz(Long userId, QuizSubmitRequest req) {
        if (req == null || !StringUtils.hasText(req.getQuizId())) {
            throw new IllegalArgumentException("invalid quizId");
        }
        String quizId = req.getQuizId().trim();
        QuizSession session = sessionStore.get(quizId);
        if (session == null || !userId.equals(session.userId())) {
            throw new IllegalArgumentException("invalid quizId");
        }
        sessionStore.remove(quizId, session);

        int total = session.answerMap().size();
        int correct = 0;
        List<Map<String, Object>> wrongWords = new ArrayList<>();
        List<Map<String, Object>> details = new ArrayList<>();
        List<QuizSubmitRequest.AnswerItem> answers = req.getAnswers() == null ? Collections.emptyList() : req.getAnswers();
        Map<String, String> userAnswerMap = new HashMap<>();

        for (QuizSubmitRequest.AnswerItem item : answers) {
            if (item == null || !StringUtils.hasText(item.getQuestionId())) {
                continue;
            }
            String qid = item.getQuestionId();
            String expect = session.answerMap().get(qid);
            if (!StringUtils.hasText(expect)) {
                continue;
            }
            String actual = item.getUserAnswer() == null ? "" : item.getUserAnswer().trim();
            userAnswerMap.put(qid, actual);

            boolean hit;
            if ("fill".equals(session.mode())) {
                hit = expect.equalsIgnoreCase(actual);
            } else {
                hit = expect.equals(actual);
            }

            if (hit) {
                correct++;
            } else {
                Map<String, Object> wrong = new HashMap<>();
                wrong.put("english", session.englishMap().getOrDefault(qid, ""));
                wrong.put("correctAnswer", expect);
                wrong.put("userAnswer", actual);
                wrongWords.add(wrong);
            }
        }

        List<String> orderedQuestionIds = new ArrayList<>(session.answerMap().keySet());
        Collections.sort(orderedQuestionIds);
        for (int i = 0; i < orderedQuestionIds.size(); i++) {
            String qid = orderedQuestionIds.get(i);
            String expect = session.answerMap().get(qid);
            String actual = userAnswerMap.getOrDefault(qid, "");
            boolean hit = "fill".equals(session.mode()) ? expect.equalsIgnoreCase(actual) : expect.equals(actual);

            String displayUserAnswer = "fill".equals(session.mode())
                    ? actual
                    : resolveChoiceText(expect, actual, session.correctTextMap().get(qid));
            String displayCorrect = "fill".equals(session.mode())
                    ? session.correctTextMap().getOrDefault(qid, expect)
                    : session.correctTextMap().getOrDefault(qid, expect);

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("index", i + 1);
            detail.put("questionId", qid);
            detail.put("wordId", session.wordIdMap().get(qid));
            detail.put("wordType", session.wordTypeMap().get(qid));
            detail.put("english", session.englishMap().getOrDefault(qid, ""));
            detail.put("userAnswer", displayUserAnswer);
            detail.put("correctAnswer", displayCorrect);
            detail.put("isCorrect", hit);
            details.add(detail);
        }

        long recordId = saveQuizRecord(userId, req.getQuizId().trim(), session, total, correct, details);

        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("correct", correct);
        data.put("wrongWords", wrongWords);
        data.put("recordId", recordId);
        return data;
    }

    @Override
    public List<Map<String, Object>> listQuizHistory(Long userId, Integer limit) {
        int size = limit == null ? 20 : Math.min(Math.max(limit, 1), MAX_HISTORY_LIMIT);
        try {
            return jdbcTemplate.query(
                    "SELECT id, quiz_id, word_type, quiz_type, total, correct, wrong_count, created_at " +
                            "FROM quiz_session_record WHERE user_id = ? ORDER BY created_at DESC LIMIT ?",
                    (rs, rowNum) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getLong("id"));
                        row.put("quizId", rs.getString("quiz_id"));
                        row.put("wordType", rs.getString("word_type"));
                        row.put("mode", rs.getString("quiz_type"));
                        row.put("count", rs.getInt("total"));
                        row.put("total", rs.getInt("total"));
                        row.put("correct", rs.getInt("correct"));
                        row.put("wrongCount", rs.getInt("wrong_count"));
                        Timestamp createdAt = rs.getTimestamp("created_at");
                        row.put("finishedAt", createdAt == null ? null : createdAt.toInstant().toString());
                        return row;
                    },
                    userId,
                    size
            );
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    @Override
    public Map<String, Object> getQuizHistoryDetail(Long userId, Long recordId) {
        if (recordId == null) {
            throw new IllegalArgumentException("invalid recordId");
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, quiz_id, word_type, quiz_type, total, correct, wrong_count, details_json, created_at " +
                            "FROM quiz_session_record WHERE id = ? AND user_id = ? LIMIT 1",
                    recordId,
                    userId
            );
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("quiz record not found");
            }
            Map<String, Object> row = rows.get(0);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", ((Number) row.get("id")).longValue());
            result.put("quizId", row.get("quiz_id"));
            result.put("wordType", row.get("word_type"));
            result.put("mode", row.get("quiz_type"));
            result.put("total", ((Number) row.get("total")).intValue());
            result.put("correct", ((Number) row.get("correct")).intValue());
            result.put("wrongCount", ((Number) row.get("wrong_count")).intValue());
            Timestamp createdAt = (Timestamp) row.get("created_at");
            result.put("finishedAt", createdAt == null ? null : createdAt.toInstant().toString());
            result.put("details", parseDetailsJson((String) row.get("details_json")));
            return result;
        } catch (DataAccessException ex) {
            throw new IllegalArgumentException("quiz record not found");
        }
    }

    private List<Map<String, Object>> parseDetailsJson(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private long saveQuizRecord(Long userId,
                                String quizId,
                                QuizSession session,
                                int total,
                                int correct,
                                List<Map<String, Object>> details) {
        int wrongCount = Math.max(total - correct, 0);
        String wordType = resolveSessionWordType(session.wordTypeMap().values());
        String detailsJson;
        try {
            detailsJson = objectMapper.writeValueAsString(details);
        } catch (Exception ex) {
            detailsJson = "[]";
        }

        try {
            jdbcTemplate.update(
                    "INSERT INTO quiz_session_record (quiz_id, user_id, word_type, quiz_type, total, correct, wrong_count, details_json, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    quizId,
                    userId,
                    wordType,
                    session.mode(),
                    total,
                    correct,
                    wrongCount,
                    detailsJson,
                    LocalDateTime.now()
            );
            Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            return id == null ? 0L : id;
        } catch (DataAccessException ex) {
            return 0L;
        }
    }

    private String resolveSessionWordType(Iterable<String> types) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String type : types) {
            String value = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
            if ("cet4".equals(value) || "cet6".equals(value)) {
                normalized.add(value);
            }
        }
        if (normalized.size() == 1) {
            return normalized.iterator().next();
        }
        return "mixed";
    }

    private String resolveChoiceText(String expectedChoiceId, String userChoiceId, String correctText) {
        if (!StringUtils.hasText(userChoiceId)) {
            return "";
        }
        if (StringUtils.hasText(expectedChoiceId) && expectedChoiceId.equals(userChoiceId)) {
            return correctText == null ? userChoiceId : correctText;
        }
        return userChoiceId;
    }

    private List<WordRow> loadWords(String wordType, int count) {
        if ("cet6".equals(wordType)) {
            return loadWordsFromTable("cet6zx", count);
        }
        if ("mixed".equals(wordType)) {
            int cet4Count = count / 2 + count % 2;
            int cet6Count = count / 2;
            List<WordRow> result = new ArrayList<>();
            result.addAll(loadWordsFromTable("cet4zx", cet4Count));
            result.addAll(loadWordsFromTable("cet6zx", cet6Count));
            if (result.size() < count) {
                result.addAll(loadWordsFromTable("cet4zx", count - result.size()));
            }
            if (result.size() < count) {
                result.addAll(loadWordsFromTable("cet6zx", count - result.size()));
            }
            Collections.shuffle(result);
            return result.size() <= count ? result : new ArrayList<>(result.subList(0, count));
        }
        return loadWordsFromTable("cet4zx", count);
    }

    private List<WordRow> loadWordsFromTable(String table, int count) {
        if (count <= 0) {
            return List.of();
        }
        TableStats stats = queryTableStats(table);
        if (stats == null || stats.total <= 0 || stats.minId == null || stats.maxId == null) {
            return List.of();
        }
        int target = Math.min(count, stats.total);
        List<WordRow> result = new ArrayList<>(target);
        Set<Long> seenIds = new LinkedHashSet<>();
        int attempts = Math.max(target * 12, 48);
        for (int i = 0; i < attempts && result.size() < target; i++) {
            long probeId = ThreadLocalRandom.current().nextLong(stats.minId, stats.maxId + 1);
            WordRow row = queryByLowerBound(table, probeId);
            if (row == null || row.id() == null) {
                continue;
            }
            if (seenIds.add(row.id())) {
                result.add(row);
            }
        }

        if (result.size() < target) {
            try {
                List<WordRow> fallback = jdbcTemplate.query(
                        "SELECT id, english, sent, chinese FROM " + table + " ORDER BY id DESC LIMIT ?",
                        (rs, rowNum) -> new WordRow(
                                rs.getLong("id"),
                                rs.getString("english"),
                                rs.getString("sent"),
                                rs.getString("chinese"),
                                table
                        ),
                        Math.max(target * 2, 20)
                );
                for (WordRow row : fallback) {
                    if (row != null && row.id() != null && seenIds.add(row.id())) {
                        result.add(row);
                        if (result.size() >= target) {
                            break;
                        }
                    }
                }
            } catch (DataAccessException ignored) {
                // keep best-effort result
            }
        }

        Collections.shuffle(result);
        return result;
    }

    private WordRow queryByLowerBound(String table, long lowerBoundId) {
        try {
            return jdbcTemplate.query(
                    "SELECT id, english, sent, chinese FROM " + table + " WHERE id >= ? ORDER BY id ASC LIMIT 1",
                    (rs, rowNum) -> new WordRow(
                            rs.getLong("id"),
                            rs.getString("english"),
                            rs.getString("sent"),
                            rs.getString("chinese"),
                            table
                    ),
                    lowerBoundId
            ).stream().findFirst().orElse(null);
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private TableStats queryTableStats(String table) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT MIN(id) AS min_id, MAX(id) AS max_id, COUNT(1) AS total FROM " + table
            );
            if (row == null || row.isEmpty()) {
                return null;
            }
            Number minId = (Number) row.get("min_id");
            Number maxId = (Number) row.get("max_id");
            Number total = (Number) row.get("total");
            if (minId == null || maxId == null || total == null) {
                return null;
            }
            return new TableStats(minId.longValue(), maxId.longValue(), total.intValue());
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private List<String> buildOptions(WordRow correctWord) {
        List<String> options = new ArrayList<>();
        options.add(correctWord.chinese());

        String sourceTable = StringUtils.hasText(correctWord.sourceTable()) ? correctWord.sourceTable() : "cet4zx";
        try {
            List<String> candidates = jdbcTemplate.query(
                    "SELECT chinese FROM " + sourceTable + " WHERE id <> ? AND chinese IS NOT NULL AND chinese <> '' ORDER BY id DESC LIMIT 36",
                    (rs, rowNum) -> rs.getString("chinese"),
                    correctWord.id()
            );
            Collections.shuffle(candidates);
            for (String candidate : candidates) {
                if (!StringUtils.hasText(candidate)) {
                    continue;
                }
                if (!options.contains(candidate)) {
                    options.add(candidate);
                }
                if (options.size() >= 4) {
                    break;
                }
            }
        } catch (DataAccessException ignored) {
            // keep fallback options
        }

        while (options.size() < 4) {
            options.add("N/A");
        }
        Collections.shuffle(options);
        return options;
    }

    private String toWordTypeCode(String sourceTable) {
        String table = sourceTable == null ? "" : sourceTable.trim().toLowerCase(Locale.ROOT);
        if ("cet6zx".equals(table)) {
            return "cet6";
        }
        return "cet4";
    }

    private record WordRow(Long id, String english, String phonetic, String chinese, String sourceTable) {
    }

    private record QuizSession(Long userId,
                               String mode,
                               Map<String, String> answerMap,
                               Map<String, String> englishMap,
                               Map<String, Long> wordIdMap,
                               Map<String, String> wordTypeMap,
                               Map<String, String> correctTextMap) {
    }

    private record TableStats(Long minId, Long maxId, int total) {
    }
}
