-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- =====================================================================================
-- v3.69.0  三个业务定时任务落库 + 账务写接口下线的说明
-- 撰写：2026-08-18
--
-- 背景：调度框架（SmartJob）早就建好了，但 common-api / sa-marketing 里
--       一个 @SmartJobHandler 都没有 —— 三处「永远不会自己收口」的状态一直靠人肉发现：
--         · 优惠券过了有效期还挂在「未使用」（全工程没有任何地方写 status=2）；
--         · 任务记录过了有效期还停在「进行中」
--           （idx_t_tsk_rec_expire 这个索引就是给那个扫描建的，扫描一直没写）；
--         · 提案卡在 30-待执行 / 40-执行中，钱既没发出去也没标成失败。
--
--       前两个由本次新增的收口任务解决；第三个<b>刻意只报不修</b>，原因见下。
--
-- ⚠️ 这三行只是「把任务挂上去」。执行器本身在代码里：
--      couponExpire       -> MemberCouponExpireJob
--      taskRecordExpire   -> TaskRecordExpireJob
--      proposalStuckScan  -> ProposalStuckScanJob
--    代码不发版，光执行这段 SQL 只会得到三条「handler 缺失」的任务（后台会标红）。
--
-- 可重复执行：靠 uk_job_code 幂等，已存在则跳过。
-- =====================================================================================


-- -------------------------------------------------------------------------------------
-- 一、优惠券过期收口
--
-- 首次运行会把「已过有效期且仍未使用」的券一次性置为 2-已过期。
-- 🔴 这是不可逆的。上线后建议先在后台点「执行一次」并把参数填成 {"dryRun": true}，
--    看清楚要改多少张再放开定时 —— 执行器支持试运行就是为了这一步。
-- -------------------------------------------------------------------------------------
INSERT IGNORE INTO `t_smart_job`
(`job_code`, `job_name`, `handler_name`, `job_group`, `trigger_type`, `trigger_value`,
 `next_trigger_time`, `enabled_flag`, `app_env`, `source`, `update_name`, `remark`)
VALUES ('JOBCOUPEXP', '【账务】优惠券过期收口', 'couponExpire', 'BUSINESS', 'cron', '0 10 3 * * *',
        now(), 1, 'dev', 'MANUAL', 'system',
        '每天 03:10 把过了有效期仍未使用的券置为已过期；支持 dryRun 试运行');


-- -------------------------------------------------------------------------------------
-- 二、任务记录过期收口
--
-- 同样是不可逆的一次性收口，建议先 dryRun。
-- 条件里只认 status=0，不会误伤已完成(1)/已发奖(2)的记录。
-- -------------------------------------------------------------------------------------
INSERT IGNORE INTO `t_smart_job`
(`job_code`, `job_name`, `handler_name`, `job_group`, `trigger_type`, `trigger_value`,
 `next_trigger_time`, `enabled_flag`, `app_env`, `source`, `update_name`, `remark`)
VALUES ('JOBTASKEXP', '【任务】任务记录过期收口', 'taskRecordExpire', 'BUSINESS', 'cron', '0 20 3 * * *',
        now(), 1, 'dev', 'MANUAL', 'system',
        '每天 03:20 把过了有效期仍在进行中的任务记录置为已过期；支持 dryRun 试运行');


-- -------------------------------------------------------------------------------------
-- 三、提案卡单扫描（只报不修）
--
-- 🔴 它<b>不会自动重发</b>，这是刻意的：从提案表分不出「根本没开始下发」和「发了一半」
--    （60-部分成功 这个状态的存在，说明部分到账真实发生过）。分不清就重发，
--    等于按概率给用户发两次钱，而多发出去的钱收不回来。
--    要能自动重发，前置条件是资产下发侧有一个真正的幂等键
--    （像 t_prize_log.uk_external_biz 那样，重放第二次会被唯一索引挡掉）。
--
-- 每 10 分钟扫一次，卡单数会进执行日志的 result_summary，具体单号打在 WARN 日志里。
-- -------------------------------------------------------------------------------------
INSERT IGNORE INTO `t_smart_job`
(`job_code`, `job_name`, `handler_name`, `job_group`, `trigger_type`, `trigger_value`,
 `next_trigger_time`, `enabled_flag`, `app_env`, `source`, `update_name`, `remark`, `param`)
VALUES ('JOBPROPSTK', '【风控】提案卡单扫描', 'proposalStuckScan', 'OPS', 'cron', '0 */10 * * * *',
        now(), 1, 'dev', 'MANUAL', 'system',
        '每 10 分钟扫一次卡在下发的提案，只报不修（自动重发需要下发侧先有幂等键）',
        '{"stuckMinutes": 30, "warnThreshold": 0}');


-- =====================================================================================
-- ⚠️ app_env 必须与运行环境一致
--
-- 调度扫描是按 app_env 过滤的（idx_next_trigger 的最左列就是它），
-- 上面三行写死了 'dev'。在 test / pre / prod 库执行时，先改成对应的值再跑，
-- 否则任务安安静静地永远不会被扫到 —— 不报错、后台也看得见这三条配置。
-- =====================================================================================
-- UPDATE `t_smart_job` SET `app_env` = 'prod'
--  WHERE `job_code` IN ('JOBCOUPEXP', 'JOBTASKEXP', 'JOBPROPSTK');


-- =====================================================================================
-- 附：本版还移除了账务/审计域的写接口（无 DDL 变更，仅代码）
--
-- t_member_wallet / t_member_asset_transaction / t_member_coupon / t_proposal_record
-- 的 add / update / batchDelete / delete 四个接口整组下线，t_prize_log 保留 add（人工补发）。
-- 原先的 delete 是<b>物理删除</b>，而 /memberWallet/update 能直接改余额且不写流水。
-- 这几个出口前端本来就没在用，但拿到 token 就能打。
--
-- 如果你的角色权限里配过下面这些权限点，现在可以一并清掉（它们已经没有对应接口）：
--   memberWallet:add / memberWallet:update / memberWallet:delete
--   memberCoupon:add / memberCoupon:update / memberCoupon:delete
--   memberAssetTransaction:add / memberAssetTransaction:update / memberAssetTransaction:delete
--   proposalRecord:add / proposalRecord:update / proposalRecord:delete
--   prizeLog:update / prizeLog:delete
-- 留着也不会有副作用（权限点匹配不到接口就是一句空配置），只是会让权限树越来越像考古现场。
-- =====================================================================================

-- 自查 1：三条任务是否挂上了（handler_missing_flag 为 1 说明代码没发版）
SELECT job_code, job_name, handler_name, trigger_value, enabled_flag, app_env, handler_missing_flag
FROM t_smart_job WHERE job_code IN ('JOBCOUPEXP', 'JOBTASKEXP', 'JOBPROPSTK');

-- 自查 2：首次收口的影响面（先看清楚再放开定时）
SELECT COUNT(*) AS 待收口券张数 FROM t_member_coupon WHERE status = 0 AND valid_end_time < now();
SELECT COUNT(*) AS 待收口任务记录数 FROM t_task_record WHERE status = 0 AND valid_end_time < now();
SELECT COUNT(*) AS 当前卡单提案数 FROM t_proposal_record
WHERE status IN (30, 40) AND update_time < DATE_SUB(now(), INTERVAL 30 MINUTE);
