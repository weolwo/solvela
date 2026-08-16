-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- ============================================================
-- 抽奖模块联调 / 压测造数脚本（可重复执行）
--
-- 只造「活动」和「资产大库」两层基础数据；
-- 奖池物资(t_prize_pool_item)、奖池(t_prize_pool_config)、坑位映射(t_pool_prize_mapping)
-- 一律交给「抽奖配置工作台」页面保存生成 —— 这样才能顺带验证聚合保存与回显的往返闭环。
--
-- 编码约定：活动编码 / 奖品编码统一为「10 位大写字母 + 数字」的随机组合，全局唯一。
-- 下面的编码是预先生成好的固定值，方便压测脚本直接引用；奖品含义见每行行尾注释。
--
-- 前置：v3.34.0.sql（is_fallback 列）、v3.35.0.sql（t_prize_config 唯一索引）已执行
-- ============================================================

-- ---------- 1. 抽奖活动 ----------
-- status：0-未开始（可自由增删奖项/奖池）, 1-上线（触发服务端结构锁）, 2-下线
-- 联调建议：先用 0 把配置搭起来，再改成 1 验证结构锁是否按预期拦截
INSERT INTO `t_activity_config` (`tenant_id`, `activity_code`, `activity_name`, `activity_type`, `status`, `start_time`,
                                 `end_time`, `create_by`)
VALUES ('0', '12278CBYW7', '618 年中狂欢抽奖', 'DRAW', 0, '2026-06-01 00:00:00', '2026-12-31 23:59:59', 'seed')
ON DUPLICATE KEY UPDATE `activity_name` = VALUES(`activity_name`),
                        `activity_type` = VALUES(`activity_type`),
                        `start_time`    = VALUES(`start_time`),
                        `end_time`      = VALUES(`end_time`);

-- ---------- 2. 资产大库（工作台「从资产大库引入奖项」的数据源） ----------
-- 先按活动清理再插入，保证脚本可重复执行
DELETE
FROM `t_prize_config`
WHERE `activity_code` = '12278CBYW7';

INSERT INTO `t_prize_config` (`tenant_id`, `activity_code`, `promotion_config_id`, `prize_type`, `prize_name`,
                              `prize_code`, `prize_level`, `prize_value`, `approve_mode`, `sort_weight`, `status`,
                              `create_by`)
VALUES
    -- 压测主角：库存在工作台里卡到 5，用来验证「绝不超发」
    ('0', '12278CBYW7', 0, 'PHYSICAL', 'iPhone 15 Pro', '9P6HK2T649', 1, 7999.0000, 1, 1, 1, 'seed'),
    ('0', '12278CBYW7', 0, 'PHYSICAL', 'AirPods Pro 2', 'C25C44XCHJ', 2, 1899.0000, 1, 2, 1, 'seed'),
    ('0', '12278CBYW7', 0, 'BALANCE', '88 元现金红包', 'FBJR9BAIWI', 3, 88.0000, 1, 3, 1, 'seed'),
    ('0', '12278CBYW7', 0, 'COUPON', '满100减20优惠券', '0ZXXLZ0RZ1', 4, 20.0000, 0, 4, 1, 'seed'),
    ('0', '12278CBYW7', 0, 'SCORE', '100 积分', 'G3TWEXS5L7', 5, 1.0000, 0, 5, 1, 'seed'),
    -- 兜底奖项：在工作台里配成不限量 + 兜底，承接库存不足时的降级
    ('0', '12278CBYW7', 0, 'SCORE', '谢谢参与', '2CJGRUOMUZ', 9, 0.0000, 0, 9, 1, 'seed');

-- 速查：跑压测时要用到活动编码和奖池编码
-- SELECT activity_code, activity_name FROM t_activity_config WHERE activity_type = 'DRAW';
-- SELECT pool_code, pool_name FROM t_prize_pool_config WHERE activity_code = '12278CBYW7';

-- ---------- 3. 压测账号钱包：已无需准备 ----------
-- v3.64.0 起抽奖引擎不再扣费：cost_asset_type / cost_value 两个字段连同
-- DrawExecuteService 的 deductDrawCost / refundDrawCost 一并移除了。
-- 抽一次消耗什么由上游业务自己扣，压测直连 /drawPrizeLog/execute 不碰钱包，
-- 所以这里不用再造余额。
-- 若要连同上游扣费一起压，请在压测脚本里走业务侧入口，钱包数据按那条链路的要求准备。

-- ---------- 4. 重跑压测前的清场（按需执行） ----------
-- 抽奖流水、已发库存、Redis 缓存三者必须一起清，否则第二轮压测的基线是脏的。
-- Redis 侧需另行执行（库存 + 单人限领计数，两类 key 都要清）：
--   redis-cli --scan --pattern 'draw:stock:12278CBYW7:*' | xargs -r redis-cli del
--   redis-cli --scan --pattern 'draw:user:12278CBYW7:*'  | xargs -r redis-cli del
-- 清完 Redis 后请重新在工作台点一次「保存并发布」触发库存预热，或依赖 resolveRemainStock 的回源预热。
--
-- DELETE FROM `t_draw_prize_log` WHERE `activity_code` = '12278CBYW7';
-- UPDATE `t_prize_pool_item` SET `used_stock` = 0 WHERE `activity_code` = '12278CBYW7';
