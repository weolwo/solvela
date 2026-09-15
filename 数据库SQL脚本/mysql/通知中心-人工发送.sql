-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- ============================================================================
-- 通知中心 · 人工发送  2026-09-15
-- ============================================================================
--
-- 【补的缺口】
--   管理端此前只有两个入口：公告（广播给所有人）与模板（定义系统触发的措辞），
--   没有「发给某个人」—— 客服想给一个会员补一句说明，只能去改库。
--
-- 【MANUAL 模板：唯一一个正文由发送方现填的模板】
--   占位符就是 ${title} 和 ${content}，真正的文字落在 params 里。
--
--   这看着像绕开了模板化，其实不是：模板化要省的是「同一段文字存 N 遍」，
--   而人工发送一次只面向个位数到几百个会员，那段文字本来就没有重复可消除。
--   为它单开一张带 content 列的表、或者给 t_member_notification 加一个常年
--   为空的 content 列，都是更差的选择 —— 多一套存储模型，而省不下任何东西。
--
-- 🔴 归在 SYSTEM 意味着用户关不掉。人工触达通常是「针对你这个人的事」
--    （工单答复、账号说明、补偿通知），不该被免打扰静音。
--    代价是它成了一条绕过免打扰的路，挡这件事靠留痕（create_by）+
--    单次收件人硬上限（NotificationAdminService.MANUAL_MAX_RECIPIENTS = 200）。
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/通知中心-人工发送.sql;
--   判存插入，可重复执行。
-- ============================================================================

INSERT INTO `t_notification_template`
  (`template_code`, `version`, `category`, `title_template`, `content_template`, `param_keys`, `status`, `create_by`)
SELECT
  'MANUAL', 1, 'SYSTEM',
  '${title}',
  '${content}',
  JSON_ARRAY('title', 'content'), 1, 'system'
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_notification_template`) m
   WHERE m.`template_code` = 'MANUAL' AND m.`version` = 1
);


-- ----------------------------------------------------------------------------
-- 菜单与权限
-- ----------------------------------------------------------------------------
SET @notification_catalog_id =
  (SELECT `menu_id` FROM `t_menu` WHERE `menu_type` = 1 AND `menu_name` = '消息中心' AND `deleted_flag` = 0 LIMIT 1);

INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT '人工发送', 2, @notification_catalog_id, 3, '/notification/manual-notify/send',
       '/business/notification/manual-notify.vue', 1, NULL, NULL,
       NULL, NULL, 0, NULL, 0, 1, 0, 0, 1
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_menu`) m
   WHERE m.`path` = '/notification/manual-notify/send' AND m.`deleted_flag` = 0
);

SET @manual_menu_id =
  (SELECT `menu_id` FROM `t_menu` WHERE `path` = '/notification/manual-notify/send' AND `deleted_flag` = 0 LIMIT 1);

-- 🔴 单独一个权限点，不和公告/模板合并：它能【直接给指定用户发消息】，
--    而且 MANUAL 模板是 SYSTEM 分类、用户关不掉。这个能力该单独授予。
INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT '发送', 3, @manual_menu_id, 1, NULL, NULL, 1,
       'manualNotify:send', 'manualNotify:send', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_menu`) m
   WHERE m.`api_perms` = 'manualNotify:send' AND m.`deleted_flag` = 0
);
