USE zhimian;

CREATE TABLE IF NOT EXISTS `knowledge_document` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(200) NOT NULL COMMENT '知识文档标题',
  `original_filename` VARCHAR(255) NOT NULL COMMENT '上传时的原始文件名',
  `source_type` VARCHAR(16) NOT NULL COMMENT '来源类型：MARKDOWN/TEXT',
  `content` MEDIUMTEXT NOT NULL COMMENT '规范化后的完整原文',
  `content_hash` CHAR(64) NOT NULL COMMENT '规范化原文的SHA-256',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-待处理 1-处理中 2-已完成 3-失败 4-删除中',
  `chunk_count` INT NOT NULL DEFAULT 0 COMMENT '切片数量',
  `embedding_model` VARCHAR(128) DEFAULT NULL COMMENT '实际使用的Embedding模型',
  `vector_dimension` INT DEFAULT NULL COMMENT '实际向量维度',
  `error_message` VARCHAR(500) DEFAULT NULL COMMENT '最近一次失败原因',
  `created_by` BIGINT NOT NULL COMMENT '创建人用户id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '0-正常 1-已删除',
  `active_content_hash` CHAR(64)
    GENERATED ALWAYS AS (
      CASE WHEN `is_deleted` = 0 THEN `content_hash` ELSE NULL END
    ) STORED COMMENT '只约束未删除文档的内容Hash',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_active_content_hash` (`active_content_hash`),
  KEY `idx_status_time` (`status`, `create_time`),
  KEY `idx_created_by_time` (`created_by`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'RAG知识文档';

CREATE TABLE IF NOT EXISTS `knowledge_chunk` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `document_id` BIGINT NOT NULL COMMENT '所属知识文档id',
  `chunk_index` INT NOT NULL COMMENT '片段在文档中的顺序，从0开始',
  `heading_path` VARCHAR(500) DEFAULT NULL COMMENT 'Markdown标题路径',
  `content` TEXT NOT NULL COMMENT '片段原文',
  `content_hash` CHAR(64) NOT NULL COMMENT '片段原文SHA-256',
  `token_count` INT NOT NULL DEFAULT 0 COMMENT '片段Token数量',
  `vector_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-待向量化 1-已索引 2-失败',
  `error_message` VARCHAR(500) DEFAULT NULL COMMENT '向量化失败原因',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '0-正常 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_chunk_index` (`document_id`, `chunk_index`),
  KEY `idx_document_status` (`document_id`, `vector_status`),
  KEY `idx_content_hash` (`content_hash`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'RAG知识片段';
