package com.cet46.vocab.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LocalModelListResponse {
    private Boolean serviceUp;
    private String baseUrl;
    private Integer count;
    private String selectedModel;
    private String defaultModel;
    private List<LocalModelItemResponse> models;
    private String message;
}
