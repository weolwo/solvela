-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 模板正文和列注释全部变成乱码。
SET NAMES utf8mb4;

-- ============================================================================
-- 通知中心 · 阶段 1（定向通知）  2026-09-14
-- ============================================================================
--
-- 【它解决什么】
--   平台已经有一堆「发生了但用户不知道」的事件：中奖、发货、履约失败、账号冻结、
--   券将过期。此前一条都通知不到 —— 全仓没有任何站内信/通知表。
--
-- 【只建两张表，公告那三张是阶段 4】
--   本脚本只建【定向通知】需要的两张。公告走的是另一套存储模型
--   （读扩散 + 一人一行的已读游标），不在这里。
--   完整设计见 docs/通知与公告-实现技术方案.md。
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/通知中心-建表与模板种子.sql;
--   建表用 IF NOT EXISTS；模板种子按 (template_code, version) 判存，可重复执行。
--
-- 🔴 改完表结构记得重新导出基线，别手改 schema-baseline.sql：
--   cd 数据库SQL脚本/tools && java DumpSchema.java
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1. 通知模板：按 (template_code, version) 不可变
-- ----------------------------------------------------------------------------
--
-- 🔴 编辑模板 = 新增一个 version，永远不要 UPDATE 已有行。
--
--    因为 t_member_notification 只存「模板引用 + 参数」，正文是读的时候现渲染的。
--    原地改模板 = 所有历史通知的显示被追溯篡改：用户 1 月收到的
--    「恭喜获得 100 积分」，6 月改了模板之后就变成另一句话了。
--    在金额/奖品类消息上这是事故级的 —— 用户截图的和现在显示的对不上。
--
--    更隐蔽的一种：占位符改名（${amount} → ${score}）。旧 params 里没有 score 这个 key，
--    而 SolvelaTemplateUtil 的口径是【解析不到的占位符原样保留】（刻意的，防模板注入），
--    于是用户直接看到字面的 ${score}。不报错、不告警。
--
CREATE TABLE IF NOT EXISTS `t_notification_template` (
  `template_code`    varchar(64)  NOT NULL COMMENT '模板编码：跨环境稳定，代码引用它。取值见 NotificationTemplateEnum',
  `version`          int          NOT NULL COMMENT '版本号。🔴 编辑=新增版本，永不原地改 —— 历史通知靠它锁定当年措辞',
  `category`         varchar(32)  NOT NULL COMMENT '分类：SYSTEM/TRADE/MARKETING。只影响C端tab分组与免打扰粒度，不参与存储决策',
  `title_template`   varchar(128) NOT NULL COMMENT '标题模板，${key} 占位符',
  `content_template` text         NOT NULL COMMENT '正文模板，${key} 占位符',
  `param_keys`       json                  DEFAULT NULL COMMENT '本版本用到的占位符清单。运行期校验用，跟着version走，是权威（枚举那份是编译期文档）',
  `status`           tinyint      NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用。🔴 只停用不删除：删了历史通知就渲染不出来',
  `create_by`        varchar(64)  DEFAULT NULL COMMENT '创建人',
  `create_time`      datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        varchar(64)  DEFAULT NULL COMMENT '更新人',
  `update_time`      datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`template_code`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知模板：按版本不可变';


-- ----------------------------------------------------------------------------
-- 2. 会员通知（站内信）：定向，一人一条，写扩散
-- ----------------------------------------------------------------------------
--
-- 🔴 这是整个通知方案里【唯一一张会爆】的表。
--
--    行数 = 业务事件数 × 时间，只增不减。100万活跃用户 × 300条/年 = 3亿行/年。
--
--    所以两件事从第一天就必须在：
--      · 模板化 —— 只存 template_code + version + params，不存渲染后的全文。
--        省的不只是硬盘：行宽小了每个数据页装的行数才多，buffer pool 命中率才不塌，
--        而收件箱是高频读路径。
--      · 归档 —— MemberNotificationCleanJob，默认保留 180 天。
--        等有了3亿行再补归档，第一次跑就是个大删除，那时候没人敢按下去。
--
--    对比 t_member_announcement_cursor（阶段4）：那张一人一行，行数与公告条数无关，
--    发一万条公告也不涨 —— 那才是不会爆的形状。
--
CREATE TABLE IF NOT EXISTS `t_member_notification` (
  `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT 'id',
  `member_id`        bigint       NOT NULL COMMENT '会员号：关联键',
  `template_code`    varchar(64)  NOT NULL COMMENT '模板编码',
  `template_version` int          NOT NULL COMMENT '模板版本：指向发送当时那一版。🔴 没有它，改模板就会追溯篡改历史通知',
  `params`           json                  DEFAULT NULL COMMENT '渲染参数。🔴 只存显示值不存id —— 存id就要join，而奖品可能已下架改名，又绕回篡改历史',
  `summary`          varchar(128) NOT NULL COMMENT '发送时就渲染好的短摘要。列表页直接用，零渲染零查模板表',
  `category`         varchar(32)  NOT NULL COMMENT '模板分类快照：tab分组与免打扰按它过滤',
  `biz_ref_id`       varchar(64)  DEFAULT NULL COMMENT '关联业务单号：prize_code/order_no等。不建索引，纯排查用',
  `read_flag`        tinyint      NOT NULL DEFAULT 0 COMMENT '0-未读 1-已读。通知侧已读是逐条的，跳读天然支持',
  `read_time`        datetime     DEFAULT NULL COMMENT '已读时间',
  `create_by`        varchar(64)  DEFAULT NULL COMMENT '创建人',
  `create_time`      datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        varchar(64)  DEFAULT NULL COMMENT '更新人',
  `update_time`      datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  -- 收件箱列表与未读数都走它。id 放第三位是为了让「按时间倒序分页」直接吃索引
  -- （自增id与时间同序），不用 filesort
  KEY `idx_member_read` (`member_id`, `read_flag`, `id`),
  -- 🔴 归档 job 专用。没有它，保留期清理会全表扫 —— 在一张亿级表上
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员通知（站内信）：定向，一人一条';


-- ----------------------------------------------------------------------------
-- 3. 模板种子：五个业务事件各一条，version 全部从 1 开始
-- ----------------------------------------------------------------------------
--
-- 判存插入，可重复执行。
--
-- ⚠️ 写法上有两个坑，都踩过：
--   ① 不要写成 `SELECT * FROM (SELECT 'X', 1, ..., 1, 'system') AS t`——
--      派生表里两个字面量 1 会变成两个都叫「1」的列，MySQL 直接报
--      Duplicate column name '1'。去掉外层包装、直接 SELECT 字面量即可。
--   ② 子查询里引用的正是 INSERT 的目标表，必须包一层 (SELECT * FROM t) m，
--      否则报 You can't specify target table 'xxx' for update in FROM clause。
--      这和 t_menu 那几个脚本里的写法是同一个原因。
--
-- ⚠️ 措辞是占位的，上线前请产品/运营过一遍。但【占位符不要随便改名】——
--    改名要连着改 NotificationTemplateEnum.requiredParams 和所有调用点，
--    漏一处的表现是用户看到字面的 ${xxx}。真要改，走「新增 version 2」那条路。

-- ⚠️ 正文里【没有活动名】，同样是被依赖方向逼出来的：
--    发送点在 ledger 的回写处（资产真到账那一刻），而活动表在 marketing，
--    marketing 排在 ledger 之后，ledger 依赖它直接成环。
--    t_prize_log 上只有 activity_code（形如 ACT_618），那个码对用户没意义，不如不显示。
INSERT INTO `t_notification_template`
  (`template_code`, `version`, `category`, `title_template`, `content_template`, `param_keys`, `status`, `create_by`)
SELECT
  'PRIZE_WON', 1, 'MARKETING',
  '恭喜您中奖啦',
  '您获得的 ${prizeName} ×${amount} 已发放到您的账户，快去看看吧~',
  JSON_ARRAY('prizeName', 'amount'), 1, 'system'
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_notification_template`) m WHERE m.`template_code` = 'PRIZE_WON' AND m.`version` = 1
);

-- ⚠️ 正文里是【订单号】不是商品名，这是被依赖方向逼出来的：
--    发货发生在 ledger 的履约域，t_physical_delivery 上只有 source_biz_id，没有商品名快照；
--    要查商品得让 ledger 依赖 mall，而那条缝是单向的（mall → ledger），
--    由 MallLedgerBoundaryTest 守着。
--    想显示商品名的正解是给 t_physical_delivery 加一个商品名快照列（由 mall 建单时写进来），
--    不是反向打通依赖。
INSERT INTO `t_notification_template`
  (`template_code`, `version`, `category`, `title_template`, `content_template`, `param_keys`, `status`, `create_by`)
SELECT
  'DELIVERY_SHIPPED', 1, 'TRADE',
  '您的商品已发货',
  '您的订单 ${sourceBizId} 已发货。承运：${logisticsCompany}，运单号：${logisticsNo}。请留意物流信息。',
  JSON_ARRAY('sourceBizId', 'logisticsCompany', 'logisticsNo'), 1, 'system'
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_notification_template`) m WHERE m.`template_code` = 'DELIVERY_SHIPPED' AND m.`version` = 1
);

-- 🔴 文案里【绝对不能】写「积分已退回」。MallOrderDao.markFailed 的注释把这条定死了：
--    失败不退积分 —— 东西还欠着用户，不是没买。运营可能只是漏配了券模，补上就能发。
--    真正的取消是下面那条 ORDER_CANCELLED。两条文案混了，用户会按「钱回来了」
--    去理解一次「我们欠着你」，然后再兑一单。
INSERT INTO `t_notification_template`
  (`template_code`, `version`, `category`, `title_template`, `content_template`, `param_keys`, `status`, `create_by`)
SELECT
  'ORDER_FULFILL_FAILED', 1, 'TRADE',
  '兑换未能完成',
  '很抱歉，您的订单 ${orderNo}（${commodityName}）暂时未能完成兑换：${failReason}。我们会尽快处理，您的权益不受影响。',
  JSON_ARRAY('orderNo', 'commodityName', 'failReason'), 1, 'system'
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_notification_template`) m WHERE m.`template_code` = 'ORDER_FULFILL_FAILED' AND m.`version` = 1
);

-- 超时取消：这一条【才】是积分原路退回。发送点 MallOrderExpireJob。
-- 不发的话，用户只会看到订单莫名消失、积分数字莫名变了。
INSERT INTO `t_notification_template`
  (`template_code`, `version`, `category`, `title_template`, `content_template`, `param_keys`, `status`, `create_by`)
SELECT
  'ORDER_CANCELLED', 1, 'TRADE',
  '订单已取消，积分已退回',
  '您的订单 ${orderNo}（${commodityName}）因超时未支付已取消，${refundPoints} 积分已原路退回您的账户。',
  JSON_ARRAY('orderNo', 'commodityName', 'refundPoints'), 1, 'system'
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_notification_template`) m WHERE m.`template_code` = 'ORDER_CANCELLED' AND m.`version` = 1
);

INSERT INTO `t_notification_template`
  (`template_code`, `version`, `category`, `title_template`, `content_template`, `param_keys`, `status`, `create_by`)
SELECT
  'ACCOUNT_LIMITED', 1, 'SYSTEM',
  '账号使用受限提醒',
  '您的账号因「${limitType}」被限制部分功能，预计 ${unlockTime} 自动恢复。如有疑问请联系客服。',
  JSON_ARRAY('limitType', 'unlockTime'), 1, 'system'
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_notification_template`) m WHERE m.`template_code` = 'ACCOUNT_LIMITED' AND m.`version` = 1
);

-- 🔴 参数是 count 而不是券名，这是刻意的：一个人有8张券要过期，必须发【一条】
--    「您有8张券即将过期」，不是8条。参数形状本身就把「一券一条」那种写法挡掉了。见方案 §8。
INSERT INTO `t_notification_template`
  (`template_code`, `version`, `category`, `title_template`, `content_template`, `param_keys`, `status`, `create_by`)
SELECT
  'COUPON_EXPIRING', 1, 'TRADE',
  '您有优惠券即将过期',
  '您有 ${count} 张优惠券即将过期，最近一张将于 ${nearestExpireTime} 失效，别忘了使用哦~',
  JSON_ARRAY('count', 'nearestExpireTime'), 1, 'system'
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `t_notification_template`) m WHERE m.`template_code` = 'COUPON_EXPIRING' AND m.`version` = 1
);
