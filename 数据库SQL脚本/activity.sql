-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- ⚠️⚠️ 本文件<b>不是权威定义</b>。权威是 mysql/schema-baseline.sql（从真库导出、空库验证过）。
--
-- 保留它是为了那些<b>解释「为什么这么设计」的注释</b> —— 基线是机器导出的，只有结构没有理由。
-- 计划：等各模块开发完工后，把设计注释搬进 docs/营销中台-会话交接文档.md，
--       然后删掉本文件，只留基线。
--
-- 🔴 在那之前，改表结构必须<b>同时</b>改基线和本文件，并跑一次漂移检查：
--       cd 数据库SQL脚本/tools && java -cp <mysql-connector.jar> CheckModuleDrift.java
--    2026-08-22 首次跑这个检查时，分域文件已经漂了 11 张表 —— 其中
--    t_task_record 缺 version、t_task_template 缺 status 是<b>很久以前</b>就漂的，
--    一直没人发现。靠纪律维护两份定义是不成立的，所以才有这个检查。
--
-- 1. 活动主表 (大促容器)
DROP TABLE IF EXISTS `t_activity_config`;
CREATE TABLE `t_activity_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `activity_code` varchar(32) NOT NULL COMMENT '活动编码',
  `activity_name` varchar(64) NOT NULL COMMENT '活动名称',
  `activity_type` varchar(32) NOT NULL COMMENT '活动类型：BASIC-基础活动(仅外壳,不挂玩法) / DRAW-奖池抽奖 / TASK-任务驱动 / LOTTERY-FPE彩票',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0-未开始, 1-上线, 2-下线',
  `start_time` datetime NOT NULL COMMENT '活动开始时间',
  `end_time` datetime NOT NULL COMMENT '活动结束时间',
  `data_end_time` datetime DEFAULT NULL COMMENT '数据截止时间：此刻起不再受理参与（抽奖/任务累计），但活动仍可见、奖品仍可领到 end_time。为空表示与 end_time 相同',
  `create_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_act_code` (`activity_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动配置';

-- 2. 活动全局奖项库 (全局风控与白名单)
DROP TABLE IF EXISTS `t_prize_pool_item`;
CREATE TABLE `t_prize_pool_item`
(
    `id`             bigint         NOT NULL AUTO_INCREMENT comment 'id',
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
CREATE TABLE `t_prize_pool_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `activity_code` varchar(32) NOT NULL COMMENT '活动编码',
  `pool_code` varchar(32) NOT NULL COMMENT '奖池唯一编码 (如: VIP_POOL)',
  `pool_name` varchar(128) NOT NULL COMMENT '奖池名称',
  `reset_period` varchar(32) NOT NULL DEFAULT 'DAY' COMMENT '重置周期，天，周，月，活动期间',
  `draw_mode` tinyint DEFAULT '1' COMMENT '抽奖算法: 1-按概率(probability), 2-按库存比例(stock_ratio)',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0关闭，1开启',
  `create_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pool_code` (`pool_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='奖池配置';

-- 4. 奖池转盘格子映射表 (纯概率配置)
DROP TABLE IF EXISTS `t_pool_prize_mapping`;
CREATE TABLE `t_pool_prize_mapping`
(
    `id`            bigint        NOT NULL AUTO_INCREMENT comment 'id',
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
CREATE TABLE `t_draw_prize_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `member_id` bigint NOT NULL COMMENT '会员号：关联键',
  `trace_id` varchar(64) NOT NULL COMMENT '请求ID',
  `activity_code` varchar(32) NOT NULL COMMENT '活动编码',
  `pool_code` varchar(32) NOT NULL COMMENT '奖池编码',
  `member_name` varchar(32) DEFAULT NULL COMMENT '会员账号【展示快照，非关联键，不要用于查询】',
  `prize_item_id` bigint NOT NULL COMMENT '奖项ID',
  `prize_code` varchar(32) NOT NULL COMMENT '奖品code',
  `status` tinyint DEFAULT '0' COMMENT '状态: 0-未中奖, 1-已中奖, 2-库存不足, 3-异常',
  `remark` varchar(64) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_mem_act` (`member_id`,`activity_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽奖记录';
