package com.cet46.vocab.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LlmLastUsedResponse {

    private String provider;
    private String model;
    private String source;
    private Long updatedAt;
}
