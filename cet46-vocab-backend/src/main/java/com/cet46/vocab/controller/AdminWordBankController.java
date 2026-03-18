package com.cet46.vocab.controller;

import com.cet46.vocab.common.Result;
import com.cet46.vocab.common.WordType;
import com.cet46.vocab.common.ResultCode;
import com.cet46.vocab.llm.LlmAsyncService;
import com.cet46.vocab.llm.LlmProvider;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/admin/word-bank")
public class AdminWordBankController {

    private final JdbcTemplate jdbcTemplate;
    private final LlmAsyncService llmAsyncService;

    public AdminWordBankController(JdbcTemplate jdbcTemplate, LlmAsyncService llmAsyncService) {
        this.jdbcTemplate = jdbcTemplate;
        this.llmAsyncService = llmAsyncService;
    }

    @PostMapping("/preview")
    public Result<Map<String, Object>> previewCsv(@RequestParam("wordType") String wordType,
                                                  @RequestParam("file") MultipartFile file) {
        String normalizedType = normalizeWordType(wordType);
        if (normalizedType == null) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), WordType.supportedHint());
        }
        if (file == null || file.isEmpty()) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), "file is empty");
        }
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            ParseResult parsed = parseImportRows(content, normalizedType);
            Map<String, Object> data = new HashMap<>();
            data.put("inserted", parsed.inserted);
            data.put("updated", parsed.updated);
            data.put("skipped", parsed.skipped);
            data.put("errors", parsed.errors);
            data.put("samples", parsed.samples);
            return Result.success(data);
        } catch (Exception ex) {
            return Result.fail(ResultCode.INTERNAL_ERROR.getCode(), "preview failed: " + ex.getMessage());
        }
    }

    @PostMapping("/import")
    public Result<Map<String, Object>> importCsv(@RequestParam("wordType") String wordType,
                                                 @RequestParam("file") MultipartFile file,
                                                 Authentication authentication) {
        String normalizedType = normalizeWordType(wordType);
        if (normalizedType == null) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), WordType.supportedHint());
        }
        if (file == null || file.isEmpty()) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), "file is empty");
        }
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            ParseResult parsed = parseImportRows(content, normalizedType);
            String batchId = buildBatchId(normalizedType);
            Long userId = getUserId(authentication);

            jdbcTemplate.update(
                    "INSERT INTO word_import_batch(batch_id,word_type,file_name,inserted_count,updated_count,skipped_count,status,created_by) " +
                            "VALUES(?,?,?,?,?,?,?,?)",
                    batchId, normalizedType, file.getOriginalFilename(),
                    parsed.inserted, parsed.updated, parsed.skipped, "IMPORTED", userId
            );

            String table = resolveTable(normalizedType);
            for (ImportRow row : parsed.rows) {
                if ("SKIP".equals(row.action)) {
                    continue;
                }
                if ("INSERT".equals(row.action)) {
                    jdbcTemplate.update(
                            "INSERT INTO " + table + " (id, english, sent, chinese) VALUES (?, ?, ?, ?)",
                            row.id, row.english, row.sent, row.chinese
                    );
                } else if ("UPDATE".equals(row.action)) {
                    jdbcTemplate.update(
                            "UPDATE " + table + " SET english = ?, sent = ?, chinese = ? WHERE id = ?",
                            row.english, row.sent, row.chinese, row.id
                    );
                }
                jdbcTemplate.update(
                        "INSERT INTO word_import_batch_item(batch_id,word_type,word_id,action_type,old_english,old_sent,old_chinese,new_english,new_sent,new_chinese) " +
                                "VALUES(?,?,?,?,?,?,?,?,?,?)",
                        batchId, normalizedType, row.id, row.action,
                        row.oldEnglish, row.oldSent, row.oldChinese,
                        row.english, row.sent, row.chinese
                );
            }

            Map<String, Object> data = new HashMap<>();
            data.put("batchId", batchId);
            data.put("inserted", parsed.inserted);
            data.put("updated", parsed.updated);
            data.put("skipped", parsed.skipped);
            data.put("errors", parsed.errors);
            return Result.success(data);
        } catch (Exception ex) {
            return Result.fail(ResultCode.INTERNAL_ERROR.getCode(), "import failed: " + ex.getMessage());
        }
    }

    @GetMapping("/batches")
    public Result<List<Map<String, Object>>> listBatches(@RequestParam(value = "wordType", required = false) String wordType) {
        String sql = "SELECT batch_id, word_type, file_name, inserted_count, updated_count, skipped_count, status, created_by, created_at, rolled_back_at " +
                "FROM word_import_batch ";
        List<Map<String, Object>> rows;
        if (StringUtils.hasText(wordType) && normalizeWordType(wordType) != null) {
            sql += "WHERE word_type = ? ORDER BY created_at DESC LIMIT 50";
            rows = jdbcTemplate.queryForList(sql, normalizeWordType(wordType));
        } else {
            sql += "ORDER BY created_at DESC LIMIT 50";
            rows = jdbcTemplate.queryForList(sql);
        }
        return Result.success(rows);
    }

    @PostMapping("/rollback")
    public Result<Map<String, Object>> rollback(@RequestBody RollbackRequest req) {
        if (!StringUtils.hasText(req.getBatchId())) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), "batchId is required");
        }
        List<Map<String, Object>> batchRows = jdbcTemplate.queryForList(
                "SELECT batch_id, word_type, status FROM word_import_batch WHERE batch_id = ? LIMIT 1",
                req.getBatchId().trim()
        );
        if (batchRows.isEmpty()) {
            return Result.fail(ResultCode.NOT_FOUND.getCode(), "batch not found");
        }
        Map<String, Object> batch = batchRows.get(0);
        String wordType = String.valueOf(batch.get("word_type"));
        String table = resolveTable(wordType);

        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "SELECT id, word_id, action_type, old_english, old_sent, old_chinese, new_english, new_sent, new_chinese, rolled_back " +
                        "FROM word_import_batch_item WHERE batch_id = ? ORDER BY id DESC",
                req.getBatchId().trim()
        );

        int rolledBack = 0;
        int skipped = 0;
        for (Map<String, Object> item : items) {
            int already = item.get("rolled_back") == null ? 0 : ((Number) item.get("rolled_back")).intValue();
            if (already == 1) {
                skipped++;
                continue;
            }

            Long itemId = ((Number) item.get("id")).longValue();
            Integer wordId = ((Number) item.get("word_id")).intValue();
            String actionType = String.valueOf(item.get("action_type"));

            if ("INSERT".equalsIgnoreCase(actionType)) {
                int affected = jdbcTemplate.update("DELETE FROM " + table + " WHERE id = ?", wordId);
                if (affected > 0) {
                    jdbcTemplate.update("DELETE FROM word_meta WHERE word_id = ? AND word_type = ?", wordId, wordType);
                    jdbcTemplate.update("DELETE FROM user_word_progress WHERE word_id = ? AND word_type = ?", wordId, wordType);
                    jdbcTemplate.update("DELETE FROM review_log WHERE word_id = ? AND word_type = ?", wordId, wordType);
                    jdbcTemplate.update("UPDATE word_import_batch_item SET rolled_back = 1 WHERE id = ?", itemId);
                    rolledBack++;
                } else {
                    skipped++;
                }
            } else if ("UPDATE".equalsIgnoreCase(actionType)) {
                int affected = jdbcTemplate.update(
                        "UPDATE " + table + " SET english = ?, sent = ?, chinese = ? WHERE id = ?",
                        item.get("old_english"), item.get("old_sent"), item.get("old_chinese"), wordId
                );
                if (affected > 0) {
                    jdbcTemplate.update("UPDATE word_import_batch_item SET rolled_back = 1 WHERE id = ?", itemId);
                    rolledBack++;
                } else {
                    skipped++;
                }
            } else {
                skipped++;
            }
        }

        String status = skipped == 0 ? "ROLLED_BACK" : "PARTIAL_ROLLBACK";
        jdbcTemplate.update(
                "UPDATE word_import_batch SET status = ?, rolled_back_at = NOW() WHERE batch_id = ?",
                status, req.getBatchId().trim()
        );

        Map<String, Object> data = new HashMap<>();
        data.put("batchId", req.getBatchId().trim());
        data.put("rolledBack", rolledBack);
        data.put("skipped", skipped);
        data.put("status", status);
        return Result.success(data);
    }

    @PostMapping("/generate-explain")
    public Result<Map<String, Object>> generateExplain(@RequestBody GenerateExplainRequest req) {
        String wordType = normalizeWordType(req.getWordType());
        if (wordType == null) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), WordType.supportedHint());
        }
        String style = normalizeStyle(req.getStyle());
        if (style == null) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), "style must be academic/story/sarcastic");
        }
        String provider = LlmProvider.normalize(req.getProvider());
        int limit = req.getLimit() == null ? 100 : Math.min(Math.max(req.getLimit(), 1), 500);

        String table = resolveTable(wordType);
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM " + table + " ORDER BY id ASC LIMIT ?",
                Long.class, limit
        );
        for (Long id : ids) {
            llmAsyncService.generateWordExplainContent(id, wordType, style, provider);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("queued", ids.size());
        data.put("provider", provider);
        data.put("style", style);
        return Result.success(data);
    }

    @PostMapping("/generate-explain-missing")
    public Result<Map<String, Object>> generateExplainMissing(@RequestBody GenerateExplainMissingRequest req) {
        String wordType = normalizeWordType(req.getWordType());
        if (wordType == null) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), WordType.supportedHint());
        }
        String style = normalizeStyle(req.getStyle());
        if (style == null) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), "style must be academic/story/sarcastic");
        }
        String provider = LlmProvider.normalize(req.getProvider());
        int limit = req.getLimit() == null ? 100 : Math.min(Math.max(req.getLimit(), 1), 500);

        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT w.id FROM " + resolveTable(wordType) + " w " +
                        "LEFT JOIN word_meta m ON m.word_id = w.id AND m.word_type = ? AND m.style = ? " +
                        "WHERE m.id IS NULL OR m.ai_explain IS NULL OR m.ai_explain = '' " +
                        "ORDER BY w.id ASC LIMIT ?",
                Long.class, wordType, style, limit
        );
        for (Long id : ids) {
            llmAsyncService.generateWordExplainContent(id, wordType, style, provider);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("queued", ids.size());
        data.put("provider", provider);
        data.put("style", style);
        return Result.success(data);
    }

    private ParseResult parseImportRows(String content, String wordType) {
        String table = resolveTable(wordType);
        int nextId = queryNextId(table);
        ParseResult result = new ParseResult();
        String[] lines = content.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!StringUtils.hasText(line)) {
                continue;
            }
            List<String> cols = parseCsvLine(line);
            if (cols.isEmpty()) {
                continue;
            }
            if (i == 0 && isHeader(cols)) {
                continue;
            }
            try {
                Integer id = null;
                String english;
                String sent = null;
                String chinese;
                if (cols.size() >= 4 && isInteger(cols.get(0))) {
                    id = Integer.valueOf(cols.get(0).trim());
                    english = text(cols.get(1));
                    sent = text(cols.get(2));
                    chinese = text(cols.get(3));
                } else if (cols.size() >= 3) {
                    english = text(cols.get(0));
                    sent = text(cols.get(1));
                    chinese = text(cols.get(2));
                } else if (cols.size() == 2) {
                    english = text(cols.get(0));
                    chinese = text(cols.get(1));
                } else {
                    result.skipped++;
                    result.errors.add("line " + (i + 1) + ": column count < 2");
                    continue;
                }

                if (!StringUtils.hasText(english) || !StringUtils.hasText(chinese)) {
                    result.skipped++;
                    result.errors.add("line " + (i + 1) + ": english/chinese is empty");
                    continue;
                }

                ImportRow row = new ImportRow();
                row.english = english;
                row.sent = sent;
                row.chinese = chinese;

                Map<String, Object> existing = null;
                if (id != null) {
                    existing = findById(table, id);
                } else {
                    existing = findByEnglish(table, english);
                    if (existing != null) {
                        id = ((Number) existing.get("id")).intValue();
                    }
                }
                if (id == null) {
                    id = nextId++;
                }
                row.id = id;

                if (existing == null) {
                    row.action = "INSERT";
                    result.inserted++;
                } else {
                    row.action = "UPDATE";
                    row.oldEnglish = asString(existing.get("english"));
                    row.oldSent = asString(existing.get("sent"));
                    row.oldChinese = asString(existing.get("chinese"));
                    result.updated++;
                }
                result.rows.add(row);
                if (result.samples.size() < 20) {
                    Map<String, Object> sample = new HashMap<>();
                    sample.put("id", row.id);
                    sample.put("english", row.english);
                    sample.put("action", row.action);
                    sample.put("chinese", row.chinese);
                    result.samples.add(sample);
                }
            } catch (Exception ex) {
                result.skipped++;
                result.errors.add("line " + (i + 1) + ": " + ex.getMessage());
            }
        }
        return result;
    }

    private Map<String, Object> findById(String table, Integer id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, english, sent, chinese FROM " + table + " WHERE id = ? LIMIT 1", id
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> findByEnglish(String table, String english) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, english, sent, chinese FROM " + table + " WHERE english = ? LIMIT 1", english
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Integer queryNextId(String table) {
        Integer maxId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + table, Integer.class);
        return (maxId == null ? 0 : maxId) + 1;
    }

    private Long getUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        return Long.valueOf(authentication.getPrincipal().toString());
    }

    private String buildBatchId(String wordType) {
        return wordType + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }

    private String resolveTable(String wordType) {
        WordType type = WordType.from(wordType);
        return type == null ? null : type.tableName();
    }

    private String normalizeWordType(String wordType) {
        return WordType.normalize(wordType);
    }

    private String normalizeStyle(String style) {
        if (!StringUtils.hasText(style)) {
            return "story";
        }
        String value = style.trim().toLowerCase(Locale.ROOT);
        if ("academic".equals(value) || "story".equals(value) || "sarcastic".equals(value)) {
            return value;
        }
        return null;
    }

    private String text(String value) {
        return value == null ? null : value.trim();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            Integer.parseInt(value.trim());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isHeader(List<String> cols) {
        String head = cols.get(0).trim().toLowerCase(Locale.ROOT);
        return "id".equals(head) || "english".equals(head) || "word".equals(head);
    }

    private List<String> parseCsvLine(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuote = !inQuote;
                }
                continue;
            }
            if (ch == ',' && !inQuote) {
                cols.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        cols.add(current.toString());
        return cols;
    }

    private static class ParseResult {
        private int inserted;
        private int updated;
        private int skipped;
        private final List<String> errors = new ArrayList<>();
        private final List<Map<String, Object>> samples = new ArrayList<>();
        private final List<ImportRow> rows = new ArrayList<>();
    }

    private static class ImportRow {
        private Integer id;
        private String action;
        private String english;
        private String sent;
        private String chinese;
        private String oldEnglish;
        private String oldSent;
        private String oldChinese;
    }

    @Data
    public static class RollbackRequest {
        @NotBlank
        private String batchId;
    }

    @Data
    public static class GenerateExplainRequest {
        @NotBlank
        private String wordType;
        private String style = "story";
        private String provider = LlmProvider.LOCAL;
        @Min(1)
        @Max(500)
        private Integer limit = 100;
    }

    @Data
    public static class GenerateExplainMissingRequest {
        @NotBlank
        private String wordType;
        private String style = "story";
        private String provider = LlmProvider.LOCAL;
        @Min(1)
        @Max(500)
        private Integer limit = 100;
    }
}



