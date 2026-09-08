-- 面试闲置过期功能索引升级
-- 功能本身不增加新字段；finish_reason 会写入 INACTIVITY_TIMEOUT。

SET @db_name = DATABASE();

SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @db_name
          AND table_name = 'interview_session'
          AND index_name = 'idx_user_status_update'
    ),
    'SELECT 1',
    'ALTER TABLE interview_session ADD INDEX idx_user_status_update (user_id, status, update_time)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @db_name
          AND table_name = 'interview_session'
          AND index_name = 'idx_status_update'
    ),
    'SELECT 1',
    'ALTER TABLE interview_session ADD INDEX idx_status_update (status, update_time)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
