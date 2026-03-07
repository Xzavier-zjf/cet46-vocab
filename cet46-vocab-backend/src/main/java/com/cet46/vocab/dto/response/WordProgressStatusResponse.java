package com.cet46.vocab.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WordProgressStatusResponse {
    private Long wordId;
    private String wordType;
    private String status;
    private Boolean isLearning;
    private Boolean isCompleted;
}
