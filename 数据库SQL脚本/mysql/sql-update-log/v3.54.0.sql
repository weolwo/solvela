-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- 文件模块：清退双写字段（2026-08-10）
--
-- 配套：docs/文件模块-架构设计文档.md，前置 v3.53.0
--
-- ⚠️⚠️ 这个脚本修的是一个当前就存在的故障，不是单纯的清理 ⚠️⚠️
--
-- v3.53.0 之后、旧的 FileService 被删除之后，新的 FileAssetService.upload 不再写
-- folder_type 与 file_type 这两列。而它们都是 NOT NULL 且没有默认值：
--     `folder_type` tinyint unsigned NOT NULL
--     `file_type`   varchar(50)      NOT NULL
-- MyBatis-Plus 默认跳过 null 字段不拼进 INSERT，于是 MySQL 8 严格模式下
-- 每一次上传都会失败：Field 'folder_type' doesn't have a default value。
--
-- 单测没抓到是因为 DAO 全是 mock 的 —— 这类"列约束与代码写入面不一致"的问题，
-- 只有集成测试或真实跑一次才发现得了。
--
-- 执行顺序：本脚本要在部署新代码之前或同时执行。新代码不写这两列，老代码已经删了，
-- 所以先跑 SQL 是安全的。
--
-- 被清退的五列及其接班人：
--   folder_type       -> category_id（v3.53.0 已平移，值相同）
--   file_type         -> extension（从嗅探 MIME 反推，比"用户文件名的后缀"可靠）
--   creator_id        -> 无。审计只保留人名，见设计文档 §4.6 的取舍说明
--   creator_name      -> create_by（v3.53.0 已平移，语义完全一致）
--   creator_user_type -> 无

-- 先确认平移都做过了。以下三句都应返回 0 行，有非 0 就说明 v3.53.0 没跑完，此时不要继续。
-- SELECT COUNT(*) FROM t_file WHERE category_id IS NULL;
-- SELECT COUNT(*) FROM t_file WHERE create_by IS NULL AND creator_name IS NOT NULL;
-- SELECT COUNT(*) FROM t_file WHERE extension = '' AND file_type <> '';

ALTER TABLE `t_file`
    DROP COLUMN `folder_type`,
    DROP COLUMN `file_type`,
    DROP COLUMN `creator_id`,
    DROP COLUMN `creator_name`,
    DROP COLUMN `creator_user_type`;

-- category_id 平移完成后收紧约束：它现在是分类的唯一来源，不该允许 NULL
ALTER TABLE `t_file`
    MODIFY COLUMN `category_id` bigint NOT NULL COMMENT '分类';

-- 自查：以下应返回 0 行（仍有 NOT NULL 但无默认值、且代码不写入的列）
-- SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_DEFAULT, EXTRA
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_file'
--    AND IS_NULLABLE = 'NO' AND COLUMN_DEFAULT IS NULL AND EXTRA NOT LIKE '%auto_increment%';
-- 预期只剩 storage_key 与 category_id —— 这两列代码每次插入都会写。
