-- 1. 活动主表 (大促容器)
DROP TABLE IF EXISTS `t_activity_config`;
CREATE TABLE `t_activity_config`
(
    `id`            bigint      NOT NULL AUTO_INCREMENT comment 'id',
    `tenant_id`     varchar(16) NOT NULL DEFAULT '0' comment '租户id',
    `activity_code` varchar(32) NOT NULL COMMENT '活动编码：10位大写字母+数字，全局唯一',
    `activity_name` varchar(64) NOT NULL COMMENT '活动名称',
    `activity_type` varchar(32) NOT NULL COMMENT '活动类型：BASIC-基础活动(仅外壳,不挂玩法) / DRAW-奖池抽奖 / TASK-任务驱动 / LOTTERY-FPE彩票',
    `status`        tinyint     NOT NULL DEFAULT 0 COMMENT '状态：0-未开始, 1-上线, 2-下线',
    `start_time`    datetime    NOT NULL COMMENT '活动开始时间',
    `end_time`      datetime    NOT NULL COMMENT '活动结束时间',
    `script_id`     varchar(32)          DEFAULT NULL COMMENT '规则脚本id',
    `create_by`     varchar(32)          DEFAULT NULL comment '创建人',
    `create_time`   datetime             DEFAULT CURRENT_TIMESTAMP comment '创建时间',
    `update_by`     varchar(32)          DEFAULT NULL comment '更新人',
    `update_time`   datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_act_code` (`activity_code`)
) COMMENT ='活动配置';

-- 2. 活动全局奖项库 (全局风控与白名单)
DROP TABLE IF EXISTS `t_prize_pool_item`;
CREATE TABLE `t_prize_pool_item`
(
    `id`             bigint         NOT NULL AUTO_INCREMENT comment 'id',
    `tenant_id`      varchar(16)    NOT NULL DEFAULT '0' comment '租户id',
    `activity_code`  varchar(32)    NOT NULL COMMENT '活动编码',
    `prize_code`     varchar(64)    NOT NULL COMMENT '奖品编码',
    `user_max_count` int            NOT NULL DEFAULT '-1' COMMENT '单人限领次数: -1不限, 1表示每人最多中一次',
    `total_stock`    int            NOT NULL DEFAULT '-1' COMMENT '总库存',
    `used_stock`     int            NOT NULL DEFAULT '0' COMMENT '已用库存',
    `version`        int                     DEFAULT 0 COMMENT '乐观锁版本号',
    `white_list`     json                    DEFAULT NULL COMMENT '白名单：指定用户必中',

    `create_by`      varchar(32)             DEFAULT NULL comment '创建人',
    `create_time`    datetime                DEFAULT CURRENT_TIMESTAMP comment '创建时间',
    `update_by`      varchar(32)             DEFAULT NULL comment '更新人',
    `update_time`    datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_activity` (`activity_code`)
) COMMENT ='奖池奖项';

-- 3. 奖池配置表 (多池支持与抽奖门票)
DROP TABLE IF EXISTS `t_prize_pool_config`;
CREATE TABLE `t_prize_pool_config`
(
    `id`              bigint         NOT NULL AUTO_INCREMENT comment 'id',
    `tenant_id`       varchar(16)    NOT NULL DEFAULT '0' comment '租户id',
    `activity_code`   varchar(32)    NOT NULL COMMENT '活动编码',
    `pool_code`       varchar(32)    NOT NULL COMMENT '奖池编码：10位大写字母+数字，全局唯一 (如: H88JHKJFNE)',
    `pool_name`       varchar(128)   NOT NULL COMMENT '奖池名称',
    -- ⚠️ cost_asset_type / cost_value 已于 v3.64.0 移除：
    -- 抽一次消耗什么、消耗多少属于业务规则，和「去哪个奖池抽」一样由上游算完再调进来，
    -- 不是抽奖引擎该决定的事（同 t_lottery_config：彩票模块从一开始就没有消耗字段）。
    `reset_period`    varchar(32)    NOT NULL DEFAULT 'DAY' COMMENT '重置周期，天，周，月，活动期间',
    `draw_mode`       tinyint        NULL     DEFAULT 1 COMMENT '抽奖算法: 1-按概率(probability), 2-按库存比例(stock_ratio)',
    `script_id`       varchar(64)             DEFAULT NULL COMMENT '进入该奖池的前置脚本',
    `status`          tinyint        NOT NULL DEFAULT 1 comment '0关闭，1开启',
    `create_by`       varchar(32)             DEFAULT NULL comment '创建人',
    `create_time`     datetime                DEFAULT CURRENT_TIMESTAMP comment '创建时间',
    `update_by`       varchar(32)             DEFAULT NULL comment '更新人',
    `update_time`     datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pool_code` (`pool_code`)
) COMMENT ='奖池配置';

-- 4. 奖池转盘格子映射表 (纯概率配置)
DROP TABLE IF EXISTS `t_pool_prize_mapping`;
CREATE TABLE `t_pool_prize_mapping`
(
    `id`            bigint        NOT NULL AUTO_INCREMENT comment 'id',
    `tenant_id`     varchar(16)   NOT NULL DEFAULT '0' comment '租户id',
    `pool_code`     varchar(32)   NOT NULL COMMENT '奖池编码',
    `prize_item_id` bigint        NOT NULL COMMENT '奖项id',
    `probability`   decimal(8, 4) NOT NULL DEFAULT 0.0000 COMMENT '中奖概率(万分位)',
    `is_fallback`   tinyint       NOT NULL DEFAULT 0 COMMENT '是否兜底奖项：1-兜底(自动吃掉剩余概率/库存不足时降级命中)，每池最多一个',
    `sort_weight`   int           NOT NULL DEFAULT 0 COMMENT '序号',

    `create_by`     varchar(32)            DEFAULT NULL comment '创建人',
    `create_time`   datetime               DEFAULT CURRENT_TIMESTAMP comment '创建时间',
    `update_by`     varchar(32)            DEFAULT NULL comment '更新人',
    `update_time`   datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pool_prize` (`pool_code`, `prize_item_id`)
) COMMENT ='奖池奖项映射';

-- 5. 抽奖流水记录表
DROP TABLE IF EXISTS `t_draw_prize_log`;
CREATE TABLE `t_draw_prize_log`
(
    `id`            bigint      NOT NULL AUTO_INCREMENT comment 'id',
    `tenant_id`     varchar(16) NOT NULL DEFAULT '0' comment '租户id',
    `trace_id`      varchar(64) NOT NULL COMMENT '请求ID',
    `activity_code` varchar(32) NOT NULL COMMENT '活动编码',
    `pool_code`     varchar(32) NOT NULL COMMENT '奖池编码',
    `member_name`   varchar(64) NOT NULL COMMENT '会员名',

    `prize_item_id` bigint      NOT NULL COMMENT '奖项ID',
    `prize_code`    varchar(32) NOT NULL COMMENT '奖品code',
    `status`        tinyint     NULL     DEFAULT 0 COMMENT '状态: 0-未中奖, 1-已中奖, 2-库存不足, 3-异常',
    `remark`        varchar(64) NULL COMMENT '备注',

    `create_time`   datetime             DEFAULT CURRENT_TIMESTAMP comment '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_mem_act` (`member_name`, `activity_code`)
) COMMENT ='抽奖记录';
