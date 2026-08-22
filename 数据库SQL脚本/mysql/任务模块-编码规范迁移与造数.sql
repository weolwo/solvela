-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- ============================================================
-- 任务模块：存量编码迁移 + 联调造数
--
-- 背景：铁律 8 把 template_code 也纳入了「10 位大写字母+数字，全局唯一」的统一约定，
-- 而开发库里的存量模板 `DAILY_SIGN_TPL` 不符合该格式 —— 模板设计器保存时会被 @Pattern 拦下，
-- 且它被 t_task_config 引用，改编码必须级联。
--
-- 执行前请先自查存量违规编码（新库/干净库可能什么都查不到，属正常）：
--   SELECT activity_code FROM t_activity_config WHERE activity_code NOT REGEXP '^[A-Z0-9]{10}$';
--   SELECT prize_code    FROM t_prize_config    WHERE prize_code    NOT REGEXP '^[A-Z0-9]{10}$';
--   SELECT pool_code     FROM t_prize_pool_config WHERE pool_code   NOT REGEXP '^[A-Z0-9]{10}$';
--   SELECT template_code FROM t_task_template   WHERE template_code NOT REGEXP '^[A-Z0-9]{10}$';
-- ============================================================

-- ---------- 1. 任务类活动 ----------
-- 任务向导的活动下拉只拉 activity_type='TASK'，库里一个都没有的话下拉是空的。
INSERT INTO `t_activity_config` (`tenant_id`, `activity_code`, `activity_name`, `activity_type`, `status`, `start_time`,
                                 `end_time`, `create_by`)
VALUES ('taozi', 'WLO9SMXDKD', '新人成长营', 'TASK', 0, '2026-06-01 00:00:00', '2026-12-31 23:59:59', 'seed')
ON DUPLICATE KEY UPDATE `activity_name` = VALUES(`activity_name`),
                        `activity_type` = VALUES(`activity_type`);

-- ---------- 2. 存量模板编码迁移：DAILY_SIGN_TPL -> FRWAYF2X6N ----------
-- 必须连同引用方一起改，否则 t_task_config 会指向一个不存在的模板。
-- 两条 UPDATE 放在同一事务里，避免中途失败留下断链。
START TRANSACTION;

UPDATE `t_task_template`
SET `template_code` = 'FRWAYF2X6N'
WHERE `template_code` = 'DAILY_SIGN_TPL';

UPDATE `t_task_config`
SET `template_code` = 'FRWAYF2X6N'
WHERE `template_code` = 'DAILY_SIGN_TPL';

COMMIT;

-- ---------- 3. 给存量模板补 ui_schema 的卡片展示字段 ----------
-- 向导的模板卡片从 ui_schema.icon / ui_schema.desc 取图标与描述（这两个字段不入列，只存在 JSON 里）。
-- 不补也能用，只是卡片会显示兜底图标 🧩 且没有描述。
UPDATE `t_task_template`
SET `ui_schema` = JSON_SET(`ui_schema`,
                           '$.icon', '📅',
                           '$.desc', '连续/累计签到达标，支持补签联动配置')
WHERE `template_code` = 'FRWAYF2X6N';

-- ---------- 4. 存量脏数据清理（按需，默认不执行） ----------
-- 开发库里有 3 条 task_name='ggg' 的测试任务，其 activity_code='NEWBIE_CAMP' 来自前端 mock 常量，
-- 而 t_activity_config 里根本没有这个活动 —— 属于 mock 时代的残留，列表页会显示成孤儿数据。
-- 确认无用后放开执行；若想留着，把它们挂到上面新建的任务活动下即可（二选一）。
--
-- DELETE FROM `t_task_config` WHERE `activity_code` = 'NEWBIE_CAMP';
-- UPDATE `t_task_config` SET `activity_code` = 'WLO9SMXDKD' WHERE `activity_code` = 'NEWBIE_CAMP';

-- ---------- 5. 迁移后自查：以下四条都应返回 0 行 ----------
-- SELECT activity_code FROM t_activity_config WHERE activity_code NOT REGEXP '^[A-Z0-9]{10}$';
-- SELECT prize_code    FROM t_prize_config    WHERE prize_code    NOT REGEXP '^[A-Z0-9]{10}$';
-- SELECT pool_code     FROM t_prize_pool_config WHERE pool_code   NOT REGEXP '^[A-Z0-9]{10}$';
-- SELECT template_code FROM t_task_template   WHERE template_code NOT REGEXP '^[A-Z0-9]{10}$';
-- 另外查一下引用是否还成立（应返回 0 行）：
-- SELECT c.id, c.template_code FROM t_task_config c
--   LEFT JOIN t_task_template t ON t.template_code = c.template_code WHERE t.id IS NULL;
