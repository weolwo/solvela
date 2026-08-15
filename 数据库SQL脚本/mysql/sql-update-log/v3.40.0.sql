-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- 彩票中台：期号与购彩记录的建模补齐（2026-07-27，彩票中台 P2 配置态）
--
-- 三处改动都源自「原型 / 派发链路 与 现有表结构对不上」，逐条说明理由。
--
-- 执行前自查（本次新增列都给了默认值，不会触发「NOT NULL 无默认值」那类插入失败）：
--   SELECT table_name, column_name FROM information_schema.columns
--    WHERE table_schema='smart_admin_v3' AND table_name LIKE 't_lottery%'
--      AND is_nullable='NO' AND column_default IS NULL AND extra NOT LIKE '%auto_increment%';
--   已知会命中且必须由代码显式赋值的：
--   t_lottery_record 的 sequence_no / ticket_number / security_sign / lottery_code / issue_no、
--   t_lottery_config.total_count、t_lottery_issue 的 sale_start_time / sale_end_time、
--   t_lottery_prize_rule 的四个匹配列。


-- ---------- 1. 期号：区分「计划开奖时间」与「实际开奖时间」 ----------
-- 原型的创设表单里两者是分开的，而表里只有 settle_time 一列。
-- 语义完全不同：plan_draw_time 是运营对外承诺的开奖时刻（可编辑、可提前录入），
-- settle_time 是实际执行核销的时刻（由系统在开奖那一刻写入，只读）。
-- 合成一列的话，「计划几点开」和「实际几点开的」就永远只能留一个。
--
-- ⚠️ settle_time 没有 ON UPDATE CURRENT_TIMESTAMP 兜底，必须在开奖 SQL 里显式赋值
--    （同 t_proposal_record.approve_time 的处理方式）。这是铁律 9 的例外分支。
ALTER TABLE `t_lottery_issue`
    ADD COLUMN `plan_draw_time` datetime NULL DEFAULT NULL COMMENT '计划开奖时间：对外承诺的开奖时刻，与实际执行的 settle_time 区分' AFTER `sale_end_time`;


-- ---------- 2. 购彩记录：中奖奖品编码快照 ----------
-- 原先中奖只记 prize_level，奖品要回查 t_lottery_prize_rule 才知道。
-- 问题在于规则表是可编辑的：开奖之后运营改一次 prize_code，
-- 历史中奖记录该发什么奖就跟着漂移了 —— 已经公示的中奖结果被追溯篡改。
-- 核销那一刻把 prize_code 快照进记录，与奖品 SKU 化的取舍同源。
ALTER TABLE `t_lottery_record`
    ADD COLUMN `prize_code` varchar(64) NULL DEFAULT NULL COMMENT '中奖奖品编码：核销时从规则表快照，防规则被改后历史中奖结果漂移' AFTER `prize_level`;


-- ---------- 3. 购彩记录：派发状态 ----------
-- 一期可能有几千个中奖者。派发链路已挂 @Async 走有界线程池，
-- 若在核销事务里逐条 publish 会直接把队列打满，所以派发要拆成独立的分页任务。
-- 分页任务需要一个「这条发过没有」的标记 —— 靠 t_prize_log.uk_external_biz 的
-- DuplicateKeyException 来去重是不行的：本项目已经证明过，基于异常的静默去重会掩盖真问题
-- （见 v3.36.0.sql 里那次「防重索引压根没建、catch 一直在空转」）。
ALTER TABLE `t_lottery_record`
    ADD COLUMN `dispatch_status` tinyint NOT NULL DEFAULT 0 COMMENT '派发状态：0-待派发/无需派发, 1-已投递, 2-投递失败' AFTER `prize_code`,
    ADD KEY `idx_dispatch` (`issue_no`, `dispatch_status`);


-- ---------- 4. 未中奖 / 未开奖的奖级统一落 99 ----------
-- C 端「我的号码」要按奖级排序展示（一等奖在前、未中奖沉底）。
-- 若未中奖是 NULL，MySQL 的 ORDER BY prize_level ASC 会把 NULL 排到最前面，正好排反，
-- 前端只能写 `ORDER BY prize_level IS NULL, prize_level` 这类绕弯表达式或在 JS 里二次排序。
-- 落 99 之后排序退化成朴素的 ORDER BY prize_level ASC, id DESC。
-- 默认值也设成 99，这样刚领到、还没开奖的号码天生就沉底，不用等核销来纠正排序。
--
-- ⚠️ 两句的顺序不能反：MySQL 严格模式下，列里还存在 NULL 时直接 MODIFY 成 NOT NULL 会失败。
--    开发库当前该表为空，但脚本要能在有数据的环境重放，UPDATE 那句不能省。
UPDATE `t_lottery_record` SET `prize_level` = 99 WHERE `prize_level` IS NULL;

ALTER TABLE `t_lottery_record`
    MODIFY COLUMN `prize_level` int NOT NULL DEFAULT 99 COMMENT '奖励等级：1..N 为中奖奖级(数字越小奖越大)，99-未中奖/未开奖';


-- ---------- 自查 ----------
-- 以下应各返回 1 行，且默认值与可空性符合预期：
-- SELECT column_name, is_nullable, column_default, column_comment
--   FROM information_schema.columns
--  WHERE table_schema='smart_admin_v3' AND table_name='t_lottery_record'
--    AND column_name IN ('prize_code','dispatch_status','prize_level');
-- SELECT column_name, is_nullable FROM information_schema.columns
--  WHERE table_schema='smart_admin_v3' AND table_name='t_lottery_issue' AND column_name='plan_draw_time';
