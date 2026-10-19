-- =============================================================================
-- 生产升级：删除代码生成模块（2026-10-19 一次性脚本）
-- =============================================================================
-- 为什么删：
--   代码生成器是开发期工具，却一直随生产进程跑着，而且它的 controller 没有权限点
--   —— 任意一个登录了的员工都能调 /support/codeGenerator/table/queryTableList，
--   那个接口会把整个库的表结构交出去。
--
--   更要紧的是它的 .vm 模板把 VO 直接写进了数据层，而模板会被当成「这个项目的约定」
--   学走（人和 AI 都一样）。项目早已重构过，这套示范现在是负资产。
--
-- 代码侧同一个提交里删掉的东西：
--   · solvela-admin 的 module/system/codegenerator 整包（42 个类）与 20 个 .vm 模板
--   · velocity-engine-core / velocity-tools-generic 两条依赖
--     （连带 commons-beanutils:1.9.4、commons-digester3:3.2 两个传递依赖）
--   · 管理端前端 views/support/code-generator 整个目录、api、const
--   · highlight.js 的直接依赖（代码生成器的预览弹窗是本项目唯一的使用点）
--   · 两份基线 SQL 里对应的建表与种子
--
-- 这个脚本只做库里的三件事：
--   1. 删掉菜单「代码生成」（menu_id = 151）
--   2. 删掉它的角色授权（t_role_menu 里 menu_id = 151 的行）
--   3. 删掉配置表 t_code_generator_config（生产里 31 行，全是基线种子）
--
-- 🔴 顺序不能反：先删授权再删菜单。t_role_menu 没有外键，但反过来删会在中间留下
--    一段「菜单已不存在、授权还指着它」的状态，而管理端加载菜单树时对这种悬空授权
--    的表现是静默跳过 —— 真出问题时查不到原因。
--
-- 可重复执行：删除都带 WHERE / IF EXISTS，跑第二遍影响 0 行。
--
-- 执行方式（在项目根目录）：
--   docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" solvela' \
--     < "数据库SQL脚本/mysql/生产升级-20261019-删除代码生成模块.sql"
--
-- ⚠️ 这台机器目前没有任何备份机制（deploy/ 与 compose 里一处 mysqldump 都没有）。
--    这个脚本删掉的东西本身没有业务价值（一个开发工具的菜单与它的种子配置），
--    但在补上备份之前，任何删数据的脚本都值得先手动导一份：
--    docker compose exec -T mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" \
--      --single-transaction --no-tablespaces solvela' > 备份.sql
-- =============================================================================

-- 1. 角色授权（先删，理由见文件头）
DELETE FROM `t_role_menu` WHERE `menu_id` = 151;

-- 2. 菜单本体。按 menu_id 而不是按名字删 —— 名字是运营可改的
DELETE FROM `t_menu` WHERE `menu_id` = 151;

-- 3. 配置表整张
DROP TABLE IF EXISTS `t_code_generator_config`;

-- =============================================================================
-- 执行后自检（期望三个都是 0）
-- =============================================================================
-- SELECT
--   (SELECT COUNT(*) FROM t_role_menu WHERE menu_id = 151)                   AS grant_left,
--   (SELECT COUNT(*) FROM t_menu      WHERE menu_id = 151)                   AS menu_left,
--   (SELECT COUNT(*) FROM information_schema.tables
--      WHERE table_schema = DATABASE() AND table_name = 't_code_generator_config') AS table_left;
