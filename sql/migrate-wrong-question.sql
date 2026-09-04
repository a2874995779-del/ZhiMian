-- 为已有智面数据库补充错题本表，重复执行不会报错。
USE zhimian;

CREATE TABLE IF NOT EXISTS `wrong_question` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `question_id` BIGINT NOT NULL,
  `wrong_count` INT NOT NULL DEFAULT 0,
  `correct_count` INT NOT NULL DEFAULT 0,
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-未掌握 1-已掌握',
  `last_wrong_time` DATETIME DEFAULT NULL,
  `last_review_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_question` (`user_id`, `question_id`),
  KEY `idx_user_status_time` (`user_id`, `status`, `last_wrong_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户错题本';
