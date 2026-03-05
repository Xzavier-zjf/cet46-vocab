package com.cet46.vocab.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCardResponse {
    private Long wordId;
    private String wordType;
    private String english;
    private String phonetic;
    private String chinese;
    private Double easiness;
    private Integer interval;
    private Integer repetition;
}
