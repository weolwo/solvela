-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- ============================================================================
-- 优惠券 · 卡单释放定时任务  2026-09-15
-- ============================================================================
--
-- 【为什么必须有这个脚本】
--   @SolvelaJobHandler 只是把执行器注册进内存注册表，让它在后台的下拉里选得到。
--   **调度计划是数据**：t_solvela_job 里没有行，这个任务就永远不会被触发，
--   而且不报错 —— 代码在、类在、后台看得到执行器，只是什么都不会发生。
--
-- 【它做什么】
--   把卡在「4-锁定中」超过 N 分钟的券放回「0-未使用」。
--
--   进程在锁定和确认之间挂掉，券会永远停在锁定中 —— 用户手里那张券既用不了
--   也不过期，券包里看着还在，点进去用不了。
--
-- 🔴 【阈值不能随便调小】
--   商城订单自己的支付超时是 30 分钟，到点由 mallOrderExpire 取消并走正常的
--   释放路径。本任务默认 120 分钟，是那个窗口的 4 倍 —— 走到这里的券，
--   对应订单几乎不可能还活着。
--
--   配得比 30 分钟还短的话，这个任务会去抢【活着的订单】手里的券，
--   那不是兜底，那是制造故障。代码里有个 60 分钟的硬下限兜着，
--   但请不要依赖它：那是安全网，不是设计意图。
--
-- 【频率】
--   每 30 分钟一次。比阈值密得多是对的 —— 卡住的券早一点放回去，
--   用户就早一点能用。
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/优惠券-卡单释放定时任务.sql;
--   可重复执行：按 handler_name 判存，跑第二遍影响 0 行。
--
-- 🔴 执行完必须重新导出种子数据基线：
--       cd 数据库SQL脚本/tools && java -cp <mysql-connector.jar> DumpSeedData.java
-- ============================================================================

INSERT INTO `t_solvela_job`
    (`job_code`, `job_name`, `handler_name`, `job_group`, `trigger_type`, `trigger_value`,
     `next_trigger_time`, `trigger_version`, `jitter_seconds`, `enabled_flag`, `param`, `preset_code`,
     `timeout_seconds`, `retry_times`, `retry_interval`, `misfire_strategy`, `misfire_threshold_sec`,
     `block_strategy`, `sort`, `remark`, `deleted_flag`, `update_name`, `app_env`, `source`)
SELECT 'JOBCOUPLCK', '【账务】优惠券卡单释放', 'couponStuckLockRelease', 'BUSINESS', 'cron', '0 5/30 * * * *',
       -- 下次触发时间给一个「马上就到」的值，让它装完就能被扫描线程捞走；
       -- 真正的节奏由 cron 接管
       DATE_ADD(NOW(), INTERVAL 1 MINUTE), 0, 0, 1, NULL, 'NORMAL',
       300, 0, 30, 'SKIP', 300,
       'DISCARD', 0,
       '每 30 分钟把锁定超过 120 分钟仍未确认的券放回未使用；支持 dryRun 试运行。阈值不要调到 60 分钟以下',
       0, 'system', 'dev', 'MANUAL'
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_solvela_job`) j
   WHERE j.`handler_name` = 'couponStuckLockRelease' AND j.`deleted_flag` = 0
);


-- ---------------------------------------------------------------------------
-- 核对
-- ---------------------------------------------------------------------------
SELECT `job_code`, `job_name`, `handler_name`, `trigger_value`, `enabled_flag`, `next_trigger_time`
  FROM `t_solvela_job`
 WHERE `handler_name` = 'couponStuckLockRelease';
