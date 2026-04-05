SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `quiz_session_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `quiz_id` varchar(64) NOT NULL,
  `user_id` bigint NOT NULL,
  `word_type` varchar(10) NOT NULL COMMENT 'cet4/cet6/mixed',
  `quiz_type` varchar(20) NOT NULL COMMENT 'choice/fill',
  `total` int NOT NULL,
  `correct` int NOT NULL,
  `wrong_count` int NOT NULL,
  `details_json` longtext NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_quiz_session_user_created` (`user_id`, `created_at`),
  KEY `idx_quiz_session_quiz_id` (`quiz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测验会话历史（后端持久化）';

CREATE TABLE IF NOT EXISTS `llm_usage_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `date_key` char(8) NOT NULL COMMENT 'yyyyMMdd',
  `user_id` bigint NOT NULL,
  `scope` varchar(20) NOT NULL COMMENT 'public/private',
  `provider` varchar(40) NOT NULL,
  `model_key` varchar(160) NOT NULL,
  `source` varchar(80) NULL,
  `runtime_source` varchar(80) NULL,
  `calls` bigint NOT NULL DEFAULT 0,
  `last_used_at` bigint NULL COMMENT 'epoch milli',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_llm_usage_daily` (`date_key`, `user_id`, `scope`, `provider`, `model_key`),
  KEY `idx_llm_usage_daily_user_date` (`user_id`, `date_key`),
  KEY `idx_llm_usage_daily_scope_date` (`scope`, `date_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云端模型调用按日聚合（持久化）';
