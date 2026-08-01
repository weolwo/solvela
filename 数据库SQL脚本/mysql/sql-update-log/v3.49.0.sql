-- =====================================================================================
-- v3.49.0  任务中台：人群过滤与参与频次落地（把两个「配得出来但不生效」的字段兑现）
-- 撰写：2026-08-01
--
-- 背景：`target_audience` 与 `limit_count` 在表里、界面上都存在，运行态却零实现 ——
--   运营配了「只给新会员」会对所有人生效，配了「每日限 3 次」也不起作用，而且<b>系统不报任何错</b>。
--   这类「沉默的错配」比报错难查得多，本次予以兑现。
--
-- 本脚本只改列注释（把真实语义写进 DDL），不改结构、不迁移数据。
-- =====================================================================================


-- -------------------------------------------------------------------------------------
-- 一、target_audience：判据由上游在事件里给
--
-- 🔴 营销域<b>不拥有会员数据</b>：库里只有 t_member_wallet / t_member_asset_transaction /
--    t_member_coupon，全都以 member_name 字符串为键，没有注册时间、没有会员档案。
--    而且「新会员」的定义本就属于会员域的业务概念（注册 7 天内？首单前？各家不同），
--    不该由任务引擎去猜 —— 猜出来的判据大概率与业务真实定义对不上，
--    那是把一个诚实的缺口换成一个隐蔽的错误。
--
--    故契约是：上游上报事件时带 isNewMember；未告知时，配了人群的任务
--    <b>丢弃该事件并在 t_task_record_flow 写明原因</b>，而不是默默放行。
-- -------------------------------------------------------------------------------------
ALTER TABLE `t_task_config`
    MODIFY COLUMN `target_audience` varchar(32) NULL DEFAULT 'ALL'
        COMMENT '目标人群：ALL(全部), NEW_MEMBER(新会员), OLD_MEMBER(老会员)。非ALL时上游上报事件必须带 isNewMember，否则事件被丢弃';


-- -------------------------------------------------------------------------------------
-- 二、limit_count：周期内可完成的轮数
--
-- 语义：一个周期内最多完成 limit_count 轮任务、领 limit_count 次奖。
--   实现靠给 period_key 追加轮次后缀（20260801#2），
--   🔴 第 1 轮刻意<b>不带后缀</b>（就是裸的 20260801）—— 存量记录全是裸键，
--      若第 1 轮也加 #1，那些记录会与新逻辑算出的键对不上，
--      表现是「老用户的进度突然从头开始」。
--
--   只有 DAILY / WEEKLY 受它限制：
--     ONCE      语义就是终身一轮，界面上也不让填次数；
--     UNLIMITED 是「轮次不限」—— 若让 limit_count 生效，
--               「无限制 + 限制 1 次」会退化成「终身一次」，与它自己的名字矛盾。
-- -------------------------------------------------------------------------------------
ALTER TABLE `t_task_config`
    MODIFY COLUMN `limit_type` varchar(32) NOT NULL DEFAULT 'ONCE'
        COMMENT '参与频次：ONCE(终身一次), DAILY(每日重复), WEEKLY(每周重复), UNLIMITED(无限制)';

ALTER TABLE `t_task_config`
    MODIFY COLUMN `limit_count` int NOT NULL DEFAULT '1'
        COMMENT '周期内可完成的轮数，仅 DAILY/WEEKLY 生效；>1 时 period_key 追加轮次后缀(如 20260801#2)，第1轮不带后缀';


-- -------------------------------------------------------------------------------------
-- 三、period_key 的注释补上轮次形态
-- -------------------------------------------------------------------------------------
ALTER TABLE `t_task_record`
    MODIFY COLUMN `period_key` varchar(32) NOT NULL DEFAULT 'NONE'
        COMMENT '业务期数标识(防重用)：NONE / 日期(20260402) / 周(2026W14)；limit_count>1 时第2轮起带后缀(20260402#2)';


-- =====================================================================================
-- 自查
-- =====================================================================================

-- 1. 注释已更新
SELECT column_name, column_type, column_default, column_comment
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 't_task_config'
  AND column_name IN ('target_audience', 'limit_type', 'limit_count');

-- 2. 🔴 存量里配了人群但会受新行为影响的任务（上游若不传 isNewMember，这些任务将不再推进）
--    改造前它们是「配了也对所有人生效」，改造后变成「上游不给属性就丢弃」——
--    这是刻意的行为变更，但要先知道影响到谁。
SELECT id, task_name, trigger_event, target_audience, status
FROM t_task_config
WHERE target_audience IS NOT NULL AND target_audience <> 'ALL';

-- 3. 存量里配了 limit_count>1 的任务（改造前不生效，改造后开始生效）
SELECT id, task_name, limit_type, limit_count, status
FROM t_task_config
WHERE limit_count > 1;
