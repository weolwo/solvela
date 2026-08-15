-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- ============================================================
-- 奖励派发链路联调造数（可重复执行）
--
-- 背景：ProposalRecordService.addProposal() 第一步就是
--   promotionConfigService.getById(req.getPromotionConfigId())
-- 而开发库 t_promotion_config 是空表、t_prize_config.promotion_config_id 全是 0，
-- 于是每条提案都在这里被判「资产配置异常」直接返回。
--
-- 下面按风控责任链的**实际判定条件**配值，保证压测时能顺利通过：
--   BasicLimitRiskFilter  : single_max_amount > 0 且 申请额 > 它 -> 拒绝。故设成远大于奖品面额的值
--   FrequencyRiskFilter   : identify_limit <= 0 -> 直接放行。故设 -1
--   GlobalBudgetRiskFilter: total_amount == -1 -> 直接放行。故设 -1
--   calculateInitStatus   : review_level == 0 -> 提案状态 30(待执行/免审)
-- ============================================================

-- ---------- 1. 按资产类型建优惠配置 ----------
DELETE
FROM `t_promotion_config`
WHERE `promo_name` LIKE '联调-%';

INSERT INTO `t_promotion_config` (`tenant_id`, `promo_name`, `prize_type`, `total_quota`, `used_quota`, `total_amount`,
                                  `used_amount`, `review_level`, `first_review_threshold`, `second_review_threshold`,
                                  `single_max_quota`, `single_max_amount`, `limit_period`, `identify_limit`,
                                  `phone_limit`, `ip_limit`, `device_limit`, `fingerprint_limit`, `status`)
VALUES ('0', '联调-积分池', 'SCORE', -1, 0, -1.0000, 0.0000, 0, 0.0000, 0.0000, 1, 10000.0000, 'LIFETIME', -1, -1, -1, -1, -1, 1),
       ('0', '联调-现金池', 'BALANCE', -1, 0, -1.0000, 0.0000, 0, 0.0000, 0.0000, 1, 10000.0000, 'LIFETIME', -1, -1, -1, -1, -1, 1),
       ('0', '联调-优惠券池', 'COUPON', -1, 0, -1.0000, 0.0000, 0, 0.0000, 0.0000, 1, 10000.0000, 'LIFETIME', -1, -1, -1, -1, -1, 1),
       ('0', '联调-实物池', 'PHYSICAL', -1, 0, -1.0000, 0.0000, 0, 0.0000, 0.0000, 1, 100000.0000, 'LIFETIME', -1, -1, -1, -1, -1, 1);

-- ---------- 2. 把奖品按资产类型挂到对应的优惠配置上 ----------
UPDATE `t_prize_config` c
    JOIN `t_promotion_config` p ON p.`prize_type` = c.`prize_type` AND p.`promo_name` LIKE '联调-%'
SET c.`promotion_config_id` = p.`id`
WHERE c.`activity_code` = '12278CBYW7';

-- 自查：不应再有 promotion_config_id = 0 的奖品
-- SELECT prize_code, prize_name, prize_type, promotion_config_id FROM t_prize_config WHERE activity_code = '12278CBYW7';

-- ---------- 3. 重跑派发前的清场（按需） ----------
-- 提案表靠 uk_t_prm_prop_tsk_stg(source_type, source_biz_id) 防重，
-- 压测脚本现在每轮 RUN_ID 都不同，sourceBizId 不会跨轮撞车，一般无需清理。
-- 若要彻底回到干净基线：
--
-- DELETE FROM `t_proposal_record`;
-- DELETE FROM `t_prize_log`;
-- DELETE FROM `t_draw_prize_log`;
-- UPDATE `t_prize_pool_item` SET `used_stock` = 0 WHERE `activity_code` = '12278CBYW7';
-- 清完记得到抽奖工作台点一次「保存并发布」，触发 warmStock 重刷 Redis 库存
--
-- FrequencyRiskFilter 会在 Redis 写 risk:freq:{周期}:promo_{id}:{用户} 计数，
-- identify_limit=-1 时不影响判定，但想清干净可以执行：
--   redis-cli --scan --pattern 'risk:freq:*' | xargs -r redis-cli del
