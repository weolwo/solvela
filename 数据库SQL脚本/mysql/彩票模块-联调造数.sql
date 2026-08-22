-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- ============================================================
-- 彩票模块：联调造数（可重复执行）
--
-- 【为什么没有 lottery_code 迁移】
-- 技术方案里原本列了一条「存量 lottery_code = L_5DIGIT_001 不符合编码规范，需级联迁移三张表」。
-- 实际核对开发库后确认：t_lottery_config / t_lottery_issue / t_lottery_prize_rule / t_lottery_record
-- 四张表全部为 0 行 —— L_5DIGIT_001 只存在于原型 HTML 的 mock 数据里，库里从来没有过。
-- 所以迁移脚本不需要，这里只造联调所需的上游数据。
--
-- 【本脚本只造上游，不造彩票本身】
-- 彩票配置与奖级规则刻意留给「彩票工作台」页面去创建，顺带验证保存 <-> 回显的往返一致性，
-- 做法与 抽奖模块-联调造数.sql 一致（那份也把奖池/物资/坑位留给工作台生成）。
-- ============================================================

-- ---------- 1. 彩票类活动 ----------
-- 彩票工作台的活动下拉只拉 activity_type='LOTTERY'。
-- 核对时开发库只有 DRAW(12278CBYW7) 与 TASK(WLO9SMXDKD) 两条，LOTTERY 一条都没有，下拉会是空的。
INSERT INTO `t_activity_config` (`tenant_id`, `activity_code`, `activity_name`, `activity_type`, `status`,
                                 `start_time`, `end_time`, `create_by`)
VALUES ('taozi', 'LTQ7M3XKD8', '618仲夏夜幸运号', 'LOTTERY', 0, '2026-07-01 00:00:00', '2026-12-31 23:59:59', 'seed')
ON DUPLICATE KEY UPDATE `activity_name` = VALUES(`activity_name`),
                        `activity_type` = VALUES(`activity_type`);


-- ---------- 2. 资产大库：四个奖级对应的奖品 ----------
-- 奖级规则的「指派奖品」下拉来自 GET /prizeConfig/optionList?activityCode=LTQ7M3XKD8，
-- 这张表没数据的话，工作台右侧的资产池是空的、奖级规则绑不上奖。
--
-- ⚠️ prize_code 有全局唯一索引 uk_prize_code，不能复用抽奖活动那 6 个编码，故另起一套。
-- ⚠️ promotion_config_id 必须指向真实存在且 prize_type 一致的优惠配置，
--    否则发奖时会被判「资产配置异常」；服务端在 PrizeConfigService 里也会重校验两者类型一致。
--    现有配置：1=SCORE 积分池 / 2=BALANCE 现金池 / 3=COUPON 优惠券池 / 4=PHYSICAL 实物池。
INSERT INTO `t_prize_config` (`tenant_id`, `activity_code`, `promotion_config_id`, `prize_type`, `prize_name`,
                              `prize_code`, `prize_level`, `prize_value`, `approve_mode`, `sort_weight`, `status`, `create_by`)
VALUES
    -- 一等奖：实物，approve_mode=1 人工审批（大额实物不该自动发货）
    ('0', 'LTQ7M3XKD8', 4, 'PHYSICAL', 'iPhone 15 Pro', 'PZ8KWQ3N7T', 1, 7999.0000, 1, 1, 1, 'seed'),
    -- 二等奖：现金，approve_mode=1 人工审批
    ('0', 'LTQ7M3XKD8', 2, 'BALANCE', '588 元现金红包', 'B5R2YHJ9MC', 2, 588.0000, 1, 2, 1, 'seed'),
    -- 三等奖：优惠券，免审直发
    ('0', 'LTQ7M3XKD8', 3, 'COUPON', '满100减20优惠券', 'CU4XN6VTLQ', 3, 20.0000, 0, 3, 1, 'seed'),
    -- 四等奖：积分，免审直发
    ('0', 'LTQ7M3XKD8', 1, 'SCORE', '100 积分', 'SC9DFB2WK5', 4, 1.0000, 0, 4, 1, 'seed')
ON DUPLICATE KEY UPDATE `prize_name`          = VALUES(`prize_name`),
                        `prize_type`          = VALUES(`prize_type`),
                        `prize_value`         = VALUES(`prize_value`),
                        `promotion_config_id` = VALUES(`promotion_config_id`),
                        `approve_mode`        = VALUES(`approve_mode`),
                        `activity_code`       = VALUES(`activity_code`);


-- ============================================================
-- 以下是「到 P4 派奖联调时才需要」的两件事，现在先不执行，留在这里免得到时候现查
-- ============================================================

-- ⚠️ 积分池预算早已用尽（id=1 的 total_amount=50 / used_amount=50，是此前预算硬限流验证的残留）。
-- 不重置的话，四等奖积分在派发时会全部落「预算或发放数量已耗尽」，看起来像 bug 其实是配置。
-- P4 联调前放开这句：
-- UPDATE `t_promotion_config` SET `used_amount` = 0, `used_quota` = 0, `total_amount` = -1 WHERE `id` = 1;

-- 重跑发号/开奖压测前的清场（DB 与 Redis 是两份状态，只清 DB 等于没清）：
-- DELETE FROM `t_lottery_record` WHERE `lottery_code` = '你的彩票编码';
-- UPDATE `t_lottery_issue` SET `sold_count` = 0, `status` = 0, `winning_number` = NULL, `settle_time` = NULL
--  WHERE `lottery_code` = '你的彩票编码';
-- Redis:  DEL lottery:seq:{彩票编码}:{期号}
-- ⚠️ 发号游标绝不能只清一边：DB 清空而 Redis 游标还在高位，只是浪费号段（安全）；
--    反过来 Redis 被清而 DB 还有记录，游标会从 0 重来，直接撞 uk_issue_ticket 导致领号报错。
--    所以清场顺序永远是「先清 DB，再清 Redis」。


-- ---------- 遗留：孤儿表 t_lottery_number_pool ----------
-- 号池方案已废弃（改用 FPE 发号，不预生成号码），代码侧已停用、菜单已在 v3.39.0.sql 清理，
-- 但开发库里这张表还在（实测存在，0 行）。lottery.sql 里写的「已永久移除」只是注释，没有真的 DROP。
-- 属于纯孤儿表，确认无用后可执行：
-- DROP TABLE IF EXISTS `t_lottery_number_pool`;
