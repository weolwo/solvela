-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- ⚠️ 本脚本不带 NOW()/CURDATE()，不受 README 里那个时区坑影响。

-- ============================================================================
-- 优惠券 · 人工发券（阶段 5）  2026-09-15
-- ============================================================================
--
-- 三件事：
--   1. 给人工发券加一道**数据库级**的防重；
--   2. 加一条「收到券」的通知模板；
--   3. 加管理端菜单与权限。
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. 🔴 人工发券的防重：一个【只对 MANUAL 生效】的唯一索引
--
-- 【为什么不能直接 UNIQUE(source_type, source_biz_id)】
--   库里【已经有 53 组重复】（全是 PROPOSAL，来自早期造数脚本，
--   同一个 source_biz_id 落在了不同会员身上）。直接加唯一键会建不出来。
--
--   ⚠️ 顺带暴露了一件事：CouponAssetHandler 里那段 catch DuplicateKeyException
--      的「防重拦截」**从来没有生效过** —— 那张表上压根没有唯一键可违反
--      （只有普通索引 idx_source）。发奖那条路的幂等目前实际依赖的是
--      派发引擎自己的状态机，不是这里。这条留给后续单独处理，
--      本脚本不动存量。
--
-- 【所以用函数索引做「部分唯一」】
--   CASE WHEN source_type='MANUAL' THEN source_biz_id END
--   —— 非 MANUAL 的行算出 NULL，而唯一索引允许多个 NULL，于是它们不受影响；
--      MANUAL 的行则被真正挡住。MySQL 8.0.13+ 支持函数索引，本项目是 8.4。
--
-- 【为什么人工发券值得这一道，自动发券不值】
--   人工发券是【一个人在后台点按钮】，最真实的故障就是双击 / 网络慢了再点一次。
--   而它直接对应钱：多发一张就是多送一次钱，且没人会发现。
--   自动发券那条路有派发引擎的状态机兜着，形状不一样。
--
--   幂等键是 source_biz_id = '<工单号>:<序号>'，序号让「一次发 3 张」也能唯一，
--   格式与商城发券（AssetGrantApiService）一致，都能用 LIKE '单号:%' 反查。
-- ---------------------------------------------------------------------------
ALTER TABLE `t_member_coupon`
  ADD UNIQUE KEY `uk_manual_src` ((CASE WHEN `source_type` = 'MANUAL' THEN `source_biz_id` END));


-- ---------------------------------------------------------------------------
-- 2. 通知模板：收到一张人工发的券
--
-- 🔴 发了必须告诉用户。券静悄悄躺进券包的话，客服为一次投诉补的那张券
--    用户根本不知道 —— 补偿没有起到补偿的作用，他还会再投诉一次。
--
-- 分类是 MARKETING 不是 SYSTEM：它是一次营销/补偿动作，用户【可以】关掉。
-- SYSTEM 那一档要留给账号安全这种「关掉就出事」的消息。
-- ---------------------------------------------------------------------------
INSERT INTO `t_notification_template`
    (`template_code`, `version`, `category`, `title_template`, `content_template`,
     `param_keys`, `status`, `create_by`)
SELECT 'COUPON_GRANTED', 1, 'MARKETING', '您收到一张优惠券',
       '您收到一张「${couponName}」。${reason}有效期至 ${validEndTime}，记得在券包里查看。',
       '["couponName","reason","validEndTime"]', 1, 'system'
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_notification_template`) t
   WHERE t.`template_code` = 'COUPON_GRANTED' AND t.`version` = 1
);


-- ---------------------------------------------------------------------------
-- 3. 管理端菜单与权限：挂在「财务中心」下，和券模板并排
--
-- 🔴 权限点单独一个 manualCoupon:send，不并进 couponTemplate:save。
--    配规则和【直接给用户发钱】是两件要分别授予的事 ——
--    这和公告那边把「删除（含确认留痕）」单列出来是同一条道理。
-- ---------------------------------------------------------------------------
SET @ledger_catalog_id =
  (SELECT `menu_id` FROM `t_menu` WHERE `menu_type` = 1 AND `menu_name` = '财务中心' AND `deleted_flag` = 0 LIMIT 1);

INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT '人工发券', 2, @ledger_catalog_id, 5, '/ledger/manual-coupon',
       '/business/ledger/coupon-template/manual-coupon.vue', 1, NULL, NULL,
       'SendOutlined', NULL, 0, NULL, 1, 1, 0, 0, 1
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_menu`) m
   WHERE m.`path` = '/ledger/manual-coupon' AND m.`deleted_flag` = 0
);

SET @manual_coupon_menu_id =
  (SELECT `menu_id` FROM `t_menu` WHERE `path` = '/ledger/manual-coupon' AND `deleted_flag` = 0 LIMIT 1);

INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT '发券', 3, @manual_coupon_menu_id, 1, NULL, NULL, 1,
       'manualCoupon:send', 'manualCoupon:send', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_menu`) m
   WHERE m.`api_perms` = 'manualCoupon:send' AND m.`deleted_flag` = 0
);


-- ---------------------------------------------------------------------------
-- 核对
-- ---------------------------------------------------------------------------
SELECT INDEX_NAME AS `索引`, NON_UNIQUE AS `非唯一`
  FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 't_member_coupon'
   AND INDEX_NAME = 'uk_manual_src';

SELECT `template_code` AS `模板`, `version` AS `版本`, `category` AS `分类`
  FROM `t_notification_template` WHERE `template_code` = 'COUPON_GRANTED';

SELECT `menu_name` AS `菜单`, `api_perms` AS `权限点`
  FROM `t_menu` WHERE `path` = '/ledger/manual-coupon' OR `api_perms` = 'manualCoupon:send';
