package com.cet46.vocab.integration;

import com.cet46.vocab.common.PageResult;
import com.cet46.vocab.common.Result;
import com.cet46.vocab.common.ResultCode;
import com.cet46.vocab.common.WordType;
import com.cet46.vocab.controller.AdminWordBankController;
import com.cet46.vocab.controller.WordController;
import com.cet46.vocab.dto.request.AddWordRequest;
import com.cet46.vocab.dto.response.WordDetailResponse;
import com.cet46.vocab.dto.response.WordListItem;
import com.cet46.vocab.dto.response.WordProgressStatusResponse;
import com.cet46.vocab.entity.User;
import com.cet46.vocab.llm.LlmAsyncService;
import com.cet46.vocab.llm.LlmCacheService;
import com.cet46.vocab.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class WordTypeIntegrationTest {

    private static final String CACHE_PREFIX = "llm:content:";

    @Autowired
    private WordController wordController;

    @Autowired
    private AdminWordBankController adminWordBankController;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private LlmAsyncService llmAsyncService;

    @Autowired
    private LlmCacheService llmCacheService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .username("it_word_type_" + System.currentTimeMillis())
                .password("pwd")
                .nickname("it")
                .role("ADMIN")
                .dailyTarget(20)
                .build();
        userMapper.insert(user);
        userId = user.getId();
        authentication = new TestingAuthenticationToken(String.valueOf(userId), null);
    }

    @Test
    void listShouldCoverCet4Cet6Cet4lxCet6lx() {
        for (String type : List.of("cet4", "cet6", "cet4lx", "cet6lx")) {
            Result<PageResult<WordListItem>> result = wordController.getWordList(type, 1, 5, null, null, authentication);
            assertSuccess(result);
            assertNotNull(result.getData());
            assertNotNull(result.getData().getList());
            assertFalse(result.getData().getList().isEmpty(), "word list should not be empty for " + type);
        }
    }

    @Test
    void detailShouldCoverCet4Cet6Cet4lxCet6lx() {
        for (String type : List.of("cet4", "cet6", "cet4lx", "cet6lx")) {
            Long wordId = firstWordId(type);
            Result<WordDetailResponse> result = wordController.getWordDetail(wordId, type, authentication);
            assertSuccess(result);
            assertNotNull(result.getData());
            assertEquals(type, result.getData().getWordType());
            assertEquals(wordId, result.getData().getWordId());
            assertNotNull(result.getData().getEnglish());
        }
    }

    @Test
    void addShouldCoverCet4Cet6Cet4lxCet6lx() {
        for (String type : List.of("cet4", "cet6", "cet4lx", "cet6lx")) {
            Long wordId = firstWordId(type);
            AddWordRequest request = new AddWordRequest();
            request.setWordId(wordId);
            request.setWordType(type);

            Result<Void> addResult = wordController.addWordToLearn(request, authentication);
            assertSuccess(addResult);

            Result<WordProgressStatusResponse> statusResult =
                    wordController.getProgressStatus(wordId, type, authentication);
            assertSuccess(statusResult);
            assertNotNull(statusResult.getData());
            assertEquals(type, statusResult.getData().getWordType());
            String status = statusResult.getData().getStatus();
            assertTrue("LEARNING".equals(status) || "COMPLETED".equals(status));
        }
    }

    @Test
    void importShouldCoverCet4Cet6Cet4lxCet6lx() {
        for (WordType type : WordType.values()) {
            String uniqueWord = "it_" + type.code() + "_" + System.nanoTime();
            String csv = "english,sent,chinese\n" + uniqueWord + ",/test/,test-import";
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "it_" + type.code() + ".csv",
                    "text/csv",
                    csv.getBytes(StandardCharsets.UTF_8)
            );

            Result<Map<String, Object>> result = adminWordBankController.importCsv(type.code(), file, authentication);
            assertSuccess(result);
            assertNotNull(result.getData());
            Number inserted = (Number) result.getData().get("inserted");
            Number updated = (Number) result.getData().get("updated");
            int changed = (inserted == null ? 0 : inserted.intValue()) + (updated == null ? 0 : updated.intValue());
            assertTrue(changed >= 1, "import should insert or update at least one row for " + type.code());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentGenerateShouldKeepSingleWordMetaRow() throws Exception {
        String wordType = "cet4lx";
        Long wordId = firstWordId(wordType);
        String style = "itc" + (System.nanoTime() % 1000000);

        String sentenceHash = CACHE_PREFIX + llmCacheService.buildHash(wordId, wordType, "sentence", style);
        String synonymHash = CACHE_PREFIX + llmCacheService.buildHash(wordId, wordType, "synonym", style);
        String mnemonicHash = CACHE_PREFIX + llmCacheService.buildHash(wordId, wordType, "mnemonic", style);
        llmCacheService.setCache(sentenceHash, "{\"sentence_en\":\"A punctual student arrives early.\",\"sentence_zh\":\"A student arrives early.\",\"difficulty\":\"easy\"}");
        llmCacheService.setCache(synonymHash, "{\"synonyms\":[{\"synonym\":\"on time\",\"difference\":\"focuses on timing\",\"example\":\"He is always on time.\"}]}");
        llmCacheService.setCache(mnemonicHash, "{\"mnemonic\":\"punctual = point + time\",\"root_analysis\":\"punct- means point\"}");

        int calls = 20;
        ExecutorService pool = Executors.newFixedThreadPool(calls);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(calls);
        for (int i = 0; i < calls; i++) {
            pool.submit(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    llmAsyncService.generateWordContent(wordId, wordType, style, "local");
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "dispatch timeout");
        pool.shutdownNow();

        try {
            long deadline = System.currentTimeMillis() + 12_000;
            while (System.currentTimeMillis() < deadline) {
                Long count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM word_meta WHERE word_id = ? AND word_type = ? AND style = ?",
                        Long.class,
                        wordId, wordType, style
                );
                if (count != null && count == 1L) {
                    String genStatus = jdbcTemplate.queryForObject(
                            "SELECT gen_status FROM word_meta WHERE word_id = ? AND word_type = ? AND style = ? LIMIT 1",
                            String.class,
                            wordId, wordType, style
                    );
                    assertNotNull(genStatus);
                    assertTrue(List.of("pending", "partial", "full", "fallback").contains(genStatus));
                    return;
                }
                Thread.sleep(150);
            }

            Long finalCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM word_meta WHERE word_id = ? AND word_type = ? AND style = ?",
                    Long.class,
                    wordId, wordType, style
            );
            assertEquals(1L, finalCount, "duplicate word_meta rows detected under concurrent async generation");
        } finally {
            jdbcTemplate.update("DELETE FROM word_meta WHERE word_id = ? AND word_type = ? AND style = ?", wordId, wordType, style);
            llmCacheService.deleteCache(sentenceHash);
            llmCacheService.deleteCache(synonymHash);
            llmCacheService.deleteCache(mnemonicHash);
        }
    }

    private Long firstWordId(String type) {
        Result<PageResult<WordListItem>> listResult = wordController.getWordList(type, 1, 1, null, null, authentication);
        assertSuccess(listResult);
        assertNotNull(listResult.getData());
        assertNotNull(listResult.getData().getList());
        assertFalse(listResult.getData().getList().isEmpty(), "empty list for " + type);
        return listResult.getData().getList().get(0).getWordId();
    }

    private static void assertSuccess(Result<?> result) {
        assertNotNull(result);
        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode(), result.getMessage());
    }
}