package com.cet46.vocab.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewSubmitRequest {
    @NotNull(message = "wordId cannot be null")
    private Long wordId;

    @NotBlank(message = "wordType cannot be blank")
    private String wordType;

    @NotNull(message = "score cannot be null")
    @Min(value = 1, message = "score must be between 1 and 5")
    @Max(value = 5, message = "score must be between 1 and 5")
    private Integer score;

    @Min(value = 0, message = "timeSpentMs must be greater than or equal to 0")
    private Long timeSpentMs;
}
