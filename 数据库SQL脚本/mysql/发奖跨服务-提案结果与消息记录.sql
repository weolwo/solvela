-- =====================================================================================
-- 发奖跨服务改造：prize_log 记录提案侧结果 + 通用消息接收记录表
-- 日期：2026-08-30
--
-- 背景：营销与会员拆成两个服务之后，「发奖」变成两段：
--   ① 同步 HTTP：marketing → member 新增提案，当场知道【受理还是被拒、被拒的原因】
--   ② 异步消息：member 资产真正入账（可能经人工审批，几小时后）→ 回调 marketing 落终态
--
-- 此前这两件事都压在 t_prize_log.status 一个字段上，而且终态是 member 侧的
-- AssetDispatchEngine【直接写 marketing 的表】—— 拆开之后这条路不通了。
-- =====================================================================================

-- 1) 提案侧的结果：同步调用当场就知道，与「最终有没有发到用户手上」是两件事
ALTER TABLE `t_prize_log`
    ADD COLUMN `proposal_status` tinyint NOT NULL DEFAULT '0'
        COMMENT '提案侧结果：0-待提交, 1-已受理(提案已生成), 2-被拒绝。与 status 是两件事：本列说的是「会员服务收没收下」，status 说的是「用户最终有没有拿到」'
        AFTER `status`,
    ADD COLUMN `proposal_id` bigint DEFAULT NULL
        COMMENT '会员服务返回的提案 id。对账与人工排查用 —— 回调靠 source_biz_id 关联即可，但出问题时能直接拿这个 id 去会员库里查'
        AFTER `proposal_status`;

-- 待提交的那批要能被重投任务扫出来：进程在同步调用前挂了，行会停在 0
CREATE INDEX `idx_prize_log_proposal` ON `t_prize_log` (`proposal_status`, `create_time`);

-- 2) 通用消息接收记录
DROP TABLE IF EXISTS `t_mq_message_log`;
CREATE TABLE `t_mq_message_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `message_id` varchar(64) NOT NULL COMMENT '消息唯一标识（发送方生成）。🔴 唯一索引即【消费幂等】：重复投递在插入那一刻就被挡住，不用每个消费者各写一套去重',
  `exchange` varchar(64) NOT NULL COMMENT '交换机',
  `routing_key` varchar(64) NOT NULL COMMENT '路由键。将来活动挂事件监听就是按它路由的',
  `queue` varchar(64) NOT NULL COMMENT '队列名：同一条消息可能被多个队列消费，队列名参与定位',
  `payload` mediumtext NOT NULL COMMENT '消息 JSON 原文。存原文而不是解析后的字段 —— 重放时不需要再拼一次',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0-已接收, 1-处理成功, 2-处理失败',
  `fail_reason` varchar(255) DEFAULT NULL COMMENT '处理失败原因，截断到列宽',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数。持续增长是最直接的告警指标',
  `receive_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
  `handle_time` datetime DEFAULT NULL COMMENT '处理完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mq_msg` (`message_id`,`queue`),
  KEY `idx_mq_status` (`status`,`receive_time`),
  -- 只留 7 天，清理任务按接收时间删
  KEY `idx_mq_receive_time` (`receive_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息接收记录：唯一索引即消费幂等，只保留 7 天';

-- ⚠️ 唯一索引是 (message_id, queue) 而不是单 message_id：
--    同一条消息被多个队列消费是正常的（一个 topic 交换机绑多个队列），
--    单 message_id 唯一会让第二个消费者插不进去、误判成重复投递而跳过处理。
