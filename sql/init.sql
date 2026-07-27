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

-- ============ 阶段二:AI 模拟面试(任务 2.2~2.6)============

-- AI 面试会话表
CREATE TABLE `interview_session` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL COMMENT '所属用户',
  `direction`   VARCHAR(32)  NOT NULL COMMENT '面试方向:java_concurrency/jvm/mysql/redis/system_design',
  `title`       VARCHAR(128) DEFAULT NULL COMMENT '会话标题(默认取方向+日期)',
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0-进行中 1-已结束 2-报告已生成',
  `end_time`    DATETIME     DEFAULT NULL COMMENT '结束时间',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`, `create_time`)
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

-- 面试评价报告表(一场会话一份,uk_session_id 兼作幂等兜底)
CREATE TABLE `interview_report` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `session_id`  BIGINT   NOT NULL COMMENT '会话 id',
  `score`       INT      NOT NULL COMMENT '总分 0-100',
  `content`     JSON     NOT NULL COMMENT '结构化报告:亮点/薄弱点/总评',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_id` (`session_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '面试评价报告表';
