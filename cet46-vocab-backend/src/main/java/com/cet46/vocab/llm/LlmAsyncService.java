package com.cet46.vocab.llm;

import com.cet46.vocab.common.WordType;
import com.cet46.vocab.entity.WordMeta;
import com.cet46.vocab.mapper.WordMetaMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LlmAsyncService {

    private static final Logger log = LoggerFactory.getLogger(LlmAsyncService.class);
    private static final String CACHE_PREFIX = "llm:content:";
    private static final String TIMEOUT_FALLBACK_TEXT = "\u5185\u5bb9\u751f\u6210\u8d85\u65f6\uff0c\u5df2\u663e\u793a\u57fa\u7840\u91ca\u4e49";
    private static final Pattern FENCE_JSON_PATTERN = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*}");

    private final LlmCacheService llmCacheService;
    private final OllamaClient ollamaClient;
    private final CloudLlmClient cloudLlmClient;
    private final LlmResponseParser llmResponseParser;
    private final WordMetaMapper wordMetaMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Executor llmTaskExecutor;

    public LlmAsyncService(LlmCacheService llmCacheService,
                           OllamaClient ollamaClient,
                           CloudLlmClient cloudLlmClient,
                           LlmResponseParser llmResponseParser,
                           WordMetaMapper wordMetaMapper,
                           JdbcTemplate jdbcTemplate,
                           ObjectMapper objectMapper,
                           @Qualifier("llmTaskExecutor") Executor llmTaskExecutor) {
        this.llmCacheService = llmCacheService;
        this.ollamaClient = ollamaClient;
        this.cloudLlmClient = cloudLlmClient;
        this.llmResponseParser = llmResponseParser;
        this.wordMetaMapper = wordMetaMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.llmTaskExecutor = llmTaskExecutor;
    }

    @Async("llmTaskExecutor")
    public void generateWordContent(Long wordId, String wordType, String style, String provider) {
        doGenerateWordContent(wordId, wordType, style, provider, false);
    }

    @Async("llmTaskExecutor")
    public void regenerateWordContent(Long wordId, String wordType, String style, String provider) {
        doGenerateWordContent(wordId, wordType, style, provider, true);
    }

    @Async("llmTaskExecutor")
    public void generateWordExplainContent(Long wordId, String wordType, String style, String provider) {
        doGenerateWordExplainContent(wordId, wordType, style, provider, false);
    }

    @Async("llmTaskExecutor")
    public void regenerateWordExplainContent(Long wordId, String wordType, String style, String provider) {
        doGenerateWordExplainContent(wordId, wordType, style, provider, true);
    }

    private void doGenerateWordContent(Long wordId, String wordType, String style, String provider, boolean forceRefresh) {
        String normalizedProvider = LlmProvider.normalize(provider);
        try {
            String sentenceHash = CACHE_PREFIX + llmCacheService.buildHash(wordId, wordType, "sentence", style);
            String synonymHash = CACHE_PREFIX + llmCacheService.buildHash(wordId, wordType, "synonym", style);
            String mnemonicHash = CACHE_PREFIX + llmCacheService.buildHash(wordId, wordType, "mnemonic", style);

            if (forceRefresh) {
                llmCacheService.deleteCache(sentenceHash);
                llmCacheService.deleteCache(synonymHash);
                llmCacheService.deleteCache(mnemonicHash);
            }

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
            boolean timeoutOccurred = false;

            if (StringUtils.hasText(sentenceContent)
                    && StringUtils.hasText(synonymContent)
                    && StringUtils.hasText(mnemonicContent)) {
                LlmResponseParser.SentenceResult sentenceResult = llmResponseParser.parseSentence(sentenceContent);
                LlmResponseParser.SynonymResult synonymResult = llmResponseParser.parseSynonym(synonymContent);
                LlmResponseParser.MnemonicResult mnemonicResult = llmResponseParser.parseMnemonic(mnemonicContent);
                persistWordMeta(wordMeta, sentenceResult, synonymResult, mnemonicResult, sentenceHash, false);
                return;
            }

            if (!StringUtils.hasText(sentenceContent)) {
                try {
                    sentenceContent = safeGenerate(buildPrompt(PromptType.SENTENCE, style, wordBase, wordMeta.getPos()), normalizedProvider);
                } catch (Exception ex) {
                    timeoutOccurred = logGenerationFailure("sentence", wordId, wordType, style, normalizedProvider, ex) || timeoutOccurred;
                }
                if (StringUtils.hasText(sentenceContent)) {
                    llmCacheService.setCache(sentenceHash, sentenceContent);
                }
            }

            LlmResponseParser.SentenceResult sentenceResult = llmResponseParser.parseSentence(defaultString(sentenceContent));
            sentenceResult = ensureSentenceResult(sentenceResult, sentenceContent);
            if (hasSentenceContent(sentenceResult)) {
                persistPendingSentence(wordMeta, sentenceResult, sentenceHash);
            }

            GenerationAttempt synonymAttempt;
            GenerationAttempt mnemonicAttempt;
            if (LlmProvider.LOCAL.equals(normalizedProvider)) {
                // Local models are often single-worker; sequential calls reduce timeout/failure rate.
                synonymAttempt = generateSync(
                        synonymContent, "synonym", PromptType.SYNONYM,
                        style, wordBase, wordMeta.getPos(), normalizedProvider, wordId, wordType);
                mnemonicAttempt = generateSync(
                        mnemonicContent, "mnemonic", PromptType.MNEMONIC,
                        style, wordBase, wordMeta.getPos(), normalizedProvider, wordId, wordType);
            } else {
                CompletableFuture<GenerationAttempt> synonymFuture = generateAsync(
                        synonymContent, "synonym", PromptType.SYNONYM,
                        style, wordBase, wordMeta.getPos(), normalizedProvider, wordId, wordType);
                CompletableFuture<GenerationAttempt> mnemonicFuture = generateAsync(
                        mnemonicContent, "mnemonic", PromptType.MNEMONIC,
                        style, wordBase, wordMeta.getPos(), normalizedProvider, wordId, wordType);
                synonymAttempt = synonymFuture.join();
                mnemonicAttempt = mnemonicFuture.join();
            }

            timeoutOccurred = timeoutOccurred || synonymAttempt.timeoutOccurred() || mnemonicAttempt.timeoutOccurred();
            if (!StringUtils.hasText(synonymContent) && StringUtils.hasText(synonymAttempt.content())) {
                synonymContent = synonymAttempt.content();
                llmCacheService.setCache(synonymHash, synonymContent);
            }
            if (!StringUtils.hasText(mnemonicContent) && StringUtils.hasText(mnemonicAttempt.content())) {
                mnemonicContent = mnemonicAttempt.content();
                llmCacheService.setCache(mnemonicHash, mnemonicContent);
            }

            LlmResponseParser.SynonymResult synonymResult = llmResponseParser.parseSynonym(defaultString(synonymContent));
            LlmResponseParser.MnemonicResult mnemonicResult = llmResponseParser.parseMnemonic(defaultString(mnemonicContent));
            sentenceResult = ensureSentenceResult(sentenceResult, sentenceContent);
            synonymResult = ensureSynonymResult(synonymResult, synonymContent);
            mnemonicResult = ensureMnemonicResult(mnemonicResult, mnemonicContent);
            if (shouldTrySupplement(synonymResult, mnemonicResult)) {
                try {
                    String supplement = safeGenerate(buildSupplementPrompt(wordBase, wordMeta.getPos()), normalizedProvider);
                    LlmResponseParser.SynonymResult supplementSynonym = llmResponseParser.parseSynonym(defaultString(supplement));
                    LlmResponseParser.MnemonicResult supplementMnemonic = llmResponseParser.parseMnemonic(defaultString(supplement));
                    supplementSynonym = ensureSynonymResult(supplementSynonym, supplement);
                    supplementMnemonic = ensureMnemonicResult(supplementMnemonic, supplement);
                    if (!hasSynonymContent(synonymResult) && hasSynonymContent(supplementSynonym)) {
                        synonymResult = supplementSynonym;
                    }
                    if (!hasMnemonicContent(mnemonicResult) && hasMnemonicContent(supplementMnemonic)) {
                        mnemonicResult = supplementMnemonic;
                    }
                } catch (Exception ex) {
                    timeoutOccurred = logGenerationFailure("supplement", wordId, wordType, style, normalizedProvider, ex) || timeoutOccurred;
                }
            }

            persistWordMeta(wordMeta, sentenceResult, synonymResult, mnemonicResult, sentenceHash, timeoutOccurred);
        } catch (Exception ex) {
            log.error("generateWordContent failed, wordId={}, wordType={}, style={}, provider={}", wordId, wordType, style, normalizedProvider, ex);
            reconcileStatusAfterFailure(wordId, wordType, style);
        }
    }

    private void doGenerateWordExplainContent(Long wordId, String wordType, String style, String provider, boolean forceRefresh) {
        String normalizedProvider = LlmProvider.normalize(provider);
        String explainHash = CACHE_PREFIX + llmCacheService.buildHash(wordId, wordType, "explain", style);
        try {
            if (forceRefresh) {
                llmCacheService.deleteCache(explainHash);
            }
            WordBase wordBase = loadWordBase(wordId, wordType);
            if (wordBase == null) {
                return;
            }
            WordMeta wordMeta = ensureWordMetaForExplain(wordId, wordType, style, wordBase.english);
            if (wordMeta == null) {
                return;
            }
            wordMeta.setAiExplainStatus("pending");
            wordMetaMapper.updateById(wordMeta);

            String explain = llmCacheService.getCache(explainHash);
            String pos = StringUtils.hasText(wordMeta.getPos()) ? wordMeta.getPos() : parsePos(wordBase.chinese);
            if (!StringUtils.hasText(explain)) {
                String prompt = buildExplainPrompt(wordBase, pos, resolveLevel(wordType), "");
                explain = safeGenerate(prompt, normalizedProvider);
                if (StringUtils.hasText(explain)) {
                    llmCacheService.setCache(explainHash, explain);
                }
            }

            if (StringUtils.hasText(explain)) {
                wordMeta.setAiExplain(normalizeExplainDisplay(explain, pos));
                wordMeta.setAiExplainStatus("full");
            } else {
                wordMeta.setAiExplainStatus("fallback");
            }
            wordMetaMapper.updateById(wordMeta);
        } catch (Exception ex) {
            logGenerationFailure("explain", wordId, wordType, style, normalizedProvider, ex);
            try {
                WordMeta wordMeta = wordMetaMapper.selectByWordAndStyle(wordId, wordType, style);
                if (wordMeta != null) {
                    if (!StringUtils.hasText(wordMeta.getAiExplain())) {
                        wordMeta.setAiExplainStatus("fallback");
                    } else {
                        wordMeta.setAiExplainStatus("full");
                    }
                    wordMetaMapper.updateById(wordMeta);
                }
            } catch (Exception inner) {
                log.warn("update explain status failed, wordId={}, wordType={}, style={}",
                        wordId, wordType, style, inner);
            }
        }
    }

    private WordMeta ensurePendingWordMeta(Long wordId, String wordType, String style, String promptHash) {
        WordBase wordBase = loadWordBase(wordId, wordType);
        String english = wordBase == null ? "" : defaultString(wordBase.english);
        WordMeta wordMeta = wordMetaMapper.selectByWordAndStyle(wordId, wordType, style);
        if (wordMeta == null) {
            WordMeta created = WordMeta.builder()
                    .wordId(wordId)
                    .wordType(wordType)
                    .word(english)
                    .style(style)
                    .genStatus("pending")
                    .promptHash(promptHash)
                    .build();
            try {
                wordMetaMapper.insert(created);
                return created;
            } catch (DuplicateKeyException duplicateKeyException) {
                // Another async task inserted the same (word_id, word_type, style) row first.
                wordMeta = wordMetaMapper.selectByWordAndStyle(wordId, wordType, style);
                if (wordMeta == null) {
                    throw duplicateKeyException;
                }
            }
        }

        if (!StringUtils.hasText(wordMeta.getWord())) {
            wordMeta.setWord(english);
        }
        wordMeta.setGenStatus("pending");
        wordMeta.setPromptHash(promptHash);
        wordMetaMapper.updateById(wordMeta);
        return wordMeta;
    }

    private void persistWordMeta(WordMeta wordMeta,
                                 LlmResponseParser.SentenceResult sentenceResult,
                                 LlmResponseParser.SynonymResult synonymResult,
                                 LlmResponseParser.MnemonicResult mnemonicResult,
                                 String promptHash,
                                 boolean timeoutOccurred) {
        boolean sentenceOk = sentenceResult != null && StringUtils.hasText(sentenceResult.sentenceEn());
        boolean synonymOk = synonymResult != null && synonymResult.synonyms() != null && !synonymResult.synonyms().isEmpty();
        boolean sentenceZhOk = sentenceResult != null && StringUtils.hasText(sentenceResult.sentenceZh());
        boolean mnemonicOk = mnemonicResult != null
                && (StringUtils.hasText(mnemonicResult.mnemonic()) || StringUtils.hasText(mnemonicResult.rootAnalysis()));
        sentenceOk = sentenceOk || sentenceZhOk;

        String genStatus;
        if (sentenceOk && synonymOk && mnemonicOk) {
            genStatus = "full";
        } else if (sentenceOk || synonymOk || mnemonicOk) {
            genStatus = "partial";
        } else {
            genStatus = "fallback";
        }

        String sentenceZh = sentenceResult == null ? null : sentenceResult.sentenceZh();
        if ("fallback".equals(genStatus) && timeoutOccurred && !StringUtils.hasText(sentenceZh)) {
            sentenceZh = TIMEOUT_FALLBACK_TEXT;
        }

        wordMeta.setSentenceEn(sentenceResult == null ? null : sentenceResult.sentenceEn());
        wordMeta.setSentenceZh(sentenceZh);
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
            wordMeta.setSentenceZh(TIMEOUT_FALLBACK_TEXT);
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
        WordType type = WordType.from(wordType);
        if (type == null) {
            return null;
        }

        String sql = "SELECT english, sent, chinese FROM " + type.tableName() + " WHERE id = ? LIMIT 1";
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
    private String safeGenerate(String prompt, String provider) {
        if (!StringUtils.hasText(prompt)) {
            return null;
        }
        if (LlmProvider.CLOUD.equals(provider)) {
            return cloudLlmClient.generate(prompt);
        }
        return ollamaClient.generate(prompt);
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

    private boolean hasSentenceContent(LlmResponseParser.SentenceResult sentenceResult) {
        return sentenceResult != null
                && (StringUtils.hasText(sentenceResult.sentenceEn()) || StringUtils.hasText(sentenceResult.sentenceZh()));
    }

    private boolean hasSynonymContent(LlmResponseParser.SynonymResult synonymResult) {
        return synonymResult != null && synonymResult.synonyms() != null && !synonymResult.synonyms().isEmpty();
    }

    private boolean hasMnemonicContent(LlmResponseParser.MnemonicResult mnemonicResult) {
        return mnemonicResult != null
                && (StringUtils.hasText(mnemonicResult.mnemonic()) || StringUtils.hasText(mnemonicResult.rootAnalysis()));
    }

    private boolean shouldTrySupplement(LlmResponseParser.SynonymResult synonymResult,
                                        LlmResponseParser.MnemonicResult mnemonicResult) {
        return !hasSynonymContent(synonymResult) || !hasMnemonicContent(mnemonicResult);
    }

    private void persistPendingSentence(WordMeta wordMeta,
                                        LlmResponseParser.SentenceResult sentenceResult,
                                        String promptHash) {
        if (wordMeta == null || !hasSentenceContent(sentenceResult)) {
            return;
        }
        try {
            wordMeta.setSentenceEn(sentenceResult.sentenceEn());
            wordMeta.setSentenceZh(sentenceResult.sentenceZh());
            wordMeta.setSentenceDifficulty(sentenceResult.difficulty());
            wordMeta.setGenStatus("pending");
            wordMeta.setPromptHash(promptHash);
            if (wordMeta.getId() == null) {
                wordMetaMapper.insert(wordMeta);
            } else {
                wordMetaMapper.updateById(wordMeta);
            }
        } catch (Exception ex) {
            log.warn("persist pending sentence failed, wordMetaId={}", wordMeta.getId(), ex);
        }
    }

    private LlmResponseParser.SentenceResult ensureSentenceResult(LlmResponseParser.SentenceResult parsed, String rawContent) {
        if (parsed != null && (StringUtils.hasText(parsed.sentenceEn()) || StringUtils.hasText(parsed.sentenceZh()))) {
            return parsed;
        }
        String plain = toPlainText(rawContent, 240);
        if (!StringUtils.hasText(plain)) {
            return parsed;
        }
        return new LlmResponseParser.SentenceResult(null, plain, null);
    }

    private LlmResponseParser.SynonymResult ensureSynonymResult(LlmResponseParser.SynonymResult parsed, String rawContent) {
        if (parsed != null && parsed.synonyms() != null && !parsed.synonyms().isEmpty()) {
            return parsed;
        }
        String plain = toPlainText(rawContent, 300);
        if (!StringUtils.hasText(plain)) {
            return parsed;
        }
        List<LlmResponseParser.SynonymItem> fallbackItems = new ArrayList<>();
        fallbackItems.add(new LlmResponseParser.SynonymItem(null, plain, null));
        return new LlmResponseParser.SynonymResult(fallbackItems);
    }

    private LlmResponseParser.MnemonicResult ensureMnemonicResult(LlmResponseParser.MnemonicResult parsed, String rawContent) {
        if (parsed != null && (StringUtils.hasText(parsed.mnemonic()) || StringUtils.hasText(parsed.rootAnalysis()))) {
            return parsed;
        }
        String plain = toPlainText(rawContent, 240);
        if (!StringUtils.hasText(plain)) {
            return parsed;
        }
        return new LlmResponseParser.MnemonicResult(plain, null);
    }

    private String toPlainText(String rawContent, int maxLen) {
        if (!StringUtils.hasText(rawContent)) {
            return null;
        }
        String text = rawContent
                .replace("```json", "")
                .replace("```", "")
                .replace("\r", " ")
                .replace("\n", " ")
                .trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }

    private String buildSupplementPrompt(WordBase wordBase, String pos) {
        return """
                Return ONLY valid JSON:
                {
                  "word":"%s",
                  "synonyms":[
                    {"synonym":"...", "difference":"Chinese explanation", "example":"English example"}
                  ],
                  "mnemonic":"Chinese memory tip",
                  "root_analysis":"optional"
                }
                word=%s
                phonetic=%s
                pos=%s
                chinese=%s
                """.formatted(
                defaultString(wordBase.english),
                defaultString(wordBase.english),
                defaultString(wordBase.phonetic),
                defaultString(pos),
                defaultString(wordBase.chinese)
        );
    }

    private String buildExplainPrompt(WordBase wordBase, String pos, String level, String context) {
        return PromptTemplate.SMART_EXPLAIN_JSON
                .replace("{{word}}", defaultString(wordBase.english))
                .replace("{{phonetic}}", defaultString(wordBase.phonetic))
                .replace("{{pos}}", defaultString(pos))
                .replace("{{level}}", defaultString(level))
                .replace("{{context}}", defaultString(context))
                .replace("{{chinese}}", defaultString(wordBase.chinese));
    }

    private String resolveLevel(String wordType) {
        WordType type = WordType.from(wordType);
        if (type == null) {
            return "";
        }
        if (type == WordType.CET6 || type == WordType.CET6_LX) {
            return "CET6";
        }
        return "CET4";
    }
    private String normalizeExplainDisplay(String rawExplain, String pos) {
        JsonNode node = parseJsonNode(rawExplain);
        if (node == null) {
            String plain = toPlainText(rawExplain, 800);
            if (!StringUtils.hasText(plain)) {
                return null;
            }
            if (plain.contains("Grammar usage:")) {
                return plain;
            }
            String fallbackGrammar = fallbackGrammarUsage(pos);
            if (!StringUtils.hasText(fallbackGrammar)) {
                return plain;
            }
            return plain + "\nGrammar usage:" + fallbackGrammar;
        }

        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, getText(node, "word"), "Word: ");

        JsonNode meanings = node.get("core_meanings");
        if (meanings != null && meanings.isArray() && !meanings.isEmpty()) {
            List<String> lines = new ArrayList<>();
            int count = 0;
            for (JsonNode meaning : meanings) {
                if (meaning == null || !meaning.isObject() || count >= 3) {
                    continue;
                }
                String sense = getText(meaning, "sense");
                String cn = getText(meaning, "cn_explanation");
                String line = StringUtils.hasText(sense) && StringUtils.hasText(cn)
                        ? sense + ": " + cn
                        : (StringUtils.hasText(cn) ? cn : sense);
                if (StringUtils.hasText(line)) {
                    lines.add(line);
                    count++;
                }
            }
            if (!lines.isEmpty()) {
                appendIfPresent(sb, String.join("; ", lines), "Core meanings: ");
            }
        }

        JsonNode examUsage = node.get("exam_usage");
        if (examUsage != null && examUsage.isObject()) {
            appendIfPresent(sb, getText(examUsage, "note"), "Exam usage: ");
        }
        appendIfPresent(sb, getText(node, "memory_tip"), "Memory tip: ");

        String grammarUsage = buildGrammarUsage(node.get("grammar_usage"));
        if (!StringUtils.hasText(grammarUsage)) {
            grammarUsage = fallbackGrammarUsage(pos);
        }
        appendIfPresent(sb, grammarUsage, "Grammar usage:");

        JsonNode confusables = node.get("confusables");
        if (confusables != null && confusables.isArray() && !confusables.isEmpty()) {
            JsonNode first = confusables.get(0);
            if (first != null && first.isObject()) {
                String word = getText(first, "word");
                String difference = getText(first, "difference");
                if (StringUtils.hasText(word) || StringUtils.hasText(difference)) {
                    appendIfPresent(
                            sb,
                            defaultString(word) + (StringUtils.hasText(difference) ? ": " + difference : ""),
                            "Confusable: "
                    );
                }
            }
        }

        String normalized = sb.toString().trim();
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        return toPlainText(rawExplain, 800);
    }

    private JsonNode parseJsonNode(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String trimmed = content.trim();
        List<String> candidates = new ArrayList<>();
        candidates.add(trimmed);

        Matcher fenceMatcher = FENCE_JSON_PATTERN.matcher(trimmed);
        if (fenceMatcher.find()) {
            candidates.add(fenceMatcher.group(1).trim());
        }

        Matcher objectMatcher = JSON_OBJECT_PATTERN.matcher(trimmed);
        if (objectMatcher.find()) {
            candidates.add(objectMatcher.group());
        }

        for (String candidate : candidates) {
            try {
                JsonNode root = objectMapper.readTree(candidate);
                if (root != null && root.isObject()) {
                    return root;
                }
            } catch (Exception ignore) {
                // Try next candidate.
            }
        }
        return null;
    }

    private String getText(JsonNode node, String key) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        if (!StringUtils.hasText(text) || "null".equalsIgnoreCase(text.trim())) {
            return null;
        }
        return text.trim();
    }

    private void appendIfPresent(StringBuilder sb, String value, String prefix) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(prefix).append(value.trim());
    }

    private String buildGrammarUsage(JsonNode grammarNode) {
        if (grammarNode == null || !grammarNode.isObject()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        String countability = getText(grammarNode, "countability");
        if (StringUtils.hasText(countability)) {
            parts.add(countability);
        }
        String verbPatterns = joinArray(grammarNode.get("verb_patterns"), 2);
        if (StringUtils.hasText(verbPatterns)) {
            parts.add("Verb patterns: " + verbPatterns);
        }
        String structures = joinArray(grammarNode.get("common_structures"), 2);
        if (StringUtils.hasText(structures)) {
            parts.add("Common structures: " + structures);
        }
        String usageTip = getText(grammarNode, "usage_tip");
        if (StringUtils.hasText(usageTip)) {
            parts.add(usageTip);
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join("; ", parts);
    }

    private String fallbackGrammarUsage(String pos) {
        if (!StringUtils.hasText(pos)) {
            return null;
        }
        String normalized = pos.toLowerCase();
        if (normalized.contains("n")) {
            return "Pay attention to countability, singular/plural, and article usage.";
        }
        if (normalized.contains("v")) {
            return "Pay attention to tense changes and common verb patterns (to do / doing / transitive).";
        }
        if (normalized.contains("adj")) {
            return "Used mainly as modifier/predicative; pay attention to comparative forms and collocations.";
        }
        if (normalized.contains("adv")) {
            return "Usually modifies verbs/adjectives; pay attention to position in sentence.";
        }
        return "Pay attention to sentence role and fixed collocations.";
    }

    private String joinArray(JsonNode node, int limit) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        int count = 0;
        for (JsonNode item : node) {
            if (count >= limit) {
                break;
            }
            if (item == null || item.isNull()) {
                continue;
            }
            String text = item.asText();
            if (!StringUtils.hasText(text) || "null".equalsIgnoreCase(text.trim())) {
                continue;
            }
            values.add(text.trim());
            count++;
        }
        if (values.isEmpty()) {
            return null;
        }
        return String.join(" / ", values);
    }

    private WordMeta ensureWordMetaForExplain(Long wordId, String wordType, String style, String english) {
        WordMeta wordMeta = wordMetaMapper.selectByWordAndStyle(wordId, wordType, style);
        if (wordMeta != null) {
            if (!StringUtils.hasText(wordMeta.getWord())) {
                wordMeta.setWord(defaultString(english));
                wordMetaMapper.updateById(wordMeta);
            }
            return wordMeta;
        }

        WordMeta created = WordMeta.builder()
                .wordId(wordId)
                .wordType(wordType)
                .word(defaultString(english))
                .style(style)
                .genStatus("pending")
                .promptHash(CACHE_PREFIX + llmCacheService.buildHash(wordId, wordType, "sentence", style))
                .aiExplainStatus("pending")
                .build();
        try {
            wordMetaMapper.insert(created);
            return created;
        } catch (DuplicateKeyException duplicateKeyException) {
            WordMeta concurrent = wordMetaMapper.selectByWordAndStyle(wordId, wordType, style);
            if (concurrent != null) {
                if (!StringUtils.hasText(concurrent.getWord())) {
                    concurrent.setWord(defaultString(english));
                    wordMetaMapper.updateById(concurrent);
                }
                return concurrent;
            }
            throw duplicateKeyException;
        }
    }
    private CompletableFuture<GenerationAttempt> generateAsync(String existingContent,
                                                                String promptType,
                                                                PromptType promptEnum,
                                                                String style,
                                                                WordBase wordBase,
                                                                String pos,
                                                                String provider,
                                                                Long wordId,
                                                                String wordType) {
        if (StringUtils.hasText(existingContent)) {
            return CompletableFuture.completedFuture(new GenerationAttempt(existingContent, false));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = buildPrompt(promptEnum, style, wordBase, pos);
                String content = safeGenerate(prompt, provider);
                return new GenerationAttempt(content, false);
            } catch (Exception ex) {
                boolean timeoutOccurred = logGenerationFailure(promptType, wordId, wordType, style, provider, ex);
                return new GenerationAttempt(null, timeoutOccurred);
            }
        }, llmTaskExecutor);
    }

    private GenerationAttempt generateSync(String existingContent,
                                           String promptType,
                                           PromptType promptEnum,
                                           String style,
                                           WordBase wordBase,
                                           String pos,
                                           String provider,
                                           Long wordId,
                                           String wordType) {
        if (StringUtils.hasText(existingContent)) {
            return new GenerationAttempt(existingContent, false);
        }
        try {
            String prompt = buildPrompt(promptEnum, style, wordBase, pos);
            String content = safeGenerate(prompt, provider);
            return new GenerationAttempt(content, false);
        } catch (Exception ex) {
            boolean timeoutOccurred = logGenerationFailure(promptType, wordId, wordType, style, provider, ex);
            return new GenerationAttempt(null, timeoutOccurred);
        }
    }

    private boolean logGenerationFailure(String promptType,
                                         Long wordId,
                                         String wordType,
                                         String style,
                                         String provider,
                                         Exception ex) {
        LlmFailureType failureType = classifyFailure(ex);
        String reason = buildFailureReason(ex);
        if (failureType == LlmFailureType.TIMEOUT) {
            log.warn("{} generation timeout, wordId={}, wordType={}, style={}, provider={}, reason={}",
                    promptType, wordId, wordType, style, provider, reason);
            return true;
        }
        if (failureType == LlmFailureType.AUTH) {
            log.error("{} generation auth failed, wordId={}, wordType={}, style={}, provider={}, reason={}",
                    promptType, wordId, wordType, style, provider, reason);
            return false;
        }
        if (failureType == LlmFailureType.CONNECTION) {
            log.error("{} generation connection failed, wordId={}, wordType={}, style={}, provider={}, reason={}",
                    promptType, wordId, wordType, style, provider, reason);
            return false;
        }
        log.error("{} generation failed, wordId={}, wordType={}, style={}, provider={}, reason={}",
                promptType, wordId, wordType, style, provider, reason, ex);
        return false;
    }

    private LlmFailureType classifyFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException
                    || current instanceof SocketTimeoutException) {
                return LlmFailureType.TIMEOUT;
            }
            if (current instanceof HttpStatusCodeException statusCodeException) {
                int code = statusCodeException.getStatusCode().value();
                if (code == 401 || code == 403) {
                    return LlmFailureType.AUTH;
                }
            }
            if (current instanceof ConnectException || current instanceof UnknownHostException) {
                return LlmFailureType.CONNECTION;
            }
            if (current instanceof ResourceAccessException resourceAccessException) {
                String lowered = lowerCase(resourceAccessException.getMessage());
                if (lowered.contains("timed out")) {
                    return LlmFailureType.TIMEOUT;
                }
                return LlmFailureType.CONNECTION;
            }
            current = current.getCause();
        }
        String message = lowerCase(throwable == null ? null : throwable.getMessage());
        if (message.contains("api-key")
                || message.contains("unauthorized")
                || message.contains("forbidden")) {
            return LlmFailureType.AUTH;
        }
        if (message.contains("timed out")) {
            return LlmFailureType.TIMEOUT;
        }
        if (message.contains("connection refused")
                || message.contains("failed to connect")
                || message.contains("unknown host")
                || message.contains("unexpected error occurred on a send")) {
            return LlmFailureType.CONNECTION;
        }
        return LlmFailureType.OTHER;
    }

    private String buildFailureReason(Throwable throwable) {
        Throwable root = throwable;
        while (root != null && root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root == null) {
            return "unknown";
        }
        String message = StringUtils.hasText(root.getMessage()) ? root.getMessage() : "no message";
        return root.getClass().getSimpleName() + ": " + message;
    }

    private String lowerCase(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private void reconcileStatusAfterFailure(Long wordId, String wordType, String style) {
        try {
            WordMeta wordMeta = wordMetaMapper.selectByWordAndStyle(wordId, wordType, style);
            if (wordMeta == null || !"pending".equalsIgnoreCase(wordMeta.getGenStatus())) {
                return;
            }
            boolean sentenceOk = StringUtils.hasText(wordMeta.getSentenceEn()) || StringUtils.hasText(wordMeta.getSentenceZh());
            boolean synonymOk = StringUtils.hasText(wordMeta.getSynonymsJson())
                    && !"[]".equals(wordMeta.getSynonymsJson().trim())
                    && !"null".equalsIgnoreCase(wordMeta.getSynonymsJson().trim());
            boolean mnemonicOk = StringUtils.hasText(wordMeta.getMnemonic()) || StringUtils.hasText(wordMeta.getRootAnalysis());

            if (sentenceOk && synonymOk && mnemonicOk) {
                wordMeta.setGenStatus("full");
            } else if (sentenceOk || synonymOk || mnemonicOk) {
                wordMeta.setGenStatus("partial");
            } else {
                wordMeta.setGenStatus("fallback");
                if (!StringUtils.hasText(wordMeta.getSentenceZh())) {
                    wordMeta.setSentenceZh(TIMEOUT_FALLBACK_TEXT);
                }
            }
            wordMetaMapper.updateById(wordMeta);
        } catch (Exception ex) {
            log.warn("reconcile llm status after failure failed, wordId={}, wordType={}, style={}",
                    wordId, wordType, style, ex);
        }
    }

    private record WordBase(String english, String phonetic, String chinese) {
    }

    private enum PromptType {
        SENTENCE,
        SYNONYM,
        MNEMONIC
    }

    private enum LlmFailureType {
        TIMEOUT,
        CONNECTION,
        AUTH,
        OTHER
    }

    private record GenerationAttempt(String content, boolean timeoutOccurred) {
    }
}
