-- 生产升级：第二批 String 类型列在 Java 里改成枚举，【发版前】在每个环境跑一次。
--
-- 只读，不改任何数据。每一段的结果都必须是空集：
-- 新版代码按枚举读这些列，库里只要有一行取值不在枚举里（大小写不同、旧写法、拼错），读到那一行就直接报错。
-- 有结果就先订正数据，再发版。第一批（prize_type / asset_type）见同目录「资产类型枚举化-发版前核对」。
--
-- ⚠️ 必须 BINARY 比较：库的排序规则大小写不敏感，"draw" 会被当成 "DRAW" 放过，而枚举解析是大小写敏感的。
-- 以后给对应枚举加值，这里的 IN 列表跟着加。

-- ActivityTypeEnum：solvela-contract/src/main/java/solvela/enums/ActivityTypeEnum.java
SELECT 't_activity_config.activity_type' AS col, activity_type AS val, COUNT(*) AS cnt FROM t_activity_config
WHERE BINARY activity_type NOT IN ('BASIC', 'DRAW', 'TASK', 'LOTTERY') GROUP BY activity_type
UNION ALL
-- t_prize_log.activity_type 允许为空（存量行没有这个值），空值不算非法
SELECT 't_prize_log.activity_type', activity_type, COUNT(*) FROM t_prize_log
WHERE activity_type IS NOT NULL AND BINARY activity_type NOT IN ('BASIC', 'DRAW', 'TASK', 'LOTTERY') GROUP BY activity_type
UNION ALL
-- ProposalSourceTypeEnum：solvela-contract/src/main/java/solvela/enums/ProposalSourceTypeEnum.java
SELECT 't_proposal_record.source_type', source_type, COUNT(*) FROM t_proposal_record
WHERE BINARY source_type NOT IN ('TASK', 'DRAW', 'LOTTERY', 'MANUAL') GROUP BY source_type
UNION ALL
-- ScriptRefOwnerTypeEnum：solvela-contract/src/main/java/solvela/enums/ScriptRefOwnerTypeEnum.java
SELECT 't_script_ref.ref_type', ref_type, COUNT(*) FROM t_script_ref
WHERE BINARY ref_type NOT IN ('TASK_TEMPLATE', 'PRIZE_POOL', 'ACTIVITY', 'DRAW') GROUP BY ref_type
UNION ALL
-- TaskTypeEnum / TaskLimitTypeEnum / TaskPrizeModeEnum：solvela-contract/src/main/java/solvela/enums/
SELECT 't_task_template.task_type', task_type, COUNT(*) FROM t_task_template
WHERE BINARY task_type NOT IN ('SIMPLE', 'COUNT', 'AMOUNT', 'STREAK') GROUP BY task_type
UNION ALL
SELECT 't_task_config.limit_type', limit_type, COUNT(*) FROM t_task_config
WHERE limit_type IS NOT NULL AND BINARY limit_type NOT IN ('ONCE', 'DAILY', 'WEEKLY', 'UNLIMITED') GROUP BY limit_type
UNION ALL
SELECT 't_task_prize_mapping.prize_mode', prize_mode, COUNT(*) FROM t_task_prize_mapping
WHERE prize_mode IS NOT NULL AND BINARY prize_mode NOT IN ('FIXED', 'RATIO', 'FORMULA') GROUP BY prize_mode
UNION ALL
-- 商品类型对齐 PrizeTypeEnum（只用其中 PHYSICAL / COUPON / BALANCE，但库里只要是 PrizeTypeEnum 的名字就读得出来）
SELECT 't_mall_commodity.commodity_type', commodity_type, COUNT(*) FROM t_mall_commodity
WHERE BINARY commodity_type NOT IN ('SCORE', 'BALANCE', 'COUPON', 'PHYSICAL', 'MARKER', 'LOTTERY', 'CUSTOM') GROUP BY commodity_type
UNION ALL
SELECT 't_mall_order.commodity_type', commodity_type, COUNT(*) FROM t_mall_order
WHERE BINARY commodity_type NOT IN ('SCORE', 'BALANCE', 'COUPON', 'PHYSICAL', 'MARKER', 'LOTTERY', 'CUSTOM') GROUP BY commodity_type
UNION ALL
-- MallOrderSourceEnum
SELECT 't_mall_order.source_type', source_type, COUNT(*) FROM t_mall_order
WHERE source_type IS NOT NULL AND BINARY source_type NOT IN ('NORMAL', 'FLASH_SALE') GROUP BY source_type
UNION ALL
-- AssetSourceEnum（含只为读老数据的 REISSUE / DRAW / TASK / MANUAL_SEND）
SELECT 't_member_coupon.source_type', source_type, COUNT(*) FROM t_member_coupon
WHERE BINARY source_type NOT IN ('PROPOSAL', 'MALL', 'GRADE_ENTITLEMENT', 'MANUAL', 'REISSUE', 'DRAW', 'TASK', 'MANUAL_SEND') GROUP BY source_type
UNION ALL
SELECT 't_physical_delivery.source_type', source_type, COUNT(*) FROM t_physical_delivery
WHERE BINARY source_type NOT IN ('PROPOSAL', 'MALL', 'GRADE_ENTITLEMENT', 'MANUAL', 'REISSUE', 'DRAW', 'TASK', 'MANUAL_SEND') GROUP BY source_type
UNION ALL
-- CouponTypeEnum
SELECT 't_member_coupon.coupon_type', coupon_type, COUNT(*) FROM t_member_coupon
WHERE BINARY coupon_type NOT IN ('GENERAL') GROUP BY coupon_type
UNION ALL
-- CouponWriteOffBizEnum
SELECT 't_coupon_write_off.biz_type', biz_type, COUNT(*) FROM t_coupon_write_off
WHERE BINARY biz_type NOT IN ('MALL', 'EXTERNAL', 'BOUNCE') GROUP BY biz_type
UNION ALL
-- AssetBizTypeEnum（含只为读老数据的 TASK_PRIZE / CONSUME / MANUAL_ADJUST）
SELECT 't_member_asset_transaction.biz_type', biz_type, COUNT(*) FROM t_member_asset_transaction
WHERE BINARY biz_type NOT IN ('PROPOSAL_REWARD', 'MALL_EXCHANGE', 'GRADE_ENTITLEMENT', 'TASK_PRIZE', 'CONSUME', 'MANUAL_ADJUST') GROUP BY biz_type
UNION ALL
SELECT 't_member_growth_log.biz_type', biz_type, COUNT(*) FROM t_member_growth_log
WHERE biz_type IS NOT NULL AND BINARY biz_type NOT IN ('PROPOSAL_REWARD', 'MALL_EXCHANGE', 'GRADE_ENTITLEMENT', 'TASK_PRIZE', 'CONSUME', 'MANUAL_ADJUST') GROUP BY biz_type
UNION ALL
-- RegisterSourceEnum。⚠️ 这一列以前存的是网关原样传来的值，老数据里可能有枚举外的写法；查出来要先订正（改成 UNKNOWN 会丢原值，订正前先导出留底）
SELECT 't_member.register_source', register_source, COUNT(*) FROM t_member
WHERE BINARY register_source NOT IN ('H5', 'APP', 'WECHAT', 'PC', 'INVITE', 'IMPORT', 'CANARY', 'UNKNOWN') GROUP BY register_source
UNION ALL
-- DeviceTypeEnum。设备表在注册时就校验过；登录日志以前原样记客户端上报值，同上，订正前先导出留底
SELECT 't_device.device_type', device_type, COUNT(*) FROM t_device
WHERE BINARY device_type NOT IN ('APP', 'H5', 'WECHAT', 'PC', 'UNKNOWN') GROUP BY device_type
UNION ALL
SELECT 't_member_login_log.device_type', device_type, COUNT(*) FROM t_member_login_log
WHERE device_type IS NOT NULL AND BINARY device_type NOT IN ('APP', 'H5', 'WECHAT', 'PC', 'UNKNOWN') GROUP BY device_type;
