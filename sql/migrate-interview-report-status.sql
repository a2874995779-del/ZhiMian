-- 6.3.1 Interview report async-generation migration.
-- Run once for an existing local database. New databases only need init.sql.

ALTER TABLE interview_report
  MODIFY COLUMN score INT DEFAULT NULL COMMENT 'score 0-100, nullable while generating',
  MODIFY COLUMN content JSON DEFAULT NULL COMMENT 'report json, nullable while generating',
  ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '0-generating 1-success 2-failed' AFTER content,
  ADD COLUMN error_message VARCHAR(255) DEFAULT NULL COMMENT 'generation error message' AFTER status,
  ADD COLUMN update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER create_time;
