-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- ⚠️ 本脚本不带 NOW()/CURDATE()，不受 README 里那个时区坑影响。

-- ============================================================================
-- 优惠券 · 外部场景消费单（阶段 7）  2026-09-15
-- ============================================================================
--
-- 【这张表是什么】
--   「用户在某个**外部场景**消费了一笔钱，用券抵扣」的单据。
--   充话费是第一个接它的场景，但表和代码都<b>不叫充话费</b> ——
--   下一个场景（视频会员、加油卡…）不该需要再建一张一模一样的表。
--
-- 🔴 【为什么不复用 t_mall_order】
--   商城单挂着商品、SKU、库存、限购、收货地址、履约单引用 —— 充话费一个都没有。
--   硬塞进去的结果是一张有一半列永远为 NULL 的表，而那一半列上的每一处判空
--   都要写成「如果是外部场景就跳过」。两种单据的生命周期也不一样：
--   商城要发货，这里要调外部接口。
--
-- 🔴 【target_account 是密文】
--   手机号属于个人信息，与 t_physical_delivery.receiver_phone 同一套
--   PiiTypeHandler、同一把密钥。⚠️ 实体上必须 autoResultMap = true，
--   否则 MyBatis-Plus 只在写的时候用 typeHandler，读回来是一串密文。
--
-- 【状态机，与商城订单同构】
--   0-待支付 → 10-待执行 → 20-执行中 → 30-成功 / 60-失败
--                        ↘ 40-已取消（超时或用户取消，券要放回去）
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/优惠券-外部场景消费单.sql;
--   ⚠️ 一次性脚本，与本仓其它建表同一个约定：不做幂等保护。
--
-- 🔴 执行完必须重新导出结构基线：
--       cd 数据库SQL脚本/tools && java -cp <mysql-connector.jar> DumpSchema.java
-- ============================================================================

CREATE TABLE `t_external_order` (
  `id`              bigint       NOT NULL AUTO_INCREMENT,
  `order_no`        varchar(64)  NOT NULL COMMENT '单号。服务端生成，同时是锁券与幂等的键',
  `member_id`       bigint       NOT NULL COMMENT '会员号：关联键',
  `member_name`     varchar(32)  DEFAULT NULL COMMENT '会员账号【展示快照，非关联键，不要用于查询】',

  `scene_code`      varchar(32)  NOT NULL COMMENT '场景码，如 MOBILE_RECHARGE。券的 scope_refs 按它匹配',
  `target_account`  varchar(255) NOT NULL COMMENT '充值目标【密文】：手机号 / 账号。与 t_physical_delivery 同一套 PiiTypeHandler',
  `target_masked`   varchar(32)  DEFAULT NULL COMMENT '打码后的目标，如 138****8888。给列表展示用，省一次解密',

  `original_amount` decimal(10,2) NOT NULL COMMENT '抵扣前应付（用户选的面额）',
  `coupon_id`       bigint       DEFAULT NULL COMMENT '用掉的会员券 id。软引用，不加外键。NULL=没用券',
  `coupon_discount` decimal(10,2) DEFAULT NULL COMMENT '券抵扣了多少。冗余，权威在 t_coupon_write_off',
  `pay_amount`      decimal(10,2) NOT NULL COMMENT '实付 = 抵扣前 - 券抵扣',

  `status`          tinyint      NOT NULL DEFAULT 0 COMMENT '0-待支付 10-待执行 20-执行中 30-成功 40-已取消 60-失败',
  `expire_time`     datetime     DEFAULT NULL COMMENT '待支付超时时间，到点由 job 取消并放回券',
  `pay_time`        datetime     DEFAULT NULL COMMENT '支付时间',
  `finish_time`     datetime     DEFAULT NULL COMMENT '执行完成时间',
  `external_ref_no` varchar(128) DEFAULT NULL COMMENT '外部流水号（运营商返回的）。对账靠它',
  `fail_reason`     varchar(255) DEFAULT NULL COMMENT '失败原因',

  `create_by`       varchar(64)  DEFAULT NULL,
  `create_time`     datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_by`       varchar(64)  DEFAULT NULL,
  `update_time`     datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_member` (`member_id`, `id`),
  KEY `idx_expire` (`status`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部场景消费单：充话费等。券的第一个非商城出口';


-- ---------------------------------------------------------------------------
-- 定时任务：超时未支付的外部单自动取消并放回券
--
-- 🔴 和商城订单那个 job 是同构的、但<b>各自一个</b>：
--    两种单据的表不同、补偿动作不同（商城要退积分放库存，这里只放券）。
--    合成一个的结果是一个满是 if 的任务，而它一旦写错会同时影响两条业务线。
-- ---------------------------------------------------------------------------
INSERT INTO `t_solvela_job`
    (`job_code`, `job_name`, `handler_name`, `job_group`, `trigger_type`, `trigger_value`,
     `next_trigger_time`, `trigger_version`, `jitter_seconds`, `enabled_flag`, `param`, `preset_code`,
     `timeout_seconds`, `retry_times`, `retry_interval`, `misfire_strategy`, `misfire_threshold_sec`,
     `block_strategy`, `sort`, `remark`, `deleted_flag`, `update_name`, `app_env`, `source`)
SELECT 'JOBEXTEXP', '【外部场景】超时单取消', 'externalOrderExpire', 'BUSINESS', 'cron', '0 2/10 * * * *',
       DATE_ADD(NOW(), INTERVAL 1 MINUTE), 0, 0, 1, NULL, 'NORMAL',
       300, 0, 30, 'SKIP', 300,
       'DISCARD', 0,
       '每 10 分钟取消超时未支付的外部场景单，并把锁定的券放回去；支持 dryRun 试运行',
       0, 'system', 'dev', 'MANUAL'
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_solvela_job`) j
   WHERE j.`handler_name` = 'externalOrderExpire' AND j.`deleted_flag` = 0
);


-- ---------------------------------------------------------------------------
-- 种子：一张充话费券模板
--
-- ⚠️ scope_type = EXTERNAL + scope_refs 指明场景码 —— 这正是券模板里
--    EXTERNAL 那一档存在的理由，阶段 1 建它的时候就是为了这一天。
-- ---------------------------------------------------------------------------
INSERT INTO `t_coupon_template`
    (`coupon_code`, `version`, `coupon_name`, `discount_type`, `discount_value`,
     `min_amount`, `max_discount`, `deduct_target`, `scope_type`, `scope_refs`,
     `valid_days`, `remark`, `status`, `create_by`)
SELECT 'RECHARGE10', 1, '话费充值满100减10', 'FIXED', 10.00,
       100.00, NULL, 'CASH', 'EXTERNAL', '["MOBILE_RECHARGE"]',
       30, '阶段 7 种子：外部场景券的第一张。上线前请运营核对面额与门槛', 1, 'system'
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_coupon_template`) t
   WHERE t.`coupon_code` = 'RECHARGE10' AND t.`version` = 1
);


-- ---------------------------------------------------------------------------
-- 核对
-- ---------------------------------------------------------------------------
SELECT COUNT(*) AS `外部单表建好没` FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name = 't_external_order';

SELECT `job_code` AS `任务`, `handler_name` AS `执行器`, `trigger_value` AS `cron`
  FROM `t_solvela_job` WHERE `handler_name` = 'externalOrderExpire';

SELECT `coupon_code` AS `券编码`, `coupon_name` AS `券名`, `scope_type` AS `范围`, `scope_refs` AS `场景`
  FROM `t_coupon_template` WHERE `coupon_code` = 'RECHARGE10';
