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
-- =======================================================
-- 1. 彩票基础配置表 (纯粹玩法基建，0资产耦合，0密钥明文)
-- =======================================================
DROP TABLE IF EXISTS `t_lottery_config`;
CREATE TABLE `t_lottery_config`
(
    `id`              bigint         NOT NULL AUTO_INCREMENT comment '主键id',
    `activity_code`   varchar(32)    NOT NULL COMMENT '活动编码',
    `lottery_code`    varchar(32)    NOT NULL COMMENT '彩票编码',
    `lottery_name`    varchar(128)   NOT NULL COMMENT '彩票名称',
    `number_charset`  varchar(32)    NOT NULL DEFAULT '0-9' COMMENT '发号字符集',
    `number_length`   tinyint        NOT NULL DEFAULT 5 COMMENT '号码长度',

    -- 【防资损/控盘核心】：直接限制总号码数空间。如 5位纯数字最高 100,000。
    -- Redis 游标绝不会超过这个值，从物理上限死预算，无需在开奖时恶心截断。
    `total_count`     int            NOT NULL COMMENT '单期发行总数上限',

    `status`          tinyint        NOT NULL DEFAULT '1' COMMENT '状态：0-下线, 1-上线',
    `create_by`       varchar(32)    DEFAULT NULL comment '创建人',
    `create_time`     datetime       DEFAULT CURRENT_TIMESTAMP comment '创建时间',
    `update_by`       varchar(32)    DEFAULT NULL comment '更新人',
    `update_time`     datetime       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_lottery_code` (`lottery_code`)
) COMMENT ='彩票配置';


-- =======================================================
-- 2. 彩票期号配置表 (极简生命周期，0混淆盐值冗余)
-- =======================================================
DROP TABLE IF EXISTS `t_lottery_issue`;
CREATE TABLE `t_lottery_issue` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `lottery_code` varchar(32) NOT NULL COMMENT '彩票编码',
  `issue_no` varchar(32) NOT NULL COMMENT '期号',
  `sold_count` int NOT NULL DEFAULT '0' COMMENT '已售/已派发数量',
  `sale_start_time` datetime NOT NULL COMMENT '售卖开始时间',
  `sale_end_time` datetime NOT NULL COMMENT '售卖结束时间',
  `plan_draw_time` datetime DEFAULT NULL COMMENT '计划开奖时间：对外承诺的开奖时刻，与实际执行的 settle_time 区分',
  `settle_time` datetime DEFAULT NULL COMMENT '开奖时间',
  `winning_number` varchar(32) DEFAULT NULL COMMENT '开奖号码',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0-待开奖, 1-部分开奖, 2-已开奖',
  `create_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_issue_no` (`lottery_code`,`issue_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='期号配置';


-- =======================================================
-- 3. 彩票奖励规则表 (结构化匹配逻辑与资产路由)
-- =======================================================
DROP TABLE IF EXISTS `t_lottery_prize_rule`;
CREATE TABLE `t_lottery_prize_rule`
(
    `id`                  bigint         NOT NULL AUTO_INCREMENT comment '主键id',
    `lottery_code`        varchar(32)    NOT NULL COMMENT '彩票编码',

    -- 【核心匹配逻辑】
    `prize_level`         int            NOT NULL COMMENT '奖品奖级',
    `match_rule`          varchar(16)    NOT NULL COMMENT '匹配规则,EXACT:全号, TAIL:尾号匹配, HEAD:首号匹配',
    `match_length`        int            NOT NULL COMMENT '匹配长度',

    -- 【资产桥梁】(彻底解耦，彩票核心只抛出这个 Code)
    `prize_code`          varchar(64)    NOT NULL COMMENT '奖品编码',
    `create_by`           varchar(32)    DEFAULT NULL comment '创建人',
    `create_time`         datetime       DEFAULT CURRENT_TIMESTAMP comment '创建时间',
    `update_by`           varchar(32)    DEFAULT NULL comment '更新人',
    `update_time`         datetime       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_lottery_level` (`lottery_code`, `prize_level`)
) COMMENT ='彩票匹配与资产路由规则表';


-- =======================================================
-- 4. 用户购彩记录表 (极速反查，100% 自证清白)
-- =======================================================
DROP TABLE IF EXISTS `t_lottery_record`;
CREATE TABLE `t_lottery_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `member_id` bigint NOT NULL COMMENT '会员号：关联键',
  `lottery_code` varchar(32) NOT NULL COMMENT '彩票编码',
  `issue_no` varchar(32) NOT NULL COMMENT '期号',
  `sequence_no` int NOT NULL COMMENT 'FPE算号基数',
  `ticket_number` varchar(32) NOT NULL COMMENT '彩票号码',
  `member_name` varchar(32) DEFAULT NULL COMMENT '会员账号【展示快照，非关联键，不要用于查询】',
  `obtain_time` datetime DEFAULT NULL COMMENT '领号时间',
  `win_status` tinyint NOT NULL DEFAULT '0' COMMENT '中奖状态: 0-未开奖, 1-未中奖, 2-已中奖',
  `prize_level` int NOT NULL DEFAULT '99' COMMENT '奖励等级：1..N 为中奖奖级(数字越小奖越大)，99-未中奖/未开奖',
  `prize_code` varchar(64) DEFAULT NULL COMMENT '中奖奖品编码：核销时从规则表快照，防规则被改后历史中奖结果漂移',
  `dispatch_status` tinyint NOT NULL DEFAULT '0' COMMENT '派发状态：0-待派发/无需派发, 1-已投递, 2-投递失败',
  `security_sign` varchar(32) NOT NULL COMMENT '防篡改签名',
  `create_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_issue_ticket` (`lottery_code`,`issue_no`,`ticket_number`),
  KEY `idx_status` (`issue_no`,`win_status`),
  KEY `idx_dispatch` (`issue_no`,`dispatch_status`),
  KEY `idx_member` (`member_id`,`issue_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户号码记录';

-- =======================================================
-- 💥 t_lottery_number_pool (彩票号池表) 已永久移除 
-- =======================================================