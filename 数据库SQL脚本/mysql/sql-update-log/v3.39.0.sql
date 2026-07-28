-- 清理「彩票号码池」的菜单与权限（2026-07-27，彩票中台 P0 清场）
--
-- 背景：号池方案（预生成号码灌表）已被 FPE 发号方案取代 ——
-- 发号改为「Redis 连续游标 --FPE加密--> 不可预测且不重复的号码」，无需任何预生成号码，
-- 表 t_lottery_number_pool 早已在 数据库SQL脚本/lottery.sql 里移除。
--
-- 后端对应的 Controller / Mapper.xml 与前端页面已删除，只保留 Service 里的
-- DynamicNumbersGenerator 作为算法学习材料（不进 Spring 容器）。
-- 但菜单还留在库里：运营点进去是白屏，权限点也变成了永远指不到东西的空壳。
--
-- 菜单结构：394「彩票管理」-> 420「彩票号码池」-> 421~424（查询/添加/更新/删除 四个权限点）
--
-- ⚠️ 顺序不能反：t_role_menu 先删，否则角色上会残留指向已删菜单的授权行。
-- 那种孤儿授权不会报错，但会让「角色有哪些权限」的统计口径长期虚高，排查时很难想到是这里。

DELETE FROM `t_role_menu` WHERE `menu_id` IN (420, 421, 422, 423, 424);

DELETE FROM `t_menu` WHERE `menu_id` IN (420, 421, 422, 423, 424);

-- 自查：以下两句都应返回 0 行
-- SELECT * FROM t_menu      WHERE menu_id IN (420,421,422,423,424);
-- SELECT * FROM t_role_menu WHERE menu_id IN (420,421,422,423,424);
--
-- 顺带自查全库有没有别的孤儿授权（正常应为 0 行）：
-- SELECT rm.* FROM t_role_menu rm
--   LEFT JOIN t_menu m ON m.menu_id = rm.menu_id
--  WHERE m.menu_id IS NULL;
