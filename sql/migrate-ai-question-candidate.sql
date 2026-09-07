USE zhimian;

CREATE TABLE IF NOT EXISTS `ai_question_candidate` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(256) NOT NULL COMMENT '从 AI 回复中提取的问题',
  `normalized_title` VARCHAR(256) NOT NULL COMMENT '标准化后的问题',
  `fingerprint` CHAR(64) NOT NULL COMMENT '标准化文本的 SHA-256',
  `answer` TEXT DEFAULT NULL COMMENT 'AI 生成的参考答案',
  `difficulty` TINYINT DEFAULT NULL COMMENT '1-简单 2-中等 3-困难',
  `category_id` BIGINT DEFAULT NULL COMMENT 'AI 建议的分类 id',
  `tag_ids` JSON DEFAULT NULL COMMENT 'AI 建议的标签 id 数组',
  `direction` VARCHAR(32) NOT NULL COMMENT '来源面试方向',
  `source_session_id` BIGINT NOT NULL COMMENT '来源会话 id',
  `source_message_id` BIGINT NOT NULL COMMENT '来源 assistant 消息 id',
  `duplicate_count` INT NOT NULL DEFAULT 1 COMMENT '被重复问到的次数',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-补全中 1-待审核 2-已收录 3-已忽略 4-补全失败 5-收录中',
  `error_message` VARCHAR(255) DEFAULT NULL,
  `question_id` BIGINT DEFAULT NULL COMMENT '审核后生成的正式题目 id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fingerprint` (`fingerprint`),
  UNIQUE KEY `uk_source_message` (`source_message_id`),
  KEY `idx_status_time` (`status`, `create_time`),
  KEY `idx_direction` (`direction`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'AI 面试候选题';
