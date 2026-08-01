-- =====================================================================================
-- v3.47.0  任务中台 P1：事件注册表 t_task_event
-- 方案：docs/任务中台-改进技术方案.md v2 §2.2
-- 撰写：2026-08-01
--
-- 解决的问题：trigger_event 此前是「前端常量数组 + DDL 注释」里写死的 5 个值，
--   加一个「分享商品」事件要改前端常量、改 DDL 注释、可能还要加后端枚举 ——
--   与「新增任务模板前端零改动」的目标<b>正面冲突</b>。事件本质是开放集合，不该用封闭枚举表达。
--
-- 加事件 = 加一行数据 + 上游埋点，前端从 /taskEvent/optionList 拉，零代码改动。
--
-- 可重复执行（建表 IF NOT EXISTS；种子数据按 event_code upsert）。
-- =====================================================================================


-- -------------------------------------------------------------------------------------
-- 一、建表
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_task_event`
(
    `id`                bigint      NOT NULL AUTO_INCREMENT COMMENT 'id',
    `tenant_id`         varchar(16) NOT NULL DEFAULT '0' COMMENT '租户ID',
    `event_code`        varchar(64) NOT NULL COMMENT '事件编码：DAILY_SIGN / ORDER_PAID / GOODS_SHARE',
    `event_name`        varchar(64) NOT NULL COMMENT '展示名：签到 / 支付成功 / 分享商品',

    -- 计量来源：AMOUNT 类任务从事件的哪个字段取金额。
    -- NONE = 该事件不带金额（计次型用）；其余填 payload 里的字段名，如 payAmount。
    -- 调用方显式传 amount 时以 amount 为准，本字段只是「没显式传时去哪儿找」。
    `metric_source`     varchar(32) NOT NULL DEFAULT 'NONE' COMMENT '计量来源：NONE(计次) 或 payload 里的字段名(计额)',

    -- payload_schema 顺带解决一个当前无解的问题：模板作者写规则时
    -- 根本不知道这个事件会带来哪些字段。
    `payload_schema`    json                 DEFAULT NULL COMMENT '该事件会带哪些字段，供模板设计器提示与校验',

    -- 🔴 把「上游必须带幂等单号」从口头约定变成表里的强制契约。
    -- ORDER_PAID 这类有天然单号的必须置 1：不带单号时服务端只能按「事件日」兜底，
    -- 那对订单事件意味着「一天只算一笔」，是错的。
    -- DAILY_SIGN 这类天然无单号的置 0，用事件日兜底才是对的。
    `biz_id_required`   tinyint     NOT NULL DEFAULT 0 COMMENT '上游是否必须带幂等单号：1-必须, 0-可按事件日兜底',

    -- ⚠️ 本期只建字段、不实现路由优化。
    -- 高频事件（PAGE_VIEW）理想做法是先走 Redis 判「该用户有没有接这个任务」、没接就在内存丢弃，
    -- 但那要配套一整套缓存一致性与配置变更失效逻辑，而本系统当前连一个真实事件源都没有，
    -- 没有真实流量剖面就是凭空设计。建字段成本近乎零（将来改表比改字段贵），实现留到有流量之后。
    `is_high_frequency` tinyint     NOT NULL DEFAULT 0 COMMENT '是否高频事件：1-是（预留给路由优化，本期未实现）',

    -- 丢弃流水是客诉自证的关键（「用户下了99元的单为什么没进度」），但高频事件
    -- 每条不匹配都写一行会直接把 t_task_record_flow 写爆，故做成开关。
    -- 关掉时仍打 DEBUG 日志，不会什么都不留。
    `discard_log_flag`  tinyint     NOT NULL DEFAULT 1 COMMENT '是否记录被丢弃事件的流水：1-记录, 0-不记录（高频事件建议关）',

    `remark`            varchar(255)         DEFAULT NULL COMMENT '备注：上游由谁埋点、什么时机触发',
    `status`            tinyint     NOT NULL DEFAULT 1 COMMENT '状态：0-停用, 1-启用',

    `create_by`         varchar(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`       datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         varchar(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`       datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_t_tsk_evt_code` (`event_code`)
) COMMENT ='任务事件注册表';


-- -------------------------------------------------------------------------------------
-- 二、种子数据
--
-- 前四个是前端 TRIGGER_EVENT_OPTIONS 里写死的存量取值，必须先注册进来，
-- 否则改造后那些已配好的任务会因为「事件未注册」全部失效。
-- ⚠️ CUSTOM 刻意不注册：它是「自定义」占位符，不是一个真实事件，
--    留着只会让运营选到一个永远不会有上游触发的事件。存量若有配 CUSTOM 的任务需单独处理。
-- -------------------------------------------------------------------------------------
INSERT INTO `t_task_event`
    (`event_code`, `event_name`, `metric_source`, `payload_schema`, `biz_id_required`,
     `is_high_frequency`, `discard_log_flag`, `remark`, `status`)
VALUES
    ('DAILY_SIGN', '每日签到', 'NONE',
     '{"fields":[]}', 0, 0, 1, '天然无单号，按事件自然日兜底幂等（一天算一次）', 1),

    ('ORDER_PAID', '订单支付成功', 'NONE',
     '{"fields":[{"key":"orderId","type":"string","desc":"订单号"}]}', 1, 0, 1,
     '🔴 必须带 eventBizId=订单号，否则同一天多笔订单只会算一笔', 1),

    ('MEMBER_REGISTER', '会员注册', 'NONE',
     '{"fields":[]}', 0, 0, 1, '一个会员一辈子一次，无需单号', 1),

    ('PAGE_VIEW', '页面浏览', 'NONE',
     '{"fields":[{"key":"pageId","type":"string","desc":"页面标识"}]}', 0, 1, 0,
     '高频事件：discard_log_flag 关掉，否则不匹配的浏览会把流水表写爆', 1),

    ('GOODS_SHARE', '分享商品', 'NONE',
     '{"fields":[{"key":"goodsId","type":"string","desc":"商品ID"}]}', 0, 0, 1,
     '本次新增，用来演示「加事件只加一行数据、前端零改动」', 1),

    ('ORDER_AMOUNT', '订单金额累计', 'payAmount',
     '{"fields":[{"key":"orderId","type":"string","desc":"订单号"},{"key":"payAmount","type":"decimal","desc":"实付金额"}]}',
     1, 0, 1, 'AMOUNT 类任务用；未显式传 amount 时从 payload.payAmount 取', 1),

    ('CONCURRENT_ADD', '并发累加(验收专用)', 'payAmount',
     '{"fields":[{"key":"payAmount","type":"decimal","desc":"金额"}]}', 1, 0, 1,
     'P0 判据4 专用，生产环境可停用', 1)
ON DUPLICATE KEY UPDATE
    `event_name`        = VALUES(`event_name`),
    `metric_source`     = VALUES(`metric_source`),
    `payload_schema`    = VALUES(`payload_schema`),
    `biz_id_required`   = VALUES(`biz_id_required`),
    `is_high_frequency` = VALUES(`is_high_frequency`),
    `discard_log_flag`  = VALUES(`discard_log_flag`),
    `remark`            = VALUES(`remark`),
    `status`            = VALUES(`status`);


-- -------------------------------------------------------------------------------------
-- 三、菜单与权限功能点
--     ⚠️ 执行前已核对：v3.46 占用到 448，449 起安全。
-- -------------------------------------------------------------------------------------
DELETE FROM `t_role_menu` WHERE `menu_id` IN (449, 450, 451, 452, 453);
DELETE FROM `t_menu`      WHERE `menu_id` IN (449, 450, 451, 452, 453);

INSERT INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`,
                      `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`,
                      `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`,
                      `create_user_id`)
VALUES
    -- 挂在 319「任务中心」下，排在最前 —— 事件是任务的输入，先有事件才配得出任务。
    -- ⚠️ path 是前端路由，component 是 src/views 下的相对路径，两者别混（写错不报错，只白屏）。
    (449, '任务事件注册', 2, 319, 0, '/task/task-event/list',
     '/business/task/task-event/task-event-list.vue', 1, NULL, NULL, 'ApiOutlined', NULL,
     0, NULL, 0, 1, 0, 0, 1),

    (450, '查询', 3, 449, NULL, NULL, NULL, 1, 'taskEventDef:query', 'taskEventDef:query', NULL, 449, 0, NULL, 0, 1, 0, 0, 1),
    (451, '新增', 3, 449, NULL, NULL, NULL, 1, 'taskEventDef:add', 'taskEventDef:add', NULL, 449, 0, NULL, 0, 1, 0, 0, 1),
    (452, '编辑', 3, 449, NULL, NULL, NULL, 1, 'taskEventDef:update', 'taskEventDef:update', NULL, 449, 0, NULL, 0, 1, 0, 0, 1),
    (453, '删除', 3, 449, NULL, NULL, NULL, 1, 'taskEventDef:delete', 'taskEventDef:delete', NULL, 449, 0, NULL, 0, 1, 0, 0, 1);

INSERT INTO `t_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, m.menu_id
FROM `t_role` r
         CROSS JOIN (SELECT 449 AS menu_id UNION ALL SELECT 450 UNION ALL SELECT 451
                     UNION ALL SELECT 452 UNION ALL SELECT 453) m
WHERE r.role_code = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM `t_role_menu` rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);


-- =====================================================================================
-- 自查
-- =====================================================================================

-- 1. 注册表内容
SELECT event_code, event_name, metric_source, biz_id_required, is_high_frequency, discard_log_flag, status
FROM t_task_event ORDER BY id;

-- 2. 🔴 存量任务配置引用的事件是否都已注册（应为空集；有输出说明那些任务改造后会失效）
SELECT DISTINCT c.trigger_event, COUNT(*) AS task_count
FROM t_task_config c
         LEFT JOIN t_task_event e ON e.event_code = c.trigger_event AND e.status = 1
WHERE e.id IS NULL
GROUP BY c.trigger_event;

-- 3. NOT NULL 且无默认值的列（建实体前必查，该模式已复发 6 次）
SELECT column_name FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 't_task_event'
  AND is_nullable = 'NO' AND column_default IS NULL AND extra NOT LIKE '%auto_increment%';
