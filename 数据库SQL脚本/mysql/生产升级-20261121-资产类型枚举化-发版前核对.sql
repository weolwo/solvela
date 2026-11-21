-- 生产升级：prize_type / asset_type 七列在 Java 里改成 PrizeTypeEnum，【发版前】在每个环境跑一次。
--
-- 只读，不改任何数据。结果必须是空集：
-- 新版代码按枚举名读这几列，库里只要有一行取值不在下面七个名字里（大小写不同、旧写法、拼错），
-- 读到那一行就直接报错 —— 不是降级，是那一页 / 那次发奖整个失败。有结果就先订正数据，再发版。
--
-- 枚举定义：solvela-api/solvela-contract/src/main/java/solvela/enums/PrizeTypeEnum.java
-- 以后给 PrizeTypeEnum 加值，下面的 IN 列表跟着加。
-- ⚠️ 必须 BINARY 比较：库的排序规则大小写不敏感，"score" 会被当成 "SCORE" 放过，而 Enum.valueOf 是大小写敏感的。

SELECT 't_prize_config' AS tbl, prize_type AS val, COUNT(*) AS cnt FROM t_prize_config
WHERE BINARY prize_type NOT IN ('SCORE', 'BALANCE', 'COUPON', 'PHYSICAL', 'MARKER', 'LOTTERY', 'CUSTOM') GROUP BY prize_type
UNION ALL
SELECT 't_prize_log', prize_type, COUNT(*) FROM t_prize_log
WHERE BINARY prize_type NOT IN ('SCORE', 'BALANCE', 'COUPON', 'PHYSICAL', 'MARKER', 'LOTTERY', 'CUSTOM') GROUP BY prize_type
UNION ALL
SELECT 't_promotion_config', prize_type, COUNT(*) FROM t_promotion_config
WHERE BINARY prize_type NOT IN ('SCORE', 'BALANCE', 'COUPON', 'PHYSICAL', 'MARKER', 'LOTTERY', 'CUSTOM') GROUP BY prize_type
UNION ALL
SELECT 't_proposal_record', asset_type, COUNT(*) FROM t_proposal_record
WHERE BINARY asset_type NOT IN ('SCORE', 'BALANCE', 'COUPON', 'PHYSICAL', 'MARKER', 'LOTTERY', 'CUSTOM') GROUP BY asset_type
UNION ALL
SELECT 't_member_wallet', asset_type, COUNT(*) FROM t_member_wallet
WHERE BINARY asset_type NOT IN ('SCORE', 'BALANCE', 'COUPON', 'PHYSICAL', 'MARKER', 'LOTTERY', 'CUSTOM') GROUP BY asset_type
UNION ALL
SELECT 't_member_asset_transaction', asset_type, COUNT(*) FROM t_member_asset_transaction
WHERE BINARY asset_type NOT IN ('SCORE', 'BALANCE', 'COUPON', 'PHYSICAL', 'MARKER', 'LOTTERY', 'CUSTOM') GROUP BY asset_type
UNION ALL
SELECT 't_grade_entitlement', asset_type, COUNT(*) FROM t_grade_entitlement
WHERE BINARY asset_type NOT IN ('SCORE', 'BALANCE', 'COUPON', 'PHYSICAL', 'MARKER', 'LOTTERY', 'CUSTOM') GROUP BY asset_type;
