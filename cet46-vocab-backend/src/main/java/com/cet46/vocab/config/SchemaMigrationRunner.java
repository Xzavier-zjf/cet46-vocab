package com.cet46.vocab.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaMigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public SchemaMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        ensureUserLlmProviderColumn();
        ensureUserLlmLocalModelColumn();
        ensureUserLlmCloudModelColumn();
        ensureWordMetaAiExplainColumns();
        ensureWordImportBatchTables();
    }

    private void ensureUserLlmProviderColumn() {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'llm_provider'",
                    Integer.class
            );
            if (exists != null && exists > 0) {
                return;
            }

            jdbcTemplate.execute(
                    "ALTER TABLE user " +
                            "ADD COLUMN llm_provider VARCHAR(20) NOT NULL DEFAULT 'local' AFTER llm_style"
            );
            log.info("added user.llm_provider column with default local");
        } catch (Exception ex) {
            log.error("failed to ensure user.llm_provider column", ex);
        }
    }

    private void ensureUserLlmLocalModelColumn() {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'llm_local_model'",
                    Integer.class
            );
            if (exists != null && exists > 0) {
                return;
            }

            jdbcTemplate.execute(
                    "ALTER TABLE user " +
                            "ADD COLUMN llm_local_model VARCHAR(128) NULL AFTER llm_provider"
            );
            log.info("added user.llm_local_model column");
        } catch (Exception ex) {
            log.error("failed to ensure user.llm_local_model column", ex);
        }
    }


    private void ensureUserLlmCloudModelColumn() {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'llm_cloud_model'",
                    Integer.class
            );
            if (exists != null && exists > 0) {
                return;
            }

            jdbcTemplate.execute(
                    "ALTER TABLE user " +
                            "ADD COLUMN llm_cloud_model VARCHAR(128) NULL AFTER llm_local_model"
            );
            log.info("added user.llm_cloud_model column");
        } catch (Exception ex) {
            log.error("failed to ensure user.llm_cloud_model column", ex);
        }
    }
    private void ensureWordMetaAiExplainColumns() {
        try {
            Integer explainExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'word_meta' AND COLUMN_NAME = 'ai_explain'",
                    Integer.class
            );
            if (explainExists == null || explainExists == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE word_meta " +
                                "ADD COLUMN ai_explain TEXT NULL AFTER root_analysis"
                );
                log.info("added word_meta.ai_explain column");
            }

            Integer statusExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'word_meta' AND COLUMN_NAME = 'ai_explain_status'",
                    Integer.class
            );
            if (statusExists == null || statusExists == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE word_meta " +
                                "ADD COLUMN ai_explain_status VARCHAR(20) NOT NULL DEFAULT 'pending' AFTER ai_explain"
                );
                log.info("added word_meta.ai_explain_status column");
            }
        } catch (Exception ex) {
            log.error("failed to ensure word_meta ai_explain columns", ex);
        }
    }

    private void ensureWordImportBatchTables() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS word_import_batch (
                      batch_id VARCHAR(64) PRIMARY KEY,
                      word_type VARCHAR(10) NOT NULL,
                      file_name VARCHAR(255) NULL,
                      inserted_count INT NOT NULL DEFAULT 0,
                      updated_count INT NOT NULL DEFAULT 0,
                      skipped_count INT NOT NULL DEFAULT 0,
                      status VARCHAR(20) NOT NULL DEFAULT 'IMPORTED',
                      created_by BIGINT NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      rolled_back_at DATETIME NULL
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS word_import_batch_item (
                      id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                      batch_id VARCHAR(64) NOT NULL,
                      word_type VARCHAR(10) NOT NULL,
                      word_id INT NOT NULL,
                      action_type VARCHAR(10) NOT NULL,
                      old_english VARCHAR(255) NULL,
                      old_sent VARCHAR(255) NULL,
                      old_chinese VARCHAR(255) NULL,
                      new_english VARCHAR(255) NULL,
                      new_sent VARCHAR(255) NULL,
                      new_chinese VARCHAR(255) NULL,
                      rolled_back TINYINT(1) NOT NULL DEFAULT 0,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      INDEX idx_word_import_batch_item_batch (batch_id)
                    )
                    """);
        } catch (Exception ex) {
            log.error("failed to ensure word import batch tables", ex);
        }
    }
}



