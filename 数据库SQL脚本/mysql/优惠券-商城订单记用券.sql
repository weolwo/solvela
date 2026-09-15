-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- ⚠️ 本脚本不带 NOW()/CURDATE()，不受 README 里那个时区坑影响。
--    但带时间的脚本执行前请先 SELECT NOW(), @@session.time_zone; 确认一下。

-- ============================================================================
-- 优惠券 · 商城订单记下用了哪张券  2026-09-15（阶段 4）
-- ============================================================================
--
-- 【为什么订单上要记】
--   券的核销流水（t_coupon_write_off）能按单号反查「这一单用了哪张券」，
--   那是**对账**方向。但订单详情页要正着显示「原价 5000 分，券减 1000 分，
--   实付 4000 分」—— 为这一行去 join 一张流水表，等于把每次看订单都变成一次对账。
--
--   和 t_member_coupon.discount_amount 是同一个理由：
--   **权威在流水，这里是冗余，为的是零 join 显示。**
--
-- 🔴 【pay_points 是抵扣后的实付，不要再改它的语义】
--   原价从 points_price * quantity 就能算出来，所以不另加一列存原价 ——
--   多一列就多一处会不一致的地方，而这三个数之间是有恒等式的：
--       points_price * quantity - coupon_discount = pay_points
--   体检 SQL 可以直接按这个式子找出对不上的单。
--
-- 【coupon_id 是软引用，不加外键】
--   与 address_id 同一个做法。券行永远不会被物理删除（账务流水只增不改），
--   所以悬空的风险本来就不存在；加外键换来的只是一次跨表锁。
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/优惠券-商城订单记用券.sql;
--   ⚠️ 一次性脚本，与本仓其它 ALTER 同一个约定：不做幂等保护。
--
-- 🔴 执行完必须重新导出结构基线：
--       cd 数据库SQL脚本/tools && java -cp <mysql-connector.jar> DumpSchema.java
-- ============================================================================

ALTER TABLE `t_mall_order`
  ADD COLUMN `coupon_id`       bigint        DEFAULT NULL COMMENT '用掉的会员券 id。软引用，不加外键。NULL=这一单没用券'
    AFTER `pay_cash`,
  ADD COLUMN `coupon_discount` decimal(10,2) DEFAULT NULL COMMENT '券抵扣了多少（阶段 4 只抵积分）。冗余，权威在 t_coupon_write_off；为订单详情零 join 显示';


-- ---------------------------------------------------------------------------
-- 核对
-- ---------------------------------------------------------------------------
SELECT COLUMN_NAME    AS `列`,
       COLUMN_TYPE    AS `类型`,
       IS_NULLABLE    AS `可空`,
       COLUMN_COMMENT AS `注释`
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND table_name = 't_mall_order'
   AND COLUMN_NAME IN ('coupon_id', 'coupon_discount');
