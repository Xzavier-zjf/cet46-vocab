package com.cet46.vocab.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class SchemaMigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final CloudLlmProperties cloudLlmProperties;
    private final SecurityRbacProperties securityRbacProperties;

    public SchemaMigrationRunner(JdbcTemplate jdbcTemplate,
                                 CloudLlmProperties cloudLlmProperties,
                                 SecurityRbacProperties securityRbacProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.cloudLlmProperties = cloudLlmProperties;
        this.securityRbacProperties = securityRbacProperties;
    }

    @PostConstruct
    public void migrate() {
        ensureUserLlmProviderColumn();
        ensureUserLlmLocalModelColumn();
        ensureUserLlmCloudModelColumn();
        ensureWordMetaAiExplainColumns();
        ensureWordImportBatchTables();
        ensureCloudLlmModelTable();
        ensureCloudLlmModelIsolationColumns();
        ensureCloudLlmModelUniqueIndex();
        ensureRolePermissionTables();
        seedRolePermissionsFromConfig();
        seedCloudLlmModelsFromConfig();
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

    private void ensureCloudLlmModelTable() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS cloud_llm_model (
                      id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                      provider VARCHAR(32) NOT NULL DEFAULT 'bailian',
                      model_key VARCHAR(128) NOT NULL,
                      display_name VARCHAR(128) NOT NULL,
                      enabled TINYINT(1) NOT NULL DEFAULT 1,
                      is_default TINYINT(1) NOT NULL DEFAULT 0,
                      visibility VARCHAR(32) NOT NULL DEFAULT 'global',
                      owner_user_id BIGINT NOT NULL DEFAULT 0,
                      tenant_id BIGINT NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      UNIQUE KEY uk_cloud_llm_model_visibility_owner_model (visibility, owner_user_id, model_key)
                    )
                    """);
        } catch (Exception ex) {
            log.error("failed to ensure cloud_llm_model table", ex);
        }
    }

    private void ensureCloudLlmModelIsolationColumns() {
        try {
            Integer visibilityExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cloud_llm_model' AND COLUMN_NAME = 'visibility'",
                    Integer.class
            );
            if (visibilityExists == null || visibilityExists == 0) {
                jdbcTemplate.execute("ALTER TABLE cloud_llm_model ADD COLUMN visibility VARCHAR(32) NOT NULL DEFAULT 'global' AFTER is_default");
                log.info("added cloud_llm_model.visibility column");
            }

            Integer ownerExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cloud_llm_model' AND COLUMN_NAME = 'owner_user_id'",
                    Integer.class
            );
            if (ownerExists == null || ownerExists == 0) {
                jdbcTemplate.execute("ALTER TABLE cloud_llm_model ADD COLUMN owner_user_id BIGINT NOT NULL DEFAULT 0 AFTER visibility");
                log.info("added cloud_llm_model.owner_user_id column");
            }

            Integer tenantExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cloud_llm_model' AND COLUMN_NAME = 'tenant_id'",
                    Integer.class
            );
            if (tenantExists == null || tenantExists == 0) {
                jdbcTemplate.execute("ALTER TABLE cloud_llm_model ADD COLUMN tenant_id BIGINT NULL AFTER owner_user_id");
                log.info("added cloud_llm_model.tenant_id column");
            }

            jdbcTemplate.update(
                    "UPDATE cloud_llm_model SET visibility = 'global' WHERE visibility IS NULL OR visibility = ''"
            );
            jdbcTemplate.update(
                    "UPDATE cloud_llm_model SET owner_user_id = 0 WHERE owner_user_id IS NULL"
            );
            jdbcTemplate.execute("ALTER TABLE cloud_llm_model MODIFY COLUMN owner_user_id BIGINT NOT NULL DEFAULT 0");
        } catch (Exception ex) {
            log.error("failed to ensure cloud_llm_model isolation columns", ex);
        }
    }

    private void ensureCloudLlmModelUniqueIndex() {
        try {
            Integer oldUk = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.STATISTICS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cloud_llm_model' AND INDEX_NAME = 'uk_cloud_llm_model_model_key'",
                    Integer.class
            );
            if (oldUk != null && oldUk > 0) {
                jdbcTemplate.execute("ALTER TABLE cloud_llm_model DROP INDEX uk_cloud_llm_model_model_key");
            }

            Integer newUk = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.STATISTICS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cloud_llm_model' AND INDEX_NAME = 'uk_cloud_llm_model_visibility_owner_model'",
                    Integer.class
            );
            if (newUk == null || newUk == 0) {
                jdbcTemplate.execute("ALTER TABLE cloud_llm_model ADD UNIQUE KEY uk_cloud_llm_model_visibility_owner_model (visibility, owner_user_id, model_key)");
            }
        } catch (Exception ex) {
            log.error("failed to ensure cloud_llm_model unique index", ex);
        }
    }


    private void ensureRolePermissionTables() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS rbac_role_permission (
                      role_code VARCHAR(32) NOT NULL,
                      permission_code VARCHAR(64) NOT NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (role_code, permission_code)
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS rbac_role_permission_audit (
                      id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                      actor_user_id BIGINT NULL,
                      role_code VARCHAR(32) NOT NULL,
                      before_permissions VARCHAR(512) NULL,
                      after_permissions VARCHAR(512) NULL,
                      changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      INDEX idx_rbac_role_permission_audit_changed_at (changed_at)
                    )
                    """);
        } catch (Exception ex) {
            log.error("failed to ensure rbac role permission tables", ex);
        }
    }

    private void seedRolePermissionsFromConfig() {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM rbac_role_permission", Integer.class);
            if (count != null && count > 0) {
                return;
            }

            List<String> roles = List.of("ADMIN", "USER");
            for (String role : roles) {
                Set<String> permissions = new LinkedHashSet<>(securityRbacProperties.resolvePermissions(role));
                for (String permission : permissions) {
                    jdbcTemplate.update(
                            "INSERT INTO rbac_role_permission(role_code, permission_code) VALUES (?, ?)",
                            role,
                            permission
                    );
                }
            }
        } catch (Exception ex) {
            log.error("failed to seed role permissions from config", ex);
        }
    }

    private void seedCloudLlmModelsFromConfig() {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM cloud_llm_model", Integer.class);
            if (count != null && count > 0) {
                return;
            }

            List<String> configured = cloudLlmProperties.resolveModels();
            Set<String> deduped = new LinkedHashSet<>();
            for (String item : configured) {
                if (StringUtils.hasText(item)) {
                    deduped.add(item.trim());
                }
            }

            String defaultModel = cloudLlmProperties.resolveDefaultModel();
            if (!StringUtils.hasText(defaultModel) && !deduped.isEmpty()) {
                defaultModel = deduped.iterator().next();
            }

            if (!StringUtils.hasText(defaultModel) && deduped.isEmpty()) {
                return;
            }

            if (StringUtils.hasText(defaultModel)) {
                deduped.add(defaultModel.trim());
            }

            for (String modelKey : deduped) {
                boolean isDefault = StringUtils.hasText(defaultModel) && modelKey.equals(defaultModel.trim());
                jdbcTemplate.update(
                        "INSERT INTO cloud_llm_model(provider, model_key, display_name, enabled, is_default, visibility, owner_user_id, tenant_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        "bailian",
                        modelKey,
                        modelKey,
                        1,
                        isDefault ? 1 : 0,
                        "global",
                        0L,
                        null
                );
            }
        } catch (Exception ex) {
            log.error("failed to seed cloud_llm_model from config", ex);
        }
    }
}




