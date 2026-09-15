-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 菜单名全部变成乱码。
SET NAMES utf8mb4;

-- ============================================================================
-- 优惠券 · 券模板菜单与权限  2026-09-15
-- ============================================================================
--
-- 【为什么必须有这个脚本】
--   管理端是菜单驱动的：一个页面在 t_menu 里没有记录，前端就压根没有那条路由。
--   .vue 文件写得再对，没有菜单行也进不去。
--
-- 【挂在哪】
--   挂到已有的「财务中心」目录下，和「会员优惠券」（那是用户手里的券）并排。
--   两者的关系是：券模板管【规则】，会员优惠券管【谁有哪张券】。
--   sort 取 4（接在交易明细表之后），这样不用去动已有三个菜单的排序 ——
--   改它们的 sort 会让种子基线多出三行无关 diff。
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/优惠券-券模板菜单与权限.sql;
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


SET @ledger_catalog_id =
  (SELECT `menu_id` FROM `t_menu` WHERE `menu_type` = 1 AND `menu_name` = '财务中心' AND `deleted_flag` = 0 LIMIT 1);


-- ---------------------------------------------------------------------------
-- 券模板
--
--    🔴 页面上没有「删除」，也没有「修改」——两者都是刻意的，而且服务端也没有对应接口：
--      · 模板按 (coupon_code, version) 不可变，编辑 = 新增下一版。
--        发券时把规则【快照】进 t_member_coupon，核销读的是那份快照。
--        如果核销读模板当前值，运营把「满100减20」改成「满200减20」之后，
--        用户手里那张券就在他不知情的时候贬值了 —— 那是资损与信任问题；
--      · 只停用不删除。删了一版，运营就再也回答不了「用户手里这张券当时是什么规则」，
--        而券的纠纷恰恰总是要回答这个。
--    所以权限点只有 query 和 save 两个。
-- ---------------------------------------------------------------------------
INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT '券模板', 2, @ledger_catalog_id, 4, '/ledger/coupon-template/list',
       '/business/ledger/coupon-template/coupon-template-list.vue', 1, NULL, NULL,
       'TagsOutlined', NULL, 0, NULL, 1, 1, 0, 0, 1
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_menu`) m
   WHERE m.`path` = '/ledger/coupon-template/list' AND m.`deleted_flag` = 0
);

SET @coupon_template_menu_id =
  (SELECT `menu_id` FROM `t_menu` WHERE `path` = '/ledger/coupon-template/list' AND `deleted_flag` = 0 LIMIT 1);

INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT '查询', 3, @coupon_template_menu_id, 1, NULL, NULL, 1,
       'couponTemplate:query', 'couponTemplate:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_menu`) m
   WHERE m.`api_perms` = 'couponTemplate:query' AND m.`deleted_flag` = 0
);

INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT '新增版本 / 停用', 3, @coupon_template_menu_id, 2, NULL, NULL, 1,
       'couponTemplate:save', 'couponTemplate:save', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_menu`) m
   WHERE m.`api_perms` = 'couponTemplate:save' AND m.`deleted_flag` = 0
);
