-- 面试计划与综合场景模式升级
-- 适用于已经执行过 init.sql 的旧数据库。每条语句执行前请确认目标库。

SET @db_name = DATABASE();

SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @db_name AND table_name = 'interview_session' AND column_name = 'mode'
    ),
    'SELECT 1',
    'ALTER TABLE interview_session ADD COLUMN mode VARCHAR(16) NOT NULL DEFAULT ''direction'' COMMENT ''direction-专项面试 scenario-综合场景面试'' AFTER direction'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @db_name AND table_name = 'interview_session' AND column_name = 'scenario_code'
    ),
    'SELECT 1',
    'ALTER TABLE interview_session ADD COLUMN scenario_code VARCHAR(64) DEFAULT NULL COMMENT ''综合面试场景编码'' AFTER mode'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @db_name AND table_name = 'interview_session' AND column_name = 'plan_json'
    ),
    'SELECT 1',
    'ALTER TABLE interview_session ADD COLUMN plan_json JSON DEFAULT NULL COMMENT ''本场面试生成的题目计划快照'' AFTER scenario_code'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
