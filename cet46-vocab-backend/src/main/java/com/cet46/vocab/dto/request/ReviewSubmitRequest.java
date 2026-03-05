package com.cet46.vocab.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewSubmitRequest {
    @NotNull
    private Long wordId;

    @NotBlank
    private String wordType;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer score;

    private Long timeSpentMs;
}
