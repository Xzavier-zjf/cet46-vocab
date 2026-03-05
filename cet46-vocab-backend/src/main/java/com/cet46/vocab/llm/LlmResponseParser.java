package com.cet46.vocab.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LlmResponseParser {

    private static final Pattern QUOTE_PATTERN = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern ARRAY_ITEM_PATTERN = Pattern.compile(
            "\\{\\s*\"synonym\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"difference\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"example\"\\s*:\\s*\"([^\"]*)\"\\s*\\}"
    );

    private final ObjectMapper objectMapper;

    public LlmResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SentenceResult parseSentence(String content) {
        SentenceResult result = parseSentenceByJson(content);
        if (result != null) {
            return result;
        }
        return parseSentenceByRegex(content);
    }

    public SynonymResult parseSynonym(String content) {
        SynonymResult result = parseSynonymByJson(content);
        if (result != null) {
            return result;
        }
        return parseSynonymByRegex(content);
    }

    public MnemonicResult parseMnemonic(String content) {
        MnemonicResult result = parseMnemonicByJson(content);
        if (result != null) {
            return result;
        }
        return parseMnemonicByRegex(content);
    }

    private SentenceResult parseSentenceByJson(String content) {
        try {
            JsonNode node = objectMapper.readTree(content);
            String sentenceEn = getText(node, "sentence_en", "sentenceEn");
            String sentenceZh = getText(node, "sentence_zh", "sentenceZh");
            String difficulty = getText(node, "difficulty");
            if (sentenceEn == null && sentenceZh == null && difficulty == null) {
                return null;
            }
            return new SentenceResult(sentenceEn, sentenceZh, difficulty);
        } catch (Exception ex) {
            return null;
        }
    }

    private SynonymResult parseSynonymByJson(String content) {
        try {
            JsonNode node = objectMapper.readTree(content);
            JsonNode arrayNode = node.get("synonyms");
            if (arrayNode == null || !arrayNode.isArray()) {
                return null;
            }
            List<SynonymItem> items = new ArrayList<>();
            for (JsonNode item : arrayNode) {
                items.add(new SynonymItem(
                        getText(item, "synonym"),
                        getText(item, "difference"),
                        getText(item, "example")
                ));
            }
            if (items.isEmpty()) {
                return null;
            }
            return new SynonymResult(items);
        } catch (Exception ex) {
            return null;
        }
    }

    private MnemonicResult parseMnemonicByJson(String content) {
        try {
            JsonNode node = objectMapper.readTree(content);
            String mnemonic = getText(node, "mnemonic");
            String rootAnalysis = getText(node, "root_analysis", "rootAnalysis");
            if (mnemonic == null && rootAnalysis == null) {
                return null;
            }
            return new MnemonicResult(mnemonic, rootAnalysis);
        } catch (Exception ex) {
            return null;
        }
    }

    private SentenceResult parseSentenceByRegex(String content) {
        String sentenceEn = extractByKey(content, "sentence_en");
        if (sentenceEn == null) {
            sentenceEn = extractByKey(content, "sentenceEn");
        }
        String sentenceZh = extractByKey(content, "sentence_zh");
        if (sentenceZh == null) {
            sentenceZh = extractByKey(content, "sentenceZh");
        }
        String difficulty = extractByKey(content, "difficulty");
        if (sentenceEn == null && sentenceZh == null && difficulty == null) {
            return null;
        }
        return new SentenceResult(sentenceEn, sentenceZh, difficulty);
    }

    private SynonymResult parseSynonymByRegex(String content) {
        Matcher matcher = ARRAY_ITEM_PATTERN.matcher(content);
        List<SynonymItem> items = new ArrayList<>();
        while (matcher.find()) {
            items.add(new SynonymItem(matcher.group(1), matcher.group(2), matcher.group(3)));
        }
        if (items.isEmpty()) {
            return null;
        }
        return new SynonymResult(items);
    }

    private MnemonicResult parseMnemonicByRegex(String content) {
        String mnemonic = extractByKey(content, "mnemonic");
        String rootAnalysis = extractByKey(content, "root_analysis");
        if (rootAnalysis == null) {
            rootAnalysis = extractByKey(content, "rootAnalysis");
        }
        if (mnemonic == null && rootAnalysis == null) {
            return null;
        }
        return new MnemonicResult(mnemonic, rootAnalysis);
    }

    private String extractByKey(String content, String key) {
        Pattern pattern = Pattern.compile(String.format(QUOTE_PATTERN.pattern(), Pattern.quote(key)));
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String getText(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                return value.asText();
            }
        }
        return null;
    }

    public record SentenceResult(String sentenceEn, String sentenceZh, String difficulty) {
    }

    public record SynonymResult(List<SynonymItem> synonyms) {
    }

    public record SynonymItem(String synonym, String difference, String example) {
    }

    public record MnemonicResult(String mnemonic, String rootAnalysis) {
    }
}
