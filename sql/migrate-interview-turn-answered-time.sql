-- 已执行过 migrate-interview-turn-and-limits.sql 的数据库使用本脚本升级。
USE zhimian;

SET @answered_time_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'interview_turn'
      AND COLUMN_NAME = 'answered_time'
);
SET @add_answered_time_sql = IF(
    @answered_time_exists = 0,
    'ALTER TABLE interview_turn ADD COLUMN answered_time DATETIME DEFAULT NULL COMMENT ''用户回答完成并写入评分结果的时间'' AFTER status',
    'SELECT 1'
);
PREPARE add_answered_time_stmt FROM @add_answered_time_sql;
EXECUTE add_answered_time_stmt;
DEALLOCATE PREPARE add_answered_time_stmt;

-- 历史已完成回合没有精确回答时间，用最后更新时间作为最接近的回填值。
UPDATE interview_turn
SET answered_time = update_time
WHERE status IN (2, 3)
  AND answered_time IS NULL;

SET @old_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'interview_turn'
      AND INDEX_NAME = 'idx_user_status_time'
);
SET @drop_old_index_sql = IF(
    @old_index_exists > 0,
    'ALTER TABLE interview_turn DROP INDEX idx_user_status_time',
    'SELECT 1'
);
PREPARE drop_old_index_stmt FROM @drop_old_index_sql;
EXECUTE drop_old_index_stmt;
DEALLOCATE PREPARE drop_old_index_stmt;

CREATE INDEX idx_user_status_time
    ON interview_turn (user_id, status, answered_time);
