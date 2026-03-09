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
    private static final int MAX_CONTINUATION_ROUNDS = 2;
    private static final int OVERLAP_MIN_LEN = 20;

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
            String effectiveProvider = normalizedProvider;
            GeneratedAnswer generated;
            try {
                generated = generateCompleteAnswer(prompt, normalizedProvider, modeConfig);
            } catch (Exception firstEx) {
                if (!LlmProvider.CLOUD.equals(normalizedProvider)) {
                    throw firstEx;
                }
                generated = generateCompleteAnswer(prompt, LlmProvider.LOCAL, modeConfig);
                effectiveProvider = LlmProvider.LOCAL;
            }
            AssistantChatResponse data = AssistantChatResponse.builder()
                    .answer(cleanAnswer(generated.content()))
                    .provider(effectiveProvider)
                    .style(style)
                    .suggestions(buildSuggestions(req))
                    .autoContinued(generated.continuationRounds() > 0)
                    .continuationRounds(generated.continuationRounds())
                    .build();
            return Result.success(data);
        } catch (Exception ex) {
            return Result.fail(ResultCode.LLM_ERROR.getCode(), "\u5b66\u4e60\u52a9\u624b\u6682\u65f6\u4e0d\u53ef\u7528\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
        }
    }

    private GeneratedAnswer generateCompleteAnswer(String prompt, String provider, ModeConfig modeConfig) {
        String merged = "";
        String currentPrompt = prompt;
        int continuationRounds = 0;

        for (int i = 0; i <= MAX_CONTINUATION_ROUNDS; i++) {
            GenerationChunk chunk = callModel(currentPrompt, provider, modeConfig);
            String piece = chunk.content() == null ? "" : chunk.content().trim();
            if (!StringUtils.hasText(piece)) {
                break;
            }
            merged = mergeAnswerChunks(merged, piece);
            if (!chunk.truncated()) {
                break;
            }
            continuationRounds += 1;
            currentPrompt = buildContinuationPrompt(prompt, merged);
        }
        return new GeneratedAnswer(merged, continuationRounds);
    }

    private GenerationChunk callModel(String prompt, String provider, ModeConfig modeConfig) {
        if (LlmProvider.CLOUD.equals(provider)) {
            CloudLlmClient.GenerationResult result = cloudLlmClient.generateWithMeta(prompt, modeConfig.cloudMaxTokens());
            return new GenerationChunk(result.content(), result.truncated());
        }
        OllamaClient.GenerationResult result = ollamaClient.generatePlainTextWithMeta(prompt, modeConfig.localNumPredict());
        return new GenerationChunk(result.content(), result.truncated());
    }

    private String buildContinuationPrompt(String originalPrompt, String partialAnswer) {
        StringBuilder sb = new StringBuilder(originalPrompt.length() + partialAnswer.length() + 220);
        sb.append(originalPrompt).append('\n');
        sb.append("\n# PARTIAL ANSWER ALREADY SENT\n");
        sb.append(trimText(partialAnswer, 2800)).append('\n');
        sb.append("\n# CONTINUE RULES\n");
        sb.append("Continue from the last unfinished sentence.\n");
        sb.append("Do not repeat any sentence from the partial answer.\n");
        sb.append("Only output the continuation text.\n");
        return sb.toString();
    }

    private String mergeAnswerChunks(String current, String nextChunk) {
        String base = StringUtils.hasText(current) ? current.trim() : "";
        String incoming = StringUtils.hasText(nextChunk) ? nextChunk.trim() : "";
        if (!StringUtils.hasText(base)) {
            return incoming;
        }
        if (!StringUtils.hasText(incoming)) {
            return base;
        }

        int maxOverlap = Math.min(base.length(), incoming.length());
        int overlap = 0;
        for (int len = maxOverlap; len >= OVERLAP_MIN_LEN; len--) {
            String suffix = base.substring(base.length() - len);
            String prefix = incoming.substring(0, len);
            if (suffix.equals(prefix)) {
                overlap = len;
                break;
            }
        }
        String tail = overlap > 0 ? incoming.substring(overlap).trim() : incoming;
        if (!StringUtils.hasText(tail)) {
            return base;
        }
        return base + "\n" + tail;
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
        sb.append("target=\u4e2d\u56fd\u5927\u5b66\u751fCET\u5907\u8003\n");
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
        sb.append("\u8bf7\u76f4\u63a5\u8f93\u51fa\u56de\u7b54\u6b63\u6587\uff0c\u4e0d\u8981\u4f7f\u7528Markdown\u4ee3\u7801\u5757\u3002\n");
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
            return "\u8fd9\u4e2a\u95ee\u9898\u6211\u6682\u65f6\u6ca1\u6709\u751f\u6210\u5230\u6709\u6548\u7b54\u6848\uff0c\u4f60\u53ef\u4ee5\u6362\u4e2a\u95ee\u6cd5\u518d\u8bd5\u4e00\u6b21\u3002";
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
            if (c == '\"') {
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
            suggestions.add("\u518d\u7ed9\u62112\u4e2a " + word + " \u7684\u56db\u516d\u7ea7\u4f8b\u53e5");
            suggestions.add(word + " \u5e38\u89c1\u6613\u9519\u642d\u914d\u6709\u54ea\u4e9b");
            suggestions.add(word + " \u548c\u8fd1\u4e49\u8bcd\u5728\u8bed\u6c14\u4e0a\u6709\u4ec0\u4e48\u533a\u522b");
            return suggestions;
        }
        suggestions.add("\u5e2e\u6211\u5236\u5b9a\u672c\u5468\u56db\u516d\u7ea7\u80cc\u8bcd\u8ba1\u5212");
        suggestions.add("\u6211\u603b\u8bb0\u4e0d\u4f4f\u5355\u8bcd\uff0c\u5e94\u8be5\u600e\u4e48\u590d\u4e60");
        suggestions.add("\u7ed9\u62113\u6761\u56db\u516d\u7ea7\u9605\u8bfb\u63d0\u5206\u5efa\u8bae");
        return suggestions;
    }

    private record ModeConfig(int historyCount,
                              int textLimit,
                              int localNumPredict,
                              int cloudMaxTokens,
                              String lengthInstruction) {
    }

    private record GenerationChunk(String content, boolean truncated) {
    }

    private record GeneratedAnswer(String content, int continuationRounds) {
    }
}
