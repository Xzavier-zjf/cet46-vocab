package com.cet46.vocab.service.impl;

import com.cet46.vocab.dto.request.QuizGenerateRequest;
import com.cet46.vocab.dto.request.QuizSubmitRequest;
import com.cet46.vocab.service.QuizService;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuizServiceImpl implements QuizService {

    private final JdbcTemplate jdbcTemplate;

    private final Map<String, QuizSession> sessionStore = new ConcurrentHashMap<>();

    public QuizServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> generateQuiz(Long userId, QuizGenerateRequest req) {
        int count = req.getCount() == null ? 10 : req.getCount();
        String wordType = StringUtils.hasText(req.getWordType()) ? req.getWordType().trim().toLowerCase(Locale.ROOT) : "cet4";
        String mode = StringUtils.hasText(req.getMode()) ? req.getMode().trim().toLowerCase(Locale.ROOT) : "choice";
        boolean fillMode = "fill".equals(mode);

        List<WordRow> words = loadWords(wordType, count);
        List<Map<String, Object>> questions = new ArrayList<>();
        Map<String, String> answerMap = new HashMap<>();
        Map<String, String> englishMap = new HashMap<>();

        for (int i = 0; i < words.size(); i++) {
            WordRow word = words.get(i);
            String questionId = "q_" + (i + 1);
            Map<String, Object> question = new LinkedHashMap<>();
            question.put("questionId", questionId);
            question.put("english", word.english());
            question.put("phonetic", word.phonetic());
            question.put("mode", fillMode ? "fill" : "choice");

            if (fillMode) {
                answerMap.put(questionId, word.english());
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
            }

            englishMap.put(questionId, word.english());
            questions.add(question);
        }

        String quizId = UUID.randomUUID().toString().replace("-", "");
        sessionStore.put(quizId, new QuizSession(userId, mode, answerMap, englishMap));

        Map<String, Object> data = new HashMap<>();
        data.put("quizId", quizId);
        data.put("questions", questions);
        return data;
    }

    @Override
    public Map<String, Object> submitQuiz(Long userId, QuizSubmitRequest req) {
        QuizSession session = sessionStore.get(req.getQuizId());
        if (session == null || !userId.equals(session.userId())) {
            throw new IllegalArgumentException("invalid quizId");
        }

        int total = session.answerMap().size();
        int correct = 0;
        List<Map<String, Object>> wrongWords = new ArrayList<>();

        for (QuizSubmitRequest.AnswerItem item : req.getAnswers()) {
            if (item == null || !StringUtils.hasText(item.getQuestionId())) {
                continue;
            }
            String qid = item.getQuestionId();
            String expect = session.answerMap().get(qid);
            if (!StringUtils.hasText(expect)) {
                continue;
            }
            String actual = item.getUserAnswer() == null ? "" : item.getUserAnswer().trim();

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

        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("correct", correct);
        data.put("wrongWords", wrongWords);
        return data;
    }

    private List<WordRow> loadWords(String wordType, int count) {
        String sql;
        if ("cet6".equals(wordType)) {
            sql = "SELECT id, english, sent, chinese FROM cet6zx ORDER BY RAND() LIMIT ?";
        } else if ("mixed".equals(wordType)) {
            sql = "SELECT id, english, sent, chinese FROM (" +
                    "SELECT id, english, sent, chinese FROM cet4zx " +
                    "UNION ALL " +
                    "SELECT id, english, sent, chinese FROM cet6zx" +
                    ") t ORDER BY RAND() LIMIT ?";
        } else {
            sql = "SELECT id, english, sent, chinese FROM cet4zx ORDER BY RAND() LIMIT ?";
        }
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> new WordRow(
                    rs.getLong("id"),
                    rs.getString("english"),
                    rs.getString("sent"),
                    rs.getString("chinese")
            ), count);
        } catch (DataAccessException ex) {
            return Collections.emptyList();
        }
    }

    private List<String> buildOptions(WordRow correctWord) {
        List<String> options = new ArrayList<>();
        options.add(correctWord.chinese());
        try {
            List<String> others = jdbcTemplate.query(
                    "SELECT chinese FROM cet4zx WHERE id <> ? ORDER BY RAND() LIMIT 3",
                    (rs, rowNum) -> rs.getString("chinese"),
                    correctWord.id()
            );
            options.addAll(others);
        } catch (DataAccessException ignored) {
            // keep fallback options
        }
        while (options.size() < 4) {
            options.add("N/A");
        }
        Collections.shuffle(options);
        return options;
    }

    private record WordRow(Long id, String english, String phonetic, String chinese) {
    }

    private record QuizSession(Long userId, String mode, Map<String, String> answerMap, Map<String, String> englishMap) {
    }
}
