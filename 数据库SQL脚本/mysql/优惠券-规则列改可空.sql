-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- ============================================================================
-- 优惠券 · 修阶段 1 留下的一个坑：规则快照列不该有默认值  2026-09-15
-- ============================================================================
--
-- 【问题】
--   阶段 1 给 t_member_coupon 加 8 个规则快照列时，为了能在一张非空表上加
--   NOT NULL 列，给了默认值：
--       discount_type NOT NULL DEFAULT 'FIXED'
--       discount_value NOT NULL DEFAULT 0
--       min_amount     NOT NULL DEFAULT 0
--       deduct_target  NOT NULL DEFAULT 'CASH'
--       scope_type     NOT NULL DEFAULT 'ALL'
--       template_version NOT NULL DEFAULT 1
--
--   后果是：一张【没有规则】的券，在库里长得像一张【无门槛减 0 的现金券】。
--   那正是阶段 2 的代码注释里明确说要避免的东西 ——
--   「填 0 会变成一张『无门槛减 0』的券，看起来配好了，实际上试算时减不出钱，
--     而且再也分不清是没配还是真配成了 0」。
--
--   代码那边确实留了 null（CouponIssueService 降级时一个规则字段都不 set），
--   但 MyBatis-Plus 的默认字段策略会把 null 字段整个从 INSERT 里省掉，
--   于是 MySQL 拿默认值填上 —— 代码的意图在库这一层被悄悄抹掉了。
--   这类不一致最难查：两边看单独看都对。
--
-- 【怎么改】
--   规则列一律改成可空、且不带默认值。降级发出去的券从此在库里也是 NULL，
--   阶段 3 的试算据此判「这张券没有规则，用不了」，而不是算出减 0 元。
--
--   max_discount / scope_refs 本来就是可空的，不动。
--
-- 【存量】
--   已经落成 0 的那 845 行不会因为这次 ALTER 变回 NULL —— 改列定义不改数据。
--   它们由「存量券作废重发」脚本处理（方案 §10.2 选 A）。
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/优惠券-规则列改可空.sql;
--   ⚠️ 一次性脚本，与阶段 1 的建表脚本同一个约定：不做幂等保护
--      （重复执行 MODIFY 到同样的定义是安全的，只是会重建一次表结构）。
--
-- 🔴 执行完必须重新导出结构基线：
--       cd 数据库SQL脚本/tools && java -cp <mysql-connector.jar> DumpSchema.java
-- ============================================================================

ALTER TABLE `t_member_coupon`
  MODIFY COLUMN `template_version` int           DEFAULT NULL COMMENT '发券时的模板版本，排查用。NULL=发券时没有模板',
  MODIFY COLUMN `discount_type`    varchar(16)   DEFAULT NULL COMMENT '规则快照：FIXED-固定金额/PERCENT-百分比。NULL=这张券没有规则',
  MODIFY COLUMN `discount_value`   decimal(10,2) DEFAULT NULL COMMENT '规则快照：抵扣额或折扣率。NULL=没有规则，不是减 0',
  MODIFY COLUMN `min_amount`       decimal(10,2) DEFAULT NULL COMMENT '规则快照：最低消费门槛。0=无门槛，NULL=没有规则',
  MODIFY COLUMN `deduct_target`    varchar(16)   DEFAULT NULL COMMENT '规则快照：CASH-抵现金/SCORE-抵积分。NULL=没有规则',
  MODIFY COLUMN `scope_type`       varchar(16)   DEFAULT NULL COMMENT '规则快照：ALL/COMMODITY/CATEGORY/EXTERNAL。NULL=没有规则';


-- ---------------------------------------------------------------------------
-- 核对：应当全部是 YES / NULL
-- ---------------------------------------------------------------------------
SELECT COLUMN_NAME   AS `列`,
       IS_NULLABLE   AS `可空`,
       COLUMN_DEFAULT AS `默认值`
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND table_name = 't_member_coupon'
   AND COLUMN_NAME IN ('template_version', 'discount_type', 'discount_value',
                       'min_amount', 'deduct_target', 'scope_type')
 ORDER BY ORDINAL_POSITION;
