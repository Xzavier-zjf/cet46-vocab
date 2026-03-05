package com.cet46.vocab.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LlmResponseParserTest {

    private LlmResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new LlmResponseParser(new ObjectMapper());
    }

    @Test
    void sentenceJsonShouldParseSuccessfully() {
        // 测试意图：标准 JSON 能被正确解析为 sentence 结构
        String json = "{" +
                "\"sentenceEn\":\"He abandoned the plan.\"," +
                "\"sentenceZh\":\"\u4ed6\u653e\u5f03\u4e86\u8ba1\u5212\u3002\"," +
                "\"difficulty\":\"CET-4\"" +
                "}";

        LlmResponseParser.SentenceResult result = parser.parseSentence(json);

        assertNotNull(result);
        assertEquals("He abandoned the plan.", result.sentenceEn());
        assertEquals("他放弃了计划。", result.sentenceZh());
        assertEquals("CET-4", result.difficulty());
    }

    @Test
    void brokenJsonShouldFallbackToRegex() {
        // 测试意图：JSON 非法时，仍可从键值片段中提取内容
        String broken = "prefix \"sentenceEn\":\"Fallback en\", \"sentenceZh\":\"Fallback zh\", \"difficulty\":\"CET-6\" suffix";

        LlmResponseParser.SentenceResult result = parser.parseSentence(broken);

        assertNotNull(result);
        assertEquals("Fallback en", result.sentenceEn());
        assertEquals("Fallback zh", result.sentenceZh());
        assertEquals("CET-6", result.difficulty());
    }

    @Test
    void invalidContentShouldReturnNull() {
        // 测试意图：既不是合法 JSON 也无可提取字段时返回 null
        String invalid = "totally-invalid-content-without-any-expected-keys";

        LlmResponseParser.SentenceResult sentenceResult = parser.parseSentence(invalid);
        LlmResponseParser.SynonymResult synonymResult = parser.parseSynonym(invalid);
        LlmResponseParser.MnemonicResult mnemonicResult = parser.parseMnemonic(invalid);

        assertNull(sentenceResult);
        assertNull(synonymResult);
        assertNull(mnemonicResult);
    }
}
