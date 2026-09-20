-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- ============================================================================
-- 定时任务 · 补齐从未挂载的 6 个 job  2026-09-20
--
-- 【问题】
--   这个仓库的 job <b>不自动注册</b>：代码里写了 @SolvelaJobHandler 只是让它
--   「可被挂载」，真要跑还得在 t_solvela_job 里有一行。于是比「上次有人手工建行」
--   更晚写的 handler，全都躺在代码里一次没跑过。
--
--   admin 每次启动其实都在喊：
--     WARN ==== SolvelaJob ==== 有 7 个 handler 尚未挂载任务（可在后台新增）：
--       [httpCaller, mqMessageLogClean, prizeDispatchReconcile, bizActionReconcile,
--        mallOrderExpire, couponExpiringNotify, memberNotificationClean]
--
-- 【为什么这 6 条值得单独立一个脚本】
--   其中两条是<b>对账兜底</b>，而好几处设计的论证前提就是「它们在跑」：
--     · bizActionReconcile —— 打点方案 §6.4 原话是「既然重推是安全的，就不需要
--       outbox，只要一个 job 反查补推」。这个 job 不跑 = <b>不引 MQ 的理由不成立</b>；
--     · prizeDispatchReconcile —— 发奖跨服务对账与重投。不跑 = 卡在中间态的奖没人捞。
--   mallOrderExpire 更直接：实测库里躺过「积分扣了、库存锁了、永远待支付」的单。
--
-- 🔴 httpCaller 刻意<b>不</b>注册。
--   它是通用工具 handler，url 是 required 参数且没有默认值 —— 挂一行空配置上去，
--   结果是每次触发都失败。对齐这个仓库已经付过学费的那条：
--   「留一个没人消费的队列比没有队列更危险 —— 要么静默堆积，要么有人以为它在工作」。
--   真需要定时调某个内网接口时，在后台按需新建，把 url 填上。
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/定时任务-补齐未挂载的六个job.sql;
--   按 job_code 判存（uk_job_code），可重复执行。
--
-- ⚠️ 执行完在 admin 的启动日志里复核：「尚未挂载任务」那行应当只剩 httpCaller。
-- 🔴 执行完重新导出种子基线（t_solvela_job 在 DumpSeedData 的清单里）：
--       cd 数据库SQL脚本/tools && java -cp <mysql-connector.jar> DumpSeedData.java
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 0. 时间基准
--
-- 🔴 next_trigger_time 必须写成【应用看到的墙上时间】，不是 MySQL 的 NOW()。
--    这个环境里 MySQL 服务端时钟是 UTC，而应用的 JDBC 会做时区转换 ——
--    从 SQL 会话看到的 NOW() 比应用的 dbNow() <b>慢 8 小时</b>。
--    直接写 NOW() 的后果：任务的原定触发时间落在 8 小时前，
--    被 misfire 判成「错过调度」按 SKIP 跳过，然后你会看到一次执行、结果是「已跳过」。
--    （这不是推测 —— 2026-09-18 联调时就这么踩过一次。）
--
-- ⚠️ 换环境前先核对：SELECT NOW(), DATE_ADD(NOW(), INTERVAL 8 HOUR);
--    如果目标库的服务端时区就是 +08，这里的 8 要改成 0。
-- ---------------------------------------------------------------------------
SET @app_now := DATE_ADD(NOW(), INTERVAL 8 HOUR);

-- 间隔型任务：给 1 分钟后的第一枪，之后由 cron 自己往前推
SET @soon := DATE_ADD(@app_now, INTERVAL 1 MINUTE);

-- 每日型任务：今天那个点还没到就用今天，过了就明天
SET @next_0330 := IF(@app_now < CONCAT(DATE(@app_now), ' 03:30:00'),
                     CONCAT(DATE(@app_now), ' 03:30:00'),
                     CONCAT(DATE(@app_now) + INTERVAL 1 DAY, ' 03:30:00'));
SET @next_0340 := IF(@app_now < CONCAT(DATE(@app_now), ' 03:40:00'),
                     CONCAT(DATE(@app_now), ' 03:40:00'),
                     CONCAT(DATE(@app_now) + INTERVAL 1 DAY, ' 03:40:00'));
SET @next_1000 := IF(@app_now < CONCAT(DATE(@app_now), ' 10:00:00'),
                     CONCAT(DATE(@app_now), ' 10:00:00'),
                     CONCAT(DATE(@app_now) + INTERVAL 1 DAY, ' 10:00:00'));


-- ---------------------------------------------------------------------------
-- 1. 六条任务
--
-- 【排期怎么定的】
--   · 三条兜底/释放型走间隔，且都<b>错开整点</b>（3/5、7/15、15/30）——
--     现有的 proposalStuckScan 是 */10、couponStuckLockRelease 是 5/30、
--     externalOrderExpire 是 2/10，全挤在 :00 会让慢车道同时排队；
--   · 两条清理型排进凌晨 3 点窗口，与已有的 couponExpire(3:10) /
--     taskRecordExpire(3:20) 各隔 10 分钟，沿用现成的间隔约定；
--   · couponExpiringNotify 是<b>要发给用户看的</b>，排在 10:00 —— 凌晨 3 点推券到期
--     提醒，收到的人只会觉得被打扰。
--
-- 【统一取值】
--   misfire_strategy = SKIP     错过就跳过，不补跑。补跑对「释放/清理」型没意义，
--                               对「通知」型是二次打扰。
--   block_strategy   = DISCARD  上一轮还在跑就丢掉这一轮。
--   retry_times      = 0        与现有 5 条一致；重试留给幂等且失败可恢复的场景。
-- ---------------------------------------------------------------------------
INSERT INTO `t_solvela_job`
    (`job_code`, `job_name`, `handler_name`, `job_group`, `trigger_type`, `trigger_value`,
     `next_trigger_time`, `trigger_version`, `jitter_seconds`, `enabled_flag`, `param`,
     `preset_code`, `timeout_seconds`, `retry_times`, `retry_interval`,
     `misfire_strategy`, `misfire_threshold_sec`, `block_strategy`, `sort`, `remark`,
     `deleted_flag`, `update_name`, `create_time`, `update_time`, `app_env`,
     `continuous_fail_count`, `handler_missing_flag`, `terminal_flag`, `source`, `manual_modified_flag`)
SELECT * FROM (

    -- 1.1 商城超时未支付订单释放
    --     实测过：不跑的话，POINTS_CASH 商品建出来的单会「积分扣了、库存锁了、
    --     限购占了」然后永远停在待支付。5 分钟一轮，释放要够快。
    SELECT 'JOBMALLEXP' AS job_code, '【商城】超时未支付订单释放' AS job_name,
           'mallOrderExpire' AS handler_name, 'BUSINESS' AS job_group,
           'cron' AS trigger_type, '0 3/5 * * * *' AS trigger_value,
           @soon AS next_trigger_time, 0 AS trigger_version, 0 AS jitter_seconds,
           1 AS enabled_flag, NULL AS param, 'NORMAL' AS preset_code,
           300 AS timeout_seconds, 0 AS retry_times, 30 AS retry_interval,
           'SKIP' AS misfire_strategy, 300 AS misfire_threshold_sec, 'DISCARD' AS block_strategy,
           10 AS sort, '到点取消并放回积分/库存/限购额度' AS remark,
           0 AS deleted_flag, 'system' AS update_name, NOW() AS create_time, NOW() AS update_time,
           'dev' AS app_env, 0 AS continuous_fail_count, 0 AS handler_missing_flag,
           0 AS terminal_flag, 'MANUAL' AS source, 0 AS manual_modified_flag

    -- 1.2 发奖跨服务对账与重投
    --     卡在「已受理但无终态」的发奖靠它捞回来。
    UNION ALL SELECT 'JOBPRZRECN', '【发奖】跨服务对账与重投', 'prizeDispatchReconcile', 'BUSINESS',
           'cron', '0 7/15 * * * *', @soon, 0, 0, 1, NULL, 'NORMAL',
           600, 0, 30, 'SKIP', 300, 'DISCARD', 20,
           '只处理 10 分钟前的记录 —— 刚落库那条可能正被线程池处理，捞它等于和自己抢',
           0, 'system', NOW(), NOW(), 'dev', 0, 0, 0, 'MANUAL', 0

    -- 1.3 任务打点漏投反查与补推
    --     🔴 打点方案 §6.4「不引 MQ」的论证前提就是这一条在跑。
    --     30 分钟一轮：它是安全网不是主链路，主链路是进程内事件，毫秒级。
    UNION ALL SELECT 'JOBBIZRECN', '【任务打点】漏投反查与补推', 'bizActionReconcile', 'BUSINESS',
           'cron', '0 15/30 * * * *', @soon, 0, 0, 1, NULL, 'NORMAL',
           600, 0, 30, 'SKIP', 300, 'DISCARD', 30,
           '往回看 24 小时，与流水求差集补推。重推被唯一键兜着，安全',
           0, 'system', NOW(), NOW(), 'dev', 0, 0, 0, 'MANUAL', 0

    -- 1.4 站内信保留期清理
    UNION ALL SELECT 'JOBNOTICLN', '【通知】站内信保留期清理', 'memberNotificationClean', 'BUSINESS',
           'cron', '0 30 3 * * *', @next_0330, 0, 0, 1, NULL, 'NORMAL',
           600, 0, 30, 'SKIP', 300, 'DISCARD', 40,
           '默认保留 180 天',
           0, 'system', NOW(), NOW(), 'dev', 0, 0, 0, 'MANUAL', 0

    -- 1.5 消息接收记录清理
    UNION ALL SELECT 'JOBMQLOGCL', '【基础】消息接收记录清理', 'mqMessageLogClean', 'SYSTEM',
           'cron', '0 40 3 * * *', @next_0340, 0, 0, 1, NULL, 'NORMAL',
           300, 0, 30, 'SKIP', 300, 'DISCARD', 50,
           '默认保留 7 天',
           0, 'system', NOW(), NOW(), 'dev', 0, 0, 0, 'MANUAL', 0

    -- 1.6 优惠券即将过期提醒
    --
    --     🔴 这一条 idempotent = false，而且它<b>真的会给用户发站内信</b>。
    --     所以首次挂载带 dryRun=true：先跑一轮只出数（日志里会写「将要提醒多少人」），
    --     运营确认过量级再放开。一个从没跑过、非幂等、面向全体会员的推送任务，
    --     第一枪就真发出去是不负责任的。
    --
    --     确认后把它打开（一句话）：
    --       UPDATE t_solvela_job SET param = NULL WHERE job_code = 'JOBCOUPNTF';
    UNION ALL SELECT 'JOBCOUPNTF', '【通知】优惠券即将过期提醒', 'couponExpiringNotify', 'BUSINESS',
           'cron', '0 0 10 * * *', @next_1000, 0, 0, 1, '{"dryRun": true}', 'NORMAL',
           600, 0, 30, 'SKIP', 300, 'DISCARD', 60,
           '⚠️ 非幂等且会发站内信。首次挂载为 dryRun，确认量级后把 param 置空',
           0, 'system', NOW(), NOW(), 'dev', 0, 0, 0, 'MANUAL', 0

) AS jobs
WHERE NOT EXISTS (
    -- 判存的子查询要套一层派生表：MySQL 不允许 INSERT ... SELECT 的子查询直接读目标表（错误 1093）
    SELECT 1 FROM (SELECT `job_code` FROM `t_solvela_job`) AS existed
     WHERE existed.`job_code` = jobs.job_code
);


-- ============================================================================
-- 自查
-- ============================================================================
--
--   SELECT job_id, job_code, handler_name, trigger_value, enabled_flag,
--          next_trigger_time, param
--     FROM t_solvela_job WHERE source = 'MANUAL' ORDER BY sort;
--
-- ⚠️ 还有一条<b>反方向</b>的问题，本脚本不处理，需要单独决策：
--
--   externalOrderExpire 在 t_solvela_job 里<b>有行</b>，但它的 handler 在
--   solvela-external 里，而 <b>solvela-admin 的 pom 里没有 solvela-external</b> ——
--   调度器在 admin 进程里，所以这个任务<b>永远不会被执行</b>。
--   admin 每次启动都会报：
--     ERROR 🔴 有 1 个任务的 handler 在代码中不存在：【外部场景】超时单取消
--
--   它不是「忘了注册」，是<b>放错了进程</b>。两条路，都得有人拍板：
--     ① admin 依赖 solvela-external（最省事，但 admin 会再胖一圈）；
--     ② 在 app-biz 起一个 WORKER 角色的调度节点（见 admin 的 application.yaml
--        里 solvela.job.role 那段注释），让业务进程执行自己的任务。
-- ============================================================================
