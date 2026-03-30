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
        String json = "{" +
                "\"sentenceEn\":\"He abandoned the plan.\"," +
                "\"sentenceZh\":\"He gave it up.\"," +
                "\"difficulty\":\"CET-4\"" +
                "}";

        LlmResponseParser.SentenceResult result = parser.parseSentence(json);

        assertNotNull(result);
        assertEquals("He abandoned the plan.", result.sentenceEn());
        assertEquals("He gave it up.", result.sentenceZh());
        assertEquals("CET-4", result.difficulty());
    }

    @Test
    void brokenJsonShouldFallbackToRegex() {
        String broken = "prefix \"sentenceEn\":\"Fallback en\", \"sentenceZh\":\"Fallback zh\", \"difficulty\":\"CET-6\" suffix";

        LlmResponseParser.SentenceResult result = parser.parseSentence(broken);

        assertNotNull(result);
        assertEquals("Fallback en", result.sentenceEn());
        assertEquals("Fallback zh", result.sentenceZh());
        assertEquals("CET-6", result.difficulty());
    }

    @Test
    void wrappedContentStringJsonShouldParseSuccessfully() {
        String wrapped = "{" +
                "\"content\":\"{\\\"sentenceEn\\\":\\\"Nested en\\\",\\\"sentenceZh\\\":\\\"Nested zh\\\",\\\"difficulty\\\":\\\"CET-6\\\"}\"" +
                "}";

        LlmResponseParser.SentenceResult result = parser.parseSentence(wrapped);

        assertNotNull(result);
        assertEquals("Nested en", result.sentenceEn());
        assertEquals("Nested zh", result.sentenceZh());
        assertEquals("CET-6", result.difficulty());
    }

    @Test
    void synonymStringArrayShouldParseSuccessfully() {
        String json = "{" +
                "\"synonyms\":[\"abandon\",\"quit\"]" +
                "}";

        LlmResponseParser.SynonymResult result = parser.parseSynonym(json);

        assertNotNull(result);
        assertNotNull(result.synonyms());
        assertEquals(2, result.synonyms().size());
        assertEquals("abandon", result.synonyms().get(0).synonym());
    }

    @Test
    void invalidContentShouldReturnNull() {
        String invalid = "totally-invalid-content-without-any-expected-keys";

        LlmResponseParser.SentenceResult sentenceResult = parser.parseSentence(invalid);
        LlmResponseParser.SynonymResult synonymResult = parser.parseSynonym(invalid);
        LlmResponseParser.MnemonicResult mnemonicResult = parser.parseMnemonic(invalid);

        assertNull(sentenceResult);
        assertNull(synonymResult);
        assertNull(mnemonicResult);
    }
}
