-- 智面(ZhiMian)阶段一建库建表脚本
-- 阶段二/三/四的表在进入对应阶段时再执行(SQL 见 docs/backend-dev-guide.md 第 2.7 节起)

CREATE DATABASE IF NOT EXISTS zhimian
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE zhimian;

-- 用户表
CREATE TABLE `user` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`    VARCHAR(64)  NOT NULL COMMENT '登录账号',
  `password`    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文(固定 60 字符,留余量)',
  `nickname`    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
  `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '头像 URL',
  `role`        VARCHAR(16)  NOT NULL DEFAULT 'user' COMMENT '角色:user/admin',
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0-正常 1-封禁',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-否 1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户表';

-- 题目分类表
CREATE TABLE `category` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(64) NOT NULL COMMENT '分类名,如 Java并发、MySQL',
  `parent_id`   BIGINT      NOT NULL DEFAULT 0 COMMENT '父分类 id,0 表示顶级',
  `sort`        INT         NOT NULL DEFAULT 0 COMMENT '排序值,越小越靠前',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '题目分类表';

-- 题目表
CREATE TABLE `question` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `title`          VARCHAR(256) NOT NULL COMMENT '题目标题',
  `content`        TEXT         DEFAULT NULL COMMENT '题干补充描述(Markdown)',
  `answer`         TEXT         NOT NULL COMMENT '参考答案(Markdown)',
  `difficulty`     TINYINT      NOT NULL DEFAULT 1 COMMENT '难度 1-简单 2-中等 3-困难',
  `category_id`    BIGINT       NOT NULL COMMENT '分类 id',
  `view_count`     INT          NOT NULL DEFAULT 0 COMMENT '浏览量(定时从 Redis 回写)',
  `create_user_id` BIGINT       NOT NULL COMMENT '创建人 id',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '题目表';

-- 标签表
CREATE TABLE `tag` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(32) NOT NULL COMMENT '标签名,如 JVM、线程池',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '标签表';

-- 题目-标签关联表
CREATE TABLE `question_tag` (
  `question_id` BIGINT NOT NULL,
  `tag_id`      BIGINT NOT NULL,
  PRIMARY KEY (`question_id`, `tag_id`),
  KEY `idx_tag_id` (`tag_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '题目-标签关联表';

-- 答题记录表
CREATE TABLE `answer_record` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT   NOT NULL COMMENT '答题用户',
  `question_id` BIGINT   NOT NULL COMMENT '题目',
  `result`      TINYINT  NOT NULL COMMENT '0-答错 1-答对(用户自评)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_question_id` (`question_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '答题记录表';

-- 用户错题本
CREATE TABLE `wrong_question` (
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

-- 用户收藏题目
CREATE TABLE `question_favorite` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `question_id` BIGINT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_question` (`user_id`, `question_id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_question_id` (`question_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '题目收藏表';

-- ============ 阶段二:AI 模拟面试(任务 2.2~2.6)============

-- AI 面试会话表
CREATE TABLE `interview_session` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL COMMENT '所属用户',
  `direction`   VARCHAR(32)  NOT NULL COMMENT '专项方向或scenario',
  `mode`        VARCHAR(16)  NOT NULL DEFAULT 'direction' COMMENT 'direction-专项面试 scenario-综合场景面试',
  `scenario_code` VARCHAR(64) DEFAULT NULL COMMENT '综合面试场景编码',
  `plan_json`   JSON         DEFAULT NULL COMMENT '本场面试生成的题目计划快照',
  `title`       VARCHAR(128) DEFAULT NULL COMMENT '会话标题(默认取方向+日期)',
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0-进行中 1-已结束 2-报告已生成',
  `target_question_count` INT NOT NULL DEFAULT 8 COMMENT '本场面试目标题数',
  `answered_question_count` INT NOT NULL DEFAULT 0 COMMENT '已经提交回答的题数',
  `finish_reason` VARCHAR(32) DEFAULT NULL COMMENT 'AUTO_LIMIT/USER_STOP/SYSTEM_ERROR/INACTIVITY_TIMEOUT',
  `end_time`    DATETIME     DEFAULT NULL COMMENT '结束时间',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`, `create_time`),
  KEY `idx_user_status_update` (`user_id`, `status`, `update_time`),
  KEY `idx_status_update` (`status`, `update_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'AI 面试会话表';

-- 面试消息表(system/user/assistant 全量历史,Redis 上下文丢失时靠它重建)
CREATE TABLE `interview_message` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `session_id`  BIGINT      NOT NULL COMMENT '会话 id',
  `role`        VARCHAR(16) NOT NULL COMMENT 'system/user/assistant',
  `content`     MEDIUMTEXT  NOT NULL COMMENT '消息内容',
  `tokens`      INT         DEFAULT NULL COMMENT '该消息估算 token 数',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '面试消息表';

-- AI 面试题目轮次。一行对应一道题，用于单题评分、统计和过滤未回答题目。
CREATE TABLE `interview_turn` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `session_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `round_no` INT NOT NULL,
  `question_text` VARCHAR(512) NOT NULL,
  `answer_text` TEXT DEFAULT NULL,
  `score` INT DEFAULT NULL COMMENT '0-100，评分失败或未回答时为空',
  `result` TINYINT DEFAULT NULL COMMENT '0-答错 1-答对，60分及以上为答对',
  `evaluation` VARCHAR(1000) DEFAULT NULL,
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-待回答 2-已评分 3-评分失败 4-用户跳过',
  `answered_time` DATETIME DEFAULT NULL COMMENT '用户回答完成并写入评分结果的时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_round` (`session_id`, `round_no`),
  KEY `idx_user_status_time` (`user_id`, `status`, `answered_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'AI 面试单题轮次';

-- AI 面试自动沉淀的候选题
CREATE TABLE `ai_question_candidate` (
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

-- 面试评价报告表(一场会话一份,uk_session_id 兼作幂等兜底)
CREATE TABLE `interview_report` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `session_id`  BIGINT   NOT NULL COMMENT '会话 id',
  `score`       INT      DEFAULT NULL COMMENT 'score 0-100, nullable while generating',
  `content`     JSON     DEFAULT NULL COMMENT 'report json, nullable while generating',
  `status`      TINYINT  NOT NULL DEFAULT 0 COMMENT '0-generating 1-success 2-failed',
  `error_message` VARCHAR(255) DEFAULT NULL COMMENT 'generation error message',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_id` (`session_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '面试评价报告表';
