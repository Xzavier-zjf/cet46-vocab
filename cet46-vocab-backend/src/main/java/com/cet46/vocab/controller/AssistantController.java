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
import com.cet46.vocab.llm.LlmUsageTracker;
import com.cet46.vocab.llm.CloudLlmRuntimeConfig;
import com.cet46.vocab.llm.CloudLlmRuntimeConfigResolver;
import com.cet46.vocab.llm.PromptTemplate;
import com.cet46.vocab.mapper.UserMapper;
import com.cet46.vocab.service.CloudLlmModelService;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/assistant")
public class AssistantController {
    private static final int OVERLAP_MIN_LEN = 8;
    private static final String ANSWER_END_MARKER = "<<END_OF_ANSWER>>";
    private static final Pattern MODEL_SIZE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)b", Pattern.CASE_INSENSITIVE);

    private final UserMapper userMapper;
    private final OllamaClient ollamaClient;
    private final CloudLlmClient cloudLlmClient;
    private final CloudLlmProperties cloudLlmProperties;
    private final ObjectMapper objectMapper;
    private final LlmUsageTracker llmUsageTracker;
    private final CloudLlmModelService cloudLlmModelService;
    private final CloudLlmRuntimeConfigResolver cloudLlmRuntimeConfigResolver;

    public AssistantController(UserMapper userMapper,
                               OllamaClient ollamaClient,
                               CloudLlmClient cloudLlmClient,
                               CloudLlmProperties cloudLlmProperties,
                               ObjectMapper objectMapper,
                               LlmUsageTracker llmUsageTracker,
                               CloudLlmModelService cloudLlmModelService,
                               CloudLlmRuntimeConfigResolver cloudLlmRuntimeConfigResolver) {
        this.userMapper = userMapper;
        this.ollamaClient = ollamaClient;
        this.cloudLlmClient = cloudLlmClient;
        this.cloudLlmProperties = cloudLlmProperties;
        this.objectMapper = objectMapper;
        this.llmUsageTracker = llmUsageTracker;
        this.cloudLlmModelService = cloudLlmModelService;
        this.cloudLlmRuntimeConfigResolver = cloudLlmRuntimeConfigResolver;
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
        String cloudModel = resolveUserCloudModel(userId);
        String normalizedProvider = chooseProvider(userId, provider, cloudModel);
        String localModel = resolveUserLocalModel(userId);
        ModeConfig modeConfig = buildModeConfig(normalizedProvider, localModel);
        String prompt = buildAssistantPrompt(req, style, modeConfig);

        try {
            String effectiveProvider = normalizedProvider;
            GeneratedAnswer generated;
            try {
                generated = generateCompleteAnswer(prompt, normalizedProvider, modeConfig, localModel, cloudModel, userId);
            } catch (Exception firstEx) {
                if (!LlmProvider.CLOUD.equals(normalizedProvider)) {
                    throw firstEx;
                }
                ModeConfig localModeConfig = buildModeConfig(LlmProvider.LOCAL, localModel);
                String localPrompt = buildAssistantPrompt(req, style, localModeConfig);
                generated = generateCompleteAnswer(localPrompt, LlmProvider.LOCAL, localModeConfig, localModel, cloudModel, userId);
                effectiveProvider = LlmProvider.LOCAL;
            }
            llmUsageTracker.record(userId, effectiveProvider, resolveUsedModel(effectiveProvider, localModel, cloudModel), "assistant.chat");
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

    private GeneratedAnswer generateCompleteAnswer(String prompt, String provider, ModeConfig modeConfig, String localModel, String cloudModel, Long userId) {
        String merged = "";
        String currentPrompt = prompt;
        int continuationRounds = 0;

        for (int i = 0; i <= modeConfig.maxContinuationRounds(); i++) {
            GenerationChunk chunk = callModel(currentPrompt, provider, modeConfig, localModel, cloudModel, userId);
            String piece = chunk.content() == null ? "" : chunk.content().trim();
            if (!StringUtils.hasText(piece)) {
                break;
            }
            merged = mergeAnswerChunks(merged, piece);
            if (containsEndMarker(merged)) {
                break;
            }
            if (!chunk.truncated() && !looksIncomplete(merged)) {
                break;
            }
            continuationRounds += 1;
            currentPrompt = buildContinuationPrompt(prompt, merged, modeConfig);
        }
        return new GeneratedAnswer(stripEndMarker(merged).trim(), continuationRounds);
    }

    private GenerationChunk callModel(String prompt, String provider, ModeConfig modeConfig, String localModel, String cloudModel, Long userId) {
        if (LlmProvider.CLOUD.equals(provider)) {
            CloudLlmRuntimeConfig runtimeConfig = cloudLlmRuntimeConfigResolver.resolve(userId, cloudModel);
            CloudLlmClient.GenerationResult result = cloudLlmClient.generateWithMeta(prompt, runtimeConfig, modeConfig.cloudMaxTokens());
            return new GenerationChunk(result.content(), result.truncated());
        }
        OllamaClient.GenerationResult result = ollamaClient.generatePlainTextWithMeta(prompt, modeConfig.localNumPredict(), localModel);
        return new GenerationChunk(result.content(), result.truncated());
    }

    private boolean looksIncomplete(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String value = text.trim();
        if (value.length() < 40) {
            return false;
        }
        if (hasSuspiciousTail(value)) {
            return true;
        }
        char last = value.charAt(value.length() - 1);
        if (isTerminalChar(last)) {
            Character prev = previousMeaningfulChar(value, value.length() - 2);
            if (prev != null && isConnectorChar(prev)) {
                return true;
            }
            return false;
        }
        return isConnectorChar(last) || Character.isDigit(last);
    }

    private boolean hasSuspiciousTail(String text) {
        String tail = text.length() > 20 ? text.substring(text.length() - 20) : text;
        if (tail.endsWith("*") || tail.endsWith("**") || tail.endsWith("")) {
            return true;
        }
        Character last = previousMeaningfulChar(tail, tail.length() - 1);
        if (last == null) {
            return false;
        }
        if (isConnectorChar(last)) {
            return true;
        }
        if (last == '"' || last == '\'' || last == '\u201d' || last == '\u2019' || last == ')' || last == '\uff09' || last == ']'
                || last == '\u3011' || last == '}') {
            Character before = previousMeaningfulChar(tail, tail.length() - 2);
            return before != null && isConnectorChar(before);
        }
        return false;
    }

    private Character previousMeaningfulChar(String text, int startIdx) {
        for (int i = Math.min(startIdx, text.length() - 1); i >= 0; i--) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) {
                return c;
            }
        }
        return null;
    }

    private boolean isConnectorChar(char c) {
        return c == ':'
                || c == '\uff1a'
                || c == '+'
                || c == '-'
                || c == '\u2014'
                || c == '/'
                || c == '\u3001'
                || c == '\uff08'
                || c == '('
                || c == '['
                || c == '\u3010';
    }

    private boolean isTerminalChar(char c) {
        return c == '.'
                || c == '!'
                || c == '?'
                || c == ','
                || c == ';'
                || c == ')'
                || c == ']'
                || c == '}'
                || c == '"'
                || c == '\''
                || c == '\u3002'
                || c == '\uff01'
                || c == '\uff1f'
                || c == '\uff0c'
                || c == '\uff1b'
                || c == '\uff09'
                || c == '\u3011'
                || c == '\u201d'
                || c == '\u2019';
    }

    private String buildContinuationPrompt(String originalPrompt, String partialAnswer, ModeConfig modeConfig) {
        StringBuilder sb = new StringBuilder(partialAnswer.length() + 520);
        sb.append("# \u539f\u59cb\u4efb\u52a1\uff08\u4e0d\u8981\u91cd\u5199\u524d\u6587\uff09\n");
        sb.append(trimText(originalPrompt, modeConfig.continuationPromptLimit())).append('\n');
        sb.append("\n# \u5df2\u7ecf\u8f93\u51fa\u7684\u5185\u5bb9\n");
        sb.append(trimText(stripEndMarker(partialAnswer), modeConfig.continuationAnswerLimit())).append('\n');
        sb.append("\n# \u7eed\u5199\u89c4\u5219\n");
        sb.append("\u5fc5\u987b\u4ece\u4e0a\u6587\u672b\u5c3e\u7ee7\u7eed\u5199\uff0c\u4e0d\u5141\u8bb8\u91cd\u590d\u4efb\u4f55\u5df2\u8f93\u51fa\u53e5\u5b50\u3002\n");
        sb.append("\u5982\u679c\u524d\u6587\u5df2\u5f00\u59cb\u7f16\u53f7\u5217\u8868\uff0c\u8bf7\u4ece\u4e0b\u4e00\u4e2a\u7f16\u53f7\u7ee7\u7eed\uff0c\u603b\u70b9\u6570\u4e0d\u8981\u8d85\u8fc7 ").append(modeConfig.answerPointLimit()).append(" \u70b9\u3002\n");
        sb.append("\u4ec5\u5f53\u5b8c\u6574\u56de\u7b54\u7ed3\u675f\u65f6\uff0c\u5728\u6700\u540e\u5355\u72ec\u8f93\u51fa ").append(ANSWER_END_MARKER).append(" \u3002\n");
        sb.append("\u53ea\u8f93\u51fa\u7eed\u5199\u6b63\u6587\uff0c\u4e0d\u8981\u52a0\u89e3\u91ca\u3002\n");
        return sb.toString();
    }

    private boolean containsEndMarker(String text) {
        return StringUtils.hasText(text) && text.contains(ANSWER_END_MARKER);
    }

    private String stripEndMarker(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replace(ANSWER_END_MARKER, "").trim();
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
        if (base.contains(incoming)) {
            return base;
        }

        incoming = trimDuplicatedPrefix(base, incoming);
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

    private String trimDuplicatedPrefix(String base, String incoming) {
        int checkLen = Math.min(320, incoming.length());
        for (int len = checkLen; len >= 24; len--) {
            String prefix = incoming.substring(0, len);
            if (base.contains(prefix)) {
                String rest = incoming.substring(len).trim();
                if (StringUtils.hasText(rest)) {
                    return rest;
                }
            }
        }
        return incoming;
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

    private String resolveUserLocalModel(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getLlmLocalModel())) {
            return null;
        }
        return user.getLlmLocalModel().trim();
    }

    private String resolveUserCloudModel(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return cloudLlmProperties.resolveDefaultModel();
        }
        if (StringUtils.hasText(user.getLlmCloudModel())) {
            return user.getLlmCloudModel().trim();
        }
        return cloudLlmProperties.resolveDefaultModel();
    }

    private String chooseProvider(Long userId, String preferredProvider, String cloudModel) {
        String normalized = LlmProvider.normalize(preferredProvider);
        if (LlmProvider.CLOUD.equals(normalized) && !isCloudAvailable(userId, cloudModel)) {
            return LlmProvider.LOCAL;
        }
        return normalized;
    }

    private boolean isCloudAvailable(Long userId, String cloudModel) {
        return cloudLlmRuntimeConfigResolver.isAvailable(userId, cloudModel);
    }

    private String resolveUsedModel(String provider, String localModel, String cloudModel) {
        if (LlmProvider.CLOUD.equals(LlmProvider.normalize(provider))) {
            return cloudModel;
        }
        if (StringUtils.hasText(localModel)) {
            return localModel.trim();
        }
        return ollamaClient.getDefaultModel();
    }

    private ModeConfig buildModeConfig(String provider, String localModel) {
        if (LlmProvider.CLOUD.equals(provider)) {
            return cloudModeConfig();
        }
        return localModeConfig(localModel);
    }

    private ModeConfig cloudModeConfig() {
        return new ModeConfig(
                6,
                500,
                1100,
                1500,
                2,
                4,
                2,
                900,
                2400
        );
    }

    private ModeConfig localModeConfig(String localModel) {
        double modelSizeB = extractModelSizeB(localModel);
        if (modelSizeB > 0 && modelSizeB <= 1.5d) {
            return new ModeConfig(
                    2,
                    220,
                    360,
                    1500,
                    1,
                    3,
                    1,
                    260,
                    900
            );
        }
        if (modelSizeB > 0 && modelSizeB <= 4.0d) {
            return new ModeConfig(
                    3,
                    280,
                    560,
                    1500,
                    1,
                    3,
                    1,
                    320,
                    1200
            );
        }
        return new ModeConfig(
                4,
                360,
                760,
                1500,
                1,
                4,
                2,
                420,
                1600
        );
    }

    private String buildAssistantPrompt(AssistantChatRequest req,
                                        String style,
                                        ModeConfig modeConfig) {
        StringBuilder sb = new StringBuilder();
        sb.append(PromptTemplate.LEARNING_ASSISTANT_SYSTEM).append('\n');
        sb.append("\n# USER PROFILE\n");
        sb.append("style=").append(style).append('\n');
        sb.append("target=\u4e2d\u56fd\u5927\u5b66\u751fCET\u5907\u8003\n");

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
        sb.append("\n# OUTPUT FORMAT\n");
        sb.append("\u8bf7\u76f4\u63a5\u8f93\u51fa\u56de\u7b54\u6b63\u6587\uff0c\u4e0d\u8981\u4f7f\u7528Markdown\u4ee3\u7801\u5757\u3002\n");
        sb.append("\u9ed8\u8ba4\u7ed9\u51fa\u77ed\u7b54\uff0c\u63a7\u5236\u5728 ").append(modeConfig.answerPointLimit()).append(" \u70b9\u4ee5\u5185\uff0c\u6bcf\u70b91-2\u53e5\u3002\n");
        sb.append("\u82e5\u6709\u5fc5\u8981\uff0c\u6700\u591a\u7ed9 ").append(modeConfig.exampleLimit()).append(" \u4e2a\u7b80\u77ed\u82f1\u6587\u4f8b\u53e5\u3002\u53ea\u6709\u5f53\u7528\u6237\u660e\u786e\u8981\u6c42\u8be6\u7ec6\u5c55\u5f00\u65f6\uff0c\u518d\u5199\u957f\u4e00\u70b9\u3002\n");
        sb.append("\u4f18\u5148\u4fdd\u8bc1\u56de\u7b54\u5b8c\u6574\uff0c\u4e0d\u8981\u4e3a\u4e86\u8ffd\u6c42\u9762\u9762\u4ff1\u5230\u800c\u62c9\u957f\u5185\u5bb9\u3002\n");
        sb.append("\u56de\u7b54\u5b8c\u6574\u7ed3\u675f\u65f6\uff0c\u5728\u6700\u540e\u5355\u72ec\u8f93\u51fa ").append(ANSWER_END_MARKER).append(" \u3002\n");
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
        if (text.startsWith("")) {
            text = text.replace("markdown", "")
                    .replace("md", "")
                    .replace("json", "")
                    .replace("", "")
                    .trim();
        }
        String extracted = extractAnswerField(text);
        if (StringUtils.hasText(extracted)) {
            text = extracted;
        }
        return stripEndMarker(text);
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

    private double extractModelSizeB(String modelName) {
        if (!StringUtils.hasText(modelName)) {
            return -1;
        }
        Matcher matcher = MODEL_SIZE_PATTERN.matcher(modelName);
        double result = -1;
        while (matcher.find()) {
            try {
                result = Double.parseDouble(matcher.group(1));
            } catch (Exception ignore) {
                // Ignore invalid capture and continue.
            }
        }
        return result;
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
                              int maxContinuationRounds,
                              int answerPointLimit,
                              int exampleLimit,
                              int continuationPromptLimit,
                              int continuationAnswerLimit) {
    }

    private record GenerationChunk(String content, boolean truncated) {
    }

    private record GeneratedAnswer(String content, int continuationRounds) {
    }
}







