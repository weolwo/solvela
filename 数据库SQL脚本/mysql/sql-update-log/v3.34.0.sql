-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- 奖池坑位映射表新增兜底标记：回显时恢复「兜底自动配平」编辑态；抽奖引擎在命中奖项库存不足时降级到兜底奖项
ALTER TABLE `t_pool_prize_mapping`
    ADD COLUMN `is_fallback` tinyint NOT NULL DEFAULT 0 COMMENT '是否兜底奖项：1-兜底，每池最多一个' AFTER `probability`;
