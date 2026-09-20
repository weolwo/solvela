-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- ============================================================================
-- 会员等级 · 成长值对账任务  2026-09-21
--
-- 方案见 docs/会员等级-实现技术方案.md §5.2
-- 前置：会员等级-Grade改名与5表模型.sql 必须已执行
--
-- 【它兑现的是方案里欠的那一笔】
--   §5.2 原话：「冗余就要对账：一个定时任务定期比对 current_period_value 与
--   SUM(log.delta WHERE period_tag=本期)，对不上要告警。冗余而不对账，
--   迟早出现明细和总数对不上，而那时已经没人知道哪个是对的。」
--   等级、保级、降级全挂在那个数上。
--
-- 🔴 默认 autoFix = false，只报不改。
--   「谁对」是确定的（流水 append-only 且带幂等唯一键，主表是累出来的派生值），
--   但自动修会让引起漂移的那个 bug <b>每半小时被悄悄抹平一次</b>，没人知道它存在。
--   与 prizeDispatchReconcile 对「已受理无终态」的处理是同一条。
--   真要修时手工跑一次 autoFix=true —— 那是显式的人为动作。
--
-- ⚠️ 默认只扫最近 24 小时有变动的人。没动过的人不可能【新产生】漂移，
--   但这也意味着<b>历史遗留的漂移它看不见</b>。首次上线、或者这个 job 停过一段时间之后，
--   要手工跑一次全量把陈账清一遍：
--     UPDATE t_solvela_job SET param = '{"lookbackHours": 0}' WHERE job_code = 'JOBGRWRECN';
--     -- 跑完记得改回 NULL，全量在会员量大了之后是纯浪费
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/会员等级-成长值对账job.sql;
--   按 job_code 判存，可重复执行。
--
-- 🔴 next_trigger_time 必须写成【应用看到的墙上时间】= MySQL NOW() + 8 小时。
--    理由与踩坑记录见 数据库SQL脚本/mysql/定时任务-补齐未挂载的六个job.sql §0。
-- ============================================================================

SET @app_now := DATE_ADD(NOW(), INTERVAL 8 HOUR);

INSERT INTO `t_solvela_job`
    (`job_code`, `job_name`, `handler_name`, `job_group`, `trigger_type`, `trigger_value`,
     `next_trigger_time`, `trigger_version`, `jitter_seconds`, `enabled_flag`, `param`,
     `preset_code`, `timeout_seconds`, `retry_times`, `retry_interval`,
     `misfire_strategy`, `misfire_threshold_sec`, `block_strategy`, `sort`, `remark`,
     `deleted_flag`, `update_name`, `create_time`, `update_time`, `app_env`,
     `continuous_fail_count`, `handler_missing_flag`, `terminal_flag`, `source`, `manual_modified_flag`)
SELECT * FROM (
    SELECT 'JOBGRWRECN' AS job_code, '【会员】成长值对账' AS job_name,
           'memberGrowthReconcile' AS handler_name, 'BUSINESS' AS job_group,
           -- 每半小时的第 50 分：避开整点，也避开 :25 的期末结算与 :15/:45 的打点对账
           'cron' AS trigger_type, '0 50 * * * *' AS trigger_value,
           DATE_ADD(@app_now, INTERVAL 1 MINUTE) AS next_trigger_time,
           0 AS trigger_version, 0 AS jitter_seconds, 1 AS enabled_flag,
           NULL AS param, 'NORMAL' AS preset_code,
           900 AS timeout_seconds, 0 AS retry_times, 30 AS retry_interval,
           'SKIP' AS misfire_strategy, 300 AS misfire_threshold_sec, 'DISCARD' AS block_strategy,
           6 AS sort,
           '只报不改。发现漂移会打 ERROR 并带样本；autoFix 是手工动作，不要常开' AS remark,
           0 AS deleted_flag, 'system' AS update_name, NOW() AS create_time, NOW() AS update_time,
           'dev' AS app_env, 0 AS continuous_fail_count, 0 AS handler_missing_flag,
           0 AS terminal_flag, 'MANUAL' AS source, 0 AS manual_modified_flag
) AS jobs
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT `job_code` FROM `t_solvela_job`) AS existed
     WHERE existed.`job_code` = jobs.job_code
);


-- ============================================================================
-- 自查
-- ============================================================================
--
--   -- 手工对一次账（和 job 里那句 SQL 同口径），应当返回 0 行：
--   SELECT g.member_id, g.current_period_value AS 主表,
--          COALESCE((SELECT SUM(l.delta) FROM t_member_growth_log l
--                     WHERE l.member_id = g.member_id
--                       AND l.period_tag = DATE_FORMAT(g.period_start,'%Y%m%d')), 0) AS 流水
--     FROM t_member_growth g
--    HAVING 主表 <> 流水;
--
--   -- 孤儿流水（有流水没主表行），应当是 0 ——
--   -- 不为 0 说明有人绕过 MemberGrowthService.accrue 直接写流水
--   SELECT COUNT(DISTINCT l.member_id) FROM t_member_growth_log l
--    WHERE NOT EXISTS (SELECT 1 FROM t_member_growth g WHERE g.member_id = l.member_id);
-- ============================================================================
