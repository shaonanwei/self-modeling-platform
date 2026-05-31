-- 升级脚本 003: 为 model_info 表添加 data_source 字段
-- 执行时间: 2026-05-25
-- 描述: 添加数据源字段到模型表，支持模型级别的数据源配置

-- 添加 data_source 字段到 model_info 表
ALTER TABLE model_info ADD COLUMN data_source TEXT NOT NULL DEFAULT 'sqlite';

-- 更新现有记录的数据源为默认值 'sqlite'
UPDATE model_info SET data_source = 'sqlite' WHERE data_source IS NULL;

-- 添加注释（如果 SQLite 支持的话）
-- COMMENT ON COLUMN model_info.data_source IS '数据源标识：sqlite/postgres/hive';
