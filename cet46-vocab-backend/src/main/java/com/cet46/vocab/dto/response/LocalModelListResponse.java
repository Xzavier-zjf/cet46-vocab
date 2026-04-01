package com.cet46.vocab.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class LocalModelListResponse {
    private Boolean serviceUp;
    private String baseUrl;
    private Integer count;
    private String selectedModel;
    private String defaultModel;
    private List<LocalModelItemResponse> models;
    private List<String> providers;
    private Map<String, String> providerLabels;
    private String message;
}
