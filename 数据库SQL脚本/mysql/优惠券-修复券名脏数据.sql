-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- ============================================================================
-- 优惠券 · 阶段 0：修「提案生成成功」这批券名  2026-09-15
-- ============================================================================
--
-- 【是什么】
--   库里 306 张券的 coupon_name 是「提案生成成功」—— 用户在券包里看到的券名
--   就是这五个字。
--
-- 【根因（代码已修，这里只清存量）】
--   CouponAssetHandler 原来取的是 proposal.getRemark()，而 remark 在
--   ProposalRecordService.saveProposal 里被固定写成「提案生成成功」。
--
--   根因不是随手写错：依赖方向从「账务 → 营销」翻转之后，券名这条信息没有了
--   搬运通道，remark 是当时唯一够得着的字段。正解是让提案携带展示名
--   （assetName），而不是让账务域回头查营销域的表 —— v3.45.0 已经这么改了，
--   现在 CouponAssetHandler 取的是 proposal.getAssetName()。
--
--   所以【本脚本只处理存量，代码侧不需要再改】。
--
-- 【为什么能精确回填，不用猜】
--   这批券的 coupon_code 就是 t_prize_config.prize_code，而那张表上有真名：
--     0ZXXLZ0RZ1 -> 满100减20优惠券   （302 张）
--     PP0COUPON1 -> P0-20元券          （4 张）
--   提案那边 asset_name 全是空的（0 张有值），所以不走提案，走奖品配置。
--
-- 【风险】
--   这 306 张【全部是 status=2 已过期】（2026-07-26 ~ 08-01 发的），
--   没有任何用户正拿着它们用。改券名只影响历史券包的展示。
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/优惠券-修复券名脏数据.sql;
--   幂等：条件里带着 coupon_name = '提案生成成功'，跑第二遍影响 0 行。
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 修之前先看一眼（不改数据）
-- ---------------------------------------------------------------------------
SELECT mc.coupon_code,
       COUNT(*)                          AS `待修张数`,
       MAX(pc.prize_name)                AS `将回填成`,
       MIN(mc.create_time)               AS `最早`,
       MAX(mc.create_time)               AS `最晚`,
       GROUP_CONCAT(DISTINCT mc.status)  AS `状态`
  FROM t_member_coupon mc
  LEFT JOIN t_prize_config pc ON pc.prize_code = mc.coupon_code
 WHERE mc.coupon_name = '提案生成成功'
 GROUP BY mc.coupon_code;


-- ---------------------------------------------------------------------------
-- 回填
--
-- 🔴 用 JOIN 而不是 LEFT JOIN：查不到奖品配置的那些【不动】。
--    宁可留着「提案生成成功」这个难看的名字，也不要回填成 NULL 或者
--    编造一个 —— 前者至少还能看出是这批脏数据，后者会把问题藏起来。
-- ---------------------------------------------------------------------------
UPDATE t_member_coupon mc
  JOIN t_prize_config pc ON pc.prize_code = mc.coupon_code
   SET mc.coupon_name = pc.prize_name
 WHERE mc.coupon_name = '提案生成成功'
   AND pc.prize_name IS NOT NULL
   AND pc.prize_name <> '';


-- ---------------------------------------------------------------------------
-- 修完核对：这两个数都该是 0
-- ---------------------------------------------------------------------------
SELECT (SELECT COUNT(*) FROM t_member_coupon WHERE coupon_name = '提案生成成功')      AS `还剩几张没修`,
       (SELECT COUNT(*) FROM t_member_coupon WHERE coupon_name IS NULL OR coupon_name = '') AS `有没有被改成空`;
