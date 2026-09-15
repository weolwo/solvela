-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- ============================================================================
-- 优惠券 · 存量券作废重发（方案 §10.2 选 A）  2026-09-15
-- ============================================================================
--
-- 【要解决什么】
--   阶段 2 之前发出去的券【没有规则】：它们的真实规则只存在于券名字符串里
--   （「满100减20优惠券」）。阶段 3 的试算看不懂券名，只看规则列 ——
--   所以这些券在下单页会一律显示「用不了」。
--
-- 【为什么是作废重发，不是回填】
--   方案 §10.2 列了三个选项，产品选了 A：
--     A. 作废重发         —— 最干净，用户感知是「收到一张新券」；
--     B. 按券名解析回填   —— 「满100减20优惠券」能解析，但解析不了的怎么办？
--                            而且解析错一张就是一次资损，没人会发现；
--     C. 一律按无门槛固定额 —— 会把「满100减20」变成「无门槛减20」，
--                            **那就是资损**，不要。
--
-- 🔴 【怎么认出「没有规则」的券】
--   判据是 `discount_value = 0`。
--
--   这不是拍脑袋：CouponTemplateService.validate 要求 discount_value > 0，
--   所以【任何从模板发出来的券，抵扣值都不可能是 0】。库里那些 0 只可能来自
--   阶段 1 加列时的 DDL 默认值（NOT NULL DEFAULT 0，2026-09-15 已改成可空）。
--
--   反过来说，阶段 2 之后正常发出去的券不会被这个脚本碰到 ——
--   实测库里有一张 id=3601 是 13:06 由真实发奖链路发的，discount_value=20.00，
--   它不在处理范围内，这正是判据选对了的证据。
--
-- 🔴 【只处理有模板的券编码】
--   用 JOIN 而不是 LEFT JOIN。没有模板的券【原样留着，不作废】：
--   库里那张 XTYUJHUUI（华为音乐 音乐VIP 年卡）是一张**兑换凭证**，
--   本来就没有「减多少」这回事 —— 作废它等于拿走用户一件真实的东西，
--   而重发出来的还是一张没有规则的券。同样的 JOIN 纪律见阶段 0 的脏数据脚本。
--
-- 【范围】
--   只处理 status = 0（未使用）的。已过期的 598 张不用管 ——
--   它们本来就用不了，作废重发只会凭空给用户发一批新券。
--
-- 【溯源】
--   新券的 source_type = 'REISSUE'，source_biz_id = 旧券 id。
--   于是链路是：新券 → 旧券 → 原始提案/订单，客服一路查得回去。
--   ⚠️ source_type 没有枚举约束（见 MemberCouponMapper 注释），
--      新增这个取值只会让「来源分布」统计里多一个桶，那是对的。
--
-- 【有效期】
--   新券按模板重新起算（发券后 N 天 / 固定失效时间），
--   所以用户拿到的是一张**完整有效期**的新券，比旧券剩余的那点时间更长。
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/优惠券-存量券作废重发.sql;
--   可重复执行：重发时排除「已经给它重发过」的旧券，作废时要求重发行已存在。
--   跑第二遍影响 0 行。
--
-- ⚠️ 顺序不能反：先发新券，再作废旧券。反过来的话第一步就把判据
--    （status = 0）改没了，新券一张也发不出来，而且没有任何报错。
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 0. 动手之前先看清楚要改多少
-- ---------------------------------------------------------------------------
SELECT mc.coupon_code                                      AS `券编码`,
       MIN(mc.coupon_name)                                 AS `券名`,
       COUNT(*)                                            AS `待作废重发`,
       COUNT(DISTINCT mc.member_id)                        AS `涉及会员`,
       MAX(t.coupon_name)                                  AS `新券将叫`,
       MAX(CONCAT(t.discount_type, ' ', t.discount_value)) AS `新券规则`
  FROM t_member_coupon mc
  JOIN t_coupon_template t
    ON t.coupon_code = mc.coupon_code AND t.status = 1
 WHERE mc.status = 0
   AND mc.discount_value = 0
 GROUP BY mc.coupon_code;

-- 有多少张因为没有模板而【不动】（正常情况：兑换凭证类的券）
SELECT coupon_code AS `券编码`, coupon_name AS `券名`, COUNT(*) AS `不动`
  FROM t_member_coupon mc
 WHERE mc.status = 0
   AND mc.discount_value = 0
   AND NOT EXISTS (SELECT 1 FROM t_coupon_template t
                    WHERE t.coupon_code = mc.coupon_code AND t.status = 1)
 GROUP BY coupon_code, coupon_name;


-- ---------------------------------------------------------------------------
-- 1. 先发新券
--
-- ⚠️ MySQL 不允许在 INSERT ... SELECT 的子查询里直接引用目标表，
--    所以判重那段包了一层 (SELECT * FROM t_member_coupon) —— 与本仓其它
--    幂等脚本同一个写法。
-- ---------------------------------------------------------------------------
INSERT INTO `t_member_coupon`
    (`member_id`, `member_name`, `coupon_code`, `coupon_type`, `coupon_name`, `status`,
     `source_type`, `source_biz_id`, `valid_start_time`, `valid_end_time`,
     `template_version`, `discount_type`, `discount_value`, `min_amount`, `max_discount`,
     `deduct_target`, `scope_type`, `scope_refs`, `create_by`)
SELECT mc.member_id,
       mc.member_name,
       mc.coupon_code,
       'GENERAL',
       t.coupon_name,
       0,
       'REISSUE',
       mc.id,
       NOW(),
       -- 模板保存时校验过「两种有效期二选一」，所以这里 COALESCE 不会两个都空
       COALESCE(DATE_ADD(NOW(), INTERVAL t.valid_days DAY), t.valid_end_time),
       t.version,
       t.discount_type,
       t.discount_value,
       t.min_amount,
       t.max_discount,
       t.deduct_target,
       t.scope_type,
       t.scope_refs,
       'system-reissue'
  FROM t_member_coupon mc
  JOIN t_coupon_template t
    ON t.coupon_code = mc.coupon_code AND t.status = 1
 WHERE mc.status = 0
   AND mc.discount_value = 0
   AND NOT EXISTS (
        SELECT 1 FROM (SELECT * FROM `t_member_coupon`) x
         WHERE x.`source_type` = 'REISSUE'
           AND x.`source_biz_id` = mc.id
   );


-- ---------------------------------------------------------------------------
-- 2. 再作废旧券
--
-- 条件里要求「重发行已经存在」—— 这样万一第 1 步只成功了一部分，
-- 第 2 步也只会作废那些真的拿到了新券的。
-- 宁可留下几张没处理的旧券（下次再跑），也不要作废掉没有补偿的券。
-- ---------------------------------------------------------------------------
UPDATE `t_member_coupon` mc
   SET mc.`status`    = 3,
       mc.`update_by` = 'system-reissue'
 WHERE mc.`status` = 0
   AND mc.`discount_value` = 0
   AND EXISTS (
        SELECT 1 FROM (SELECT * FROM `t_member_coupon`) x
         WHERE x.`source_type` = 'REISSUE'
           AND x.`source_biz_id` = mc.`id`
   );


-- ---------------------------------------------------------------------------
-- 3. 核对
-- ---------------------------------------------------------------------------
-- 这个数该是 0：有模板、未使用、却还没有规则的券
SELECT COUNT(*) AS `还有几张没规则的可用券`
  FROM t_member_coupon mc
  JOIN t_coupon_template t
    ON t.coupon_code = mc.coupon_code AND t.status = 1
 WHERE mc.status = 0
   AND mc.discount_value = 0;

-- 重发结果一览
SELECT coupon_code    AS `券编码`,
       coupon_name    AS `券名`,
       status         AS `状态`,
       source_type    AS `来源`,
       COUNT(*)       AS `张数`,
       MIN(discount_value) AS `抵扣值`,
       MIN(min_amount)     AS `门槛`
  FROM t_member_coupon
 GROUP BY coupon_code, coupon_name, status, source_type
 ORDER BY coupon_code, status;
