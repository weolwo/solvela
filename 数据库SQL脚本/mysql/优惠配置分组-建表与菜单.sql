-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 菜单名和列注释全部变成乱码。
SET NAMES utf8mb4;

-- ============================================================================
-- 优惠配置分组（工作台）  2026-08-30
-- ============================================================================
--
-- 【它解决什么】
--   预算与风控是按资产类型分开算的（used_amount 混不了积分和元），所以一个活动
--   有几种奖励类型就得有几条 t_promotion_config。但那几条之间通常只有资产类型和
--   预算不同，风控参数一模一样 —— 痛点不是「要建 N 条」，是「N 条里 90% 的字段
--   要重填 N 遍」。分组把那 N 条收进一个入口，工作台一页配完。
--
-- 【它不改什么】
--   ⭐ t_prize_config.promotion_config_id 保持不变，仍关联**具体那条**优惠配置。
--      分组只是管理端的配置入口，发奖链路（5 个 PrizeHandler、提案、预算 CAS）
--      从头到尾不认识分组，一行代码都没改。
--   ⭐ 存量配置零迁移：group_id 可空，为空即「未分组的独立配置」，继续按原样工作。
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/优惠配置分组-建表与菜单.sql;
--   建表部分用 IF NOT EXISTS；菜单部分按 path / api_perms 判存，可重复执行。
--
-- 🔴 执行完必须重新导出基线，否则这次改动只存在于你这一台机器上：
--       cd 数据库SQL脚本/tools && java -cp <mysql-connector.jar> DumpSchema.java
--       cd 数据库SQL脚本/tools && java -cp <mysql-connector.jar> DumpSeedData.java
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. 分组主表
--
--    🔴 刻意没有 activity_code：优惠配置是**可跨活动复用的预算池**，表上从来
--    没有活动关联列。给分组加一个就等于打破这个定位 —— 同一套风控参数想给两个
--    活动用就得复制一份，回到了这层封装本来要解决的问题。
--    想表达「这是中秋活动用的」，写在 group_name 里即可。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_promotion_group` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '分组ID',
  `group_code`  varchar(32)  NOT NULL COMMENT '分组编码：10位大写字母+数字，全局唯一，服务端生成',
  `group_name`  varchar(128) NOT NULL COMMENT '分组名称，如「2026中秋活动优惠配置」',
  `remark`      varchar(512) DEFAULT NULL COMMENT '备注',
  `status`      tinyint      NOT NULL DEFAULT '1' COMMENT '状态：0-停用, 1-启用。是组内所有配置的主开关：停用会连带停用组内全部 t_promotion_config',
  `create_by`   varchar(64)  DEFAULT NULL COMMENT '创建人',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   varchar(64)  DEFAULT NULL COMMENT '更新人',
  `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_code` (`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠配置分组';


-- ---------------------------------------------------------------------------
-- 2. t_promotion_config 挂到分组上
--
--    group_id 可空：分组是后加的配置入口，此前建的独立配置继续按原样工作，不迁移。
--
--    🔴 uk_group_prize_type 是「按奖励类型定位到具体配置」能成立的前提 ——
--    组内一种资产类型只能有一条，否则工作台不知道该改哪一条。
--    MySQL 的唯一索引**不约束 NULL**（多行 NULL 互不冲突），所以存量那些
--    group_id 为空的配置不会互相撞车，这正是能零迁移的原因。
-- ---------------------------------------------------------------------------
ALTER TABLE `t_promotion_config`
  ADD COLUMN `group_id` bigint DEFAULT NULL
    COMMENT '所属优惠配置分组ID，关联 t_promotion_group；为空表示未分组的独立配置' AFTER `id`,
  ADD UNIQUE KEY `uk_group_prize_type` (`group_id`, `prize_type`);


-- ---------------------------------------------------------------------------
-- 3. 菜单与权限点
--
--    本项目的路由是**从菜单表生成的**（solvela-admin-web/src/router/index.js: buildRoutes）：
--    一个页面在 t_menu 里没有记录，前端就压根没有那条路由，router.push 过去只会 404。
--    所以工作台那条隐藏菜单不是可选项，是能不能打开页面的前提。
--
--    父目录按名字反查，不硬编码 id —— 各环境的自增 id 不一样。
-- ---------------------------------------------------------------------------
SET @risk_catalog_id = (SELECT `parent_id` FROM `t_menu`
                         WHERE `path` = '/risk/promotion-config/list' AND `deleted_flag` = 0 LIMIT 1);

-- 3.1 分组列表页
INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT '优惠配置分组', 2, @risk_catalog_id, 8, '/risk/promotion-group/list',
       '/business/risk/promotion-group/promotion-group-list.vue', 1, NULL, NULL,
       'ClusterOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1
  FROM DUAL
 WHERE @risk_catalog_id IS NOT NULL
   -- 判存的子查询要套一层派生表：MySQL 不允许 INSERT ... SELECT 的子查询直接读目标表（错误 1093）
   AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM `t_menu`
                                   WHERE `path` = '/risk/promotion-group/list' AND `deleted_flag` = 0) AS t);

-- 3.2 🔴 工作台：隐藏菜单
--     visible_flag = 0：它不是独立入口，只能从分组列表点进来。
--     cache_flag = 0：keep-alive 的 include 用 menuId 当组件名，而新建与编辑复用同一条路由。
--     开了缓存的话，编辑完 A 组再点「新建」，工作台里会是 A 的数据 —— 保存下去就真写进了新组。
INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT '优惠配置工作台', 2, @risk_catalog_id, 9, '/risk/promotion-group/workbench',
       '/business/risk/promotion-group/PromotionGroupWorkbench.vue', 1, NULL, NULL,
       'AppstoreAddOutlined', NULL, 0, NULL, 0, 0, 0, 0, 1
  FROM DUAL
 WHERE @risk_catalog_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM `t_menu`
                                   WHERE `path` = '/risk/promotion-group/workbench' AND `deleted_flag` = 0) AS t);

-- 3.3 权限点：取值与 Controller 上的 @RequiresPermission 一一对应
SET @group_menu_id = (SELECT `menu_id` FROM `t_menu`
                       WHERE `path` = '/risk/promotion-group/list' AND `deleted_flag` = 0 LIMIT 1);

INSERT INTO `t_menu` (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`,
                      `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`,
                      `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`)
SELECT * FROM (
              SELECT '查询' AS menu_name, 3 AS menu_type, @group_menu_id AS parent_id, NULL AS sort,
                     NULL AS path, NULL AS component, 1 AS perms_type,
                     'promotionGroup:query' AS api_perms, 'promotionGroup:query' AS web_perms, NULL AS icon,
                     @group_menu_id AS context_menu_id, 0 AS frame_flag, NULL AS frame_url,
                     0 AS cache_flag, 1 AS visible_flag, 0 AS disabled_flag, 0 AS deleted_flag, 1 AS create_user_id
    UNION ALL SELECT '更新', 3, @group_menu_id, NULL, NULL, NULL, 1, 'promotionGroup:update', 'promotionGroup:update', NULL, @group_menu_id, 0, NULL, 0, 1, 0, 0, 1
    UNION ALL SELECT '删除', 3, @group_menu_id, NULL, NULL, NULL, 1, 'promotionGroup:delete', 'promotionGroup:delete', NULL, @group_menu_id, 0, NULL, 0, 1, 0, 0, 1
) AS points
WHERE points.parent_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT `api_perms` FROM `t_menu` WHERE `menu_type` = 3 AND `deleted_flag` = 0) AS existed
       WHERE existed.`api_perms` = points.api_perms
  );

-- 没有 promotionGroup:add：新建、编辑、复制、单条开关都走 promotionGroup:update，
-- 拆更细会让「能编辑但不能新建」这种组合出现，而那没有意义。


-- ---------------------------------------------------------------------------
-- 自查
-- ---------------------------------------------------------------------------
-- SELECT menu_id, menu_name, path, visible_flag FROM t_menu WHERE path LIKE '/risk/promotion-group%' AND deleted_flag = 0;
-- SHOW CREATE TABLE t_promotion_group;
-- SHOW INDEX FROM t_promotion_config WHERE Key_name = 'uk_group_prize_type';
