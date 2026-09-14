-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 公告正文和列注释全部变成乱码。
SET NAMES utf8mb4;

-- ============================================================================
-- 通知中心 · 阶段 3/4/5  2026-09-14
--   阶段 3：免打扰偏好
--   阶段 4：公告（读扩散 + 已读游标）
--   阶段 5：强制确认公告的确认留痕
-- ============================================================================
--
-- 前置：先执行 通知中心-建表与模板种子.sql（阶段 1/2 的两张表）。
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/通知中心-公告与偏好.sql;
--   全部 IF NOT EXISTS，可重复执行。
--
-- 🔴 改完表结构记得重新导出基线，别手改 schema-baseline.sql：
--   cd 数据库SQL脚本/tools && java DumpSchema.java
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1. 免打扰偏好：一人一行（阶段 3）
-- ----------------------------------------------------------------------------
--
-- 为什么是「一人一行、每档一列」而不是「一人一分类一行」：
--   · 行数 = 用户数，与分类数无关（加一档分类是加一列，不是给每个用户加一行）
--   · 读偏好是一次主键查询，而它在【每条通知的发送路径上】
--
-- 🔴 懒创建：用户第一次改设置才 insert。从没进过设置页的用户 = 0 行。
--    所以代码里【查不到必须当成「都开着」】——反了的话上线当天
--    全体存量用户就再也收不到任何通知，而且不报错。
--
-- SYSTEM 分类【故意没有对应的列】：它不可关（NotificationCategoryEnum.mutable=false）。
-- 没有列就没人能写进一个「关闭 SYSTEM」的值，这条约束由表结构兜底，
-- 而不是靠每个读的地方都记得判一次。
--
CREATE TABLE IF NOT EXISTS `t_member_notification_preference` (
  `member_id`         bigint   NOT NULL COMMENT '会员号：一人一行，直接做主键',
  `trade_enabled`     tinyint  NOT NULL DEFAULT 1 COMMENT '交易物流类：1-接收 0-关闭',
  `marketing_enabled` tinyint  NOT NULL DEFAULT 1 COMMENT '活动营销类：1-接收 0-关闭',
  `create_time`       datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员通知免打扰偏好：一人一行';


-- ----------------------------------------------------------------------------
-- 2. 公告：一条内容一行，读扩散（阶段 4）
-- ----------------------------------------------------------------------------
--
-- 🔴 这张表和 t_member_notification 的区别是整个方案的地基。
--
--    通知是定向的（一人一条，写扩散）；公告是广播的，【绝不能给每个用户插一行】。
--    10万用户 × 100条公告 = 1000万行，而真实信息量只有100条。
--
--    行业里这类系统炸库的经典姿势就这一个，而且故障现象出现在存储层、根因在写入模型
--    ——排查通常停在「数据库不行」，然后换一个更贵的数据库，买两三年喘息，
--    期间数据再长十倍。
--
--    判据：看行数增长的【斜率】。用户数翻10倍、行数跟着翻10倍是正常的；
--    翻100倍就是设计缺陷，换什么库都一样，因为存的是 N×M 笛卡尔积。
--
--    本表的行数只跟【公告条数】走：一年几百行。
--
CREATE TABLE IF NOT EXISTS `t_announcement` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT 'id',
  `title`         varchar(128) NOT NULL COMMENT '标题',
  `content`       text         NOT NULL COMMENT '正文。运营手写，不用模板——公告本来就一行，没有冗余可省',
  `category`      varchar(32)  NOT NULL COMMENT '分类：SYSTEM/TRADE/MARKETING',
  `force_ack`     tinyint      NOT NULL DEFAULT 0 COMMENT '0-普通公告 1-强制确认（弹窗+留痕）',
  `audience_type` varchar(32)  NOT NULL DEFAULT 'ALL' COMMENT '人群：ALL / REGISTER_BEFORE / REGISTER_AFTER',
  `audience_rule` json                  DEFAULT NULL COMMENT '🔴 存规则不存名单。物化名单就回到「用户数×公告数」了',
  `publish_time`  datetime     NOT NULL COMMENT '生效时间，未到不展示',
  `expire_time`   datetime     NOT NULL COMMENT '🔴 必填。未读计算的过滤条件 + 归档依据，允许为空等于两条全废',
  `status`        tinyint      NOT NULL DEFAULT 1 COMMENT '1-发布 0-下架',
  `create_by`     varchar(64)  DEFAULT NULL COMMENT '创建人',
  `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     varchar(64)  DEFAULT NULL COMMENT '更新人',
  `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_visible` (`status`, `publish_time`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告：一条内容一行，读扩散';


-- ----------------------------------------------------------------------------
-- 3. 公告已读游标：一人一行（阶段 4）
-- ----------------------------------------------------------------------------
--
-- 🔴 这张表就是「不会爆」的那个形状：
--    发10条公告和发10000条公告，本表行数【完全一样】。
--
--    参照物就在同一个库里：t_member_wallet 是 UNIQUE(member_id, asset_type)，
--    一人 × 资产类型数行，比一人一行【更多】。钱包不爆，本表更不会。
--
-- 只有一个 bigint，【没有例外集合】：
--    早期方案给游标加过一个 read_exception_ids JSON 列来支持跳读，已删除。
--    前端公告与通知是两个 tab，两种已读语义各待在自己的列表里，
--    用户不会看到「点通知清一条、点公告清一片」。
--    而那个 JSON 列会随跳读不断变宽，被频繁 UPDATE 会导致 InnoDB 页分裂和碎片。
--
--    🔴 将来若有人提「把两个 tab 合并成一个列表」，必须连带重新评估这里。
--
-- 懒创建：用户第一次打开公告 tab 才 insert。没有行 = last_read_id 视为 0
--        = 所有未过期公告都未读（红点亮着），这恰好是想要的行为。
--
CREATE TABLE IF NOT EXISTS `t_member_announcement_cursor` (
  `member_id`    bigint   NOT NULL COMMENT '会员号：一人一行，直接做主键',
  `last_read_id` bigint   NOT NULL DEFAULT 0 COMMENT '此id及以前的公告全部视为已读。不支持跳读，所以只需这一个bigint',
  `create_time`  datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告已读游标：一人一行，与公告条数无关';


-- ----------------------------------------------------------------------------
-- 4. 强制确认留痕（阶段 5）
-- ----------------------------------------------------------------------------
--
-- 🔴 只记【确认过的】，不预建「待确认」行。
--
--    反面做法是公告发布时给全部目标人群预建一批 ack_time=NULL 的待确认行 ——
--    那就是「广播写扩散」原封不动的复刻：行数 = 人群数 × 必读公告数。绝对不要。
--
--    这个选择有个必须接受的后果：能回答「谁确认了」，答不了「谁还没确认」。
--    后者 = 目标人群 ➖ ack集合，是个反连接，人群一大就很贵；
--    要让它快就得物化人群名单，又回到 N 行。所以【不要提供那个查询】。
--
--    好在业务上不需要：强制确认公告的机制本身就是「没确认就每次进来都弹，
--    直到确认为止」，它自带催办。
--
-- 为什么不叫 t_member_announcement_ack：
--    本项目的 t_member_* 前缀意味着「一人一行、生命周期跟着会员走」。
--    而这是张关联表：主键是复合的，生命周期跟着【公告】走（公告没了ack就没意义），
--    主查询方向也是运营侧的「这条公告确认覆盖率多少」。
--    关联表在本项目的命名规律是【主体在前、不加双前缀】（t_role_menu / t_task_prize_mapping）。
--
-- 主键顺序 (announcement_id, member_id) 而不是反过来：主查询是按公告聚合的覆盖率统计。
-- idx_member 覆盖用户侧的「我确认过这条吗」。
--
CREATE TABLE IF NOT EXISTS `t_announcement_ack` (
  `announcement_id` bigint      NOT NULL COMMENT '公告id',
  `member_id`       bigint      NOT NULL COMMENT '会员号：关联键',
  `ack_time`        datetime    NOT NULL COMMENT '确认时间：合规留痕，只增不改',
  `ack_ip`          varchar(64) DEFAULT NULL COMMENT '确认时IP，合规场景可能要',
  PRIMARY KEY (`announcement_id`, `member_id`),
  KEY `idx_member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='强制确认公告的确认记录：仅 force_ack=1 的公告产生';
