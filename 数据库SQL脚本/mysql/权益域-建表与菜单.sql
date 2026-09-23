-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- ============================================================================
-- 权益域 · 建表  2026-09-22
--
-- 会员等级方案 §7 那张表的最后一项：生日礼 / 月度券。
--
-- 🔴 它是【新立的一个域】，不是给 t_grade_privilege 加执行引擎。
--    那张表的类注释写着：「谁都不要给它加生效引擎。那一天来临时，应该是新立一个
--    权益域，而不是让这张展示表长出执行语义 —— 展示与执行混在一张表里之后，
--    改一句文案就有可能改掉一条业务规则。」这份脚本就是那一天。
--
--    两张表的分工：
--      t_grade_privilege   —— 用户在等级页【看见】自己在保什么。纯展示，无引擎。
--      t_grade_entitlement —— 真的会发出东西。有引擎，有流水，有预算含义。
--
-- 🔴 发放本身【不重造】：走 AssetGrantApi.grant()，三条通道各自的数据库唯一键
--    （券 uk_source、现金 UNIQUE(biz_ref_id, asset_type)、实物 uk_t_biz_phy_dlv_src）
--    才是最终的防重。本域只负责回答「谁、什么时候、拿什么」。
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/权益域-建表与菜单.sql;
--   按表名与 path 判存，可重复执行。
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. 权益配置：哪一档、什么周期、发什么
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_grade_entitlement` (
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT 'id',
    `entitlement_code`  varchar(32)  NOT NULL COMMENT '权益编码：10 位大写字母+数字，全局唯一（铁律 8）',
    `entitlement_name`  varchar(64)  NOT NULL COMMENT '权益名。会显示给用户，如「白金生日礼」',
    `entitlement_type`  varchar(32)  NOT NULL COMMENT '类型：BIRTHDAY-生日礼(一年一次), MONTHLY-月度券(一月一次)',
    `min_grade`         int          NOT NULL DEFAULT 0 COMMENT '需要的最低等级（t_member_grade.grade_code）。判据是 >=，不是 =：白金的月度券钻石也该有',

    -- 发什么。字段对齐 AssetGrantCmd，不借道 t_prize_config ——
    -- 那张表是【活动】维度的（activity_code NOT NULL），而权益不属于任何活动，
    -- 借道就得为权益造一个假活动，那个假活动会出现在活动列表里
    `asset_type`        varchar(32)  NOT NULL COMMENT '资产类型：COUPON/BALANCE/SCORE，对齐 PrizeTypeEnum',
    `asset_ref`         varchar(64)           DEFAULT NULL COMMENT 'COUPON 存券模板编码；BALANCE 存面额来源标识',
    `asset_name`        varchar(128) NOT NULL COMMENT '展示名。券名会直接显示给用户，取不到时不要拿备注顶替',
    `quantity`          int          NOT NULL DEFAULT 1 COMMENT '发几份',
    `amount`            decimal(18,4)         DEFAULT NULL COMMENT 'BALANCE 的单份面额；实发 amount × quantity',

    -- 🔴 待领取必须有有效期，否则「待领取」会无限堆积：
    --    一个从不打开 App 的人会攒下几十条永远不会被领的记录，
    --    而它们既占着预算口径、又让「有多少人享受了权益」这个数字彻底失真
    `claim_days`        int          NOT NULL DEFAULT 30 COMMENT '生成后多少天内可领，过期作废',

    `status`            tinyint      NOT NULL DEFAULT 1 COMMENT '状态：0-停用, 1-启用',
    `remark`            varchar(255)          DEFAULT NULL COMMENT '备注',
    `create_by`         varchar(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`       datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         varchar(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`       datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_t_gd_ent_code` (`entitlement_code`),
    KEY `idx_t_gd_ent_type` (`entitlement_type`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='会员等级-权益配置（会真的发出东西，与纯展示的 t_grade_privilege 分开）';


-- ---------------------------------------------------------------------------
-- 2. 权益发放记录 —— 同时也是「待领取」列表
--
-- 🔴 uk(member_id, entitlement_id, period_key) 是这个域的幂等根。
--    job 每天扫全量，靠这个键挡住重复生成 —— 不是「先查有没有再插」：
--    两个节点同时扫到同一个人，先查后插会双双通过，而那是多发一份权益。
--
-- ⚠️ period_key 的口径由类型决定，两种【长度不同】是刻意的：
--      BIRTHDAY -> 'yyyy'    一年一次
--      MONTHLY  -> 'yyyyMM'  一月一次
--    统一成 yyyyMM 的话，生日礼就变成一年能领十二次。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_grade_entitlement_grant` (
    `id`                bigint      NOT NULL AUTO_INCREMENT COMMENT 'id',
    `entitlement_id`    bigint      NOT NULL COMMENT '权益配置 id',
    `entitlement_code`  varchar(32) NOT NULL COMMENT '权益编码【快照】：配置改名之后，历史记录仍是当时那个',
    `member_id`         bigint      NOT NULL COMMENT '会员号（关联键）',
    `period_key`        varchar(16) NOT NULL COMMENT '周期键：BIRTHDAY 用 yyyy，MONTHLY 用 yyyyMM',

    -- 🔴 等级是【生成时】的快照，不是领取时再判。
    --    月初你是白金就该给你白金的月度券，月中降级不该把已经给出去的收回 ——
    --    而更要紧的是：让用户看见「可领取」却领不了，比一开始就不给更伤。
    `grade_code`        int         NOT NULL COMMENT '生成时的会员等级【快照】',

    `status`            tinyint     NOT NULL DEFAULT 0 COMMENT '状态：0-待领取, 1-已领取, 2-已过期',
    `expire_time`       datetime    NOT NULL COMMENT '领取截止时间。到点由 job 置为已过期',
    `claim_time`        datetime             DEFAULT NULL COMMENT '领取时间',

    -- 领取时传给 AssetGrantApi 的 bizRefId。落到履约单的 source_biz_id，
    -- 那边的唯一键才是最终防重 —— 本表的状态机只是第一道
    `grant_biz_id`      varchar(64) NOT NULL COMMENT '发放单号：领取时作为 AssetGrantCmd.bizRefId，是跨域防重的键',
    `grant_result`      varchar(255)         DEFAULT NULL COMMENT '发放结果/失败原因，便于排查',

    `create_time`       datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_t_gd_ent_grant` (`member_id`, `entitlement_id`, `period_key`),
    UNIQUE KEY `uk_t_gd_ent_biz` (`grant_biz_id`),
    KEY `idx_t_gd_ent_mine` (`member_id`, `status`, `expire_time`),
    KEY `idx_t_gd_ent_expire` (`status`, `expire_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='会员等级-权益发放记录，同时是「待领取」列表';


-- ============================================================================
-- 自查
-- ============================================================================
--
--   -- 两张表都建了：
--   SELECT TABLE_NAME FROM information_schema.TABLES
--    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 't_grade_entitlement%';
--
--   -- 幂等键在不在（不在的话 job 重跑会重复发权益）：
--   SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) cols
--     FROM information_schema.STATISTICS
--    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_grade_entitlement_grant'
--      AND NON_UNIQUE = 0 GROUP BY INDEX_NAME;
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 3. 定时任务：每天生成待领取 + 过期作废
--
-- 🔴 next_trigger_time 必须写成【应用看到的墙上时间】= MySQL NOW() + 8 小时。
--    服务端时钟是 UTC，用裸 NOW() 会让它一上来就被判成「错过调度」直接 SKIP。
--    理由与踩坑记录见 定时任务-补齐未挂载的六个job.sql §0（已删除，见 git 历史）。
--
-- ⚠️ 每天跑，不是「月度券每月 1 号跑」：1 号那天 job 没跑起来（部署、故障、
--    机器睡着）就是整整一个月的权益没发，而且要等下个月才有下一次机会。
--    每天扫 + uk(member_id, entitlement_id, period_key) 幂等 = 自带补跑。
-- ---------------------------------------------------------------------------
SET @app_now := DATE_ADD(NOW(), INTERVAL 8 HOUR);

INSERT INTO `t_solvela_job`
    (`job_code`, `job_name`, `handler_name`, `job_group`, `trigger_type`, `trigger_value`,
     `next_trigger_time`, `trigger_version`, `jitter_seconds`, `enabled_flag`, `param`,
     `preset_code`, `timeout_seconds`, `retry_times`, `retry_interval`,
     `misfire_strategy`, `misfire_threshold_sec`, `block_strategy`, `sort`, `remark`,
     `deleted_flag`, `update_name`, `create_time`, `update_time`, `app_env`,
     `continuous_fail_count`, `handler_missing_flag`, `terminal_flag`, `source`, `manual_modified_flag`)
SELECT * FROM (
    SELECT 'JOBGDENTGR' AS job_code, '【会员】等级权益生成与过期' AS job_name,
           'gradeEntitlementGrant' AS handler_name, 'BUSINESS' AS job_group,
           -- 每天 04:10：避开 03:xx 那几个清理任务，也在业务低峰
           'cron' AS trigger_type, '0 10 4 * * *' AS trigger_value,
           DATE_ADD(@app_now, INTERVAL 1 MINUTE) AS next_trigger_time,
           0 AS trigger_version, 0 AS jitter_seconds, 1 AS enabled_flag,
           NULL AS param, 'NORMAL' AS preset_code,
           1800 AS timeout_seconds, 0 AS retry_times, 60 AS retry_interval,
           'SKIP' AS misfire_strategy, 600 AS misfire_threshold_sec, 'DISCARD' AS block_strategy,
           7 AS sort,
           '每天扫全量生成待领取，靠唯一键幂等（自带补跑）；同时把到期未领的置为过期' AS remark,
           0 AS deleted_flag, 'system' AS update_name, NOW() AS create_time, NOW() AS update_time,
           'dev' AS app_env, 0 AS continuous_fail_count, 0 AS handler_missing_flag,
           0 AS terminal_flag, 'MANUAL' AS source, 0 AS manual_modified_flag
) AS jobs
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT `job_code` FROM `t_solvela_job`) AS existed
     WHERE existed.`job_code` = jobs.job_code
);


-- ---------------------------------------------------------------------------
-- 4. 菜单与权限
--
-- 🔴 这次【新开权限点】，不沿用 memberGrade:config。
--
--    加权益菜单时我一度想沿用 —— 上一轮的「等级权益（展示）」就是那么做的，
--    理由是「改权益文案和改等级门槛是同一类运营动作」。但这一张不是：
--    memberGrade:config 改的是【页面上写什么】，这里改的是【真的发什么出去】。
--    在这里加一条「每月给所有白金发一张 20 元券」，下一个 job 周期就真的发了。
--
--    能改一句文案和能承诺一笔预算，是两种授权。
--
-- ⚠️ 新权限点意味着【已有角色默认没有它】。超管不受影响（走的是管理员标志），
--    但普通运营角色要到「角色管理」里手工勾一下 —— 这是刻意的，
--    默认给上等于这个拆分白做了。
-- ---------------------------------------------------------------------------

-- 菜单本体：挂在「会员中心」下，排在等级权益（展示）之后
INSERT INTO `t_menu`
    (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`,
     `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`,
     `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`,
     `deleted_flag`, `create_user_id`, `create_time`)
SELECT * FROM (
    SELECT '权益发放配置' AS menu_name, 2 AS menu_type, 462 AS parent_id, 8 AS sort,
           '/member/member-entitlement/list' AS path,
           '/business/member/member-entitlement/member-entitlement-list.vue' AS component,
           1 AS perms_type, NULL AS api_perms, NULL AS web_perms,
           'RedEnvelopeOutlined' AS icon, NULL AS context_menu_id,
           0 AS frame_flag, NULL AS frame_url, 0 AS cache_flag,
           1 AS visible_flag, 0 AS disabled_flag, 0 AS deleted_flag,
           1 AS create_user_id, NOW() AS create_time
) AS m
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT `path` FROM `t_menu` WHERE `deleted_flag` = 0) AS existed
     WHERE existed.`path` = m.path
);

-- 两个权限点，挂在刚建的菜单下
INSERT INTO `t_menu`
    (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`,
     `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`,
     `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`,
     `deleted_flag`, `create_user_id`, `create_time`)
SELECT * FROM (
    SELECT '查询' AS menu_name, 3 AS menu_type,
           (SELECT `menu_id` FROM (SELECT `menu_id`, `path` FROM `t_menu`) AS t
             WHERE t.`path` = '/member/member-entitlement/list') AS parent_id,
           NULL AS sort, NULL AS path, NULL AS component,
           1 AS perms_type, 'memberEntitlement:query' AS api_perms,
           'memberEntitlement:query' AS web_perms,
           NULL AS icon, NULL AS context_menu_id,
           0 AS frame_flag, NULL AS frame_url, 0 AS cache_flag,
           1 AS visible_flag, 0 AS disabled_flag, 0 AS deleted_flag,
           1 AS create_user_id, NOW() AS create_time
    UNION ALL
    SELECT '配置（会真的发出东西）', 3,
           (SELECT `menu_id` FROM (SELECT `menu_id`, `path` FROM `t_menu`) AS t2
             WHERE t2.`path` = '/member/member-entitlement/list'),
           NULL, NULL, NULL,
           1, 'memberEntitlement:config', 'memberEntitlement:config',
           NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW()
) AS p
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT `api_perms` FROM `t_menu` WHERE `deleted_flag` = 0) AS existed
     WHERE existed.`api_perms` = p.api_perms
);


-- ============================================================================
-- 自查（续）
-- ============================================================================
--
--   SELECT menu_id, menu_name, menu_type, sort, path, api_perms FROM t_menu
--    WHERE path = '/member/member-entitlement/list'
--       OR api_perms LIKE 'memberEntitlement:%';
--
--   -- ⚠️ 新权限点默认没有任何角色拥有。要给普通运营角色开，去「角色管理」勾一下，
--   --    或者：INSERT INTO t_role_menu (role_id, menu_id) VALUES (<角色>, <权限点 menu_id>);
-- ============================================================================
