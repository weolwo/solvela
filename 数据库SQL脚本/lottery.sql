DROP TABLE IF EXISTS `t_lottery_config`;
CREATE TABLE `t_lottery_config`
(
    `id`              bigint         NOT NULL AUTO_INCREMENT comment 'id',
    `tenant_id`       varchar(16)    NOT NULL DEFAULT '0' comment '租户id',
    `activity_code`   varchar(32)    NOT NULL COMMENT '活动编码',
    `lottery_code`    varchar(32)    NOT NULL COMMENT '彩票编码',
    `lottery_name`    varchar(128)   NOT NULL COMMENT '彩票名称',
    `number_charset`  varchar(32)    NOT NULL DEFAULT '0-9' COMMENT '字符集：0-9, A-Z',
    `number_length`   tinyint        NOT NULL DEFAULT 5 COMMENT '号码长度',
    `total_count`     int            NOT NULL COMMENT '号池总数 (如: 1,000,000)',
    `cost_asset_type` varchar(32)    NOT NULL DEFAULT 'SCORE' COMMENT '入场成本类型 (SCORE, FREE)',
    `cost_value`      decimal(18, 4) NOT NULL DEFAULT '0' COMMENT '入场成本-数值',

    `status`          tinyint        NOT NULL DEFAULT '1' COMMENT '状态：0-下线, 1-上线',
    `create_by`     varchar(32)          DEFAULT NULL comment '创建人',
    `create_time`   datetime             DEFAULT CURRENT_TIMESTAMP comment '创建时间',
    `update_by`     varchar(32)          DEFAULT NULL comment '更新人',
    `update_time`   datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_lottery_code` (`lottery_code`)
) COMMENT ='彩票配置';
DROP TABLE IF EXISTS `t_lottery_issue`;
CREATE TABLE `t_lottery_issue`
(
    `id`              bigint      NOT NULL AUTO_INCREMENT comment 'id',
    `tenant_id`       varchar(16) NOT NULL DEFAULT '0' comment '租户id',
    `lottery_code`    varchar(32) NOT NULL COMMENT '彩票编码',
    `issue_no`        varchar(32) NOT NULL COMMENT '期号',
    `start_offset`         int NOT NULL COMMENT '开始偏移量',
    `sold_count`      int         NOT NULL DEFAULT 0 COMMENT '已售/已派发数量',
    -- 【时间与开奖控制】
    `sell_start_time` datetime    NOT NULL COMMENT '开始售卖',
    `sell_end_time`   datetime    NOT NULL COMMENT '结束时间',
    `open_time`       datetime             DEFAULT NULL COMMENT '开奖时间',
    `can_repeat`      tinyint     NOT NULL DEFAULT 0 COMMENT '是否可重复开奖：0否，1是',
    `winning_number`  json                 DEFAULT NULL COMMENT '开奖号码',
    `status`          tinyint     NOT NULL DEFAULT '0' COMMENT '状态: 0-待开奖, 1-部分开奖, 2-已开奖',
    `create_by`     varchar(32)          DEFAULT NULL comment '创建人',
    `create_time`   datetime             DEFAULT CURRENT_TIMESTAMP comment '创建时间',
    `update_by`     varchar(32)          DEFAULT NULL comment '更新人',
    `update_time`   datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_issue_no` (`lottery_code`, `issue_no`)
) COMMENT ='期号配置';
DROP TABLE IF EXISTS `t_lottery_prize_rule`;
CREATE TABLE `t_lottery_prize_rule`
(
    `id`           bigint         NOT NULL AUTO_INCREMENT comment 'id',
    `tenant_id`    varchar(16)    NOT NULL DEFAULT '0' comment '租户id',
    `lottery_code` varchar(32)    NOT NULL COMMENT '彩票编码',
    `issue_no`     varchar(32)    NOT NULL COMMENT '期号',
    `prize_details` json          NOT NULL COMMENT '奖励明细',
    `create_by`     varchar(32)          DEFAULT NULL comment '创建人',
    `create_time`   datetime             DEFAULT CURRENT_TIMESTAMP comment '创建时间',
    `update_by`     varchar(32)          DEFAULT NULL comment '更新人',
    `update_time`   datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_lottery_level` (`lottery_code`, `issue_no`)
) COMMENT ='彩票奖励配置';

DROP TABLE IF EXISTS `t_lottery_record`;
CREATE TABLE `t_lottery_record`
(
    `id`            bigint       NOT NULL AUTO_INCREMENT comment 'id',
    `tenant_id`     varchar(16)  NOT NULL DEFAULT '0'comment '租户id',
    `lottery_code`  varchar(32)  NOT NULL COMMENT '彩票编码',
    `issue_no`      varchar(32)  NOT NULL COMMENT '归属期号',
    `ticket_number` varchar(32)  NOT NULL COMMENT '彩票号码',

    -- 【用户归属】初始为NULL，售出时UPDATE
    `member_name`   varchar(64)           DEFAULT NULL COMMENT '会员名',
    `source_type`   varchar(32)           DEFAULT NULL COMMENT '获取来源: EXCHANGE, TASK_REWARD',
    `source_biz_id` varchar(64)           DEFAULT NULL COMMENT '溯源单号',
    `obtain_time`   datetime              DEFAULT NULL COMMENT '领取时间',

    -- 【状态管理】
    `win_status`    tinyint      NOT NULL DEFAULT 0 COMMENT '中奖状态: 0-未开奖, 1-未中奖, 已开奖',
    `prize_level`   int                   DEFAULT NULL COMMENT '奖励等级',
    `security_sign` varchar(128) NOT NULL COMMENT '签名',
    `create_by`     varchar(32)          DEFAULT NULL comment '创建人',
    `create_time`   datetime             DEFAULT CURRENT_TIMESTAMP comment '创建时间',
    `update_by`     varchar(32)          DEFAULT NULL comment '更新人',
    `update_time`   datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
    PRIMARY KEY (`id`),
    -- 【核心索引】
    UNIQUE KEY `uk_issue_ticket` (`lottery_code`, `issue_no`, `ticket_number`),
    -- C端用户查询我的号码簿索引
    KEY `idx_member` (`member_name`, `issue_no`)
) COMMENT ='用户号码记录';
DROP TABLE IF EXISTS `t_lottery_number_pool`;
CREATE TABLE `t_lottery_number_pool`
(
    `id`            bigint      NOT NULL AUTO_INCREMENT comment 'id',
    `lottery_code`  varchar(32) NOT NULL COMMENT '彩票编码',
    `ticket_number` varchar(32) NOT NULL COMMENT '彩票号码',
    -- 【终极发号游标键】每个活动内部绝对连续的序号 (1, 2, 3... 100000)
    `sequence_no`   int         NOT NULL COMMENT '发号序列号',
    PRIMARY KEY (`id`),
    -- 索引用于：开奖时根据中奖号字符串反查 ID
    UNIQUE KEY `uk_lottery_num` (`lottery_code`, `ticket_number`),
    KEY `idx_number_pool_member` (`sequence_no`)
) COMMENT ='彩票号码池';
