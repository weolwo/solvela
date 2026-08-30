-- =====================================================================================
-- 消息接收记录：隔离列改名 consumer_key
-- 日期：2026-08-30
--
-- 原来唯一键是 (message_id, queue)，隔离粒度是【队列】。但真正需要隔离的是【消费者】：
--   · 活动事件：一条会员登录消息被 A、B 两个活动消费 —— 后台重试 A 不能碰到 B；
--   · 发奖回写：消费它的是一个固定 handler，不是活动。
-- 两类消息共用一张表，所以这一列不能叫 activity_code，叫 consumer_key：
--   活动事件填活动编码，固定消费者填 handler 名。
--
-- queue 列保留（定位用），但不再参与唯一键。
-- =====================================================================================

ALTER TABLE `t_mq_message_log`
    ADD COLUMN `consumer_key` varchar(64) NOT NULL DEFAULT '' 
        COMMENT '消费者标识：活动事件填活动编码，固定消费者填 handler 名。后台重试按它隔离 —— 重跑 A 活动不会碰到 B'
        AFTER `routing_key`;

ALTER TABLE `t_mq_message_log` DROP INDEX `uk_mq_msg`;
ALTER TABLE `t_mq_message_log` ADD UNIQUE KEY `uk_mq_msg` (`message_id`,`consumer_key`);
ALTER TABLE `t_mq_message_log` ADD KEY `idx_mq_retry` (`consumer_key`,`status`,`receive_time`);

-- ⚠️ 唯一键必须含 consumer_key：只按 message_id 唯一的话，同一条消息的第二个消费者
--    插不进去，会被误判成「重复投递」而静默跳过 —— 正是「一个活动收到了、另一个没收到」
--    这类最难查的问题。
