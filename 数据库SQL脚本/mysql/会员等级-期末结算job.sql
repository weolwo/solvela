-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- ============================================================================
-- 会员等级 · 阶段 4：挂载期末结算任务  2026-09-20
--
-- 方案见 docs/会员等级-实现技术方案.md §8 阶段 4
-- 前置：会员等级-Grade改名与5表模型.sql 必须已执行（要 t_member_period_summary）
--
-- 【它做什么】
--   · 周期到点、不在缓冲期 → 攒够了就结清；没攒够<b>先给缓冲期</b>，不直接降；
--   · 缓冲期满 → 补够了保住（写一条 KEEP 留痕），还是不够就按成长值降级。
--   两种情况都写一行 t_member_period_summary，然后清零、推进周期。
--
-- 🔴 降级是这套系统里最能生投诉的动作，而它对一个人<b>一年只发生一次</b> ——
--    线上跑错了，要等一年才有第二次机会验证修复。所以：
--      · 首次挂载带 dryRun=true，先看待结算人数对不对；
--      · 确认后再放开：UPDATE t_solvela_job SET param = NULL WHERE job_code = 'JOBGRDSETL';
--
-- 【为什么一小时一轮，不是一天一次】
--   周期是各人自己的 12 个月（不是自然年），所以每天都有人到期。
--   周期到点到真正结算之间的延迟用户是看得见的 —— 他的等级页上「本期截止」
--   已经过了却还没动。一天一次意味着最多晚 24 小时才有反应。
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/会员等级-期末结算job.sql;
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
    SELECT 'JOBGRDSETL' AS job_code, '【会员】等级期末结算与保级判定' AS job_name,
           'memberGradeSettle' AS handler_name, 'BUSINESS' AS job_group,
           -- 每小时的第 25 分：避开 3 点那批清理，也避开整点
           'cron' AS trigger_type, '0 25 * * * *' AS trigger_value,
           DATE_ADD(@app_now, INTERVAL 1 MINUTE) AS next_trigger_time,
           0 AS trigger_version, 0 AS jitter_seconds, 1 AS enabled_flag,
           '{"dryRun": true}' AS param, 'NORMAL' AS preset_code,
           900 AS timeout_seconds, 0 AS retry_times, 30 AS retry_interval,
           'SKIP' AS misfire_strategy, 300 AS misfire_threshold_sec, 'DISCARD' AS block_strategy,
           5 AS sort,
           '⚠️ 首次为 dryRun。确认待结算人数后把 param 置空放开 —— 它会真降级' AS remark,
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
--   SELECT job_id, job_code, trigger_value, enabled_flag, next_trigger_time, param
--     FROM t_solvela_job WHERE job_code = 'JOBGRDSETL';
--
--   -- 结算结果（每人每期一行）
--   SELECT member_id, period_no, final_growth_value, grade_before, settled_grade,
--          settle_result, next_grade, next_threshold, settled_at
--     FROM t_member_period_summary ORDER BY id DESC LIMIT 20;
--
--   -- 正在缓冲期里的人（运营召回的第一批目标）
--   SELECT member_id, current_grade, current_period_value, protect_grade, protect_until
--     FROM t_member_growth
--    WHERE protect_until IS NOT NULL AND protect_until > DATE_ADD(NOW(), INTERVAL 8 HOUR);
-- ============================================================================
