-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 菜单名全部变成乱码。
SET NAMES utf8mb4;

-- ============================================================================
-- 商城模块 菜单补齐（商品编辑页 + 缺失的权限点）
-- ============================================================================
--
-- 【本脚本只补三样东西】
--   ① 商品编辑页的**隐藏菜单** —— 这是新增/编辑商品能不能打开的前提
--   ② 商品分类的四个权限点（菜单 /mall/mall-category/list 建了，但功能点没建）
--   ③ 商品收藏的四个权限点（同上）
--
--   商城的目录、商品管理、兑换订单、SKU、限兑、地址等菜单与权限点**已经在库里了**
--   （menu_id 463~497，2026-08-22 基线导出之后手工建的），本脚本不会重复创建它们。
--
-- 【为什么 ① 是必须的】
--   本项目的路由是**从菜单表生成的**（solvela-admin-web/src/router/index.js: buildRoutes）：
--   一个页面在 t_menu 里没有记录，前端就压根没有那条路由 —— 列表页点「新建商品」
--   router.push 过去只会得到 404。
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/商城模块-菜单与权限.sql;
--   可重复执行：按 path / api_perms 判存（并排除软删），跑第二遍不会产生重复菜单。
--
-- 🔴 执行完必须重新导出种子数据基线，否则这次改动只存在于你这一台机器上：
--       cd 数据库SQL脚本/tools && java -cp <mysql-connector.jar> DumpSeedData.java
--       git diff 数据库SQL脚本/mysql/data-baseline.sql
--   ⚠️ 顺带说明：库里现在的商城菜单（463~497）**也还没进基线** ——
--      data-baseline.sql 里一条 /mall 菜单都没有。这次导出会把它们一起带进去，
--      diff 会比本脚本插入的行数多，属正常。
--
-- 【授权】
--   超级管理员不需要授权（RoleMenuService.getMenuList 对 administratorFlag 直接返回全部菜单）。
--   其他角色要到「权限管理 → 角色管理」里勾选，本脚本刻意不写 t_role_menu ——
--   往别人的角色里塞权限是越权，得由管理员自己决定给谁。
-- ============================================================================


-- 商城目录与已有菜单的 id，按 path 反查，不硬编码 —— 各环境的自增 id 不一样
SET @mall_catalog_id   = (SELECT `menu_id` FROM `t_menu` WHERE `menu_type` = 1 AND `menu_name` = '积分商城' AND `deleted_flag` = 0 LIMIT 1);
SET @commodity_menu_id = (SELECT `menu_id` FROM `t_menu` WHERE `path` = '/mall/mall-commodity/list' AND `deleted_flag` = 0 LIMIT 1);
SET @category_menu_id  = (SELECT `menu_id` FROM `t_menu` WHERE `path` = '/mall/mall-category/list'  AND `deleted_flag` = 0 LIMIT 1);
SET @favorite_menu_id  = (SELECT `menu_id` FROM `t_menu` WHERE `path` = '/mall/mall-favorite/list'  AND `deleted_flag` = 0 LIMIT 1);


-- ---------------------------------------------------------------------------
-- 1. 🔴 商品编辑页：隐藏菜单
--
--    visible_flag = 0：它不是一个独立入口，只能从商品列表点进来，
--    出现在左侧菜单里没有意义（「新建商品」是一个动作，不是一个页面）。
--    但**必须有这条记录**，否则路由不存在。
--
--    cache_flag = 0：keep-alive 的 include 用 menuId 当组件名，而新建与编辑复用同一条路由。
--    开了缓存的话，编辑完 A 商品再点「新建」，表单里会是 A 的数据 —— 保存下去就真的写进了新商品。
--
--    权限点不单独建：编辑页的按钮用的是商品管理已有的 mallCommodity:add / update。
-- ---------------------------------------------------------------------------
INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT '商品编辑', 2, @mall_catalog_id, 3, '/mall/mall-commodity/editor',
       '/business/mall/mall-commodity/mall-commodity-editor.vue', 1, NULL, NULL,
       'FormOutlined', NULL, 0, NULL, 0, 0, 0, 0, 1
  FROM DUAL
 WHERE @mall_catalog_id IS NOT NULL
   -- 判存的子查询要套一层派生表：MySQL 不允许 INSERT ... SELECT 的子查询直接读目标表（错误 1093）
   AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM `t_menu`
                                   WHERE `path` = '/mall/mall-commodity/editor' AND `deleted_flag` = 0) AS t);


-- ---------------------------------------------------------------------------
-- 2. 补齐缺失的权限点
--
--    库里已有 mallCommodity / mallOrder / mallSku / mallAddress / mallExchangeLimit
--    四个动作齐全，唯独**商品分类和商品收藏只有菜单、没有功能点** ——
--    表现是非管理员角色进得去页面，但一调接口就被 Sa-Token 拦下，
--    而报错只说「无权限」，不会告诉你是权限点压根没建。
--
--    取值与后端 @SaCheckPermission 一一对应。生成器原先留的是 ":query" 这种空前缀，
--    配到角色上永远匹配不到任何接口，已在代码里一并补成 表名:动作。
-- ---------------------------------------------------------------------------
INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT * FROM (
              SELECT '查询' AS menu_name, 3 AS menu_type, @category_menu_id AS parent_id, NULL AS sort,
                     NULL AS path, NULL AS component, 1 AS perms_type,
                     'mallCategory:query' AS api_perms, 'mallCategory:query' AS web_perms, NULL AS icon,
                     @category_menu_id AS context_menu_id, 0 AS frame_flag, NULL AS frame_url,
                     0 AS cache_flag, 1 AS visible_flag, 0 AS disabled_flag, 0 AS deleted_flag, 1 AS create_user_id
    UNION ALL SELECT '添加', 3, @category_menu_id, NULL, NULL, NULL, 1, 'mallCategory:add',    'mallCategory:add',    NULL, @category_menu_id, 0, NULL, 0, 1, 0, 0, 1
    UNION ALL SELECT '更新', 3, @category_menu_id, NULL, NULL, NULL, 1, 'mallCategory:update', 'mallCategory:update', NULL, @category_menu_id, 0, NULL, 0, 1, 0, 0, 1
    UNION ALL SELECT '删除', 3, @category_menu_id, NULL, NULL, NULL, 1, 'mallCategory:delete', 'mallCategory:delete', NULL, @category_menu_id, 0, NULL, 0, 1, 0, 0, 1
    UNION ALL SELECT '查询', 3, @favorite_menu_id, NULL, NULL, NULL, 1, 'mallFavorite:query',  'mallFavorite:query',  NULL, @favorite_menu_id, 0, NULL, 0, 1, 0, 0, 1
    UNION ALL SELECT '添加', 3, @favorite_menu_id, NULL, NULL, NULL, 1, 'mallFavorite:add',    'mallFavorite:add',    NULL, @favorite_menu_id, 0, NULL, 0, 1, 0, 0, 1
    UNION ALL SELECT '更新', 3, @favorite_menu_id, NULL, NULL, NULL, 1, 'mallFavorite:update', 'mallFavorite:update', NULL, @favorite_menu_id, 0, NULL, 0, 1, 0, 0, 1
    UNION ALL SELECT '删除', 3, @favorite_menu_id, NULL, NULL, NULL, 1, 'mallFavorite:delete', 'mallFavorite:delete', NULL, @favorite_menu_id, 0, NULL, 0, 1, 0, 0, 1
) AS points
WHERE points.parent_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT `api_perms` FROM `t_menu` WHERE `menu_type` = 3 AND `deleted_flag` = 0) AS existed
       WHERE existed.`api_perms` = points.api_perms
  );


-- ---------------------------------------------------------------------------
-- 自查：跑完应该能看到 /mall/mall-commodity/editor 这一行（visible_flag=0, cache_flag=0）
-- ---------------------------------------------------------------------------
SELECT `menu_id`, `menu_name`, `menu_type`, `parent_id`, `path`, `component`, `api_perms`,
       `cache_flag`, `visible_flag`
  FROM `t_menu`
 WHERE `deleted_flag` = 0
   AND (`path` LIKE '/mall/%' OR `api_perms` LIKE 'mall%')
 ORDER BY `menu_type`, `parent_id`, `sort`, `menu_id`;
