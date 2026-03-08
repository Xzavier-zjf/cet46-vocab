package com.cet46.vocab.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AssistantChatResponse {
    private String answer;
    private String provider;
    private String style;
    private List<String> suggestions;
}
