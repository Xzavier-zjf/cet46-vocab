SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `daily_plan_cache` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `plan_date` date NOT NULL,
  `due_count` int NOT NULL DEFAULT 0 COMMENT '当日计划待复习总量（00:00快照）',
  `reviewed_count` int NOT NULL DEFAULT 0 COMMENT '当日已完成复习数',
  `daily_target` int NOT NULL DEFAULT 20 COMMENT '当日用户目标',
  `completion_rate` decimal(6,2) NOT NULL DEFAULT 0.00 COMMENT '完成率百分比 0-100',
  `generated_at` datetime NOT NULL,
  `last_synced_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_daily_plan_user_date` (`user_id`, `plan_date`),
  KEY `idx_daily_plan_date` (`plan_date`),
  KEY `idx_daily_plan_user_updated` (`user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日复习计划快照缓存';
