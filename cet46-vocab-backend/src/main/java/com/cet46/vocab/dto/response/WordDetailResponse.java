package com.cet46.vocab.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WordDetailResponse {
    private Long wordId;
    private String wordType;
    private String english;
    private String phonetic;
    private String chinese;
    private String pos;
    private LlmContent llmContent;
    private Progress progress;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LlmContent {
        private String genStatus;
        private String style;
        private Sentence sentence;
        private List<SynonymItem> synonyms;
        private Mnemonic mnemonic;
        private String smartExplain;
        private String grammarUsage;
        private String explainStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sentence {
        private String sentenceEn;
        private String sentenceZh;
        private String difficulty;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SynonymItem {
        private String synonym;
        private String difference;
        private String example;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Mnemonic {
        private String mnemonic;
        private String rootAnalysis;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Progress {
        private Boolean isLearning;
        private String status;
        private Double easiness;
        private Integer interval;
        private Integer repetition;
        private LocalDate nextReviewDate;
    }
}
