-- =====================================================================================
-- v3.44.0  任务中台运行态 P0：事件流水表 + 任务记录乐观锁
-- 方案：docs/任务中台-改进技术方案.md v2 §4.3 ~ §4.8
-- 撰写：2026-08-01
--
-- 本脚本可重复执行（建表带 IF NOT EXISTS，加列前先查 information_schema）。
-- =====================================================================================


-- -------------------------------------------------------------------------------------
-- 一、任务事件流水表（方案 §4.5）
--
-- 这张表一次解决三个问题，不要拆：
--   ① 事件幂等   —— uk_t_tsk_flw_evt 挡住上游/MQ 的重复投递
--   ② 客诉自证   —— 被丢弃的事件也留痕，能回答「用户下了 99 元的单为什么没进度」
--   ③ STREAK 日内幂等 —— STREAK 的 period_key 恒为 NONE，唯一索引不再承担日内防重，改由本表承担
--
-- 刻意否决的方案：把 handled_event_ids 数组塞进 t_task_record.progress_data。
--   它本身是一次读-改-写 JSON（与 Lost Update 同源），且数组无上界
--   （AMOUNT 类任务一个用户一个周期可能上百笔订单）。
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_task_record_flow`
(
    `id`             bigint         NOT NULL AUTO_INCREMENT COMMENT 'id',
    `tenant_id`      varchar(16)    NOT NULL DEFAULT '0' COMMENT '租户ID',
    `member_name`    varchar(64)    NOT NULL COMMENT '会员名',
    `task_config_id` bigint         NOT NULL COMMENT '任务配置ID',
    `record_id`      bigint         NULL     DEFAULT NULL COMMENT '任务记录ID：被丢弃的事件可能还没建记录，故可空',
    `event_code`     varchar(64)    NOT NULL COMMENT '事件编码：DAILY_SIGN / ORDER_PAID ...',
    `event_biz_id`   varchar(128)   NOT NULL COMMENT '幂等键：上游单号；无天然单号的事件用 D+yyyyMMdd(事件日) 兜底',
    `flow_type`      tinyint        NOT NULL DEFAULT 1 COMMENT '1-进度推进(已生效), 2-事件丢弃(未生效)',
    `delta_metric`   decimal(18, 4) NOT NULL DEFAULT '0.0000' COMMENT '本次增量',
    `after_metric`   decimal(18, 4) NOT NULL DEFAULT '0.0000' COMMENT '推进后进度值，便于按时间轴复盘',
    `discard_reason` varchar(255)   NULL     DEFAULT NULL COMMENT '丢弃原因：flow_type=2 时必填',
    `event_payload`  json           NULL     DEFAULT NULL COMMENT '事件原文快照，供客诉复盘',
    `create_by`      varchar(64)             DEFAULT NULL COMMENT '创建人',
    `create_time`    datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      varchar(64)             DEFAULT NULL COMMENT '更新人',
    `update_time`    datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 幂等主力：一个事件对一个任务配置只能生效一次。
    -- 带 task_config_id 是必要的：一个 ORDER_PAID 可能同时命中「累计消费满500」和「下单3次」两个配置，
    -- 它对每个配置都该各生效一次。
    UNIQUE KEY `uk_t_tsk_flw_evt` (`task_config_id`, `member_name`, `event_biz_id`),
    KEY `idx_t_tsk_flw_rec` (`record_id`, `create_time`),
    KEY `idx_t_tsk_flw_mbr` (`member_name`, `create_time`)
) COMMENT ='任务事件流水表：幂等防重 + 客诉自证';


-- -------------------------------------------------------------------------------------
-- 二、t_task_record 增加乐观锁版本号（方案 §4.4）
--
-- ⚠️ 这一列是给 STREAK 用的，不是给 COUNT/AMOUNT 用的。
--    COUNT/AMOUNT/SIMPLE 走「条件更新原子累加」：
--        UPDATE t_task_record SET current_metric = current_metric + ? WHERE id = ? AND status = 0
--    一条 SQL、零冲突、零重试，与 t_prize_pool_item.increaseUsedStock 同一模式。
--    只有 STREAK 必须先读 lastHitDate 才知道是「清零再+1」还是「+1」，读-改-写无法避免，才用版本号。
--    别因为加了这一列就把三个策略都改成读-改-写 —— 那是把一个能一条 SQL 解决的问题
--    主动降级成需要重试的问题。
-- -------------------------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = 't_task_record'
                      AND column_name = 'version');

SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE `t_task_record` ADD COLUMN `version` int NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号：仅 STREAK 的读-改-写路径使用，累加型走条件更新不需要它'' AFTER `current_metric`',
               'SELECT ''t_task_record.version 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- -------------------------------------------------------------------------------------
-- 三、自查：新表是否踩了「NOT NULL 且无默认值」（该模式在本项目已复发 5 次）
--     期望输出：空集。有输出说明建实体时必须显式赋值，否则 MyBatis-Plus 省略 null 字段 +
--     MySQL 严格模式会直接拒绝插入。
-- -------------------------------------------------------------------------------------
SELECT table_name, column_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 't_task_record_flow'
  AND is_nullable = 'NO'
  AND column_default IS NULL
  AND extra NOT LIKE '%auto_increment%';

-- 自查：时间列是否带 ON UPDATE（铁律 9）。期望 update_time 一行、extra 含 on update。
SELECT column_name, column_default, extra
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 't_task_record_flow'
  AND column_name IN ('create_time', 'update_time');
