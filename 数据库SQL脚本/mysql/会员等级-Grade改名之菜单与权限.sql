-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- ============================================================================
-- 会员等级 · Grade 改名的菜单与权限收尾  2026-09-20
--
-- 前置：数据库SQL脚本/mysql/会员等级-Grade改名与5表模型.sql 必须已执行
-- 原始菜单来自：数据库SQL脚本/mysql/会员等级-菜单与权限.sql（阶段 2）
--
-- 【为什么这一份必须跟着改名一起上】
--   🔴 路由与权限点都是【库里的数据】，不是代码里的常量：
--     · t_menu.path      → 前端路由。库里还写着 /member/member-level/... 的话，
--                          菜单点进去是 404（组件文件已经改名了）；
--     · t_menu.component → 同上，指向一个不存在的 .vue；
--     · t_menu.api_perms → 与 @RequiresPermission("memberGrade:xxx") 逐字比对。
--                          对不上的后果是【所有人都没有权限】，而且返回的是
--                          一句笼统的无权限，排查时根本想不到是菜单表没改。
--
--   代码改了、这份没跑 = 整个会员等级模块在管理端彻底打不开。
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/会员等级-Grade改名之菜单与权限.sql;
--   按新旧值判存，可重复执行（第二遍影响 0 行）。
--
-- 🔴 执行完重新导出种子基线：
--       cd 数据库SQL脚本/tools && java -cp <mysql-connector.jar> DumpSeedData.java
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. 三个页面：路由与组件路径
--
--    ⚠️ 用 UPDATE 而不是「删了重建」：重建会换掉 menu_id，
--    而 t_role_menu 是按 menu_id 授权的 —— 已经给角色勾过的权限会全部丢失，
--    且丢得悄无声息（角色页面上那些勾自己消失了，没有任何提示）。
-- ---------------------------------------------------------------------------
UPDATE `t_menu`
   SET `path`      = '/member/member-grade/growth-list',
       `component` = '/business/member/member-grade/member-growth-list.vue'
 WHERE `path` = '/member/member-level/growth-list' AND `deleted_flag` = 0;

UPDATE `t_menu`
   SET `menu_name` = '等级配置',
       `path`      = '/member/member-grade/config-list',
       `component` = '/business/member/member-grade/member-grade-config-list.vue'
 WHERE `path` = '/member/member-level/config-list' AND `deleted_flag` = 0;

UPDATE `t_menu`
   SET `path`      = '/member/member-grade/grade-log-list',
       `component` = '/business/member/member-grade/member-grade-log-list.vue'
 WHERE `path` = '/member/member-level/level-log-list' AND `deleted_flag` = 0;


-- ---------------------------------------------------------------------------
-- 2. 三个权限点：memberLevel:* → memberGrade:*
--
--    api_perms 与 web_perms 必须一起改：前者是后端 @RequiresPermission 比对的，
--    后者是前端 v-privilege 比对的。只改一边的话，
--    会出现「按钮看得见、点下去说没权限」或者反过来。
-- ---------------------------------------------------------------------------
UPDATE `t_menu`
   SET `api_perms` = REPLACE(`api_perms`, 'memberLevel:', 'memberGrade:'),
       `web_perms` = REPLACE(`web_perms`, 'memberLevel:', 'memberGrade:')
 WHERE `menu_type` = 3
   AND `deleted_flag` = 0
   AND `api_perms` LIKE 'memberLevel:%';


-- ============================================================================
-- 自查
-- ============================================================================
--
--   SELECT menu_id, menu_name, menu_type, path, component, api_perms
--     FROM t_menu
--    WHERE (path LIKE '/member/member-grade%' OR api_perms LIKE 'memberGrade:%')
--      AND deleted_flag = 0
--    ORDER BY menu_type, sort;
--
--   -- 这一条必须返回 0 行，否则说明还有旧值没改到：
--   SELECT menu_id, path, api_perms FROM t_menu
--    WHERE deleted_flag = 0
--      AND (path LIKE '%member-level%' OR api_perms LIKE 'memberLevel:%'
--           OR component LIKE '%member-level%');
-- ============================================================================
