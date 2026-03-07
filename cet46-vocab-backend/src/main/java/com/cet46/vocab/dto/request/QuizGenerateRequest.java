package com.cet46.vocab.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QuizGenerateRequest {

    @Min(value = 1, message = "count must be >= 1")
    @Max(value = 30, message = "count must be <= 30")
    private Integer count = 10;

    @NotBlank(message = "mode is required")
    private String mode;

    @NotBlank(message = "wordType is required")
    private String wordType;
}
