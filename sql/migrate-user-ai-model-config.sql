USE zhimian;

ALTER TABLE `user`
  ADD COLUMN `ai_model` VARCHAR(128) DEFAULT NULL COMMENT '用户自定义聊天模型名' AFTER `avatar`,
  ADD COLUMN `ai_api_key_encrypted` TEXT DEFAULT NULL COMMENT '用户聊天模型 API Key 密文' AFTER `ai_model`;
