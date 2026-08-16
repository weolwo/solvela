-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- =====================================================================================
-- v3.65.0  回收四个页面的 增/改/删 功能点：接口已删，权限点不能继续挂着
-- 撰写：2026-08-16
--
-- 背景：以下四个页面已收口为只读（Java 侧 add/update/delete/batchDelete 已在前序提交中删除），
--   但 t_menu 里对应的功能点还在。留着有两个坏处：
--     ① 角色授权界面上仍能勾到这些权限，勾了却没有任何接口 —— 授权与实际能力对不上；
--     ② 后来者看到权限点存在，会以为接口只是"暂时没接"，进而把它加回来 ——
--        而这几个接口恰恰是绕过校验的后门（详见各 Controller 的类注释）。
--
--   四个页面收口的理由（每条都是实打实的后果，不是洁癖）：
--
--   1) 奖池奖项映射(405)：概率总和必须整池闭环到 100%。差一点点，DrawPoolSnapshot 构造就抛
--      IllegalArgumentException，而抽奖执行链路没有捕获它 ——
--      **该奖池的每一次抽奖请求都直接报错**。原先那套零校验的写接口，一个下拉框就是一个线上开关。
--      唯一写入口：抽奖工作台（它按池整表重建坑位，从别处写进去的也活不过下次保存）。
--
--   2) 奖池奖项(400)：表单直接开放 used_stock —— 跨奖池累计已出数量、库存对账的基准。
--      工作台落库处明写「used_stock/version 永不接受前端值」，那条路径却照单全收。
--      手改一个数，DB 账目当场错乱，而运行态真正预扣依据的 Redis 剩余量根本不会跟着变。
--      唯一写入口：抽奖工作台。
--
--   3) 抽奖记录(388)：流水是发奖凭证与对账依据。用户说「我明明抽中了」、财务对「这个月发了多少奖」，
--      依据都是这张表；后台能改能删等于这套审计不存在。而且它从来没有正当用途 ——
--      流水由抽奖链路自己写入，没有任何场景需要人工补录或修改一条抽奖记录。
--      清理数据走 DBA 脚本（还须同时清 Redis 库存与限领计数，只删表会更不一致）。
--
--   4) 彩票奖励配置(425)：奖级规则的唯一写入口是彩票配置工作台。原先这条路径绕过全部校验，
--      写进去的脏规则会导致「开奖时该奖级被整条跳过」或「用户中了奖拿不到东西」；
--      且工作台按玩法整表重建奖级，从这里写的也活不过下次保存。
--
-- ⚠️ 保留项，不要顺手一起删：
--   - 四个页面的 :query 全部保留（页面本身还在，只是变成只读分析视图）；
--   - drawPrizeLog:execute(456) 保留 —— 它是抽奖的运行态入口，不是对流水的写操作；
--   - prizePoolConfig 的 add/update/delete(397/398/399) 全部保留 ——
--     奖池配置页仍可编辑，它是 reset_period（限领重置周期）的唯一入口。
--     其 delete 已在同一批次补上守卫：已上线活动禁删、有坑位禁删、有流水禁删。
--
-- 可重复执行。
-- =====================================================================================

-- 先解绑角色授权，再删功能点：反过来会留下指向不存在菜单的 t_role_menu 脏行
-- （写法与 v3.63.0 回收 lotteryConfig 功能点时一致，保持这类操作只有一种形状）
DELETE FROM `t_role_menu`
WHERE `menu_id` IN (SELECT `menu_id`
                    FROM `t_menu`
                    WHERE `api_perms` IN ('drawPrizeLog:add', 'drawPrizeLog:update', 'drawPrizeLog:delete',
                                          'prizePoolItem:add', 'prizePoolItem:update', 'prizePoolItem:delete',
                                          'poolPrizeMapping:add', 'poolPrizeMapping:update', 'poolPrizeMapping:delete',
                                          'lotteryPrizeRule:add', 'lotteryPrizeRule:update', 'lotteryPrizeRule:delete'));

DELETE FROM `t_menu`
WHERE `api_perms` IN ('drawPrizeLog:add', 'drawPrizeLog:update', 'drawPrizeLog:delete',
                      'prizePoolItem:add', 'prizePoolItem:update', 'prizePoolItem:delete',
                      'poolPrizeMapping:add', 'poolPrizeMapping:update', 'poolPrizeMapping:delete',
                      'lotteryPrizeRule:add', 'lotteryPrizeRule:update', 'lotteryPrizeRule:delete');

-- ⚠️ 再按 menu_id 兜一次底，不能省。
-- 上面那句解绑走的是「从 t_menu 反查 menu_id」的子查询 —— 前提是功能点还在 t_menu 里。
-- 但存在一条很现实的路径会打破这个前提：**先重新导入了基线 t_menu.sql（已不含这 12 行），
-- 再来跑本脚本**。此时子查询查不到任何 menu_id，解绑语句一行都删不掉，
-- t_role_menu 里指向这 12 个 id 的授权就永久变成孤儿 —— 而孤儿授权在角色授权回显时
-- 会被当作「已勾选」，表现为怎么也去不掉的幽灵权限。
-- 这些 menu_id 来自基线且固定不变，直接按 id 删是幂等且安全的。
--   390/391/392 = drawPrizeLog     add/update/delete
--   402/403/404 = prizePoolItem    add/update/delete
--   407/408/409 = poolPrizeMapping add/update/delete
--   427/428/429 = lotteryPrizeRule add/update/delete
DELETE FROM `t_role_menu`
WHERE `menu_id` IN (390, 391, 392, 402, 403, 404, 407, 408, 409, 427, 428, 429);


-- ---------- 同步基线：t_menu.sql 里的对应 INSERT 也已一并移除 ----------
-- 否则全新环境按基线初始化出来又会带上这 12 个功能点，与升级后的环境不一致。

-- ---------- 自查 ----------
-- ① 以下应返回 0 行（功能点已清理干净）：
-- SELECT menu_id, menu_name, api_perms FROM t_menu
--  WHERE api_perms IN ('drawPrizeLog:add','drawPrizeLog:update','drawPrizeLog:delete',
--                      'prizePoolItem:add','prizePoolItem:update','prizePoolItem:delete',
--                      'poolPrizeMapping:add','poolPrizeMapping:update','poolPrizeMapping:delete',
--                      'lotteryPrizeRule:add','lotteryPrizeRule:update','lotteryPrizeRule:delete');
--
-- ② 以下应各返回 1 行（保留项还在）：
-- SELECT menu_id, api_perms FROM t_menu
--  WHERE api_perms IN ('drawPrizeLog:query','drawPrizeLog:execute','prizePoolItem:query',
--                      'poolPrizeMapping:query','lotteryPrizeRule:query',
--                      'prizePoolConfig:add','prizePoolConfig:update','prizePoolConfig:delete');
--
-- ③ 不该有指向已删菜单的孤儿授权：
-- SELECT rm.* FROM t_role_menu rm LEFT JOIN t_menu m ON m.menu_id = rm.menu_id
--  WHERE m.menu_id IS NULL;
