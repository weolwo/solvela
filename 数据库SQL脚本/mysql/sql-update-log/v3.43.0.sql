-- activity_type 列注释补上 BASIC（2026-07-29，活动创建向导 P3 收尾）
--
-- 背景：活动类型新增了第四个取值 BASIC（基础活动，只有活动外壳、不挂玩法引擎），
-- 但列注释还停在 'DRAW，TASK,LOTTERY'。取值本身不需要 DDL 变更
-- （varchar(32) NOT NULL 装得下），过时的只是注释 —— 而注释是下一个人查表时唯一的线索。
--
-- ⚠️ 只改 COMMENT，不动类型、不动约束、不动默认值。
--    MODIFY COLUMN 会重建列定义，所以必须把原定义原样抄全（varchar(32) NOT NULL），
--    漏一个 NOT NULL 就等于顺手把约束放开了。
--
-- 可重复执行。

ALTER TABLE `t_activity_config`
    MODIFY COLUMN `activity_type` varchar(32) NOT NULL
        COMMENT '活动类型：BASIC-基础活动(仅外壳,不挂玩法) / DRAW-奖池抽奖 / TASK-任务驱动 / LOTTERY-FPE彩票';

-- 自查：注释应包含 BASIC，且 is_nullable 仍为 NO
-- SELECT column_name, column_type, is_nullable, column_comment
-- FROM information_schema.columns
-- WHERE table_schema = 'smart_admin_v3' AND table_name = 't_activity_config' AND column_name = 'activity_type';
