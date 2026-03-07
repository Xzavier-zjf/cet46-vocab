package com.cet46.vocab.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuizSubmitRequest {

    @NotBlank(message = "quizId is required")
    private String quizId;

    private List<AnswerItem> answers = new ArrayList<>();

    @Data
    public static class AnswerItem {
        private String questionId;
        private String userAnswer;
        private Long timeSpentMs;
    }
}
