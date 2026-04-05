package com.cet46.vocab.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalModelItemResponse {
    private String name;
    private String displayName;
    private String visibility;
    private String baseUrl;
    private String path;
    private String protocol;
    private Boolean hasApiKey;
    private String apiKeyMask;
    private Long sizeBytes;
    private String modifiedAt;
    private String digest;
}

