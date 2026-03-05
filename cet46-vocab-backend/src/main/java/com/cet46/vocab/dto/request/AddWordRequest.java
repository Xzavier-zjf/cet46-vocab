package com.cet46.vocab.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddWordRequest {
    @NotNull(message = "wordId cannot be null")
    private Long wordId;

    @NotBlank(message = "wordType cannot be blank")
    private String wordType;
}
