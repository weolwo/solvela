-- ============================================================================
-- v3.59.0  定时任务：PENDING 记录不该被迫填写「执行时才有」的列
-- ============================================================================
--
-- 前置：v3.58.0 必须先执行。
--
-- 🔴 起因是一个实测（2026-08-12）暴露的连锁故障：
--
--   失败重试要插一条全新的 PENDING 记录，但 execute_start_time / ip /
--   process_id / program_path 四列都是 NOT NULL 且无默认值 ——
--   而一条**还没开始执行**的记录，本来就没有「开始时间」和「在哪个节点跑」。
--   于是插入报 `Field 'execute_start_time' doesn't have a default value`。
--
--   连带效应才是真正致命的：置 FAIL 与插 PENDING 在**同一个事务**里
--   （那是为了防止「重试静默丢失」刻意设计的），插入失败导致整个事务回滚，
--   **连那条本该把状态改成 FAIL 的 UPDATE 也一起没了** ——
--   记录永久停在 RUNNING，而 RUNNING 又会让阻塞判断永久误判为「上一次还在跑」，
--   这个任务从此再也不会被执行。
--   <b>为防静默丢失而加的安全机制，自己成了放大器。</b>
--
-- 修法不是给这几列填占位值（手动触发那条路径原本就是靠 ip='-'、
-- execute_start_time=now 硬填过去的）—— 那是在数据里写谎话：
-- 列表页会给一条根本没跑的记录显示「开始时间」，而且第三条创建 PENDING 记录的
-- 路径迟早再撞一次同样的墙。
--
-- 正解是让模型说实话：**没开始执行，这几列就是 NULL**。
-- ============================================================================

ALTER TABLE `t_smart_job_log`
    MODIFY COLUMN `execute_start_time` datetime NULL DEFAULT NULL
        COMMENT '开始执行时间。PENDING（待执行/待重试）记录为 NULL —— 它还没开始',
    MODIFY COLUMN `ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL
        COMMENT '执行节点 ip。抢占到该记录时才知道，PENDING 为 NULL',
    MODIFY COLUMN `process_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL
        COMMENT '执行进程 id，PENDING 为 NULL',
    MODIFY COLUMN `program_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL
        COMMENT '执行程序目录，PENDING 为 NULL';

-- 把手动触发路径写进去的占位值清掉，让存量数据也说实话。
-- 这些记录当时被填了 ip='-'、process_id='-'、program_path='-'。
--
-- ⚠️ 判据只看 process_id，不要写成「三列都等于 '-'」：
--    抢占那一步（preemptPendingLog）只覆盖 ip 一列，所以一条「抢到了但随即被判 BLOCKED」
--    的记录会是 ip=真实地址、process_id='-'、program_path='-' 的混合状态 ——
--    按三列全等去匹配就会漏掉它们。2026-08-12 实测确实漏了一条。
UPDATE `t_smart_job_log`
   SET `ip` = CASE WHEN `ip` = '-' THEN NULL ELSE `ip` END,
       `process_id` = NULL,
       `program_path` = NULL
 WHERE `process_id` = '-';


-- ----------------------------------------------------------------------------
-- 自查
-- ----------------------------------------------------------------------------
-- 1) 四列是否都已可空
-- SELECT column_name, is_nullable FROM information_schema.columns
--  WHERE table_schema='smart_admin_v3' AND table_name='t_smart_job_log'
--    AND column_name IN ('execute_start_time','ip','process_id','program_path');
--
-- 2) 有没有卡在 RUNNING 的僵尸记录（本次故障留下的，可由 _jobZombieScan 回收，
--    也可手工置为 7-INTERRUPTED）
-- SELECT log_id, job_name, status, execute_start_time FROM t_smart_job_log WHERE status = 1;
