-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 菜单名和权限名全部变成乱码。
SET NAMES utf8mb4;

-- ============================================================================
-- 会员等级 · 阶段 2：菜单与权限  2026-09-18
--
-- 方案见 docs/会员等级-实现技术方案.md §8 阶段 2
-- 建表见 数据库SQL脚本/mysql/会员等级-建表与种子.sql（阶段 1，需先执行）
--
-- 【它解决什么】
--   阶段 1 把成长值算对了，但除了直接查库没人看得见。这一批菜单让运营和客服
--   能回答三个问题：这个人什么等级 / 他的成长值是怎么来的 / 谁改过他的等级。
--
-- 🔴 一个页面在 t_menu 里没有记录，前端就压根没有那条路由，
--    router.push 过去只会 404 —— 所以这不是可选项，是能不能打开页面的前提。
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/会员等级-菜单与权限.sql;
--   按 path / api_perms 判存，可重复执行。
--
-- 🔴 执行完必须重新导出基线，否则这次改动只存在于你这一台机器上：
--       cd 数据库SQL脚本/tools && java -cp <mysql-connector.jar> DumpSeedData.java
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. 父目录：会员管理
--
--    按已知子菜单反查，不硬编码 id —— 各环境的自增 id 不一样。
--    用 /member/list 反查而不是按目录名匹配：目录名可能被运营改过，路由不会。
-- ---------------------------------------------------------------------------
SET @member_catalog_id = (SELECT `parent_id` FROM `t_menu`
                           WHERE `path` = '/member/list' AND `deleted_flag` = 0 LIMIT 1);


-- ---------------------------------------------------------------------------
-- 2.1 会员成长值（主页面）
--
--     排在等级配置前面：日常打开这个模块九成是为了查某个人，不是改规则。
-- ---------------------------------------------------------------------------
INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT '会员成长值', 2, @member_catalog_id, 5, '/member/member-level/growth-list',
       '/business/member/member-level/member-growth-list.vue', 1, NULL, NULL,
       'RiseOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1
  FROM DUAL
 WHERE @member_catalog_id IS NOT NULL
   -- 判存的子查询要套一层派生表：MySQL 不允许 INSERT ... SELECT 的子查询直接读目标表（错误 1093）
   AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM `t_menu`
                                   WHERE `path` = '/member/member-level/growth-list' AND `deleted_flag` = 0) AS t);

-- 2.2 等级配置
INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT '等级配置', 2, @member_catalog_id, 6, '/member/member-level/config-list',
       '/business/member/member-level/member-level-config-list.vue', 1, NULL, NULL,
       'TrophyOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1
  FROM DUAL
 WHERE @member_catalog_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM `t_menu`
                                   WHERE `path` = '/member/member-level/config-list' AND `deleted_flag` = 0) AS t);

-- 2.3 等级变更留痕（审计视角）
--
--     会员成长值页里已经有「按会员看留痕」的抽屉。这个页面存在的理由只有一个：
--     「最近一段时间谁被人工调过级」—— 那是跨会员的问题，抽屉答不了，
--     而它正是审计第一个会问的。
INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT '等级变更留痕', 2, @member_catalog_id, 7, '/member/member-level/level-log-list',
       '/business/member/member-level/member-level-log-list.vue', 1, NULL, NULL,
       'HistoryOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1
  FROM DUAL
 WHERE @member_catalog_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM `t_menu`
                                   WHERE `path` = '/member/member-level/level-log-list' AND `deleted_flag` = 0) AS t);


-- ---------------------------------------------------------------------------
-- 3. 权限点：取值与 Controller 上的 @RequiresPermission 一一对应
--
--    🔴 memberLevel:adjust 与 memberLevel:config 【分开】。
--       「能改等级规则」和「能改某一个人的等级」是两种完全不同的授权 ——
--       后者是能被拿来给自己人送权益的那一种，必须能单独授予和单独收回。
--
--    三个页面共用同一组权限点，挂在成长值那个菜单下（它是这个模块的主入口）。
-- ---------------------------------------------------------------------------
SET @growth_menu_id = (SELECT `menu_id` FROM `t_menu`
                        WHERE `path` = '/member/member-level/growth-list' AND `deleted_flag` = 0 LIMIT 1);

INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT * FROM (
              SELECT '查询' AS menu_name, 3 AS menu_type, @growth_menu_id AS parent_id, NULL AS sort,
                     NULL AS path, NULL AS component, 1 AS perms_type,
                     'memberLevel:query' AS api_perms, 'memberLevel:query' AS web_perms, NULL AS icon,
                     @growth_menu_id AS context_menu_id, 0 AS frame_flag, NULL AS frame_url,
                     0 AS cache_flag, 1 AS visible_flag, 0 AS disabled_flag, 0 AS deleted_flag, 1 AS create_user_id
    UNION ALL SELECT '等级配置', 3, @growth_menu_id, NULL, NULL, NULL, 1, 'memberLevel:config', 'memberLevel:config', NULL, @growth_menu_id, 0, NULL, 0, 1, 0, 0, 1
    UNION ALL SELECT '人工调级', 3, @growth_menu_id, NULL, NULL, NULL, 1, 'memberLevel:adjust', 'memberLevel:adjust', NULL, @growth_menu_id, 0, NULL, 0, 1, 0, 0, 1
) AS points
WHERE points.parent_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT `api_perms` FROM `t_menu` WHERE `menu_type` = 3 AND `deleted_flag` = 0) AS existed
       WHERE existed.`api_perms` = points.api_perms
  );


-- ============================================================================
-- 自查
-- ============================================================================
--
--   SELECT menu_id, menu_name, menu_type, path, api_perms FROM t_menu
--    WHERE (path LIKE '/member/member-level%' OR api_perms LIKE 'memberLevel:%')
--      AND deleted_flag = 0 ORDER BY menu_type, sort;
--
-- ⚠️ 菜单加完要给角色授权，否则除了超管谁都看不见 —— 在【系统管理 → 角色】里勾。
-- ============================================================================
