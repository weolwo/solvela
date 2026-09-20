-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- ⚠️ 本脚本不带 NOW()/CURDATE()，不受 README 里那个时区坑影响。

-- ============================================================================
-- 会员等级 · 阶段 1：建表与种子  2026-09-18
--
-- 方案见 docs/会员等级-实现技术方案.md
-- 模型：考核周期制（升级即时 / 降级期末按成长值纯映射 / 保级期成长值翻倍）
--
-- 【可重复执行】建表用 IF NOT EXISTS，种子用 ON DUPLICATE KEY UPDATE。
--
-- ⚠️ 2026-09-20：本文件是【历史脚本】，它建出来的是改名前的形状
--    （t_member_level / level / period_value / version …）。
--    现行结构以 数据库SQL脚本/mysql/会员等级-Grade改名与5表模型.sql 为准，
--    新环境请直接走 schema-baseline.sql + data-baseline.sql，不要单跑这一份。
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1. 等级定义
-- ----------------------------------------------------------------------------
--
-- 🔴 等级是【配置】不是枚举：门槛、名称、档数都要能在后台改。
--    写成 Java 枚举的话运营加一档要发版 —— 而加一档恰恰是这类体系最常见的运营动作。
--
CREATE TABLE IF NOT EXISTS `t_member_level` (
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT 'id',
    `level`         int          NOT NULL COMMENT '等级：0 起，数字越大越高。0 必须存在（新会员的落点）',
    `level_name`    varchar(32)  NOT NULL COMMENT '等级名：普通会员/银卡/金卡/白金/钻石',
    `threshold`     bigint       NOT NULL COMMENT '周期内成长值门槛（含）。level=0 必须为 0',
    `icon_file_id`  bigint                DEFAULT NULL COMMENT '等级图标 file_id，走文件模块',
    `benefits`      varchar(500)          DEFAULT NULL COMMENT '权益描述【纯展示，不驱动逻辑】：等级页给用户看"我在保什么"。真正的权益靠任务人群/脚本实现',
    `status`        tinyint      NOT NULL DEFAULT '1' COMMENT '状态：0-停用, 1-启用',
    `create_by`     varchar(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`   datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     varchar(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`   datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_t_mbr_lv_level` (`level`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='会员等级定义';


-- ----------------------------------------------------------------------------
-- 2. 会员成长值与等级（一人一行）
-- ----------------------------------------------------------------------------
--
-- 🔴 等级【不】加到 t_member 上：它是派生状态，跟着成长值走。
--    放这里一次主键点查就拿得到，而 t_member 是身份表，不该混入会随业务变动的列。
--
CREATE TABLE IF NOT EXISTS `t_member_growth` (
    `member_id`      bigint     NOT NULL COMMENT '会员号：关联键，一人一行',
    `level`          int        NOT NULL DEFAULT '0' COMMENT '当前等级。缓冲期内取 protect_target，不等于 f(period_value)',
    `level_since`    datetime            DEFAULT NULL COMMENT '当前等级是什么时候到的',
    `period_start`   datetime   NOT NULL COMMENT '本考核周期开始：入会日（或上一周期结束日）',
    `period_end`     datetime   NOT NULL COMMENT '本考核周期结束：period_start + 12 个月',
    `period_value`   bigint     NOT NULL DEFAULT '0' COMMENT '本周期累计成长值【冗余：可由流水求和得出，但判级是热路径。必须有对账任务】',
    `total_value`    bigint     NOT NULL DEFAULT '0' COMMENT '终身累计成长值【只展示，不参与定级】',
    `protect_until`  datetime            DEFAULT NULL COMMENT '保级缓冲到期时刻；为空表示不在缓冲期',
    `protect_target` int                 DEFAULT NULL COMMENT '缓冲期要保住的等级',
    `version`        int        NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
    `create_by`      varchar(64)         DEFAULT NULL COMMENT '创建人',
    `create_time`    datetime            DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      varchar(64)         DEFAULT NULL COMMENT '更新人',
    `update_time`    datetime            DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`member_id`),
    KEY `idx_t_mbr_gr_period_end` (`period_end`),
    KEY `idx_t_mbr_gr_protect` (`protect_until`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='会员成长值与等级';


-- ----------------------------------------------------------------------------
-- 3. 成长值流水
-- ----------------------------------------------------------------------------
--
-- 🔴 uk_t_mbr_gr_log_src 不能省。
--    打点是【至少一次】的（对账 job 会重推），没有这个唯一键，
--    同一笔积分入账会被加两次成长值。
--    形状照抄 t_task_record_flow.uk_t_tsk_flw_evt —— 那条已经在生产里验证过。
--
CREATE TABLE IF NOT EXISTS `t_member_growth_log` (
    `id`                 bigint      NOT NULL AUTO_INCREMENT COMMENT 'id',
    `member_id`          bigint      NOT NULL COMMENT '会员号：关联键',
    `delta`              bigint      NOT NULL COMMENT '本次增减的成长值（已乘倍率）',
    `base_value`         bigint      NOT NULL COMMENT '倍率之前的基数 —— 客诉时要能说清"为什么是 200 不是 100"',
    `multiplier`         int         NOT NULL DEFAULT '1' COMMENT '本次倍率：1-常态, 2-保级缓冲期',
    `after_period_value` bigint      NOT NULL COMMENT '变动后的周期累计值（对账锚点）',
    `source`             varchar(32) NOT NULL COMMENT '来源：SCORE_EARNED / 将来的 BIND_PHONE 等',
    `biz_type`           varchar(64)          DEFAULT NULL COMMENT '上游业务类型，如 PROPOSAL_REWARD。白名单判据就是它',
    `biz_id`             varchar(64) NOT NULL COMMENT '上游业务单号：幂等键',
    `period_tag`         varchar(32) NOT NULL COMMENT '计入哪个周期（period_start 的 yyyyMMdd）。🔴 保级期的加速计入【上一周期】',
    `remark`             varchar(255)         DEFAULT NULL COMMENT 'C 端展示摘要',
    `create_by`          varchar(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`        datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`          varchar(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`        datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_t_mbr_gr_log_src` (`source`, `biz_id`),
    KEY `idx_t_mbr_gr_log_mbr` (`member_id`, `create_time`),
    KEY `idx_t_mbr_gr_log_period` (`member_id`, `period_tag`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='会员成长值流水';


-- ----------------------------------------------------------------------------
-- 4. 等级变更留痕
-- ----------------------------------------------------------------------------
--
-- 🔴 这张表和上面三张一样是第一版的一部分，不是「以后再补」。
--    用户会真的来问「我为什么掉级了」—— 等级变更和积分对不上并列，
--    是客服最常被问到的两件事。答不上来的会员体系，用几次就没人信了。
--
CREATE TABLE IF NOT EXISTS `t_member_level_log` (
    `id`               bigint      NOT NULL AUTO_INCREMENT COMMENT 'id',
    `member_id`        bigint      NOT NULL COMMENT '会员号：关联键',
    `from_level`       int         NOT NULL COMMENT '变更前等级',
    `to_level`         int         NOT NULL COMMENT '变更后等级',
    `change_type`      varchar(32) NOT NULL COMMENT '类型：UPGRADE-升级, DOWNGRADE-降级, KEEP-保级, MANUAL-人工调整, RISK_REVOKE-风控扣回',
    `period_value`     bigint      NOT NULL COMMENT '变更时的周期成长值快照 —— 事后复盘唯一的依据',
    `reason`           varchar(255)         DEFAULT NULL COMMENT '原因。人工调整时必填',
    `operator`         varchar(64)          DEFAULT NULL COMMENT '操作人：系统变更为空，人工调整记员工',
    `create_by`        varchar(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`      datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        varchar(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`      datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_t_mbr_lv_log_mbr` (`member_id`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='会员等级变更留痕';


-- ----------------------------------------------------------------------------
-- 5. 五档等级种子
-- ----------------------------------------------------------------------------
--
-- ⚠️ 门槛是【示意值】，不是拍定的业务口径。
--    真实门槛要等跑过一个季度、看到成长值的真实分布之后再定 ——
--    门槛拍早了必然要改，而改门槛是会上新闻的那种改。
--
-- 🔴 level=0 的那一行不能删：新会员落在这儿，判级函数也拿它当兜底。
--
INSERT INTO `t_member_level` (`level`, `level_name`, `threshold`, `benefits`, `status`, `create_by`)
VALUES (0, '普通会员', 0,
        '基础权益：参与全部公开活动', 1, 'seed'),
       (1, '银卡会员', 1000,
        '银卡专享任务｜活动优先参与', 1, 'seed'),
       (2, '金卡会员', 5000,
        '金卡专享任务｜专享奖池｜生日礼', 1, 'seed'),
       (3, '白金会员', 20000,
        '白金专享任务｜专享奖池｜生日礼｜每月专属券', 1, 'seed'),
       (4, '钻石会员', 60000,
        '钻石专享任务｜最高奖池｜生日礼｜每月专属券｜专属客服', 1, 'seed')
ON DUPLICATE KEY UPDATE `level_name` = VALUES(`level_name`),
                        `threshold`  = VALUES(`threshold`),
                        `benefits`   = VALUES(`benefits`),
                        `status`     = VALUES(`status`);


-- ============================================================================
-- 验证
-- ============================================================================
--
--   SELECT level, level_name, threshold, benefits FROM t_member_level ORDER BY level;
--
-- ⚠️ 建完表记得重新导出基线：数据库SQL脚本/tools/DumpSchema.java
--    🔴 别手改 schema-baseline.sql（README 红线 #6）。
--
-- ⚠️ 存量会员【不预建】 t_member_growth 行：9000+ 会员建 9000 行空记录没有意义，
--    而且会让「这个人有没有参与过」变得看不出来。第一次成长值入账时按需创建
--    （MemberGrowthService.loadOrInit），周期从【那一刻】起算而不是注册日 ——
--    存量会员的注册日可能是两年前，按注册日算的话他一进来就在周期末尾。
-- ============================================================================
