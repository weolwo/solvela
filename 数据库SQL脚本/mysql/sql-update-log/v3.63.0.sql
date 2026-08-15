-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- =====================================================================================
-- v3.63.0  彩票配置页收口到工作台：回收 lotteryConfig 的 增/删 两个功能点
-- 撰写：2026-08-15
--
-- 背景：一个彩票玩法不是一张扁平表单能配出来的东西 ——
--   它至少要同时落 t_lottery_config 与 t_lottery_prize_rule（没有奖级规则的玩法上不了线，
--   online 接口的唯一前置条件就是它），而号码长度、发行总量在发过号之后是永久冻结的（结构锁）。
--   生成器产出的 add/update 绕开了这些校验：配出来的玩法上不了线，改出来的玩法会把发号引擎参数改坏。
--   所有写操作统一收口到 /lotteryConfig/workbench/save（配置 + 奖级，单事务），
--   它用的是 lotteryConfig:update —— 所以「新建玩法」也归 update，add 从此没有对应接口。
--
--   删除换成禁用（下线）：t_lottery_record 里存着 lottery_code，
--   删配置会让用户手里已发出的号码指向一条不存在的玩法，开奖与客诉自证全断。
--   下线只停后续发号，已发出的号码照常开奖，且可逆 —— 出问题时这才是运营要的止血按钮。
--   列表页的「禁用 / 批量禁用」走 /lotteryConfig/offline 与 /lotteryConfig/batchOffline，
--   两者都是 lotteryConfig:update，不新增权限点：「改配置」与「停售」对角色授权而言是同一件事。
--
--   Java 侧对应的 add / update / batchDelete / delete 四个接口已在同一提交里删除。
--
-- ⚠️ 保留 411(query) 与 413(update)：工作台的回显走 query、聚合保存与上下线走 update
--    （见 v3.41.0 建的 439 / 440 两个功能点，它们复用的正是这两个串）。
--
-- 可重复执行。
-- =====================================================================================

-- 先解绑角色授权，再删功能点：反过来会留下指向不存在菜单的 t_role_menu 脏行
DELETE FROM `t_role_menu`
WHERE `menu_id` IN (SELECT `menu_id`
                    FROM `t_menu`
                    WHERE `api_perms` IN ('lotteryConfig:add', 'lotteryConfig:delete'));

DELETE FROM `t_menu`
WHERE `api_perms` IN ('lotteryConfig:add', 'lotteryConfig:delete');

-- 自查：
-- 应只剩 query 与 update（410 页面下各一个，438 工作台下各一个，共 4 行）
-- SELECT menu_id, parent_id, menu_name, api_perms FROM t_menu WHERE api_perms LIKE 'lotteryConfig%' ORDER BY menu_id;
--
-- 应为 0 行
-- SELECT rm.* FROM t_role_menu rm LEFT JOIN t_menu m ON m.menu_id = rm.menu_id WHERE m.menu_id IS NULL;

-- =====================================================================================
-- 🔴 改完权限必须登出重登，否则不生效（基座缺陷，非本次引入）
-- =====================================================================================
