package com.cet46.vocab.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AssistantChatRequest {

    @NotBlank(message = "question cannot be blank")
    private String question;

    @Valid
    private WordContext wordContext;

    @Valid
    private List<MessageItem> history = new ArrayList<>();

    @Data
    public static class WordContext {
        private Long wordId;
        private String wordType;
        private String word;
        private String phonetic;
        private String pos;
        private String chinese;
        private String fromPage;
    }

    @Data
    public static class MessageItem {
        private String role;
        private String content;
    }
}
