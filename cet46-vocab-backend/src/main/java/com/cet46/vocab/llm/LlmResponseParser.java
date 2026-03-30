package com.cet46.vocab.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LlmResponseParser {

    private static final Pattern QUOTE_PATTERN = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern FENCE_JSON_PATTERN = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*}");
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
            JsonNode node = parseJsonNode(content);
            if (node == null) {
                return null;
            }
            String sentenceEn = getText(node,
                    "sentence_en", "sentenceEn", "sentence", "example_sentence", "sentenceEnglish", "english_sentence");
            String sentenceZh = getText(node,
                    "sentence_zh", "sentenceZh", "sentence_cn", "sentenceCn", "translation", "sentenceChinese", "chinese_sentence");
            String difficulty = getText(node, "difficulty", "level");
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
            JsonNode node = parseJsonNode(content);
            if (node == null) {
                return null;
            }
            JsonNode arrayNode = getArrayNode(node, "synonyms", "similar_words", "alternatives");
            if (arrayNode == null || !arrayNode.isArray()) {
                return null;
            }
            List<SynonymItem> items = new ArrayList<>();
            for (JsonNode item : arrayNode) {
                if (item == null || item.isNull()) {
                    continue;
                }
                if (item.isTextual()) {
                    String synonymText = item.asText();
                    if (StringUtils.hasText(synonymText) && !"null".equalsIgnoreCase(synonymText.trim())) {
                        items.add(new SynonymItem(synonymText.trim(), null, null));
                    }
                    continue;
                }
                if (!item.isObject()) {
                    continue;
                }
                String synonym = getText(item, "synonym", "word", "term");
                String difference = getText(item, "difference", "usage", "note", "explanation");
                String example = getText(item, "example", "sentence", "example_sentence");
                if (StringUtils.hasText(synonym)) {
                    items.add(new SynonymItem(synonym.trim(), difference, example));
                }
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
            JsonNode node = parseJsonNode(content);
            if (node == null) {
                return null;
            }
            String mnemonic = getText(node, "mnemonic", "memory_tip", "memoryTip", "association", "memory");
            String rootAnalysis = getText(node, "root_analysis", "rootAnalysis", "etymology", "root");
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
                    return unwrapEnvelope(root);
                }
            } catch (Exception ignore) {
                // Try next candidate.
            }
        }
        return null;
    }

    private JsonNode unwrapEnvelope(JsonNode root) {
        JsonNode[] nodes = new JsonNode[]{
                root.get("data"),
                root.get("result"),
                root.get("output"),
                root.get("content")
        };
        for (JsonNode node : nodes) {
            if (node != null && node.isObject()) {
                return node;
            }
            JsonNode embedded = parseEmbeddedJsonObject(node);
            if (embedded != null) {
                return embedded;
            }
        }
        return root;
    }

    private JsonNode parseEmbeddedJsonObject(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String text = node.asText();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            JsonNode embedded = objectMapper.readTree(text);
            return embedded != null && embedded.isObject() ? embedded : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private JsonNode getArrayNode(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isArray()) {
                return value;
            }
        }
        return null;
    }

    private String getText(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (StringUtils.hasText(text) && !"null".equalsIgnoreCase(text.trim())) {
                    return text;
                }
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


