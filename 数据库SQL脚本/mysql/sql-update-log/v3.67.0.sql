-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- =====================================================================================
-- v3.67.0  用户号码记录收口为只读：回收 lotteryRecord 的 增/改/删 三个功能点
-- 撰写：2026-08-16
--
-- 背景：Java 侧 add / update 已在同一提交里删除（delete 本就没有对应接口，见下）。
--
--   这张表存的是**用户手里的号码本身**，比抽奖流水更不能碰：
--     ① security_sign 是防篡改签名，用户凭它自证「这个号码确实是系统发给我的」。
--        后台能改签名，整套自证机制就是摆设；
--     ② win_status / prize_level / prize_code 是派奖依据，
--        改一行等于凭空造一个中奖者，或抹掉一个真中奖者；
--     ③ ticket_number 与 sequence_no 是 FPE 双射的两端，
--        改任一个都会让号码反解验真失败 —— 而「号码可反解验真」正是本模块的立身之本。
--
--   而且这两个接口从来没有正当用途：记录由 TicketPersistService 在领号链路里写入、
--   由开奖核销 SQL 批量更新中奖状态，没有任何场景需要人工补录或修改一张号码。
--
-- ⚠️ 434(lotteryRecord:delete) 是个**从一开始就没有对应接口**的功能点 ——
--    生成器按模板产出了权限点，但 Controller 里压根没有删除方法。
--    它挂在那里的唯一效果，是让角色授权界面上多出一个勾了也没用的选项，
--    并让后来者以为「删除功能只是暂时没接」。一并清掉。
--
-- ⚠️ 保留 431(lotteryRecord:query)：页面还在，只是变成了只读的明细 + 漏斗分析视图。
--
-- 可重复执行。
-- =====================================================================================

-- 先解绑角色授权，再删功能点：反过来会留下指向不存在菜单的 t_role_menu 脏行
DELETE FROM `t_role_menu`
WHERE `menu_id` IN (SELECT `menu_id`
                    FROM `t_menu`
                    WHERE `api_perms` IN ('lotteryRecord:add', 'lotteryRecord:update', 'lotteryRecord:delete'));

DELETE FROM `t_menu`
WHERE `api_perms` IN ('lotteryRecord:add', 'lotteryRecord:update', 'lotteryRecord:delete');

-- 按 menu_id 兜底再删一次，理由同 v3.65.0 / v3.66.0：
-- 若先重新导入了基线 t_menu.sql（已不含这三行）再跑本脚本，
-- 上面的子查询查不到 menu_id，解绑语句一行都删不掉，
-- t_role_menu 里指向这三个 id 的授权会永久变成孤儿，
-- 而孤儿授权在角色授权回显时会被当作「已勾选」，表现为怎么也去不掉的幽灵权限。
--   432 = lotteryRecord:add
--   433 = lotteryRecord:update
--   434 = lotteryRecord:delete（本就无对应接口）
DELETE FROM `t_role_menu` WHERE `menu_id` IN (432, 433, 434);


-- ---------- 同步基线：t_menu.sql 里的对应 INSERT 也已一并移除 ----------


-- ---------- 自查 ----------
-- ① 应返回 0 行：
-- SELECT menu_id, menu_name, api_perms FROM t_menu
--  WHERE api_perms IN ('lotteryRecord:add','lotteryRecord:update','lotteryRecord:delete');
--
-- ② 应返回 1 行（query 保留）：
-- SELECT menu_id, api_perms FROM t_menu WHERE api_perms = 'lotteryRecord:query';
--
-- ③ 不该有指向已删菜单的孤儿授权：
-- SELECT rm.* FROM t_role_menu rm LEFT JOIN t_menu m ON m.menu_id = rm.menu_id
--  WHERE m.menu_id IS NULL;
