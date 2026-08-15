-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- =====================================================================================
-- v3.60.0  发货物流表：新增 Excel 导入功能点
-- 撰写：2026-08-14
--
-- 背景：发货物流表新增了两条导入路径，共用一个权限串 physicalDelivery:import：
--   · /physicalDelivery/importShip       回填物流（按 提案ID+来源类型 更新已有履约单）
--   · /physicalDelivery/importAdd        新增履约单（整行建单）
--   · /physicalDelivery/importShipTemplate、/importAddTemplate  两个模板下载
--
--   两种模式刻意不拆成两个功能点：模板下载与导入是同一个动作的两步，
--   而「能回填不能新增」这种角色配置没有实际场景 —— 真要限制，
--   该限制的是谁能进这个页面（365），不是限制他用哪种导入模式。
--
-- ⚠️ menu_id 取 461：执行前已核对库里 MAX(menu_id) = 460（v3.52.0 占用 459/460）。
--    换环境执行前先跑：SELECT MAX(menu_id) FROM t_menu;
--
-- 可重复执行：先按 menu_id 删再插。
-- =====================================================================================

DELETE FROM `t_role_menu` WHERE `menu_id` IN (461);
DELETE FROM `t_menu`      WHERE `menu_id` IN (461);

INSERT INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`,
                      `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`,
                      `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`,
                      `create_user_id`)
VALUES
    -- 挂在 365「发货物流表」下，与已有的 366~369（查询/添加/更新/删除）并列
    (461, '导入', 3, 365, NULL, NULL, NULL, 1,
     'physicalDelivery:import', 'physicalDelivery:import', NULL, 365, 0, NULL, 0, 1, 0, 0, 1);

-- 与 v3.52.0 同样的说明：开发库里 t_role 多数 role_code 为 NULL，
-- 这段大概率更新 0 行，不是脚本写错。验收受限角色时需手工勾上 461。
INSERT INTO `t_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, m.menu_id
FROM `t_role` r
         CROSS JOIN (SELECT 461 AS menu_id) m
WHERE r.role_code = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM `t_role_menu` rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- 自查：
-- SELECT menu_id, menu_name, parent_id, api_perms FROM t_menu WHERE menu_id = 461;
--
-- ⚠️ 授权后不会立刻生效：@SaCheckPermission 走 @Cacheable(USER_PERMISSION) 的
--    LoginManager.getUserPermission()，改完权限必须登出重登。
