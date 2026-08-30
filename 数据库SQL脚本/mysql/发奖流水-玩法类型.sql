-- =====================================================================================
-- 发奖流水：新增「玩法类型」
-- 日期：2026-08-30
--
-- 为什么需要它：派发链路要把奖归类成提案来源（DRAW / TASK / LOTTERY / MANUAL），
-- 而此前是拿 activity_code 回头查 t_activity_config 反推的。
--
-- 四个服务拆开之后这条路走不通：发奖派发在【会员服务】，活动配置在【营销服务】，
-- 不在同一个进程里。让消费方反向去查发送方的域，两个服务就又绑在一起了 ——
-- 事件驱动里的正解是【消息自带上下文】，这一列就是那个上下文的落库形态。
--
-- 顺带一个收益：对账时「这个月 DRAW 发了多少奖」不用再 join 活动表。
--
-- 允许为空：存量行没有这个值，派发链路对空值降级为 MANUAL，与加列之前的行为一致。
-- =====================================================================================

ALTER TABLE `t_prize_log`
    ADD COLUMN `activity_type` varchar(32) DEFAULT NULL
        COMMENT '玩法类型 BASIC/DRAW/TASK/LOTTERY：发奖时由发放方写入，派发链路据它归类提案来源'
        AFTER `activity_code`;

-- 存量回填（可选，不回填也不影响正确性 —— 空值降级为 MANUAL）：
-- UPDATE t_prize_log l JOIN t_activity_config a ON a.activity_code = l.activity_code
--    SET l.activity_type = a.activity_type WHERE l.activity_type IS NULL;
