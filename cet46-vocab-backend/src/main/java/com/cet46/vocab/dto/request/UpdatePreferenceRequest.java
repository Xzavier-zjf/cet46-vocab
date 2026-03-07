package com.cet46.vocab.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdatePreferenceRequest {

    @Pattern(regexp = "academic|story|sarcastic", message = "llmStyle must be academic, story or sarcastic")
    private String llmStyle;

    @Pattern(regexp = "local|cloud", message = "llmProvider must be local or cloud")
    private String llmProvider;

    @NotNull(message = "dailyTarget cannot be null")
    @Min(value = 1, message = "dailyTarget must be at least 1")
    @Max(value = 100, message = "dailyTarget must be at most 100")
    private Integer dailyTarget;
}
