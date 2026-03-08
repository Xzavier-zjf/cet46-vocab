package com.cet46.vocab.controller;

import com.cet46.vocab.common.Result;
import com.cet46.vocab.common.ResultCode;
import com.cet46.vocab.config.CloudLlmProperties;
import com.cet46.vocab.dto.request.AssistantChatRequest;
import com.cet46.vocab.dto.response.AssistantChatResponse;
import com.cet46.vocab.entity.User;
import com.cet46.vocab.llm.CloudLlmClient;
import com.cet46.vocab.llm.LlmProvider;
import com.cet46.vocab.llm.OllamaClient;
import com.cet46.vocab.llm.PromptTemplate;
import com.cet46.vocab.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/assistant")
public class AssistantController {

    private static final String MODE_QUICK = "quick";
    private static final String MODE_BALANCED = "balanced";
    private static final String MODE_DETAILED = "detailed";

    private final UserMapper userMapper;
    private final OllamaClient ollamaClient;
    private final CloudLlmClient cloudLlmClient;
    private final CloudLlmProperties cloudLlmProperties;
    private final ObjectMapper objectMapper;

    public AssistantController(UserMapper userMapper,
                               OllamaClient ollamaClient,
                               CloudLlmClient cloudLlmClient,
                               CloudLlmProperties cloudLlmProperties,
                               ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.ollamaClient = ollamaClient;
        this.cloudLlmClient = cloudLlmClient;
        this.cloudLlmProperties = cloudLlmProperties;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/chat")
    public Result<AssistantChatResponse> chat(@Valid @RequestBody AssistantChatRequest req,
                                              Authentication authentication) {
        Long userId = getUserId(authentication);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }

        String style = resolveUserStyle(userId);
        String provider = resolveUserProvider(userId);
        String normalizedProvider = chooseProvider(provider);
        String answerMode = normalizeAnswerMode(req.getAnswerMode());
        ModeConfig modeConfig = resolveModeConfig(answerMode);
        String prompt = buildAssistantPrompt(req, style, answerMode, modeConfig);

        try {
            String answer = LlmProvider.CLOUD.equals(normalizedProvider)
                    ? cloudLlmClient.generate(prompt, modeConfig.cloudMaxTokens())
                    : ollamaClient.generatePlainText(prompt, modeConfig.localNumPredict());

            AssistantChatResponse data = AssistantChatResponse.builder()
                    .answer(cleanAnswer(answer))
                    .provider(normalizedProvider)
                    .style(style)
                    .suggestions(buildSuggestions(req))
                    .build();
            return Result.success(data);
        } catch (Exception ex) {
            return Result.fail(ResultCode.LLM_ERROR.getCode(), "学习助手暂时不可用，请稍后重试");
        }
    }

    private Long getUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        return Long.valueOf(authentication.getPrincipal().toString());
    }

    private String resolveUserStyle(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getLlmStyle())) {
            return "story";
        }
        return user.getLlmStyle();
    }

    private String resolveUserProvider(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return LlmProvider.LOCAL;
        }
        return LlmProvider.normalize(user.getLlmProvider());
    }

    private String chooseProvider(String preferredProvider) {
        String normalized = LlmProvider.normalize(preferredProvider);
        if (LlmProvider.CLOUD.equals(normalized) && !isCloudAvailable()) {
            return LlmProvider.LOCAL;
        }
        return normalized;
    }

    private boolean isCloudAvailable() {
        return Boolean.TRUE.equals(cloudLlmProperties.getEnabled())
                && StringUtils.hasText(cloudLlmProperties.getBaseUrl())
                && StringUtils.hasText(cloudLlmProperties.getModel())
                && StringUtils.hasText(cloudLlmProperties.getApiKey());
    }

    private String normalizeAnswerMode(String answerMode) {
        if (!StringUtils.hasText(answerMode)) {
            return MODE_BALANCED;
        }
        String mode = answerMode.trim().toLowerCase(Locale.ROOT);
        if (MODE_QUICK.equals(mode) || MODE_DETAILED.equals(mode)) {
            return mode;
        }
        return MODE_BALANCED;
    }

    private ModeConfig resolveModeConfig(String answerMode) {
        if (MODE_QUICK.equals(answerMode)) {
            return new ModeConfig(
                    2,
                    220,
                    260,
                    280,
                    "Give a concise answer in 3-5 short points."
            );
        }
        if (MODE_DETAILED.equals(answerMode)) {
            return new ModeConfig(
                    6,
                    420,
                    680,
                    760,
                    "Give a detailed structured answer with examples and a brief action plan."
            );
        }
        return new ModeConfig(
                4,
                320,
                420,
                520,
                "Give a balanced practical answer with clear structure."
        );
    }

    private String buildAssistantPrompt(AssistantChatRequest req,
                                        String style,
                                        String answerMode,
                                        ModeConfig modeConfig) {
        StringBuilder sb = new StringBuilder();
        sb.append(PromptTemplate.LEARNING_ASSISTANT_SYSTEM).append('\n');
        sb.append("\n# USER PROFILE\n");
        sb.append("style=").append(style).append('\n');
        sb.append("target=中国大学生CET备考\n");
        sb.append("answer_mode=").append(answerMode).append('\n');

        AssistantChatRequest.WordContext ctx = req.getWordContext();
        if (ctx != null) {
            sb.append("\n# WORD CONTEXT\n");
            appendIfPresent(sb, "word", ctx.getWord(), modeConfig.textLimit());
            appendIfPresent(sb, "wordId", ctx.getWordId() == null ? null : String.valueOf(ctx.getWordId()), modeConfig.textLimit());
            appendIfPresent(sb, "wordType", ctx.getWordType(), modeConfig.textLimit());
            appendIfPresent(sb, "phonetic", ctx.getPhonetic(), modeConfig.textLimit());
            appendIfPresent(sb, "pos", ctx.getPos(), modeConfig.textLimit());
            appendIfPresent(sb, "chinese", ctx.getChinese(), modeConfig.textLimit());
            appendIfPresent(sb, "fromPage", ctx.getFromPage(), modeConfig.textLimit());
        }

        List<AssistantChatRequest.MessageItem> history = req.getHistory();
        if (history != null && !history.isEmpty()) {
            sb.append("\n# RECENT CONVERSATION\n");
            int start = Math.max(0, history.size() - modeConfig.historyCount());
            for (int i = start; i < history.size(); i++) {
                AssistantChatRequest.MessageItem item = history.get(i);
                if (item == null || !StringUtils.hasText(item.getContent())) {
                    continue;
                }
                String role = normalizeRole(item.getRole());
                sb.append(role).append(": ").append(trimText(item.getContent(), modeConfig.textLimit())).append('\n');
            }
        }

        sb.append("\n# CURRENT QUESTION\n");
        sb.append(trimText(req.getQuestion(), modeConfig.textLimit())).append('\n');
        sb.append("\n# ANSWER LENGTH\n");
        sb.append(modeConfig.lengthInstruction()).append('\n');
        sb.append("\n# OUTPUT FORMAT\n");
        sb.append("请直接输出回答正文，不要使用Markdown代码块。\n");
        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String key, String value, int maxLen) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        sb.append(key).append('=').append(trimText(value, maxLen)).append('\n');
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "user";
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if ("assistant".equals(normalized)) {
            return "assistant";
        }
        return "user";
    }

    private String cleanAnswer(String answer) {
        if (!StringUtils.hasText(answer)) {
            return "这个问题我暂时没有生成到有效答案，你可以换个问法再试一次。";
        }
        String text = answer.trim();
        if (text.startsWith("```")) {
            text = text.replace("```markdown", "")
                    .replace("```md", "")
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();
        }
        String extracted = extractAnswerField(text);
        if (StringUtils.hasText(extracted)) {
            text = extracted;
        }
        return trimText(text, 3000);
    }

    private String extractAnswerField(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node != null && node.isObject()) {
                JsonNode answerNode = node.get("answer");
                if (answerNode != null && answerNode.isTextual()) {
                    return answerNode.asText().trim();
                }
            }
        } catch (Exception ignore) {
            // Ignore and fallback.
        }

        String marker = "\"answer\"";
        int markerIdx = raw.indexOf(marker);
        if (markerIdx < 0) {
            return null;
        }
        int colon = raw.indexOf(':', markerIdx + marker.length());
        if (colon < 0) {
            return null;
        }
        String tail = raw.substring(colon + 1).trim();
        if (!tail.startsWith("\"")) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = 1; i < tail.length(); i++) {
            char c = tail.charAt(i);
            if (escaped) {
                if (c == 'n') {
                    sb.append('\n');
                } else {
                    sb.append(c);
                }
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                break;
            }
            sb.append(c);
        }
        String parsed = sb.toString().trim();
        return StringUtils.hasText(parsed) ? parsed : null;
    }

    private String trimText(String text, int maxLen) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String value = text.trim();
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen) + "...";
    }

    private List<String> buildSuggestions(AssistantChatRequest req) {
        List<String> suggestions = new ArrayList<>();
        AssistantChatRequest.WordContext ctx = req.getWordContext();
        if (ctx != null && StringUtils.hasText(ctx.getWord())) {
            String word = ctx.getWord().trim();
            suggestions.add("再给我2个 " + word + " 的四六级例句");
            suggestions.add(word + " 常见易错搭配有哪些");
            suggestions.add(word + " 和近义词在语气上有什么区别");
            return suggestions;
        }
        suggestions.add("帮我制定本周四六级背词计划");
        suggestions.add("我总记不住单词，应该怎么复习");
        suggestions.add("给我3条四六级阅读提分建议");
        return suggestions;
    }

    private record ModeConfig(int historyCount,
                              int textLimit,
                              int localNumPredict,
                              int cloudMaxTokens,
                              String lengthInstruction) {
    }
}
