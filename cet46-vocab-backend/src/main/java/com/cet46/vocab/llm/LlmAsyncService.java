package com.cet46.vocab.llm;

import com.cet46.vocab.entity.WordMeta;
import com.cet46.vocab.mapper.WordMetaMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LlmAsyncService {

    private static final Logger log = LoggerFactory.getLogger(LlmAsyncService.class);
    private static final String CACHE_PREFIX = "llm:content:";

    private final LlmCacheService llmCacheService;
    private final OllamaClient ollamaClient;
    private final LlmResponseParser llmResponseParser;
    private final WordMetaMapper wordMetaMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public LlmAsyncService(LlmCacheService llmCacheService,
                           OllamaClient ollamaClient,
                           LlmResponseParser llmResponseParser,
                           WordMetaMapper wordMetaMapper,
                           JdbcTemplate jdbcTemplate,
                           ObjectMapper objectMapper) {
        this.llmCacheService = llmCacheService;
        this.ollamaClient = ollamaClient;
        this.llmResponseParser = llmResponseParser;
        this.wordMetaMapper = wordMetaMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Async("llmTaskExecutor")
    public void generateWordContent(Long wordId, String wordType, String style) {
        try {
            String sentenceHash = CACHE_PREFIX + llmCacheService.buildHash(wordId, wordType, "sentence", style);
            String synonymHash = CACHE_PREFIX + llmCacheService.buildHash(wordId, wordType, "synonym", style);
            String mnemonicHash = CACHE_PREFIX + llmCacheService.buildHash(wordId, wordType, "mnemonic", style);

            WordMeta wordMeta = ensurePendingWordMeta(wordId, wordType, style, sentenceHash);
            WordBase wordBase = loadWordBase(wordId, wordType);
            if (wordBase == null) {
                log.error("word base not found, wordId={}, wordType={}", wordId, wordType);
                updateWordMetaToFallback(wordMeta);
                return;
            }

            if (!StringUtils.hasText(wordMeta.getPos())) {
                wordMeta.setPos(parsePos(wordBase.chinese));
            }

            String sentenceContent = llmCacheService.getCache(sentenceHash);
            String synonymContent = llmCacheService.getCache(synonymHash);
            String mnemonicContent = llmCacheService.getCache(mnemonicHash);

            // 2) 三个缓存都存在：直接解析并写库
            if (StringUtils.hasText(sentenceContent)
                    && StringUtils.hasText(synonymContent)
                    && StringUtils.hasText(mnemonicContent)) {
                LlmResponseParser.SentenceResult sentenceResult = llmResponseParser.parseSentence(sentenceContent);
                LlmResponseParser.SynonymResult synonymResult = llmResponseParser.parseSynonym(synonymContent);
                LlmResponseParser.MnemonicResult mnemonicResult = llmResponseParser.parseMnemonic(mnemonicContent);
                persistWordMeta(wordMeta, sentenceResult, synonymResult, mnemonicResult, sentenceHash);
                return;
            }

            // 3) 缺失内容才调用 LLM
            if (!StringUtils.hasText(sentenceContent)) {
                sentenceContent = safeGenerate(buildPrompt(PromptType.SENTENCE, style, wordBase, wordMeta.getPos()));
                if (StringUtils.hasText(sentenceContent)) {
                    llmCacheService.setCache(sentenceHash, sentenceContent);
                }
            }
            if (!StringUtils.hasText(synonymContent)) {
                synonymContent = safeGenerate(buildPrompt(PromptType.SYNONYM, style, wordBase, wordMeta.getPos()));
                if (StringUtils.hasText(synonymContent)) {
                    llmCacheService.setCache(synonymHash, synonymContent);
                }
            }
            if (!StringUtils.hasText(mnemonicContent)) {
                mnemonicContent = safeGenerate(buildPrompt(PromptType.MNEMONIC, style, wordBase, wordMeta.getPos()));
                if (StringUtils.hasText(mnemonicContent)) {
                    llmCacheService.setCache(mnemonicHash, mnemonicContent);
                }
            }

            // 4) 解析与状态判定
            LlmResponseParser.SentenceResult sentenceResult = llmResponseParser.parseSentence(defaultString(sentenceContent));
            LlmResponseParser.SynonymResult synonymResult = llmResponseParser.parseSynonym(defaultString(synonymContent));
            LlmResponseParser.MnemonicResult mnemonicResult = llmResponseParser.parseMnemonic(defaultString(mnemonicContent));

            // 5) 6) 写库与缓存（缓存已在成功生成后写入）
            persistWordMeta(wordMeta, sentenceResult, synonymResult, mnemonicResult, sentenceHash);
        } catch (Exception ex) {
            // 7) 任何异常都不能崩溃异步线程
            log.error("generateWordContent failed, wordId={}, wordType={}, style={}", wordId, wordType, style, ex);
        }
    }

    private WordMeta ensurePendingWordMeta(Long wordId, String wordType, String style, String promptHash) {
        WordMeta wordMeta = wordMetaMapper.selectByWordAndStyle(wordId, wordType, style);
        if (wordMeta == null) {
            wordMeta = WordMeta.builder()
                    .wordId(wordId)
                    .wordType(wordType)
                    .style(style)
                    .genStatus("pending")
                    .promptHash(promptHash)
                    .build();
            wordMetaMapper.insert(wordMeta);
        } else {
            wordMeta.setGenStatus("pending");
            wordMeta.setPromptHash(promptHash);
            wordMetaMapper.updateById(wordMeta);
        }
        return wordMeta;
    }

    private void persistWordMeta(WordMeta wordMeta,
                                 LlmResponseParser.SentenceResult sentenceResult,
                                 LlmResponseParser.SynonymResult synonymResult,
                                 LlmResponseParser.MnemonicResult mnemonicResult,
                                 String promptHash) {
        boolean sentenceOk = sentenceResult != null && StringUtils.hasText(sentenceResult.sentenceEn());
        boolean synonymOk = synonymResult != null && synonymResult.synonyms() != null && !synonymResult.synonyms().isEmpty();
        boolean mnemonicOk = mnemonicResult != null && StringUtils.hasText(mnemonicResult.mnemonic());

        String genStatus;
        if (sentenceOk && synonymOk && mnemonicOk) {
            genStatus = "full";
        } else if (sentenceOk) {
            genStatus = "partial";
        } else {
            genStatus = "fallback";
        }

        wordMeta.setSentenceEn(sentenceResult == null ? null : sentenceResult.sentenceEn());
        wordMeta.setSentenceZh(sentenceResult == null ? null : sentenceResult.sentenceZh());
        wordMeta.setSentenceDifficulty(sentenceResult == null ? null : sentenceResult.difficulty());
        wordMeta.setSynonymsJson(toSynonymsJson(synonymResult));
        wordMeta.setMnemonic(mnemonicResult == null ? null : mnemonicResult.mnemonic());
        wordMeta.setRootAnalysis(mnemonicResult == null ? null : mnemonicResult.rootAnalysis());
        wordMeta.setGenStatus(genStatus);
        wordMeta.setPromptHash(promptHash);

        if (wordMeta.getId() == null) {
            wordMetaMapper.insert(wordMeta);
        } else {
            wordMetaMapper.updateById(wordMeta);
        }
    }

    private void updateWordMetaToFallback(WordMeta wordMeta) {
        try {
            wordMeta.setGenStatus("fallback");
            if (wordMeta.getId() == null) {
                wordMetaMapper.insert(wordMeta);
            } else {
                wordMetaMapper.updateById(wordMeta);
            }
        } catch (Exception ex) {
            log.error("update fallback status failed", ex);
        }
    }

    private WordBase loadWordBase(Long wordId, String wordType) {
        String tableName;
        if ("cet4".equalsIgnoreCase(wordType)) {
            tableName = "cet4zx";
        } else if ("cet6".equalsIgnoreCase(wordType)) {
            tableName = "cet6zx";
        } else {
            return null;
        }

        String sql = "SELECT english, sent, chinese FROM " + tableName + " WHERE id = ? LIMIT 1";
        List<WordBase> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new WordBase(
                        rs.getString("english"),
                        rs.getString("sent"),
                        rs.getString("chinese")
                ),
                wordId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String safeGenerate(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return null;
        }
        try {
            return ollamaClient.generate(prompt);
        } catch (Exception ex) {
            log.error("ollama generate failed", ex);
            return null;
        }
    }

    private String buildPrompt(PromptType type, String style, WordBase wordBase, String pos) {
        String template;
        if (type == PromptType.SENTENCE) {
            template = switch (style) {
                case "academic" -> PromptTemplate.SENTENCE_ACADEMIC;
                case "sarcastic" -> PromptTemplate.SENTENCE_SARCASTIC;
                default -> PromptTemplate.SENTENCE_STORY;
            };
        } else if (type == PromptType.SYNONYM) {
            template = switch (style) {
                case "academic" -> PromptTemplate.SYNONYM_ACADEMIC;
                case "sarcastic" -> PromptTemplate.SYNONYM_SARCASTIC;
                default -> PromptTemplate.SYNONYM_STORY;
            };
        } else {
            template = switch (style) {
                case "academic" -> PromptTemplate.MNEMONIC_ACADEMIC;
                case "sarcastic" -> PromptTemplate.MNEMONIC_SARCASTIC;
                default -> PromptTemplate.MNEMONIC_STORY;
            };
        }

        return template
                .replace("{{word}}", defaultString(wordBase.english))
                .replace("{{phonetic}}", defaultString(wordBase.phonetic))
                .replace("{{pos}}", defaultString(pos))
                .replace("{{chinese}}", defaultString(wordBase.chinese));
    }

    private String parsePos(String chinese) {
        if (!StringUtils.hasText(chinese)) {
            return null;
        }
        List<String> found = new ArrayList<>();
        Map<String, String> patterns = Map.of(
                "v", "\\bv\\.",
                "n", "\\bn\\.",
                "adj", "\\badj\\.",
                "adv", "\\badv\\.",
                "prep", "\\bprep\\.",
                "conj", "\\bconj\\.",
                "pron", "\\bpron\\.",
                "int", "\\bint\\."
        );
        for (Map.Entry<String, String> entry : patterns.entrySet()) {
            Matcher matcher = Pattern.compile(entry.getValue(), Pattern.CASE_INSENSITIVE).matcher(chinese);
            if (matcher.find()) {
                found.add(entry.getKey());
            }
        }
        return found.isEmpty() ? null : String.join(",", found);
    }

    private String toSynonymsJson(LlmResponseParser.SynonymResult synonymResult) {
        if (synonymResult == null || synonymResult.synonyms() == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(synonymResult.synonyms());
        } catch (JsonProcessingException ex) {
            log.error("serialize synonym json failed", ex);
            return null;
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private record WordBase(String english, String phonetic, String chinese) {
    }

    private enum PromptType {
        SENTENCE,
        SYNONYM,
        MNEMONIC
    }
}
