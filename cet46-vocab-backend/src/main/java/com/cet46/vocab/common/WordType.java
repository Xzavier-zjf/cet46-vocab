package com.cet46.vocab.common;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public enum WordType {
    CET4("cet4", "cet4zx", "CET4"),
    CET6("cet6", "cet6zx", "CET6"),
    CET4_LX("cet4lx", "cet4lx", "CET4"),
    CET6_LX("cet6lx", "cet6lx", "CET6");

    private static final Map<String, WordType> LOOKUP = Arrays.stream(values())
            .collect(Collectors.toMap(WordType::code, v -> v));

    private static final String SUPPORTED_HINT = "wordType must be cet4/cet6/cet4lx/cet6lx";

    private final String code;
    private final String tableName;
    private final String level;

    WordType(String code, String tableName, String level) {
        this.code = code;
        this.tableName = tableName;
        this.level = level;
    }

    public String code() {
        return code;
    }

    public String tableName() {
        return tableName;
    }

    public String level() {
        return level;
    }

    public static WordType from(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return LOOKUP.get(raw.trim().toLowerCase(Locale.ROOT));
    }

    public static String normalize(String raw) {
        WordType type = from(raw);
        return type == null ? null : type.code;
    }

    public static String supportedHint() {
        return SUPPORTED_HINT;
    }
}
