package com.cet46.vocab.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PosParser {

    private static final Map<String, Pattern> POS_PATTERNS = new LinkedHashMap<>();

    static {
        POS_PATTERNS.put("v", Pattern.compile("(?i)(^|[^a-z])v\\."));
        POS_PATTERNS.put("n", Pattern.compile("(?i)(^|[^a-z])n\\."));
        POS_PATTERNS.put("adj", Pattern.compile("(?i)(^|[^a-z])adj\\."));
        POS_PATTERNS.put("adv", Pattern.compile("(?i)(^|[^a-z])adv\\."));
        POS_PATTERNS.put("prep", Pattern.compile("(?i)(^|[^a-z])prep\\."));
        POS_PATTERNS.put("conj", Pattern.compile("(?i)(^|[^a-z])conj\\."));
        POS_PATTERNS.put("pron", Pattern.compile("(?i)(^|[^a-z])pron\\."));
        POS_PATTERNS.put("int", Pattern.compile("(?i)(^|[^a-z])int\\."));
        POS_PATTERNS.put("num", Pattern.compile("(?i)(^|[^a-z])num\\."));
        POS_PATTERNS.put("art", Pattern.compile("(?i)(^|[^a-z])art\\."));
    }

    private PosParser() {
    }

    public static String parse(String chinese) {
        if (chinese == null || chinese.isBlank()) {
            return "";
        }
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Pattern> entry : POS_PATTERNS.entrySet()) {
            Matcher matcher = entry.getValue().matcher(chinese);
            if (matcher.find()) {
                result.add(entry.getKey());
            }
        }
        return String.join(",", result);
    }
}
