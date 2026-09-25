-- =====================================================================================
-- 孤儿文件清理：两个定时任务的登记
--
-- 🔴 这两行的【顺序】就是上线顺序，不是随便排的：
--
--   1. fileRelationBackfill  存量回填   —— 手动跑一次
--   2. fileOrphanClean       孤儿清理   —— 确认第 1 步的结果之后，再打开开关
--
-- 反过来的后果是【不可恢复的数据丢失】：孤儿清理的判据是 t_file_relation 里有没有行，
-- 而 2026-09-25 之前有三处业务上传了图却从来没登记过引用 ——
-- 商城分类图标、任务规则说明富文本内嵌图、奖品扩展图。
-- 没回填就开清理，这些【正在用】的图会被当成垃圾删掉，连对象存储一起删。
-- 表现是 C 端图标集体变叉，而且 <img> 加载失败是静默的，没人会来投诉。
--
-- 所以 fileOrphanClean 这一行 enabled_flag 刻意是 0。别在这个文件里把它改成 1，
-- 要开就去后台「定时任务」页面开 —— 那时候你才会看见回填的结果。
--
-- @author alaric
-- @date 2026-09-25
-- =====================================================================================

-- ─────────────────────────────────────────────────────────────────────────────────────
-- 1. 存量回填。trigger_type = cron 但配在一个很远的时间点：
--    它本质是「手动跑一次」的任务，调度框架没有纯手动类型，用一个几乎不会自动触发的
--    表达式 + enabled_flag = 0，实际执行走后台页面的「执行一次」。
-- ─────────────────────────────────────────────────────────────────────────────────────
INSERT INTO `t_solvela_job`
(`job_id`, `job_code`, `job_name`, `handler_name`, `job_group`, `trigger_type`, `trigger_value`,
 `jitter_seconds`, `enabled_flag`, `preset_code`, `timeout_seconds`, `retry_times`, `retry_interval`,
 `misfire_strategy`, `misfire_threshold_sec`, `block_strategy`, `sort`, `remark`,
 `deleted_flag`, `update_name`, `app_env`, `owner_biz_type`, `source`, `manual_modified_flag`)
VALUES
(26, 'JOBFILEBKF', '【基础】文件引用存量回填', 'fileRelationBackfill', 'SYSTEM', 'cron', '0 0 4 1 1 ?',
 0, 0, 'NORMAL', 600, 0, 30,
 'SKIP', 300, 'DISCARD', 0,
 '手动跑一次即可：把商城分类图标 / 任务规则说明内嵌图 / 奖品扩展图的存量引用补进 t_file_relation。开 fileOrphanClean 之前【必须】先跑这个，否则那些图会被当垃圾删掉。幂等，重复跑无害。',
 0, 'system', 'dev', 'SYSTEM', 'MANUAL', 0);

-- ─────────────────────────────────────────────────────────────────────────────────────
-- 2. 孤儿清理。⚠️ enabled_flag = 0，见文件头。
--    03:40 这个点是接着其它清理类任务排的（03:00 日志、03:10 券、03:20 任务记录）。
-- ─────────────────────────────────────────────────────────────────────────────────────
INSERT INTO `t_solvela_job`
(`job_id`, `job_code`, `job_name`, `handler_name`, `job_group`, `trigger_type`, `trigger_value`,
 `jitter_seconds`, `enabled_flag`, `preset_code`, `timeout_seconds`, `retry_times`, `retry_interval`,
 `misfire_strategy`, `misfire_threshold_sec`, `block_strategy`, `sort`, `remark`,
 `deleted_flag`, `update_name`, `app_env`, `owner_biz_type`, `source`, `manual_modified_flag`)
VALUES
(27, 'JOBFILEORP', '【基础】孤儿文件清理', 'fileOrphanClean', 'SYSTEM', 'cron', '0 40 3 * * *',
 60, 0, 'NORMAL', 600, 0, 30,
 'SKIP', 300, 'DISCARD', 0,
 '每天 03:40 删掉「上传了但从来没有业务引用」的临时文件（默认保护期 7 天）。⚠️ 开之前必须先跑 fileRelationBackfill，并用 dryRun 看一眼数量。支持 retainDays / dryRun 两个参数。',
 0, 'system', 'dev', 'SYSTEM', 'MANUAL', 0);

-- =====================================================================================
-- 验证：两行都在，且孤儿清理是停用的
-- =====================================================================================
-- SELECT job_id, handler_name, enabled_flag, trigger_value FROM t_solvela_job
--  WHERE handler_name IN ('fileRelationBackfill', 'fileOrphanClean');
--
-- 回填前后各数一次孤儿（回填任务自己也会打印这个数）：
-- SELECT COUNT(*) FROM t_file f
--  WHERE f.deleted_flag = 0 AND f.status = 1
--    AND NOT EXISTS (SELECT 1 FROM t_file_relation r WHERE r.file_id = f.file_id);
