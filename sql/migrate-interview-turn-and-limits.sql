-- 已有数据库升级：面试题数上限、单题评分和学习统计。
USE zhimian;

ALTER TABLE interview_session
  ADD COLUMN target_question_count INT NOT NULL DEFAULT 8 COMMENT '本场面试目标题数' AFTER status,
  ADD COLUMN answered_question_count INT NOT NULL DEFAULT 0 COMMENT '已经提交回答的题数' AFTER target_question_count,
  ADD COLUMN finish_reason VARCHAR(32) DEFAULT NULL COMMENT 'AUTO_LIMIT/USER_STOP/SYSTEM_ERROR/INACTIVITY_TIMEOUT' AFTER answered_question_count;

-- 老会话没有 turn 数据，先按已经成功落库的 user 消息回填进度。
UPDATE interview_session s
SET answered_question_count = (
  SELECT COUNT(*)
  FROM interview_message m
  WHERE m.session_id = s.id
    AND m.role = 'user'
);

CREATE TABLE interview_turn (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  round_no INT NOT NULL,
  question_text VARCHAR(512) NOT NULL,
  answer_text TEXT DEFAULT NULL,
  score INT DEFAULT NULL COMMENT '0-100，评分失败或未回答时为空',
  result TINYINT DEFAULT NULL COMMENT '0-答错 1-答对，60分及以上为答对',
  evaluation VARCHAR(1000) DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0-待回答 2-已评分 3-评分失败 4-用户跳过',
  answered_time DATETIME DEFAULT NULL COMMENT '用户回答完成并写入评分结果的时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_round (session_id, round_no),
  KEY idx_user_status_time (user_id, status, answered_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'AI 面试单题轮次';
