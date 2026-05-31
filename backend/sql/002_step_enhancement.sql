-- 步骤配置增强 - 数据库变更（SQLite 版本）
-- 执行时间：2026-05-18

-- 1. 添加配置类型字段
ALTER TABLE model_step ADD COLUMN config_type TEXT DEFAULT 'SQL';

-- 2. 添加 SQL 语句字段（如果配置类型是 SQL，存储 SQL 语句）
ALTER TABLE model_step ADD COLUMN sql_statement TEXT;

-- 3. step_config 字段改为存储 JSON 配置（包含 configType + SQL 配置或图形化配置）
-- 注意：step_config 字段已存在，无需修改，只需更新应用层逻辑

-- 4. 添加索引（可选，优化查询）
CREATE INDEX IF NOT EXISTS idx_model_step_config_type ON model_step(config_type);
