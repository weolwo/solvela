-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文注释会整片乱码）。
SET NAMES utf8mb4;

-- ============================================================================
-- 优惠券 · 阶段 1：券模板 + 核销流水 + 会员券加列  2026-09-15
-- ============================================================================
--
-- 完整设计见 docs/优惠券使用闭环-实现技术方案.md。
--
-- 【它解决什么】
--   券现在【没有表达能力】：t_member_coupon 一个面额/折扣率/门槛/封顶字段都没有，
--   「满100减20」这条规则系统唯一知道它的地方是【券的名字字符串】。
--   而券模板表根本不存在 —— coupon_code 的列注释写着「券模编码」，指向空气。
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/优惠券-券模板与核销流水.sql;
--
--   ⚠️ 建表与种子可重复执行，但【第 3 段的 ALTER 只能跑一次】——
--      跟随本项目既有增量脚本的惯例（不做判存），跑第二遍会报 Duplicate column name。
--      新环境不需要跑本文件：列已经在 schema-baseline.sql 里了。
--
-- 🔴 改完表结构记得重新导出基线：
--   cd 数据库SQL脚本/tools && java DumpSchema.java
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1. 券模板：规则住这里，按 (coupon_code, version) 不可变
-- ----------------------------------------------------------------------------
--
-- 🔴 改规则 = 新增一个 version，永远不要 UPDATE 已有行。
--
--    和通知模板是同一个道理，但后果更重：用户手里那张「满100减20」，
--    运营把模板改成「满200减20」之后，如果核销时读的是模板当前值，
--    【用户手里的券就贬值了】。那不是显示问题，是资损与信任问题。
--
--    所以发券时还要把规则【快照】进 t_member_coupon（见第 3 段），
--    核销只读会员券行、永远不碰模板。模板留着版本是为了让运营能回答
--    「这张券当时是什么规则」。
--
CREATE TABLE IF NOT EXISTS `t_coupon_template` (
  `coupon_code`    varchar(64)   NOT NULL COMMENT '券模编码：跨环境稳定，发券时引用它',
  `version`        int           NOT NULL COMMENT '版本号。🔴 改规则=新增版本，永不原地改',
  `coupon_name`    varchar(128)  NOT NULL COMMENT '券名，如「满100减20」',
  `discount_type`  varchar(16)   NOT NULL COMMENT 'FIXED-固定金额 / PERCENT-百分比',
  `discount_value` decimal(10,2) NOT NULL COMMENT 'FIXED=抵扣额；PERCENT=折扣率(20 表示减20%)',
  `min_amount`     decimal(10,2) NOT NULL DEFAULT 0 COMMENT '最低消费门槛，0=无门槛。低于它这张券用不了',
  `max_discount`   decimal(10,2) DEFAULT NULL COMMENT '最高抵扣。🔴 PERCENT 必填 —— 不设上限的「8折」碰上一台iPhone就是资损',
  `deduct_target`  varchar(16)   NOT NULL COMMENT 'CASH-抵现金 / SCORE-抵积分。🔴 两者不可比，别跨类选最优',
  `scope_type`     varchar(16)   NOT NULL DEFAULT 'ALL' COMMENT 'ALL/COMMODITY/CATEGORY/EXTERNAL',
  `scope_refs`     json          DEFAULT NULL COMMENT '范围明细。ALL 时为空。CATEGORY 优于 COMMODITY——绑商品id每上新品都要改券',
  `valid_days`     int           DEFAULT NULL COMMENT '发券后N天过期。与 valid_end_time 二选一',
  `valid_end_time` datetime      DEFAULT NULL COMMENT '固定失效时间（如活动结束）。与 valid_days 二选一',
  `remark`         varchar(255)  DEFAULT NULL COMMENT '运营备注',
  `status`         tinyint       NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用。🔴 只停用不删除：删了历史券查不到当时的规则',
  `create_by`      varchar(64)   DEFAULT NULL COMMENT '创建人',
  `create_time`    datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      varchar(64)   DEFAULT NULL COMMENT '更新人',
  `update_time`    datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`coupon_code`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板：规则按版本不可变';


-- ----------------------------------------------------------------------------
-- 2. 核销流水：只增不改
-- ----------------------------------------------------------------------------
--
-- 🔴 这张表补的是一个会在上线三个月后才暴露的洞。
--
--    只在 t_member_coupon 上改状态的话，【状态列只能表达「现在是什么」，
--    表达不了「发生过什么」】：券被锁定、订单取消、券释放回去 ——
--    那次锁定的痕迹一点不剩，用户来问「我的券刚才还能用」时查无对证。
--
--    更要命的是【实际减了多少没人记】。规则是「8折最高减50」，真正减了多少
--    取决于订单金额。不落地的话：退款不知道该退多少、「本期核销金额」算不出来
--    （管理端现有的 stat 里那个口径至今为 0）、财务对不了账。
--
--    三个动作（LOCK/CONFIRM/RELEASE）各记一行，不是只记核销成功那次 ——
--    券的纠纷恰恰大多发生在锁定到释放那个窗口里。
--
CREATE TABLE IF NOT EXISTS `t_coupon_write_off` (
  `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT 'id',
  `coupon_id`       bigint        NOT NULL COMMENT '会员券 id',
  `member_id`       bigint        NOT NULL COMMENT '会员号。冗余一列，按人查时不用回表',
  `action`          varchar(16)   NOT NULL COMMENT 'LOCK-锁定 / CONFIRM-核销 / RELEASE-释放',
  `biz_type`        varchar(32)   NOT NULL COMMENT 'MALL-商城订单 / EXTERNAL-外部场景',
  `biz_ref_id`      varchar(64)   NOT NULL COMMENT '订单号 / 外部单号',
  `scene_code`      varchar(32)   DEFAULT NULL COMMENT '外部场景码，如 MOBILE_RECHARGE。biz_type=MALL 时为空',
  `original_amount` decimal(10,2) NOT NULL COMMENT '抵扣前应付',
  `discount_amount` decimal(10,2) NOT NULL COMMENT '🔴 本次实际抵扣额。退款要按它退，财务要按它对账',
  `deduct_target`   varchar(16)   NOT NULL COMMENT 'CASH / SCORE 快照',
  `remark`          varchar(255)  DEFAULT NULL COMMENT '释放原因等',
  `create_by`       varchar(64)   DEFAULT NULL COMMENT '操作人。人工核销才有值',
  `create_time`     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  -- 一张券的完整时间线：客服拿着券号要回答「它经历了什么」
  KEY `idx_coupon` (`coupon_id`, `id`),
  -- 🔴 对账用：按订单号反查「这一单用了哪张券、减了多少」
  KEY `idx_biz` (`biz_type`, `biz_ref_id`),
  -- 按期统计与将来的归档
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券核销流水：只增不改';


-- ----------------------------------------------------------------------------
-- 3. 会员券加列：规则快照 + 锁定
-- ----------------------------------------------------------------------------
--
-- 🔴 快照列给了 DEFAULT，是为了让存量 845 张券能【就地升级】，
--    而不是让新代码依赖这些默认值。存量券的真实规则只存在于券名字符串里
--    （「满100减20优惠券」），处理方案见技术方案 §10.2 —— 倾向「未使用的作废重发」。
--
--    ⚠️ 绝不要把存量券按「无门槛固定额」兜底：那会把「满100减20」
--       变成「无门槛减20」，是实打实的资损。
--
-- ⚠️ 这一段【只能执行一次】。跟随本项目既有增量脚本的惯例（见 文件分类-公开标记.sql
--    等）：ALTER 不做判存，跑第二遍会报 Duplicate column name。
--    新环境不需要跑它 —— 列已经在 schema-baseline.sql 里了，那才是新环境的入口。
--
ALTER TABLE `t_member_coupon`
  ADD COLUMN `template_version` int           NOT NULL DEFAULT 1       COMMENT '发券时的模板版本，排查用',
  ADD COLUMN `discount_type`    varchar(16)   NOT NULL DEFAULT 'FIXED' COMMENT '规则快照：FIXED-固定金额/PERCENT-百分比',
  ADD COLUMN `discount_value`   decimal(10,2) NOT NULL DEFAULT 0       COMMENT '规则快照：抵扣额或折扣率',
  ADD COLUMN `min_amount`       decimal(10,2) NOT NULL DEFAULT 0       COMMENT '规则快照：最低消费门槛，0=无门槛',
  ADD COLUMN `max_discount`     decimal(10,2) DEFAULT NULL             COMMENT '规则快照：最高抵扣。PERCENT 必填',
  ADD COLUMN `deduct_target`    varchar(16)   NOT NULL DEFAULT 'CASH'  COMMENT '规则快照：CASH-抵现金/SCORE-抵积分',
  ADD COLUMN `scope_type`       varchar(16)   NOT NULL DEFAULT 'ALL'   COMMENT '规则快照：ALL/COMMODITY/CATEGORY/EXTERNAL',
  ADD COLUMN `scope_refs`       json          DEFAULT NULL             COMMENT '规则快照：范围明细',
  ADD COLUMN `locked_biz_id`    varchar(64)   DEFAULT NULL COMMENT '锁定它的单据号。兜底释放与幂等都靠它',
  ADD COLUMN `locked_time`      datetime      DEFAULT NULL COMMENT '锁定时间，兜底 job 按它判超时',
  ADD COLUMN `discount_amount`  decimal(10,2) DEFAULT NULL COMMENT '本次实际抵扣额。核销写、释放清。权威在流水表，这里冗余给券包列表零 join',
  -- C 端券包与「可用券列表」的主查询。valid_end_time 放第三位是因为
  -- 「未过期」是范围条件，必须排在等值条件之后
  ADD KEY `idx_member_status` (`member_id`, `status`, `valid_end_time`),
  -- 只给兜底释放 job 用：找「锁定中且锁了太久」的
  ADD KEY `idx_locked` (`status`, `locked_time`);


-- ----------------------------------------------------------------------------
-- 4. 券模板种子：把库里已有的 5 个券类奖品配置提成模板
-- ----------------------------------------------------------------------------
--
-- 这几条编码是 t_prize_config 里 prize_type=COUPON 的 prize_code，
-- 发券链路（ProposalPrizeDispatcher）今天传的 assetRef 就是它们 ——
-- 所以模板用同样的编码，发券侧改成读模板时不用动上游。
--
-- ⚠️ 规则是【按券名反推的】：库里那些券的真实规则只存在于名字里
--    （「满100减20优惠券」）。所以下面的 min_amount/discount_value
--    要运营核对一遍再用于发新券。
--
INSERT INTO `t_coupon_template`
  (`coupon_code`, `version`, `coupon_name`, `discount_type`, `discount_value`,
   `min_amount`, `max_discount`, `deduct_target`, `scope_type`, `valid_days`, `remark`, `status`, `create_by`)
SELECT * FROM (
  SELECT '0ZXXLZ0RZ1' AS a,1 AS b,'满100减20优惠券' AS c,'FIXED' AS d,20.00 AS e,100.00 AS f,NULL AS g,'CASH' AS h,'ALL' AS i,30 AS j,'按券名反推，上线前请运营核对' AS k,1 AS l,'system' AS m
  UNION ALL SELECT 'CU4XN6VTLQ',1,'满100减20优惠券','FIXED',20.00,100.00,NULL,'CASH','ALL',30,'按券名反推，上线前请运营核对',1,'system'
  UNION ALL SELECT 'PP0COUPON1',1,'P0-20元券','FIXED',20.00,0.00,NULL,'CASH','ALL',30,'按券名反推，上线前请运营核对',1,'system'
  UNION ALL SELECT 'PNIX3HHMDN',1,'商城优惠券100','FIXED',100.00,0.00,NULL,'SCORE','ALL',30,'按券名反推，上线前请运营核对',1,'system'
  UNION ALL SELECT 'PK144782FR',1,'商城优惠券100','FIXED',100.00,0.00,NULL,'SCORE','ALL',30,'按券名反推，上线前请运营核对',1,'system'
) AS seed
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_coupon_template`) m WHERE m.`coupon_code` = seed.a AND m.`version` = seed.b
);
