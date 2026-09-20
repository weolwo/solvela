-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- ⚠️ 本脚本不带 NOW()/CURDATE()，不受 README 里那个时区坑影响。

-- ============================================================================
-- 任务打点 · 事件注册对齐  2026-09-17
-- ============================================================================
--
-- 【背景】
--   任务引擎此前在平台内部是【空转】的：TaskEventService 的类注释写着
--   「上游埋点唯一的进入口」，但它唯一的调用者是管理端那个给外部系统用的
--   /taskEvent/report。会员注册、商城下单、充话费这些本平台自己发生的事，
--   一件都没喂进去。
--
--   现在四处内部打点接上了（见 docs/任务事件打点-实现技术方案.md）。
--   t_task_event 里 DAILY_SIGN / ORDER_PAID / MEMBER_REGISTER 三行 v3.47.0 就有了，
--   但它们的 payload_schema 描述的是【当初设想的】字段名，和真正发出来的对不上；
--   充话费那个动作则整个还没注册。本脚本来对齐这两件事。
--
-- 【可重复执行】
--   全部是 UPDATE + INSERT ... ON DUPLICATE KEY UPDATE，跑几次结果一样。
--
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1. ORDER_AMOUNT：计量字段从 payAmount 改成 payPoints
-- ----------------------------------------------------------------------------
--
-- 【为什么改】
--   种子数据里 metric_source = 'payAmount'，而商城真正发出来的 payload 里
--   【没有】这个键 —— 它给的是 payPoints（积分）和 payCash（现金）两个。
--
--   这不是代码写错了字段名，是一个刻意的决定：
--   MallOrderActionPublisher 把两个口径都放进 payload，由本列去挑用哪一个。
--   一个「积分商城」里，"累计消费"绝大多数时候指的是积分 —— 而如果按现金算，
--   纯积分单的金额是 0，于是"累计消费 500"这类任务【永远不会动】，
--   不报错、不告警，运营只会觉得"这个任务没人做"。
--
-- 【想换回现金口径怎么办】
--   把下面这一行的 payPoints 改成 payCash，重跑本脚本。不用改一行 Java ——
--   这正是 payload 里两个口径都给的原因。
--
UPDATE `t_task_event`
SET `metric_source`  = 'payPoints',
    `payload_schema` = '{"fields": [
        {"key": "orderNo",       "desc": "订单号",     "type": "string"},
        {"key": "payPoints",     "desc": "实付积分",   "type": "decimal"},
        {"key": "payCash",       "desc": "实付现金",   "type": "decimal"},
        {"key": "quantity",      "desc": "件数",       "type": "int"},
        {"key": "commodityCode", "desc": "商品编码",   "type": "string"},
        {"key": "skuCode",       "desc": "SKU 编码",   "type": "string"}
    ]}',
    `remark`         = 'AMOUNT 类任务用。生产者：MallPayService.pay（混合单）与 MallRedeemService.redeem（纯积分单）——🔴 两个产生点，改一处必须想另一处'
WHERE `event_code` = 'ORDER_AMOUNT';


-- ----------------------------------------------------------------------------
-- 2. ORDER_PAID：payload 字段名对齐（orderId -> orderNo）
-- ----------------------------------------------------------------------------
--
-- metric_source 仍是 NONE（计次），所以 payload_schema 只影响
-- 后台任务向导里的字段提示 —— 写错不会让任务失效，只会让运营在配条件时
-- 照着一个不存在的字段名填。
--
UPDATE `t_task_event`
SET `payload_schema` = '{"fields": [
        {"key": "orderNo",       "desc": "订单号",   "type": "string"},
        {"key": "payPoints",     "desc": "实付积分", "type": "decimal"},
        {"key": "payCash",       "desc": "实付现金", "type": "decimal"},
        {"key": "quantity",      "desc": "件数",     "type": "int"},
        {"key": "commodityCode", "desc": "商品编码", "type": "string"},
        {"key": "skuCode",       "desc": "SKU 编码", "type": "string"}
    ]}',
    `remark`         = '🔴 必须带 eventBizId=订单号，否则同一天多笔订单只会算一笔。生产者：MallPayService.pay（混合单）与 MallRedeemService.redeem（纯积分单）'
WHERE `event_code` = 'ORDER_PAID';


-- ----------------------------------------------------------------------------
-- 3. MEMBER_REGISTER / DAILY_SIGN：补上生产者说明
-- ----------------------------------------------------------------------------
--
-- 只改 remark。这一列的 DDL 注释写着「上游由谁埋点、什么时机触发」——
-- 它是运营在后台唯一能看到"这个事件到底谁在发"的地方。
--
UPDATE `t_task_event`
SET `remark` = '一个会员一辈子一次。生产者：MemberRegisterService.createMember，幂等键=会员号'
WHERE `event_code` = 'MEMBER_REGISTER';

UPDATE `t_task_event`
SET `remark` = '天然无单号，按事件自然日兜底幂等（一天算一次）。生产者：MemberSignService.sign（C 端 POST /task/sign）'
WHERE `event_code` = 'DAILY_SIGN';


-- ----------------------------------------------------------------------------
-- 4. RECHARGE_PAID：新增
-- ----------------------------------------------------------------------------
--
-- 【为什么 metric_source 给 NONE 而不是 payAmount】
--   因为"充话费"这个场景今天还没有"累计充值满 X"这类任务的需求，
--   而 payload 里 payAmount 是带着的 —— 真要做的时候，把这一列改成 payAmount
--   就够了，不用动代码。
--
--   反过来（先配上计额）没有好处：一个没人订阅的计量口径改起来同样要改这一列，
--   而先配上会让人误以为它已经被验证过了。
--
-- 【不填 id，靠 event_code 的唯一键 uk_t_tsk_evt_code 判重】
--   种子数据里 id 用到了 18，这里不抢占具体数字 —— 让自增去分配。
--
INSERT INTO `t_task_event`
(`event_code`, `event_name`, `metric_source`, `payload_schema`,
 `biz_id_required`, `is_high_frequency`, `discard_log_flag`, `remark`, `status`)
VALUES ('RECHARGE_PAID', '充值成功', 'NONE',
        '{"fields": [
            {"key": "orderNo",        "desc": "外部消费单号", "type": "string"},
            {"key": "sceneCode",      "desc": "场景编码",     "type": "string"},
            {"key": "payAmount",      "desc": "实付金额",     "type": "decimal"},
            {"key": "originalAmount", "desc": "原价",         "type": "decimal"}
        ]}',
        1, 0, 1,
        '🔴 必须带 eventBizId=外部消费单号。生产者：ExternalRechargeService.payAndExecute —— 打点在 markSuccess【之后】，收了钱不等于充值成功',
        1)
ON DUPLICATE KEY UPDATE
    `event_name`      = VALUES(`event_name`),
    `metric_source`   = VALUES(`metric_source`),
    `payload_schema`  = VALUES(`payload_schema`),
    `biz_id_required` = VALUES(`biz_id_required`),
    `remark`          = VALUES(`remark`);


-- ============================================================================
-- 验证
-- ============================================================================
--
-- 跑完之后应当看到 5 行，metric_source 分别是 NONE/NONE/payPoints/NONE/NONE：
--
--   SELECT event_code, event_name, metric_source, biz_id_required, status
--     FROM t_task_event
--    WHERE event_code IN ('DAILY_SIGN','ORDER_PAID','ORDER_AMOUNT',
--                         'MEMBER_REGISTER','RECHARGE_PAID')
--    ORDER BY event_code;
--
-- ⚠️ 光注册事件【不会】让任何进度开始涨 —— 还得有任务配置订阅它
--    （t_task_config.trigger_event = 对应编码，且状态不是下线）。
--    没有订阅者时，打点会被防腐层安静地忽略，对账任务也会直接跳过：
--    那是预期行为，不是故障。
-- ============================================================================
