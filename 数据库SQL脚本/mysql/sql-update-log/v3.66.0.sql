-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- =====================================================================================
-- v3.66.0  奖池配置页收口：回收 prizePoolConfig 的 增/删 两个功能点
-- 撰写：2026-08-16
--
-- 背景：奖池配置页的四个写动作重新划分了职责，Java 侧 add / delete / batchDelete
--   三个接口已在同一提交里删除，对应功能点不能继续挂着。
--
--   1) 新建 → 抽奖工作台。
--      只有 t_prize_pool_config 一行、没有坑位映射的奖池，抽奖时 DrawPoolSnapshot 构造
--      直接抛「奖池快照不能为空」—— 是个建了就用不了的空壳。
--      工作台的聚合保存把「池 + 坑位 + 概率闭环校验」放在一个事务里，那才是奖池的完整形态。
--
--   2) 删除 → 禁用（新增 offline / online / batchOffline，复用 prizePoolConfig:update）。
--      删池会留下两类无法还原的烂摊子：
--        ① t_pool_prize_mapping 按 pool_code 关联，池没了映射还在，既用不到也无处改；
--        ② t_draw_prize_log 里存着 pool_code，那是发奖凭证与对账依据 ——
--           用户说「我明明在这个池抽中过」而那个池已不存在，客诉自证当场断掉，
--           而这种事往往几个月后才发生。
--      禁用则一个字都不动历史数据：运行态判 status 直接拒绝新的抽奖请求，且可逆。
--      与彩票玩法「删除换成下线」是同一个决定（见 v3.63.0.sql）。
--
--   3) 编辑保留，但收窄为「奖池名称 + 限领重置周期」两项。
--      status 从 UpdateForm 移除：开关的唯一入口是 offline/online，
--      它们有并发闸门（WHERE status = #{from}），表单里的下拉没有 ——
--      同一件事留两条路径、其中一条还更弱，迟早从弱的那条出事。
--      drawMode / scriptId 一并移除：后端从未读取过，是两个假开关。
--
-- ⚠️ 保留 396(query) 与 398(update)：
--    列表页一览走 query，编辑与新增的 禁用/启用/批量禁用 三个接口全部复用 update。
--    不为「禁用」新增权限点 —— 「改配置」与「停用」对角色授权而言是同一件事
--    （沿用 v3.63.0 对彩票玩法下线的同一判断）。
--    457(prizePoolConfig:workbench:save) 也保留，新建奖池现在正是走它。
--
-- 可重复执行。
-- =====================================================================================

-- 先解绑角色授权，再删功能点：反过来会留下指向不存在菜单的 t_role_menu 脏行
DELETE FROM `t_role_menu`
WHERE `menu_id` IN (SELECT `menu_id`
                    FROM `t_menu`
                    WHERE `api_perms` IN ('prizePoolConfig:add', 'prizePoolConfig:delete'));

DELETE FROM `t_menu`
WHERE `api_perms` IN ('prizePoolConfig:add', 'prizePoolConfig:delete');

-- 按 menu_id 兜底再删一次，理由同 v3.65.0：
-- 若先重新导入了基线 t_menu.sql（已不含这两行）再跑本脚本，
-- 上面的子查询查不到 menu_id，解绑语句一行都删不掉，
-- t_role_menu 里指向这两个 id 的授权会永久变成孤儿，
-- 而孤儿授权在角色授权回显时会被当作「已勾选」，表现为怎么也去不掉的幽灵权限。
--   397 = prizePoolConfig:add
--   399 = prizePoolConfig:delete
DELETE FROM `t_role_menu` WHERE `menu_id` IN (397, 399);


-- ---------- 同步基线：t_menu.sql 里的对应 INSERT 也已一并移除 ----------


-- ---------- 自查 ----------
-- ① 应返回 0 行：
-- SELECT menu_id, menu_name, api_perms FROM t_menu
--  WHERE api_perms IN ('prizePoolConfig:add','prizePoolConfig:delete');
--
-- ② 应返回 3 行（query / update / workbench:save 全部保留）：
-- SELECT menu_id, api_perms FROM t_menu
--  WHERE api_perms IN ('prizePoolConfig:query','prizePoolConfig:update','prizePoolConfig:workbench:save');
--
-- ③ 不该有指向已删菜单的孤儿授权：
-- SELECT rm.* FROM t_role_menu rm LEFT JOIN t_menu m ON m.menu_id = rm.menu_id
--  WHERE m.menu_id IS NULL;
