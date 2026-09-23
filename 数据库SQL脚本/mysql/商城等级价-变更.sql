-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文注释会整片乱码）。
SET NAMES utf8mb4;

-- =====================================================================================
-- 商城等级价：同一件商品，等级越高积分价越低
-- 2026-09-23
--
-- 【模型】每档等级一个积分折扣率，商品可单独退出。
--   不是「每商品 × 每等级」的价格矩阵 —— 那张表运营要维护 N×M 行，
--   而漏配的表现是「这件商品白金没有优惠」，不报错、没人发现。
--   折扣率只有一处配置，新上架的商品自动就有等级价。
--
-- 🔴 【只打积分，不打现金】
--   积分是平台自己发的，打折是自己的事；现金是真钱，打折牵扯支付金额、
--   退款、发票和税务口径。所以 cash_price 这一侧完全不动。
--
-- 🔴 【订单恒等式变成三项】
--   原先：points_price × quantity - coupon_discount = pay_points
--   现在：points_price × quantity - grade_discount - coupon_discount = pay_points
--   points_price 仍然是【挂牌单价】，含义没变 —— 历史订单的语义因此原样成立，
--   而且新加的两列 DEFAULT 0，老单代进新恒等式照样平。
--   刻意不把 points_price 改成「折后单价」：那样恒等式不用改，
--   但「这单等级让了多少分」就永远查不出来了，而那笔钱是真让出去的。
-- =====================================================================================

-- 1. 等级配置：积分折扣率 -------------------------------------------------------------
ALTER TABLE `t_member_grade`
    ADD COLUMN `points_discount` tinyint unsigned NULL
        COMMENT '积分折扣率 1-100，如 90=9折；NULL 或 100 表示不打折'
        AFTER `threshold`;

/*
 * 🔴 允许 NULL 而不是 DEFAULT 100。
 *   这一列和 t_mall_sku 的两个继承价是同一个道理：NULL 是「没配」，
 *   100 是「明确配了不打折」。两者在业务上没区别，但在【运营看后台】时有 ——
 *   一片空白意味着「这套还没开」，一片 100 意味着「开了，都是原价」。
 *
 * ⚠️ 不给 0：0 = 白送。折扣率是手填的，填 0 几乎一定是笔误，
 *   而它的后果是全场对这一档白送。校验在 MemberGradeConfigService，这里只是不给默认值。
 */

-- 2. 商品：参不参与等级折扣 -----------------------------------------------------------
ALTER TABLE `t_mall_commodity`
    ADD COLUMN `grade_price_flag` tinyint NOT NULL DEFAULT 1
        COMMENT '参与等级折扣：0-不参与（成本价/秒杀品）, 1-参与'
        AFTER `min_grade`;

/*
 * ⚠️ 默认 1（参与），不是 0。
 *   默认不参与的话，配了折扣率之后【一件商品都不会变便宜】，
 *   而运营会以为功能没生效去提 bug。默认参与，个别商品再退出。
 */

-- 3. 订单：等级快照与让利金额 ---------------------------------------------------------
ALTER TABLE `t_mall_order`
    ADD COLUMN `grade_code` int NOT NULL DEFAULT 0
        COMMENT '下单时的等级快照。0=无等级或未登录' AFTER `cash_price`,
    ADD COLUMN `grade_discount` int NOT NULL DEFAULT 0
        COMMENT '等级折扣让掉的积分（合计，非单价）。0=没享受折扣' AFTER `grade_code`;

/*
 * 🔴 grade_code 必须快照，不能事后 join t_member_growth 反查。
 *   等级是会变的 —— 这一单下的时候他是白金，下个月掉到黄金，
 *   反查出来的「让了多少」就对不上了。对账要回答的是「当时是谁、当时几折」。
 *
 * ⚠️ 只存 grade_code 不存 points_discount：折扣率能从 grade_discount 与
 *   points_price × quantity 反推，存两份就多一处会不一致的地方
 *   （同 coupon_discount 那条注释的理由）。
 */

-- 4. 种子：给现有等级配上折扣率 -------------------------------------------------------
--   0 档不打折（新会员没有理由先享优惠，那样等级就不值钱了）
UPDATE `t_member_grade` SET `points_discount` = 100 WHERE `grade_code` = 0;
UPDATE `t_member_grade` SET `points_discount` = 98  WHERE `grade_code` = 1;
UPDATE `t_member_grade` SET `points_discount` = 95  WHERE `grade_code` = 2;
UPDATE `t_member_grade` SET `points_discount` = 92  WHERE `grade_code` = 3;
UPDATE `t_member_grade` SET `points_discount` = 88  WHERE `grade_code` = 4;
