package com.cet46.vocab.llm;

import org.springframework.util.StringUtils;

import java.util.Locale;

public final class LlmProvider {

    public static final String LOCAL = "local";
    public static final String CLOUD = "cloud";

    private LlmProvider() {
    }

    public static String normalize(String provider) {
        if (!StringUtils.hasText(provider)) {
            return LOCAL;
        }
        String value = provider.trim().toLowerCase(Locale.ROOT);
        if (CLOUD.equals(value)) {
            return CLOUD;
        }
        return LOCAL;
    }
}

