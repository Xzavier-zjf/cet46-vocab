package com.cet46.vocab.llm;

public record CloudLlmRuntimeConfig(
        String provider,
        String model,
        String baseUrl,
        String path,
        String apiKey,
        String protocol,
        String source
) {
}
