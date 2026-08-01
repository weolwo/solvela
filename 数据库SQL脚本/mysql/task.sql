-- ==========================================
-- 一、 核心配置域 (Config Domain)
-- ==========================================

DROP TABLE IF EXISTS `t_task_template`;
CREATE TABLE `t_task_template`
(
    `id`            bigint       NOT NULL AUTO_INCREMENT comment 'id',
    `tenant_id`     varchar(16)  NOT NULL DEFAULT '0' COMMENT '租户ID',
    `template_code` varchar(64)  NOT NULL COMMENT '模板编码',
    `template_name` varchar(128) NOT NULL COMMENT '模板名称',
    `task_type`     varchar(32)  NOT NULL COMMENT '流转类型：SIMPLE(单次节点型), COUNT(计次型), AMOUNT(计额型)',
    `trigger_event` varchar(32)  NULL     DEFAULT NULL COMMENT '默认触发事件：模板建议值，向导中可覆盖',
    `ui_schema`     json         NOT NULL COMMENT '前端渲染规则',
    `rule_script`   text         NOT NULL COMMENT 'QLExpress脚本',
    `create_by`     varchar(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`   datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     varchar(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`   datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_tpl_code` (`template_code`)
) COMMENT ='任务模板表';

DROP TABLE IF EXISTS `t_task_config`;
CREATE TABLE `t_task_config`
(
    `id`              bigint       NOT NULL AUTO_INCREMENT comment 'id',
    `activity_code`   varchar(16)  NOT NULL COMMENT '活动编码',
    `tenant_id`       varchar(16)  NOT NULL DEFAULT '0' COMMENT '租户ID',
    `task_name`       varchar(128) NOT NULL COMMENT '任务名称',
    `template_code`   varchar(64)  NOT NULL COMMENT '模板Code',
    `trigger_event`   varchar(64)  NOT NULL COMMENT '触发事件：ORDER_PAID(支付), MEMBER_REGISTER(注册), DAILY_SIGN(签到), PAGE_VIEW(浏览), CUSTOM(自定义)',
    `task_group`      varchar(32)           DEFAULT 'DEFAULT' COMMENT '任务分组：NEWBIE(新手), DAILY(日常), PROMO(大促), VIP(会员专属)',
    `target_audience` varchar(32)           DEFAULT 'ALL' COMMENT '目标人群：ALL(全部), NEW_MEMBER(新会员), OLD_MEMBER(老会员)',
    `limit_type`      varchar(32)  NOT NULL DEFAULT 'ONCE' COMMENT '参与频次：ONCE(终身一次), DAILY(每日重复), WEEKLY(每周重复), UNLIMITED(无限制)',
    `limit_count`     int          NOT NULL DEFAULT '1' COMMENT '配合频次类型，限制次数',
    `rule_config`     json         NOT NULL COMMENT '规则配置',
    `sort_weight`     int          NULL     DEFAULT '0' COMMENT '排序权重，越大越靠前',
    `action_url`      varchar(255)          DEFAULT NULL COMMENT '跳转地址',
    `ui_config`       json                  DEFAULT NULL COMMENT '展示UI(图标/角标等)',
    `status`          tinyint      NULL     DEFAULT '0' COMMENT '任务状态 1-待生效, 2-生效中, 3-已下线',
    `start_time`      datetime              DEFAULT NULL COMMENT '开始时间',
    `end_time`        datetime              DEFAULT NULL COMMENT '结束时间',
    `create_by`       varchar(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`     datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       varchar(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`     datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_t_biz_tsk_cfg_grp_aud` (`task_group`, `target_audience`),
    KEY `idx_t_biz_tsk_cfg_evt_sts` (`trigger_event`, `status`)
) COMMENT ='任务配置表';

-- ==========================================
-- 二、 奖品包与资产兜底域 (Reward & Asset Config)
-- ==========================================

DROP TABLE IF EXISTS `t_promotion_config`;
CREATE TABLE `t_promotion_config`
(
    `id`                      bigint         NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    `tenant_id`               varchar(16)    NOT NULL DEFAULT '0' COMMENT '租户ID',
    `promo_name`              varchar(128)   NOT NULL COMMENT '优惠配置名称',
    `prize_type`              varchar(32)    NOT NULL COMMENT '资产类型：SCORE(积分), BALANCE(现金), COUPON(优惠券), PHYSICAL(实物)',

    -- 预算与库存控制
    `total_quota`             int            NOT NULL DEFAULT -1 COMMENT '总库存(个数)：-1为不限制(适用于券/实物)',
    `used_quota`              int            NOT NULL DEFAULT 0 COMMENT '已消耗库存(个数)',
    `total_amount`            decimal(18, 4) NOT NULL DEFAULT '-1.0000' COMMENT '总预算(金额)：-1为不限制(适用于积分/现金)',
    `used_amount`             decimal(18, 4) NOT NULL DEFAULT '0.0000' COMMENT '已消耗预算(金额)',
    `review_level`            tinyint        NOT NULL DEFAULT 0 COMMENT '审核层级控制：0-无需审核, 1-单层审批, 2-双层审批',

    `first_review_threshold`  decimal(18, 4) NOT NULL DEFAULT 0.0000 COMMENT '一审触发阈值：动账金额 >= 此值必须一审(值为0代表笔笔一审)',
    `second_review_threshold` decimal(18, 4) NOT NULL DEFAULT 0.0000 COMMENT '二审触发阈值：动账金额 >= 此值必须二审(前提 review_level=2)',
    -- 单次发放兜底限制
    `single_max_quota`        int            NOT NULL DEFAULT 1 COMMENT '单次最大数量兜底，超限阻断',
    `single_max_amount`       decimal(18, 4) NOT NULL DEFAULT 0.0000 COMMENT '单次最大金额兜底，超限阻断',

    -- 风控与防刷规则
    `limit_period`            varchar(32)    NOT NULL DEFAULT 'LIFETIME' COMMENT '限制周期：LIFETIME(终身), DAILY(每日), WEEKLY(每周), MONTHLY(每月), CUSTOM',
    `identify_limit`          int            NULL     DEFAULT -1 COMMENT '同周期内，单会员ID最多领取次数 (-1为不限)',
    `phone_limit`             int            NULL     DEFAULT -1 COMMENT '同周期内，单手机号最多领取次数 (-1为不限)',
    `ip_limit`                int            NULL     DEFAULT -1 COMMENT '同周期内，单IP地址最多领取次数 (-1为不限)',
    `device_limit`            int            NULL     DEFAULT -1 COMMENT '同周期内，单设备硬件号(IMEI/IDFA)最多领取次数 (-1为不限)',
    `fingerprint_limit`       int            NULL     DEFAULT -1 COMMENT '同周期内，单客户端指纹最多领取次数 (-1为不限)',
    `mutex_rule`              json                    DEFAULT NULL COMMENT '互斥规则：存互斥的优惠配置ID数组',

    `status`                  tinyint        NULL     DEFAULT '1' COMMENT '状态：0-停用, 1-启用',
    `create_by`               varchar(64)             DEFAULT NULL COMMENT '创建人',
    `create_time`             datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`               varchar(64)             DEFAULT NULL COMMENT '更新人',
    `update_time`             datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) COMMENT ='优惠配置表';


DROP TABLE IF EXISTS `t_prize_config`;
CREATE TABLE `t_prize_config`
(
    `id`                  bigint         NOT NULL AUTO_INCREMENT comment 'id',
    `tenant_id`           varchar(16)    NOT NULL DEFAULT '0' COMMENT '租户ID',
    `activity_code`       varchar(32)    NOT NULL COMMENT '归属活动编码：10位大写字母+数字',
    `promotion_config_id` bigint         NOT NULL COMMENT '优惠配置ID',
    `prize_type`          varchar(32)    NOT NULL COMMENT '资产类型：SCORE, BALANCE, COUPON, PHYSICAL, LOTTERY, CUSTOM',
    `prize_name`          varchar(128)   NOT NULL COMMENT '奖品名称',
     prize_code            varchar(64)    NOT null COMMENT '奖品编码：10位大写字母+数字，全局唯一',
    `prize_level`         int                     DEFAULT '0' COMMENT '奖品级别',
    `prize_value`         decimal(18, 4) NOT NULL DEFAULT '0.0000' COMMENT '奖励价值',
    `approve_mode`        tinyint      NOT NULL DEFAULT 0 COMMENT '审批模式：0-自动免审, 1-人工审批',
    `sort_weight`         int            NOT NULL DEFAULT '0' COMMENT '排序权重',
    `ext`                 json                    DEFAULT NULL COMMENT '扩展信息：如奖品图片URL、跳转链接等',
    `status`              tinyint        NULL     DEFAULT '1' COMMENT '状态：0-停用, 1-启用',
    `create_by`           varchar(64)             DEFAULT NULL COMMENT '创建人',
    `create_time`         datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`           varchar(64)             DEFAULT NULL COMMENT '更新人',
    `update_time`         datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_prize_code` (`prize_code`)
) COMMENT ='奖品配置表';

DROP TABLE IF EXISTS `t_task_prize_mapping`;
CREATE TABLE `t_task_prize_mapping`
(
    `id`              bigint      NOT NULL AUTO_INCREMENT comment 'id',
    `tenant_id`       varchar(16) NOT NULL DEFAULT '0' COMMENT '租户ID',
    `task_config_id`  bigint      NOT NULL COMMENT '任务配置ID',
    -- 达标条件
    `stage_condition` json        NOT NULL COMMENT '阶段达标条件：如 {"min": 10, "max": 99} 或 {"action": "share"}',
    `stage_level`     int         NOT NULL DEFAULT 1 COMMENT '任务阶段：单次任务填1，阶梯任务填 1, 2, 3...',
    `prize_code`      varchar(64) NOT NULL COMMENT '奖励编码',
    -- 【核心引入】计算类型下放至任务策略层！
    `prize_mode`      varchar(32) NOT NULL DEFAULT 'FIXED' COMMENT '计算类型：FIXED(固定), RATIO(比例), FORMULA(公式)',

    -- 配合 prize_code 的具体策略参数
    `prize_strategy`  json                 DEFAULT NULL COMMENT '动态发奖策略JSON：如 {"amount": 20} 或 {"ratio": 0.05}',
    `create_by`       varchar(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`     datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       varchar(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`     datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_stage` (`task_config_id`, `stage_level`),
    KEY `idx_prize_code` (`prize_code`)
) COMMENT ='任务阶段与奖励映射表';

-- ==========================================
-- 三、 任务运行与发奖调度域 (Task Runtime & Proposal)
-- ==========================================

DROP TABLE IF EXISTS `t_task_record`;
CREATE TABLE `t_task_record`
(
    `id`               bigint         NOT NULL AUTO_INCREMENT comment 'id',
    `tenant_id`        varchar(16)    NOT NULL DEFAULT '0' COMMENT '租户ID',
    `member_name`      varchar(64)    NOT NULL COMMENT '会员名',
    `task_config_id`   bigint         NOT NULL COMMENT '任务配置ID',
    `activity_code`    varchar(32)    NOT NULL COMMENT '活动编码',
    `period_key`       varchar(32)    NOT NULL DEFAULT 'NONE' COMMENT '业务期数标识(防重用)：NONE, 日期(20260402)',
    `valid_start_time` datetime       NOT NULL COMMENT '开始时间',
    `valid_end_time`   datetime       NOT NULL COMMENT '过期时间',
    `current_metric`   decimal(18, 4) NOT NULL DEFAULT '0.0000' COMMENT '当前进度值：如已签到 3.0000 天',

    `status`           tinyint        NULL     DEFAULT '0' COMMENT '状态：0-进行中, 1-已完成, 2-已发奖, 3-已过期',
    `progress_data`    json                    DEFAULT NULL COMMENT '进度详情',
    `rule_snapshot`    json           NOT NULL COMMENT '接取任务时的规则快照',
    `prize_snapshot`   json           NOT NULL COMMENT '接取任务时的奖励快照',
    `complete_time`    datetime                DEFAULT NULL COMMENT '达标时间',

    `create_by`        varchar(64)             DEFAULT NULL COMMENT '创建人',
    `create_time`      datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        varchar(64)             DEFAULT NULL COMMENT '更新人',
    `update_time`      datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_t_tsk_rec_mbr_cfg_prd` (`member_name`, `task_config_id`, `period_key`),
    KEY `idx_t_tsk_rec_mbr_sts` (`member_name`, `status`),
    KEY `idx_t_tsk_rec_expire` (`status`, `valid_end_time`)
) COMMENT ='任务记录表';

DROP TABLE IF EXISTS `t_proposal_record`;
CREATE TABLE `t_proposal_record`
(
    `id`                  bigint         NOT NULL AUTO_INCREMENT COMMENT 'id',
    `tenant_id`           varchar(16)    NOT NULL DEFAULT '0' COMMENT '租户ID',
    `trade_no`            varchar(32)    NOT NULL COMMENT '提案单号，服务端生成，对外唯一标识',
    `member_name`         varchar(64)    NOT NULL COMMENT '会员名',
    -- 发什么（账务域词汇，不含任何上游业务概念）
    `asset_type`          varchar(16)    NOT NULL COMMENT 'SCORE/BALANCE/COUPON/PHYSICAL',
    `asset_ref`           varchar(64)             DEFAULT NULL COMMENT '资产引用：券模/SKU，值类资产为空',
    -- 展示名由营销侧下传：账务侧发券/发货要用，而它不能回头查 t_prize_log（依赖方向是营销->账务单向的）
    `asset_name`          varchar(128)            DEFAULT NULL COMMENT '资产展示名（券名/商品名）：由营销侧传入，避免账务域反查营销域',
    `amount`              decimal(13, 4) NOT NULL COMMENT '发放金额/积分数',
    `quantity`            int            NOT NULL DEFAULT 1 COMMENT '发放数量，扣 used_quota 用',
    -- 从哪来（只认单号，不认上游的业务语义）；取值由 ProposalSourceResolver 从活动类型推导
    `source_type`         varchar(32)    NOT NULL COMMENT '来源：TASK(任务), DRAW(抽奖), LOTTERY(彩票), MANUAL(人工)',
    `source_biz_id`       varchar(64)    NOT NULL COMMENT '来源单号',
    -- 预算与风控归属
    `promotion_config_id` bigint         NOT NULL COMMENT '优惠配置ID',
    `status`              int            NOT NULL DEFAULT 0 COMMENT '0-等待中, 10-待一审, 11-待二审, 20-驳回, 30-待执行, 40-执行中, 50-成功, 60-部分成功, 70-彻底失败, 80-风控拦截',
    `remark`              varchar(255)            DEFAULT NULL COMMENT '执行失败/风控拦截原因，或调用方传入的场景说明',
    -- 审批字段不变 ...
    `first_reviewer`      varchar(64)             DEFAULT NULL COMMENT '一审人',
    `first_review_time`   datetime                DEFAULT NULL COMMENT '一审时间',
    `second_reviewer`     varchar(64)             DEFAULT NULL COMMENT '二审人',
    `second_review_time`  datetime                DEFAULT NULL COMMENT '二审时间',
    `review_comment`      varchar(255)            DEFAULT NULL COMMENT '审核意见/驳回理由',

    `create_by`           varchar(64)             DEFAULT NULL COMMENT '创建人',
    `create_time`         datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`           varchar(64)             DEFAULT NULL COMMENT '更新人',
    `update_time`         datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_trade_no` (`trade_no`),
    UNIQUE KEY `uk_prop_source` (`source_type`, `asset_type`, `source_biz_id`),
    KEY `idx_prop_cfg_sts` (`promotion_config_id`, `status`),
    KEY `idx_prop_member` (`member_name`, `create_time`)
) COMMENT ='提案表';

DROP TABLE IF EXISTS `t_prize_log`;
CREATE TABLE `t_prize_log`
(
    `id`                  bigint       NOT NULL AUTO_INCREMENT comment 'id',
    `tenant_id`           varchar(16)  NOT NULL DEFAULT '0' COMMENT '租户ID',
    `member_name`         varchar(64)  NOT NULL COMMENT '会员名',
    `prize_code`          varchar(64)  NOT NULL COMMENT '奖品编码',
    `activity_code`       varchar(32)  NOT NULL COMMENT '活动编码',
    `prize_level`         int                   DEFAULT 0 COMMENT '奖品级别',
    `prize_name`          varchar(128) NOT NULL COMMENT '奖品名称',
    `prize_type`          varchar(32)  NOT NULL COMMENT '奖励类型：SCORE, BALANCE, COUPON, PHYSICAL',
    `prize_value`         varchar(128) NOT NULL COMMENT '奖励体值(积分数/券ID)',
    `fail_reason`         varchar(128) NULL     DEFAULT NULL COMMENT '异常原因：发奖失败时才有值',
-- ================= 【新增】风控与审批字段 =================
    `approve_status`      tinyint      NOT NULL DEFAULT 0 COMMENT '审批状态：0-无需审批, 1-待审批, 2-已批准, 3-已驳回',
    `approve_by`          varchar(64)           DEFAULT NULL COMMENT '审批人',
    `approve_time`        datetime              DEFAULT NULL COMMENT '审批时间',
    `valid_until`        datetime              DEFAULT NULL COMMENT '过期时间',
    -- =========================================================
    `status`              tinyint      NULL     DEFAULT 0 COMMENT '执行状态：0-等待, 1-成功, 2-失败',
    `external_biz_no`     varchar(128)          DEFAULT NULL COMMENT '外部单号',
    `remark`              varchar(255)          DEFAULT NULL COMMENT '异常原因',

    `create_by`           varchar(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`         datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`           varchar(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`         datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 跨系统防重：PrizeDispatchHandler 靠捕获 DuplicateKeyException 拦重复派发，缺了这个索引防重就是空转
    UNIQUE KEY `uk_external_biz` (`external_biz_no`),
    KEY `idx_prize_log_` (`member_name`, `activity_code`)
) COMMENT ='奖励记录表';

-- ==========================================
-- 四、 资产账务与实例域 (Ledger & Asset Instances)
-- ==========================================

DROP TABLE IF EXISTS `t_member_wallet`;
CREATE TABLE `t_member_wallet`
(
    `id`            bigint         NOT NULL AUTO_INCREMENT comment 'id',
    `tenant_id`     varchar(16)    NOT NULL DEFAULT '0' COMMENT '租户ID',
    `member_name`   varchar(64)    NOT NULL COMMENT '会员名',
    `asset_type`    varchar(32)    NOT NULL COMMENT '资产类型：SCORE-积分, BALANCE-现金，取值对齐 PrizeTypeEnum，与流水表 asset_type 同一字典',
    `balance`       decimal(18, 4) NOT NULL DEFAULT '0.0000' COMMENT '余额',
    `status`        tinyint        NULL     DEFAULT '1' COMMENT '状态：0-冻结, 1-正常',
    `version`       int            NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',

    `create_by`     varchar(64)             DEFAULT NULL COMMENT '创建人',
    `create_time`   datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     varchar(64)             DEFAULT NULL COMMENT '更新人',
    `update_time`   datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_member_asset` (`member_name`, `asset_type`)
) COMMENT ='会员钱包表（一行一种资产，扩展新资产只需新增 asset_type 取值，无需加字段）';

DROP TABLE IF EXISTS `t_member_asset_transaction`;
CREATE TABLE `t_member_asset_transaction`
(
    `id`               bigint         NOT NULL AUTO_INCREMENT comment 'id',
    `tenant_id`        varchar(16)    NOT NULL DEFAULT '0' COMMENT '租户ID',
    `member_name`      varchar(64)    NOT NULL COMMENT '会员名',
    `asset_type`       varchar(32)    NOT NULL COMMENT '资产类型：SCORE, BALANCE',
    `transaction_type` tinyint        NOT NULL COMMENT '资金流向：1-收入, 2-支出',
    `change_amount`    decimal(18, 4) NOT NULL COMMENT '变动绝对值',
    `balance_after`    decimal(18, 4) NOT NULL COMMENT '变动后最新余额',
    `biz_type`         varchar(64)    NOT NULL COMMENT '业务类型：TASK_PRIZE, CONSUME, MANUAL_ADJUST',
    `biz_ref_id`       varchar(64)    NOT NULL COMMENT '关联外部业务ID(如 prize_code)',
    `remark`           varchar(255)            DEFAULT NULL COMMENT 'C端展示摘要',

    `create_by`        varchar(64)             DEFAULT NULL COMMENT '创建人',
    `create_time`      datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        varchar(64)             DEFAULT NULL COMMENT '更新人',
    `update_time`      datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_t_biz_mbr_ast_txn_time` (`member_name`, `asset_type`, `create_time`),
    UNIQUE KEY `uk_t_biz_mbr_ast_txn_ref` (`biz_ref_id`, `asset_type`)
) COMMENT ='交易明细表';

DROP TABLE IF EXISTS `t_member_coupon`;
CREATE TABLE `t_member_coupon`
(
    `id`               bigint       NOT NULL AUTO_INCREMENT comment 'id',
    `tenant_id`        varchar(16)  NOT NULL DEFAULT '0' COMMENT '租户ID',
    `member_name`      varchar(64)  NOT NULL COMMENT '会员名',
    `coupon_code`      varchar(64)  NOT NULL COMMENT '券模编码',
    `coupon_type`      varchar(16)  NOT NULL COMMENT '券类型',
    `coupon_name`      varchar(128) NOT NULL COMMENT '券名称',
    `status`           tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0-未使用, 1-已使用, 2-已过期, 3-已作废',

    -- 【核心溯源】：只认单据，不认活动！
    `source_type`      varchar(32)  NOT NULL COMMENT '来源：DRAW, TASK, MANUAL_SEND',
    `source_biz_id`    varchar(64)  NOT NULL COMMENT '关联单号',

    `valid_start_time` datetime     NOT NULL COMMENT '有效期开始',
    `valid_end_time`   datetime     NOT NULL COMMENT '有效期结束',
    `used_time`        datetime              DEFAULT NULL COMMENT '核销时间',

    `create_by`        varchar(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`      datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        varchar(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`      datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 优化索引：通常运营或系统需要根据会员查询其名下有效的券
    KEY `idx_mbr_sts` (`member_name`, `status`),
    KEY `idx_source` (`source_type`, `source_biz_id`)
) COMMENT ='会员优惠券';

DROP TABLE IF EXISTS `t_physical_delivery`;
CREATE TABLE `t_physical_delivery`
(
    `id`                bigint       NOT NULL AUTO_INCREMENT comment 'id',
    `tenant_id`         varchar(16)  NOT NULL DEFAULT '0' COMMENT '租户ID',
    `member_name`       varchar(64)  NOT NULL COMMENT '会员名',
    `proposal_id`       bigint       NOT NULL COMMENT '发奖提案ID',
    `source_type`       varchar(64)  NOT NULL COMMENT '来源类型',

    -- 中奖时用户还没填地址，履约单先落、收件信息后补（详见 v3.37.0.sql）
    `receiver_name`     varchar(64)  NULL DEFAULT NULL COMMENT '收件人姓名：中奖时未知，由用户后续补填',
    `receiver_phone`    varchar(32)  NULL DEFAULT NULL COMMENT '收件人电话：中奖时未知，由用户后续补填',
    `receiver_address`  varchar(255) NULL DEFAULT NULL COMMENT '收件详细地址：中奖时未知，由用户后续补填',
    `logistics_company` varchar(64)           DEFAULT NULL COMMENT '物流公司',
    `logistics_no`      varchar(128)          DEFAULT NULL COMMENT '物流单号',
    `status`            tinyint      NULL     DEFAULT 0 COMMENT '状态：0-待发货, 1-已发货, 2-已签收, 3-异常退回',

    `create_by`         varchar(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`       datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         varchar(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`       datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_t_biz_phy_dlv_prop_pool` (`proposal_id`, `source_type`)
) COMMENT ='发货物流表';
