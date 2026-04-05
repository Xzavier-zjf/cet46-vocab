SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `assistant_chat_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `client_session_id` varchar(80) NOT NULL COMMENT '前端会话ID（迁移兼容）',
  `title` varchar(128) NOT NULL,
  `updated_at` datetime NOT NULL,
  `has_interaction` tinyint(1) NOT NULL DEFAULT 0,
  `pinned` tinyint(1) NOT NULL DEFAULT 0,
  `group_id` varchar(80) NULL,
  `group_name` varchar(64) NULL,
  `context_json` longtext NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `last_synced_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_assistant_session_user_client` (`user_id`, `client_session_id`),
  KEY `idx_assistant_session_user_updated` (`user_id`, `updated_at`),
  KEY `idx_assistant_session_user_pinned` (`user_id`, `pinned`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='助手会话持久化';

CREATE TABLE IF NOT EXISTS `assistant_chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` bigint NOT NULL,
  `client_message_id` varchar(80) NOT NULL COMMENT '前端消息ID（迁移兼容）',
  `role` varchar(16) NOT NULL COMMENT 'user/assistant',
  `content` longtext NOT NULL,
  `feedback` varchar(16) NULL,
  `auto_continued` tinyint(1) NOT NULL DEFAULT 0,
  `continuation_rounds` int NOT NULL DEFAULT 0,
  `sort_order` int NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_assistant_message_session_client` (`session_id`, `client_message_id`),
  KEY `idx_assistant_message_session_order` (`session_id`, `sort_order`),
  CONSTRAINT `fk_assistant_message_session` FOREIGN KEY (`session_id`) REFERENCES `assistant_chat_session` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='助手消息持久化';
