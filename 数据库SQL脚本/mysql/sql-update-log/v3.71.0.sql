-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- =====================================================================================
-- v3.71.0  全链路关联键：member_name → member_id
-- 撰写：2026-08-22
--
-- 背景：系统此前<b>没有会员主表</b>，`member_name varchar(64)` 是事实上的全局主键，
--       却从来没有一张表来定义它。member.sql 建起 t_member 之后，
--       member_name 被重新定义为「微信号那种可读、可改的账号」，
--       于是它<b>不能再当关联键</b>：
--         ① 改名即断链 —— 微信号一年可改一次，改完历史数据全部指向一个不存在的账号。
--            不报错，只是查不到了：钱包余额还在，但这个人查不出自己的流水。
--         ② 合并库时必然撞车 —— A 库有 zhangsan、B 库也有 zhangsan，两个不是同一个人。
--            关联键是 member_name 就只能让某一方用户改自己的号（产品灾难）；
--            关联键是 member_id 就给冲突方重新发号 + 留张映射表，用户完全无感。
--
--       所以关联键换成 member_id（10 位数字，全局发号，永不可变，见 member.sql）。
--
-- 影响面（实测，不是估算）：<b>10 张表</b>，不是 9 张 ——
--       t_task_record_flow 也带 member_name，且在两条索引里（含一条唯一索引）。
--       它同时也是 v3.70.0 差点漏掉的那张，按文件 grep 找不到它，
--       因为它的建表语句在历史迁移里、不在主 schema 文件中。
--
--   表                            行数   不同会员   分类
--   t_member_wallet                859      859    状态
--   t_task_record                 1691      827    状态
--   t_member_asset_transaction    1000      859    单据
--   t_member_coupon                601      601    单据
--   t_proposal_record              712      513    单据
--   t_physical_delivery              1        1    单据
--   t_prize_log                    879      680    单据
--   t_draw_prize_log               500      500    单据
--   t_lottery_record                40       12    单据
--   t_task_record_flow            2933      873    单据
--   合计 2417 个不同 member_name
--
-- 【单据 or 状态：决定 member_name 留不留】
--   ✅ 单据类（写完就不再改的历史记录）→ <b>保留 member_name 作展示快照</b>，
--      但删掉它身上的索引、收窄到 varchar(32)、改注释写死「非关联键」。
--      理由：后台不用 join 会员表就能认出是谁；而且单据上本就该记「下单当时那个账号」，
--      不是这人现在叫什么 —— 审计要回答的是「当时是谁」。
--      这和 t_mall_order 里 commodity_name / sku_attrs 快照是同一个模式。
--   ❌ 状态类（表达"当前是什么"，会被反复 UPDATE）→ <b>DROP COLUMN</b>。
--      钱包余额、任务进度这种表放快照没意义，还会和主表长期不一致：
--      用户改了名，钱包表里永远是老名字，反而更难认。
--
-- 【前置条件，缺一不可】
--   ① member.sql 已执行（t_member / t_member_id_seq / t_member_login_log / t_member_verify）
--   ② t_member 已<b>播种完毕</b>：十张表里每一个不同的 member_name 都要有对应行。
--      播种必须用 Java 工具（会员号要过 Feistel 置换，纯 SQL 算不出来），
--      见 common-api 的 MemberIdCodec / MemberIdAllocator。
--      🔴 播种没做完就跑回填，结果全是 NULL，而第 3 步的闸门会拦住你。
--
-- 🔴 排序规则前置检查：t_member.member_name 与十张表的 member_name 必须是<b>同一个</b>
--    排序规则（本库统一 utf8mb4_0900_ai_ci）。不一致时 JOIN 会抛
--    「Illegal mix of collations」，而这个错只在跨表比较时才暴露，建表当下看不出来。
--    自查：
--      SELECT TABLE_NAME, COLLATION_NAME FROM information_schema.COLUMNS
--       WHERE TABLE_SCHEMA=DATABASE() AND COLUMN_NAME='member_name';
--    —— 结果必须只有一种 COLLATION_NAME。
-- =====================================================================================


-- -------------------------------------------------------------------------------------
-- 第 1 步：加列（先允许为空，回填期需要）
-- -------------------------------------------------------------------------------------
-- INSTANT DDL，只改元数据，与表大小无关。
-- 刻意<b>不</b>一上来就 NOT NULL：那样 MySQL 会给存量行填 0，
-- 而 0 是个看起来很正常的 bigint —— 一旦填进去，你就再也分不清
-- 「这行还没回填」和「这行回填成了 0」，第 3 步的闸门也就形同虚设。
-- -------------------------------------------------------------------------------------
ALTER TABLE `t_member_wallet`            ADD COLUMN `member_id` bigint NULL COMMENT '会员号：关联键' AFTER `tenant_id`, ALGORITHM=INSTANT;
ALTER TABLE `t_member_asset_transaction` ADD COLUMN `member_id` bigint NULL COMMENT '会员号：关联键' AFTER `tenant_id`, ALGORITHM=INSTANT;
ALTER TABLE `t_member_coupon`            ADD COLUMN `member_id` bigint NULL COMMENT '会员号：关联键' AFTER `tenant_id`, ALGORITHM=INSTANT;
ALTER TABLE `t_physical_delivery`        ADD COLUMN `member_id` bigint NULL COMMENT '会员号：关联键' AFTER `tenant_id`, ALGORITHM=INSTANT;
ALTER TABLE `t_proposal_record`          ADD COLUMN `member_id` bigint NULL COMMENT '会员号：关联键' AFTER `tenant_id`, ALGORITHM=INSTANT;
ALTER TABLE `t_task_record`              ADD COLUMN `member_id` bigint NULL COMMENT '会员号：关联键' AFTER `tenant_id`, ALGORITHM=INSTANT;
ALTER TABLE `t_task_record_flow`         ADD COLUMN `member_id` bigint NULL COMMENT '会员号：关联键' AFTER `tenant_id`, ALGORITHM=INSTANT;
ALTER TABLE `t_prize_log`                ADD COLUMN `member_id` bigint NULL COMMENT '会员号：关联键' AFTER `tenant_id`, ALGORITHM=INSTANT;
ALTER TABLE `t_draw_prize_log`           ADD COLUMN `member_id` bigint NULL COMMENT '会员号：关联键' AFTER `tenant_id`, ALGORITHM=INSTANT;
ALTER TABLE `t_lottery_record`           ADD COLUMN `member_id` bigint NULL COMMENT '会员号：关联键' AFTER `tenant_id`, ALGORITHM=INSTANT;


-- -------------------------------------------------------------------------------------
-- 第 2 步：回填
-- -------------------------------------------------------------------------------------
-- 幂等：带 `WHERE x.member_id IS NULL`，中断了直接重跑，已回填的行不会被再动。
-- -------------------------------------------------------------------------------------
UPDATE `t_member_wallet`            x JOIN `t_member` m ON m.`member_name` = x.`member_name` SET x.`member_id` = m.`member_id` WHERE x.`member_id` IS NULL;
UPDATE `t_member_asset_transaction` x JOIN `t_member` m ON m.`member_name` = x.`member_name` SET x.`member_id` = m.`member_id` WHERE x.`member_id` IS NULL;
UPDATE `t_member_coupon`            x JOIN `t_member` m ON m.`member_name` = x.`member_name` SET x.`member_id` = m.`member_id` WHERE x.`member_id` IS NULL;
UPDATE `t_physical_delivery`        x JOIN `t_member` m ON m.`member_name` = x.`member_name` SET x.`member_id` = m.`member_id` WHERE x.`member_id` IS NULL;
UPDATE `t_proposal_record`          x JOIN `t_member` m ON m.`member_name` = x.`member_name` SET x.`member_id` = m.`member_id` WHERE x.`member_id` IS NULL;
UPDATE `t_task_record`              x JOIN `t_member` m ON m.`member_name` = x.`member_name` SET x.`member_id` = m.`member_id` WHERE x.`member_id` IS NULL;
UPDATE `t_task_record_flow`         x JOIN `t_member` m ON m.`member_name` = x.`member_name` SET x.`member_id` = m.`member_id` WHERE x.`member_id` IS NULL;
UPDATE `t_prize_log`                x JOIN `t_member` m ON m.`member_name` = x.`member_name` SET x.`member_id` = m.`member_id` WHERE x.`member_id` IS NULL;
UPDATE `t_draw_prize_log`           x JOIN `t_member` m ON m.`member_name` = x.`member_name` SET x.`member_id` = m.`member_id` WHERE x.`member_id` IS NULL;
UPDATE `t_lottery_record`           x JOIN `t_member` m ON m.`member_name` = x.`member_name` SET x.`member_id` = m.`member_id` WHERE x.`member_id` IS NULL;


-- -------------------------------------------------------------------------------------
-- 🔴 第 3 步：闸门 —— 必须返回 0 行，否则<b>停在这里，不要往下走</b>
-- -------------------------------------------------------------------------------------
-- 有输出说明某些 member_name 在 t_member 里没有对应行（播种漏了，或存在脏数据）。
-- <b>先查清楚是哪些名字、为什么没有</b>，不要给个默认值糊过去 ——
-- 糊过去的后果是那些行的归属从此永久错乱，且没有任何线索能追回来。
-- -------------------------------------------------------------------------------------
SELECT 't_member_wallet' AS `表`, COUNT(*) AS `未回填行数` FROM `t_member_wallet`            WHERE `member_id` IS NULL
UNION ALL SELECT 't_member_asset_transaction', COUNT(*) FROM `t_member_asset_transaction` WHERE `member_id` IS NULL
UNION ALL SELECT 't_member_coupon',            COUNT(*) FROM `t_member_coupon`            WHERE `member_id` IS NULL
UNION ALL SELECT 't_physical_delivery',        COUNT(*) FROM `t_physical_delivery`        WHERE `member_id` IS NULL
UNION ALL SELECT 't_proposal_record',          COUNT(*) FROM `t_proposal_record`          WHERE `member_id` IS NULL
UNION ALL SELECT 't_task_record',              COUNT(*) FROM `t_task_record`              WHERE `member_id` IS NULL
UNION ALL SELECT 't_task_record_flow',         COUNT(*) FROM `t_task_record_flow`         WHERE `member_id` IS NULL
UNION ALL SELECT 't_prize_log',                COUNT(*) FROM `t_prize_log`                WHERE `member_id` IS NULL
UNION ALL SELECT 't_draw_prize_log',           COUNT(*) FROM `t_draw_prize_log`           WHERE `member_id` IS NULL
UNION ALL SELECT 't_lottery_record',           COUNT(*) FROM `t_lottery_record`           WHERE `member_id` IS NULL;


-- -------------------------------------------------------------------------------------
-- 第 4 步：换索引 —— 先建新的、再删旧的、最后改名
-- -------------------------------------------------------------------------------------
-- 🔴 为什么不直接「DROP 旧的再 ADD 同名的」：
--    新旧索引同名，不能共存，所以那种写法中间必然有一段<b>索引不存在</b>的窗口。
--    对普通索引只是慢；对<b>唯一索引</b>是致命的 —— 那段窗口里重复数据能直接写进去，
--    等你把唯一索引加回来时才发现加不上了，而脏数据已经落库。
--    先用临时名建新索引，唯一约束在整个过程中<b>一刻都没有失效过</b>。
-- -------------------------------------------------------------------------------------
-- t_member_wallet：uk_member_asset (member_name, asset_type) -> (member_id, asset_type)
ALTER TABLE `t_member_wallet` ADD UNIQUE KEY `uk_member_asset__new` (`member_id`, `asset_type`);
ALTER TABLE `t_member_wallet` DROP INDEX `uk_member_asset`;
ALTER TABLE `t_member_wallet` RENAME INDEX `uk_member_asset__new` TO `uk_member_asset`;

-- t_member_asset_transaction
ALTER TABLE `t_member_asset_transaction` ADD KEY `idx_t_biz_mbr_ast_txn_time__new` (`member_id`, `asset_type`, `create_time`);
ALTER TABLE `t_member_asset_transaction` DROP INDEX `idx_t_biz_mbr_ast_txn_time`;
ALTER TABLE `t_member_asset_transaction` RENAME INDEX `idx_t_biz_mbr_ast_txn_time__new` TO `idx_t_biz_mbr_ast_txn_time`;

-- t_member_coupon
ALTER TABLE `t_member_coupon` ADD KEY `idx_mbr_sts__new` (`member_id`, `status`);
ALTER TABLE `t_member_coupon` DROP INDEX `idx_mbr_sts`;
ALTER TABLE `t_member_coupon` RENAME INDEX `idx_mbr_sts__new` TO `idx_mbr_sts`;

-- t_proposal_record
ALTER TABLE `t_proposal_record` ADD KEY `idx_prop_member__new` (`member_id`, `create_time`);
ALTER TABLE `t_proposal_record` DROP INDEX `idx_prop_member`;
ALTER TABLE `t_proposal_record` RENAME INDEX `idx_prop_member__new` TO `idx_prop_member`;

-- t_task_record：一条普通 + 一条唯一
ALTER TABLE `t_task_record` ADD KEY `idx_t_tsk_rec_mbr_sts__new` (`member_id`, `status`);
ALTER TABLE `t_task_record` DROP INDEX `idx_t_tsk_rec_mbr_sts`;
ALTER TABLE `t_task_record` RENAME INDEX `idx_t_tsk_rec_mbr_sts__new` TO `idx_t_tsk_rec_mbr_sts`;
ALTER TABLE `t_task_record` ADD UNIQUE KEY `uk_t_tsk_rec_mbr_cfg_prd__new` (`member_id`, `task_config_id`, `period_key`);
ALTER TABLE `t_task_record` DROP INDEX `uk_t_tsk_rec_mbr_cfg_prd`;
ALTER TABLE `t_task_record` RENAME INDEX `uk_t_tsk_rec_mbr_cfg_prd__new` TO `uk_t_tsk_rec_mbr_cfg_prd`;

-- t_task_record_flow：一条普通 + 一条唯一（注意唯一索引里 member 不在最左）
ALTER TABLE `t_task_record_flow` ADD KEY `idx_t_tsk_flw_mbr__new` (`member_id`, `create_time`);
ALTER TABLE `t_task_record_flow` DROP INDEX `idx_t_tsk_flw_mbr`;
ALTER TABLE `t_task_record_flow` RENAME INDEX `idx_t_tsk_flw_mbr__new` TO `idx_t_tsk_flw_mbr`;
ALTER TABLE `t_task_record_flow` ADD UNIQUE KEY `uk_t_tsk_flw_evt__new` (`task_config_id`, `member_id`, `event_biz_id`);
ALTER TABLE `t_task_record_flow` DROP INDEX `uk_t_tsk_flw_evt`;
ALTER TABLE `t_task_record_flow` RENAME INDEX `uk_t_tsk_flw_evt__new` TO `uk_t_tsk_flw_evt`;

-- t_prize_log（索引名末尾那个下划线是存量就有的，不是笔误，别"顺手修正"）
ALTER TABLE `t_prize_log` ADD KEY `idx_prize_log___new` (`member_id`, `activity_code`);
ALTER TABLE `t_prize_log` DROP INDEX `idx_prize_log_`;
ALTER TABLE `t_prize_log` RENAME INDEX `idx_prize_log___new` TO `idx_prize_log_`;

-- t_draw_prize_log
ALTER TABLE `t_draw_prize_log` ADD KEY `idx_mem_act__new` (`member_id`, `activity_code`);
ALTER TABLE `t_draw_prize_log` DROP INDEX `idx_mem_act`;
ALTER TABLE `t_draw_prize_log` RENAME INDEX `idx_mem_act__new` TO `idx_mem_act`;

-- t_lottery_record
ALTER TABLE `t_lottery_record` ADD KEY `idx_member__new` (`member_id`, `issue_no`);
ALTER TABLE `t_lottery_record` DROP INDEX `idx_member`;
ALTER TABLE `t_lottery_record` RENAME INDEX `idx_member__new` TO `idx_member`;

-- t_physical_delivery 的 member_name 上本来就没有索引，无需处理。


-- -------------------------------------------------------------------------------------
-- 第 5 步：member_id 收紧为 NOT NULL
-- -------------------------------------------------------------------------------------
-- 过了第 3 步的闸门才能执行。这一步同时也是第二道保险：
-- 万一还有 NULL 残留，这里会直接失败而不是让脏数据继续往下走。
-- -------------------------------------------------------------------------------------
ALTER TABLE `t_member_wallet`            MODIFY `member_id` bigint NOT NULL COMMENT '会员号：关联键';
ALTER TABLE `t_member_asset_transaction` MODIFY `member_id` bigint NOT NULL COMMENT '会员号：关联键';
ALTER TABLE `t_member_coupon`            MODIFY `member_id` bigint NOT NULL COMMENT '会员号：关联键';
ALTER TABLE `t_physical_delivery`        MODIFY `member_id` bigint NOT NULL COMMENT '会员号：关联键';
ALTER TABLE `t_proposal_record`          MODIFY `member_id` bigint NOT NULL COMMENT '会员号：关联键';
ALTER TABLE `t_task_record`              MODIFY `member_id` bigint NOT NULL COMMENT '会员号：关联键';
ALTER TABLE `t_task_record_flow`         MODIFY `member_id` bigint NOT NULL COMMENT '会员号：关联键';
ALTER TABLE `t_prize_log`                MODIFY `member_id` bigint NOT NULL COMMENT '会员号：关联键';
ALTER TABLE `t_draw_prize_log`           MODIFY `member_id` bigint NOT NULL COMMENT '会员号：关联键';
ALTER TABLE `t_lottery_record`           MODIFY `member_id` bigint NOT NULL COMMENT '会员号：关联键';


-- -------------------------------------------------------------------------------------
-- 第 6 步：验收
-- -------------------------------------------------------------------------------------
-- ① 不该再有任何 member_name 参与索引
SELECT TABLE_NAME AS `表`, INDEX_NAME AS `索引`
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'member_name';

-- ② 每张表的 member_id 都应 NOT NULL 且有值
SELECT TABLE_NAME AS `表`, IS_NULLABLE AS `可空`
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'member_id' AND IS_NULLABLE <> 'NO';

-- ③ 抽样对账：随便挑个会员，新旧两条路查出来的行数必须一致
--    （v3.72.0 执行前才能跑，跑完 member_name 快照就只剩单据类那几张表了）
-- SELECT (SELECT COUNT(*) FROM t_member_asset_transaction WHERE member_name = 'testuu') AS `按旧键`,
--        (SELECT COUNT(*) FROM t_member_asset_transaction t JOIN t_member m ON m.member_id = t.member_id
--          WHERE m.member_name = 'testuu') AS `按新键`;
-- =====================================================================================

-- =====================================================================================
-- 本文件到此为止。member_name 列<b>刻意保留原样</b>，此刻数据库处于「双键并存」状态：
--   · member_id  已就位、NOT NULL、索引已全部切过去   -> 新代码可以开始用
--   · member_name 仍在，但<b>身上已经没有任何索引</b>   -> 旧代码照常跑，不受影响
-- 这个中间态是<b>刻意</b>的，它让「改库」和「改代码」可以分两次上线、各自回滚。
--
-- 收口在 <b>v3.72.0.sql</b>：等 Java 代码全部切到 member_id 并观察一个版本之后，
-- 再执行那个文件（单据类把 member_name 收窄成展示快照，状态类直接删列）。
-- 🔴 不要提前跑 v3.72.0 —— 代码还在读 member_name 的时候删列，是直接把服务打挂。
-- =====================================================================================
