-- =====================================================================================
-- 发奖投递 outbox
-- 日期：2026-08-30
--
-- 为什么需要这张表：拆成四个服务之后，「抽奖」在营销服务、「派发」在会员服务，
-- 中间靠 RabbitMQ 连接。而 MQ 解决的是【投递】，解决不了这个窗口：
--
--     事务提交成功  →  ✅ 奖已判定、流水已落库
--                   →  ❌ 进程在发消息之前挂了
--     结果：奖没发出去，而且【没有任何地方记得这件事】
--
-- publisher-confirm 也救不了它 —— 确认回调只能告诉你 broker 收没收到，
-- 前提是消息真的发出去了。
--
-- 所以：MQ 负责投递，本表负责不丢。发消息之前先在【同一个事务里】写一行，
-- 提交后再投递；投递成功标记完成，失败/进程挂掉则由重投任务扫出来重发。
--
-- ⚠️ 消费方必须幂等：重投必然带来重复投递。发奖侧靠 t_prize_log 的
--    uk_external_biz 唯一索引兜底，与 source_biz_id 是同一个值。
-- =====================================================================================

DROP TABLE IF EXISTS `t_prize_dispatch_outbox`;
CREATE TABLE `t_prize_dispatch_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `source_biz_id` varchar(64) NOT NULL COMMENT '来源单号：与 t_prize_log.external_biz_no 同值，消费方据它幂等',
  `routing_key` varchar(64) NOT NULL COMMENT '路由键，见 MqTopology',
  `payload` mediumtext NOT NULL COMMENT '事件 JSON 原文。存原文而不是存字段：重投时不需要再拼一次，也就不可能拼得跟当初不一样',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0-待投递, 1-已投递',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重投次数。持续增长说明下游有问题，是最直接的告警指标',
  `last_error` varchar(255) DEFAULT NULL COMMENT '最后一次失败原因，截断到列宽',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '写入时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  -- 同一个来源单号只写一行：重复发奖在这里就被挡住，不用等到消费端
  UNIQUE KEY `uk_outbox_biz` (`source_biz_id`),
  -- 重投任务的扫描索引：只扫待投递的，按写入时间先进先出
  KEY `idx_outbox_pending` (`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发奖投递 outbox：MQ 负责投递，本表负责不丢';
