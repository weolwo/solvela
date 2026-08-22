-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- =====================================================================================
-- v3.72.0  member_name 收口：单据类降级为展示快照，状态类删列
-- 撰写：2026-08-22
--
-- 这是 v3.71.0（member_name -> member_id 换关联键）的<b>收口步骤</b>，被刻意拆成
-- 独立文件，因为它和前面那批的执行前提完全不同：
--
--   v3.71.0  纯增量，对旧代码<b>无害</b>            -> 改完库就能上
--   v3.72.0  会动/删 member_name 列，对旧代码<b>有害</b> -> 必须等代码切完
--
-- 🔴 执行前置（缺一不可，请逐条确认）：
--   ① v3.71.0 已执行且全量对账通过（每行的 member_id 都能正确指回 t_member）
--   ② 十张表对应的 Entity / Form / VO / Mapper XML <b>已全部切到 memberId</b>
--   ③ 已上线并<b>观察至少一个版本</b>，确认没有任何地方还在读 member_name
--
--   ⚠️ 判据不是「grep 不到 memberName 了」。MyBatis 的 XML、SmartBeanUtil.copy 的
--      同名拷贝、以及前端传上来的 queryForm 字段都可能间接引用到它，
--      而这些<b>编译期一个都不报错</b>。稳妥做法是先在预发跑一轮全链路。
--
-- 🔴 <b>本文件不可回滚</b>：DROP COLUMN 之后 member_name 的值就没了。
--    t_member_wallet / t_task_record 这两张的 member_name 属于「可从 t_member 反查」
--    的冗余，删了不丢信息；但执行前仍建议对这两张表做一次备份，
--    毕竟恢复一张备份表比解释一次数据丢失便宜得多。
-- =====================================================================================


-- -------------------------------------------------------------------------------------
-- 单据类留快照，状态类删列
-- -------------------------------------------------------------------------------------
-- 分类依据（单据 or 状态）见 v3.71.0.sql 的文件头。
-- -------------------------------------------------------------------------------------

-- ① 单据类：保留为展示快照。收窄到 varchar(32) 对齐 t_member.member_name，改注释钉死语义。
--    🔴 它身上的索引在第 4 步已经换成 member_id 版本了 —— 快照列<b>不再有任何索引</b>，
--       这是刻意的：没索引，谁写了 `WHERE member_name=?` 会立刻表现为慢查询被发现；
--       建了索引，关联键就会悄悄退回 member_name，改名断链的问题原样复活。
ALTER TABLE `t_member_asset_transaction` MODIFY `member_name` varchar(32) NULL COMMENT '会员账号【展示快照，非关联键，不要用于查询】';
ALTER TABLE `t_member_coupon`            MODIFY `member_name` varchar(32) NULL COMMENT '会员账号【展示快照，非关联键，不要用于查询】';
ALTER TABLE `t_physical_delivery`        MODIFY `member_name` varchar(32) NULL COMMENT '会员账号【展示快照，非关联键，不要用于查询】';
ALTER TABLE `t_proposal_record`          MODIFY `member_name` varchar(32) NULL COMMENT '会员账号【展示快照，非关联键，不要用于查询】';
ALTER TABLE `t_task_record_flow`         MODIFY `member_name` varchar(32) NULL COMMENT '会员账号【展示快照，非关联键，不要用于查询】';
ALTER TABLE `t_prize_log`                MODIFY `member_name` varchar(32) NULL COMMENT '会员账号【展示快照，非关联键，不要用于查询】';
ALTER TABLE `t_draw_prize_log`           MODIFY `member_name` varchar(32) NULL COMMENT '会员账号【展示快照，非关联键，不要用于查询】';
ALTER TABLE `t_lottery_record`           MODIFY `member_name` varchar(32) NULL COMMENT '会员账号【展示快照，非关联键，不要用于查询】';

-- ② 状态类：删列。放快照没意义，还会和主表长期不一致。
ALTER TABLE `t_member_wallet` DROP COLUMN `member_name`;
ALTER TABLE `t_task_record`   DROP COLUMN `member_name`;

-- -------------------------------------------------------------------------------------
-- 验收
-- -------------------------------------------------------------------------------------
-- ① 全库不该再有任何 member_name 参与索引（t_member.uk_mbr_name 除外 ——
--    那是会员主表自己的账号唯一索引，本来就该有）
SELECT TABLE_NAME AS `表`, INDEX_NAME AS `索引`
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'member_name'
   AND TABLE_NAME <> 't_member';

-- ② 状态类两张表的 member_name 应当已不存在
SELECT TABLE_NAME AS `列还在的表`
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'member_name'
   AND TABLE_NAME IN ('t_member_wallet', 't_task_record');

-- ③ 单据类的快照列应当都已收窄到 varchar(32) 且可空
SELECT TABLE_NAME AS `表`, COLUMN_TYPE AS `类型`, IS_NULLABLE AS `可空`
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'member_name'
   AND TABLE_NAME <> 't_member'
 ORDER BY TABLE_NAME;
-- =====================================================================================
