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
    private Long sizeBytes;
    private String modifiedAt;
    private String digest;
}
