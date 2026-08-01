-- =====================================================================================
-- 任务中台运行态 P0 验收造数
-- 方案：docs/任务中台-改进技术方案.md v2 §6「P0 验收判据」
-- 撰写：2026-08-01
--
-- 前置：先执行 sql-update-log/v3.44.0.sql（建 t_task_record_flow + t_task_record.version）
-- 本脚本可重复执行（先删后建，编码固定，不会越造越多）。
--
-- ⚠️ 造数前的现状核对（读库实测，2026-08-01）—— 说明为什么必须造数而不能直接用现有数据：
--   ① t_task_prize_mapping 里 id=1,2,3 挂的 task_config_id=3/4/5 都<b>已不存在</b>，是孤儿行；
--   ② 所有映射的 prize_code 都是 'SCORE_100'，而 t_prize_config 里<b>没有</b>这个编码
--      （现存奖品编码都是 10 位业务码），发奖时会命中「奖品配置不存在」；
--   ③ t_promotion_config id=1（积分池）total_amount=50 / used_amount=50，<b>预算已耗尽</b>，
--      任何 SCORE 派发都会被预算硬闸门拦在 status=70；
--   ④ 唯一的任务配置 limit_type=ONCE、且没有阶梯配置，验不了判据 2。
-- =====================================================================================


-- -------------------------------------------------------------------------------------
-- 〇、清场（可重复执行的前提）
-- -------------------------------------------------------------------------------------
DELETE FROM t_task_record_flow WHERE member_name LIKE 'p0_%';
DELETE FROM t_task_record      WHERE member_name LIKE 'p0_%';
DELETE FROM t_task_prize_mapping WHERE task_config_id IN
    (SELECT id FROM (SELECT id FROM t_task_config WHERE task_name LIKE 'P0验收-%') t);
DELETE FROM t_task_config   WHERE task_name LIKE 'P0验收-%';
DELETE FROM t_task_template WHERE template_code IN ('TP0COUNT01', 'TP0LADDER1', 'TP0STREAK1');
DELETE FROM t_prize_config  WHERE prize_code IN ('PP0SCORE01', 'PP0SCORE02', 'PP0COUPON1');
DELETE FROM t_activity_config WHERE activity_code = 'AP0TASKRUN';

-- 顺带清掉①里那三行孤儿映射（它们指向的任务配置已不存在，留着只会让排查时看花眼）
DELETE FROM t_task_prize_mapping
WHERE task_config_id NOT IN (SELECT id FROM (SELECT id FROM t_task_config) t);


-- -------------------------------------------------------------------------------------
-- 一、活动（编码遵循铁律 8：A=活动前缀 + 9 位随机，总长 10）
-- -------------------------------------------------------------------------------------
INSERT INTO t_activity_config (activity_code, activity_name, activity_type, status, start_time, end_time)
VALUES ('AP0TASKRUN', 'P0验收-任务运行态', 'TASK', 1, '2026-01-01 00:00:00', '2099-12-31 23:59:59');


-- -------------------------------------------------------------------------------------
-- 二、奖品（P=奖品前缀）
--     promotion_config_id 指向积分池(1)/优惠券池(3)，与 prize_type 必须一致 ——
--     AssetDispatchEngine 是按「优惠配置的 prize_type」选发货策略的，配错会发错东西。
-- -------------------------------------------------------------------------------------
INSERT INTO t_prize_config
    (activity_code, promotion_config_id, prize_type, prize_name, prize_code, prize_value, approve_mode, sort_weight, status)
VALUES
    -- approve_mode=0 免审：验收要一路走到钱包，别卡在人工审批
    ('AP0TASKRUN', 1, 'SCORE',  'P0-10积分',   'PP0SCORE01', 10.0000, 0, 10, 1),
    ('AP0TASKRUN', 1, 'SCORE',  'P0-30积分',   'PP0SCORE02', 30.0000, 0, 20, 1),
    ('AP0TASKRUN', 3, 'COUPON', 'P0-20元券',   'PP0COUPON1', 20.0000, 0, 30, 1);


-- -------------------------------------------------------------------------------------
-- 三、任务模板（T=任务模板前缀）
--     🔴 ui_schema 的参数键用<b>契约主形态</b> targetCount，不要再用存量模板那个 targetDays。
--        键名是第②层的契约不是自由文本：起错名字的后果是「进度照涨、永远不完成」，且零报错。
--     rule_script 本期不执行（已降级为兜底通道），给一段说明性内容占位即可。
-- -------------------------------------------------------------------------------------
INSERT INTO t_task_template (template_code, template_name, task_type, trigger_event, ui_schema, rule_script)
VALUES
('TP0COUNT01', 'P0-累计签到', 'COUNT', 'DAILY_SIGN',
 '{"icon":"📅","desc":"累计签到N天送奖励","version":1,"params":[{"key":"targetCount","label":"累计签到目标","widget":"number","min":1,"unit":"天","default":3,"required":true}]}',
 '// P0 阶段进度由 CountTaskStrategy 计算，本脚本不参与运行态（rule_script 已降级为兜底通道）'),

('TP0LADDER1', 'P0-阶梯签到', 'COUNT', 'DAILY_SIGN',
 '{"icon":"🪜","desc":"阶梯奖励：3次送积分、5次再送券","version":1,"params":[{"key":"targetCount","label":"最高档目标","widget":"number","min":1,"unit":"次","default":5,"required":true}]}',
 '// 同上，阶梯由 t_task_prize_mapping 的 stage_level 表达'),

('TP0STREAK1', 'P0-连续签到', 'STREAK', 'DAILY_SIGN',
 '{"icon":"🔥","desc":"连续签到N天，断签清零","version":1,"params":[{"key":"targetCount","label":"连续签到目标","widget":"number","min":1,"unit":"天","default":3,"required":true},{"key":"tolerance","label":"允许断签次数","widget":"number","min":0,"default":0}]}',
 '// STREAK 由 StreakTaskStrategy 计算：断档归零再+1，容忍度由 tolerance 控制');


-- -------------------------------------------------------------------------------------
-- 四、任务配置
--     ⚠️ status 落 1（待生效）与 wizardSubmit 一致 —— 运行态的订阅判据是「status != 3」+ 时间窗，
--        不是「status == 2」：全工程没有任何地方把 1 改成 2，判 ==2 会让所有任务永不触发。
--     ⚠️ limit_type 用 UNLIMITED，让 period_key = NONE，一条记录累加到底；
--        用 DAILY 的话每天一条新记录，判据 1「连发 3 次进度 1/2/3」当天就跑不出来。
-- -------------------------------------------------------------------------------------
INSERT INTO t_task_config
    (activity_code, task_name, template_code, trigger_event, task_group, target_audience,
     limit_type, limit_count, rule_config, sort_weight, status, start_time, end_time)
VALUES
-- 判据 1：单档，累计签到 3 次送 10 积分
('AP0TASKRUN', 'P0验收-累计签到3天', 'TP0COUNT01', 'DAILY_SIGN', 'DAILY', 'ALL',
 'UNLIMITED', 1, '{"taskType":"COUNT","targetCount":3}', 10, 1,
 '2026-01-01 00:00:00', '2099-12-31 23:59:59'),

-- 判据 2：阶梯，3 次送积分 / 5 次再送券（最高档 5）
('AP0TASKRUN', 'P0验收-阶梯签到', 'TP0LADDER1', 'ORDER_PAID', 'DAILY', 'ALL',
 'UNLIMITED', 1, '{"taskType":"COUNT","targetCount":5}', 20, 1,
 '2026-01-01 00:00:00', '2099-12-31 23:59:59'),

-- STREAK：连续签到 3 天，断签清零
('AP0TASKRUN', 'P0验收-连续签到3天', 'TP0STREAK1', 'GOODS_SHARE', 'DAILY', 'ALL',
 'UNLIMITED', 1, '{"taskType":"STREAK","targetCount":3,"tolerance":0}', 30, 1,
 '2026-01-01 00:00:00', '2099-12-31 23:59:59'),

-- AMOUNT：累计消费满 500，单笔需满 100（用来验丢弃流水的原因是不是人话）
('AP0TASKRUN', 'P0验收-累计消费500', 'TP0COUNT01', 'ORDER_AMOUNT', 'DAILY', 'ALL',
 'UNLIMITED', 1, '{"taskType":"AMOUNT","targetAmount":500,"minAmount":100}', 40, 1,
 '2026-01-01 00:00:00', '2099-12-31 23:59:59'),

-- 判据 4 专用：目标值刻意设得极高，让 10 笔并发全部落进同一条记录。
-- ⚠️ 不能复用上面那条 targetAmount=500 的任务 —— 10×100 会在第 5 笔就达标，
--    达标后 advanceMetric 的 WHERE status=0 会正确地挡住后 5 笔，
--    结果停在 500。那是「完成闸门生效」，不是 Lost Update，
--    但两者的现象一模一样（进度偏小），混在一起测就分不清是哪个（铁律 16 的另一种形状）。
('AP0TASKRUN', 'P0验收-并发累加', 'TP0COUNT01', 'CONCURRENT_ADD', 'DAILY', 'ALL',
 'UNLIMITED', 1, '{"taskType":"AMOUNT","targetAmount":999999}', 50, 1,
 '2026-01-01 00:00:00', '2099-12-31 23:59:59');


-- -------------------------------------------------------------------------------------
-- 五、奖励档位
--     🔴 阶梯任务必须真的配出两档，否则判据 2 是「空过」而不是「通过」（铁律 16）。
-- -------------------------------------------------------------------------------------
INSERT INTO t_task_prize_mapping (task_config_id, stage_level, prize_code, prize_mode, stage_condition, prize_strategy)
SELECT id, 1, 'PP0SCORE01', 'FIXED', '{"target": 3}', '{"value": 10}'
FROM t_task_config WHERE task_name = 'P0验收-累计签到3天';

-- 阶梯：第 1 档 3 次送 10 积分，第 2 档 5 次送 20 元券
INSERT INTO t_task_prize_mapping (task_config_id, stage_level, prize_code, prize_mode, stage_condition, prize_strategy)
SELECT id, 1, 'PP0SCORE01', 'FIXED', '{"target": 3}', '{"value": 10}'
FROM t_task_config WHERE task_name = 'P0验收-阶梯签到';
INSERT INTO t_task_prize_mapping (task_config_id, stage_level, prize_code, prize_mode, stage_condition, prize_strategy)
SELECT id, 2, 'PP0COUPON1', 'FIXED', '{"target": 5}', '{"value": 20}'
FROM t_task_config WHERE task_name = 'P0验收-阶梯签到';

INSERT INTO t_task_prize_mapping (task_config_id, stage_level, prize_code, prize_mode, stage_condition, prize_strategy)
SELECT id, 1, 'PP0SCORE02', 'FIXED', '{"target": 3}', '{"value": 30}'
FROM t_task_config WHERE task_name = 'P0验收-连续签到3天';

INSERT INTO t_task_prize_mapping (task_config_id, stage_level, prize_code, prize_mode, stage_condition, prize_strategy)
SELECT id, 1, 'PP0SCORE02', 'FIXED', '{"target": 500}', '{"value": 30}'
FROM t_task_config WHERE task_name = 'P0验收-累计消费500';

INSERT INTO t_task_prize_mapping (task_config_id, stage_level, prize_code, prize_mode, stage_condition, prize_strategy)
SELECT id, 1, 'PP0SCORE02', 'FIXED', '{"target": 999999}', '{"value": 30}'
FROM t_task_config WHERE task_name = 'P0验收-并发累加';


-- -------------------------------------------------------------------------------------
-- 六、🔴 恢复积分池预算，否则派发会全部停在 status=70「预算或发放数量已耗尽」
--     现状：id=1 的 total_amount=50 / used_amount=50 已跑满（上一轮预算硬限流验证的遗留）。
--     不做这一步，判据 1 会以「发奖失败」的形式失败，而根因与任务模块毫无关系 —— 白查一轮。
-- -------------------------------------------------------------------------------------
UPDATE t_promotion_config SET total_amount = -1.0000, used_amount = 0.0000, used_quota = 0
WHERE id = 1 AND prize_type = 'SCORE';


-- =====================================================================================
-- 七、验收查询（跑完事件后逐条核对）
-- =====================================================================================

-- 造数结果自查：四个任务配置、五条档位映射
SELECT c.id, c.task_name, c.trigger_event, c.limit_type, c.status,
       JSON_UNQUOTE(JSON_EXTRACT(c.rule_config, '$.taskType')) AS task_type,
       COUNT(m.id) AS stage_count
FROM t_task_config c
LEFT JOIN t_task_prize_mapping m ON m.task_config_id = c.id
WHERE c.task_name LIKE 'P0验收-%'
GROUP BY c.id, c.task_name, c.trigger_event, c.limit_type, c.status, task_type
ORDER BY c.sort_weight;

-- 判据 1/3：进度与流水
-- SELECT id, member_name, task_config_id, period_key, current_metric, version, status, progress_data
-- FROM t_task_record WHERE member_name LIKE 'p0_%' ORDER BY id;
-- SELECT id, member_name, task_config_id, record_id, event_biz_id, flow_type,
--        delta_metric, after_metric, discard_reason
-- FROM t_task_record_flow WHERE member_name LIKE 'p0_%' ORDER BY id;

-- 判据 2：阶梯必须有 2 行发奖流水，且 external_biz_no 形如 {recordId}:{stage}
-- SELECT id, member_name, prize_code, prize_name, external_biz_no, status, approve_status, fail_reason
-- FROM t_prize_log WHERE member_name LIKE 'p0_%' ORDER BY id;

-- 资产落账
-- SELECT member_name, asset_type, balance FROM t_member_wallet WHERE member_name LIKE 'p0_%';
-- SELECT member_name, coupon_code, coupon_name, valid_start_time, valid_end_time
-- FROM t_member_coupon WHERE member_name LIKE 'p0_%';
-- SELECT id, member_name, asset_type, amount, source_type, source_biz_id, status, remark
-- FROM t_proposal_record WHERE member_name LIKE 'p0_%' ORDER BY id;
