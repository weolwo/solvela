-- 实物履约单的收件信息改为可空（2026-07-26 实物审批派发实测暴露）
--
-- 现象：审批通过实物奖后抛
--   Field 'receiver_name' doesn't have a default value
--
-- 根因不是漏赋值，是建模问题：中奖那一刻用户根本还没填地址。
-- PhysicalAssetHandler 里那句「收件人信息通常在生成提案前就已经存到 proposal.ext 或另外一张收货表里了」
-- 描述的是一个并不存在的前置环节 —— 整条链路没有任何地方采集过收货信息。
--
-- 实物履约的真实形态是三段式：
--   ① 中奖 -> 生成履约单（此时只知道发什么、发给谁，不知道寄到哪）
--   ② 用户在 C 端补填收货信息
--   ③ 运营发货、回填物流单号
-- 所以收件三要素必须可空，由第 ② 步补齐。
--
-- 「待补充收货信息」不新增状态值，仍归属 status=0(待发货)，用 receiver_address IS NULL 判定，
-- 避免动状态字典引发上下游连锁改动。运营列表按这个条件分两个 tab 即可。
ALTER TABLE `t_physical_delivery`
    MODIFY COLUMN `receiver_name` varchar(64) NULL DEFAULT NULL COMMENT '收件人姓名：中奖时未知，由用户后续补填',
    MODIFY COLUMN `receiver_phone` varchar(32) NULL DEFAULT NULL COMMENT '收件人电话：中奖时未知，由用户后续补填',
    MODIFY COLUMN `receiver_address` varchar(255) NULL DEFAULT NULL COMMENT '收件详细地址：中奖时未知，由用户后续补填';

-- 便于运营筛出「待用户补地址」的履约单
CREATE INDEX `idx_delivery_status` ON `t_physical_delivery` (`status`, `create_time`);
