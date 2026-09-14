-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 菜单名全部变成乱码。
SET NAMES utf8mb4;

-- ============================================================================
-- 通知中心 · 管理端菜单与权限  2026-09-14
-- ============================================================================
--
-- 【为什么必须有这个脚本】
--   管理端是菜单驱动的：一个页面在 t_menu 里没有记录，前端就压根没有那条路由。
--   .vue 文件写得再对，没有菜单行也进不去。
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/通知中心-菜单与权限.sql;
--   可重复执行：按 path / api_perms 判存（并排除软删），跑第二遍不会产生重复菜单。
--
-- 🔴 执行完必须重新导出种子数据基线，否则这次改动只存在于你这一台机器上：
--       cd 数据库SQL脚本/tools && java -cp <mysql-connector.jar> DumpSeedData.java
--       git diff 数据库SQL脚本/mysql/data-baseline.sql
--
-- 【授权】
--   超级管理员不需要授权（RoleMenuService.getMenuList 对 administratorFlag 直接返回全部菜单）。
--   其他角色要到「权限管理 → 角色管理」里勾选，本脚本刻意不写 t_role_menu ——
--   往别人的角色里塞权限是越权，得由管理员自己决定给谁。
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. 目录：消息中心
-- ---------------------------------------------------------------------------
INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT '消息中心', 1, 0, 80, NULL, NULL, 1, NULL, NULL, 'MessageOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_menu`) m
   WHERE m.`menu_type` = 1 AND m.`menu_name` = '消息中心' AND m.`deleted_flag` = 0
);

SET @notification_catalog_id =
  (SELECT `menu_id` FROM `t_menu` WHERE `menu_type` = 1 AND `menu_name` = '消息中心' AND `deleted_flag` = 0 LIMIT 1);


-- ---------------------------------------------------------------------------
-- 2. 通知模板
--
--    🔴 页面上没有「删除」，也没有「修改」——两者都是刻意的，而且服务端也没有对应接口：
--      · 模板按 (code, version) 不可变，编辑 = 新增下一版。原地改会追溯篡改
--        所有历史通知（它们只存版本号，正文是读的时候现渲染的）；
--      · 只停用不删除。删了一行，指向它的历史通知就渲染不出来了。
--    所以权限点只有 query 和 save 两个。
-- ---------------------------------------------------------------------------
INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT '通知模板', 2, @notification_catalog_id, 1, '/notification/notification-template/list',
       '/business/notification/notification-template-list.vue', 1, NULL, NULL,
       NULL, NULL, 0, NULL, 1, 1, 0, 0, 1
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_menu`) m
   WHERE m.`path` = '/notification/notification-template/list' AND m.`deleted_flag` = 0
);

SET @template_menu_id =
  (SELECT `menu_id` FROM `t_menu` WHERE `path` = '/notification/notification-template/list' AND `deleted_flag` = 0 LIMIT 1);

INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT '查询', 3, @template_menu_id, 1, NULL, NULL, 1,
       'notificationTemplate:query', 'notificationTemplate:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_menu`) m
   WHERE m.`api_perms` = 'notificationTemplate:query' AND m.`deleted_flag` = 0
);

INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT '新增版本 / 停用', 3, @template_menu_id, 2, NULL, NULL, 1,
       'notificationTemplate:save', 'notificationTemplate:save', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_menu`) m
   WHERE m.`api_perms` = 'notificationTemplate:save' AND m.`deleted_flag` = 0
);


-- ---------------------------------------------------------------------------
-- 3. 公告
--
--    ⚠️ delete 是【物理删除，连同确认记录】。强制确认公告的确认记录是合规留痕，
--       所以它单独一个权限点，不和 save 合并 —— 让「能编辑」和「能销毁留痕」
--       是两件要分别授予的事。
-- ---------------------------------------------------------------------------
INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT '公告管理', 2, @notification_catalog_id, 2, '/notification/announcement/list',
       '/business/notification/announcement-list.vue', 1, NULL, NULL,
       NULL, NULL, 0, NULL, 1, 1, 0, 0, 1
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_menu`) m
   WHERE m.`path` = '/notification/announcement/list' AND m.`deleted_flag` = 0
);

SET @announcement_menu_id =
  (SELECT `menu_id` FROM `t_menu` WHERE `path` = '/notification/announcement/list' AND `deleted_flag` = 0 LIMIT 1);

INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT '查询', 3, @announcement_menu_id, 1, NULL, NULL, 1,
       'announcement:query', 'announcement:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_menu`) m
   WHERE m.`api_perms` = 'announcement:query' AND m.`deleted_flag` = 0
);

INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT '编辑 / 下架', 3, @announcement_menu_id, 2, NULL, NULL, 1,
       'announcement:save', 'announcement:save', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_menu`) m
   WHERE m.`api_perms` = 'announcement:save' AND m.`deleted_flag` = 0
);

INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT '删除（含确认留痕）', 3, @announcement_menu_id, 3, NULL, NULL, 1,
       'announcement:delete', 'announcement:delete', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_menu`) m
   WHERE m.`api_perms` = 'announcement:delete' AND m.`deleted_flag` = 0
);
