-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- 任务模板表新增默认触发事件列：模板级建议值，任务配置向导中可覆盖
ALTER TABLE `t_task_template`
    ADD COLUMN `trigger_event` varchar(32) NULL DEFAULT NULL COMMENT '默认触发事件：模板建议值，向导中可覆盖' AFTER `task_type`;
