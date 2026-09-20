-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- ============================================================================
-- 会员等级 · 命名统一（Level → Grade）与 5 表模型  2026-09-20
--
-- 方案见 docs/会员等级-实现技术方案.md
-- 前置：数据库SQL脚本/mysql/会员等级-建表与种子.sql（阶段 1）必须已执行
--
-- 【这次改什么】
--   ① 会员域的 level 全面改叫 grade，from/to 改成 old/new；
--   ② 新增 t_member_period_summary —— 期末结算快照，补上「历史周期追溯」这个洞；
--   ③ 新增 t_grade_privilege —— 权益拆成独立表，t_member_grade.benefits 那列废弃；
--   ④ 删掉 t_member_growth.version —— 它是个【从未生效的乐观锁】，见下。
--
-- 🔴 为什么不是「level 是保留字」
--   MySQL 8.4 的 INFORMATION_SCHEMA.KEYWORDS 里 LEVEL 的 RESERVED = 0，它不是保留字
--   （RANK 才是）。改名的理由是另外两条：old_grade/new_grade 比 from_level/to_level
--   一眼就懂；而 5 表模型本来就要动表结构，一起改比将来分两次改便宜。
--
-- ⚠️ 范围只限【会员域】。仓库里还有 7 处 *_level 列（prize_level / attest_level /
--   stage_level / review_level / position_level / …），它们表达的是「奖品档位」
--   「设备可信等级」这些<b>别的概念</b>，不在本次范围内，也不该被顺手改掉。
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/会员等级-Grade改名与5表模型.sql;
--   改名部分用 information_schema 判存 + 动态 SQL，可重复执行（第二遍整体跳过）。
--
-- 🔴 执行完必须重新导出基线：
--       cd 数据库SQL脚本/tools && java -cp <mysql-connector.jar> DumpSchema.java
--       cd 数据库SQL脚本/tools && java -cp <mysql-connector.jar> DumpSeedData.java
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 0. 总开关：t_member_level 还在，说明这一份还没跑过
--
--    所有改名共用这一个判据。它们必须<b>同生共死</b> —— 跑一半的库
--    （表改了名、列没改）比没跑更难查，因为代码会在运行时才报列不存在。
-- ---------------------------------------------------------------------------
SET @todo := (SELECT COUNT(*) FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_member_level');


-- ---------------------------------------------------------------------------
-- 1. t_member_level → t_member_grade，level → grade_code，level_name → grade_name
-- ---------------------------------------------------------------------------
SET @sql := IF(@todo = 1, 'RENAME TABLE `t_member_level` TO `t_member_grade`', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

SET @sql := IF(@todo = 1,
  'ALTER TABLE `t_member_grade`
     RENAME COLUMN `level` TO `grade_code`,
     RENAME COLUMN `level_name` TO `grade_name`,
     RENAME INDEX `uk_t_mbr_lv_level` TO `uk_t_mbr_gd_code`', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

SET @sql := IF(@todo = 1,
  'ALTER TABLE `t_member_grade`
     MODIFY COLUMN `grade_code` int NOT NULL COMMENT ''等级值：0 起，数字越大越高。0 必须存在（新会员的落点）'',
     MODIFY COLUMN `grade_name` varchar(32) NOT NULL COMMENT ''等级名：普通会员/银卡/金卡/白金/钻石''', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- 🔴 benefits 废弃：权益搬到 t_grade_privilege（见 §4）。
--    一列 varchar(500) 装不下图标、跳转、多语言，而运营迟早会要这三样 ——
--    那时再拆，就得一边拆一边解析已经写进去的自由文本。
SET @sql := IF(@todo = 1, 'ALTER TABLE `t_member_grade` DROP COLUMN `benefits`', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;


-- ---------------------------------------------------------------------------
-- 2. t_member_growth：聚焦「当前周期」
--
--    level        → current_grade
--    level_since  → grade_since
--    period_value → current_period_value
--    protect_target → protect_grade
--
--    🔴 删掉 version。
--    它是个【从未生效的乐观锁】：成长值累加走的是
--        UPDATE ... SET current_period_value = current_period_value + ?
--    这条原子自增，全工程没有任何代码读写过 version。留着只会让下一个人
--    以为这里有乐观锁保护，然后基于那个错误前提去设计并发方案。
--
--    ⚠️ total_value 保留。它是终身累计，只展示、不参与定级；
--    有了周期快照表之后它理论上可由 SUM(final_growth_value) 推出，
--    但那是一次扫描，而它在自增 SQL 里只是多一个 + 号。
-- ---------------------------------------------------------------------------
SET @sql := IF(@todo = 1,
  'ALTER TABLE `t_member_growth`
     RENAME COLUMN `level` TO `current_grade`,
     RENAME COLUMN `level_since` TO `grade_since`,
     RENAME COLUMN `period_value` TO `current_period_value`,
     RENAME COLUMN `protect_target` TO `protect_grade`,
     DROP COLUMN `version`', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

SET @sql := IF(@todo = 1,
  'ALTER TABLE `t_member_growth`
     MODIFY COLUMN `current_grade` int NOT NULL DEFAULT ''0'' COMMENT ''当前等级。缓冲期内取 protect_grade，不等于 f(current_period_value)'',
     MODIFY COLUMN `grade_since` datetime DEFAULT NULL COMMENT ''当前等级是什么时候到的'',
     MODIFY COLUMN `current_period_value` bigint NOT NULL DEFAULT ''0'' COMMENT ''本周期累计成长值【冗余：可由流水求和得出，但判级是热路径。必须有对账任务】'',
     MODIFY COLUMN `protect_grade` int DEFAULT NULL COMMENT ''缓冲期要保住的等级''', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;


-- ---------------------------------------------------------------------------
-- 3. t_member_level_log → t_member_grade_log，from/to → old/new
-- ---------------------------------------------------------------------------
SET @sql := IF(@todo = 1, 'RENAME TABLE `t_member_level_log` TO `t_member_grade_log`', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

SET @sql := IF(@todo = 1,
  'ALTER TABLE `t_member_grade_log`
     RENAME COLUMN `from_level` TO `old_grade`,
     RENAME COLUMN `to_level` TO `new_grade`,
     RENAME INDEX `idx_t_mbr_lv_log_mbr` TO `idx_t_mbr_gd_log_mbr`', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

SET @sql := IF(@todo = 1,
  'ALTER TABLE `t_member_grade_log`
     MODIFY COLUMN `old_grade` int NOT NULL COMMENT ''变更前等级'',
     MODIFY COLUMN `new_grade` int NOT NULL COMMENT ''变更后等级''', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;


-- ---------------------------------------------------------------------------
-- 4.【新增】t_grade_privilege —— 等级权益
--
-- 🔴 它<b>仍然不驱动任何逻辑</b>，拆表不等于建了权益中心。
--    真正的权益靠三样东西实现，一样都没变：
--      · 高等级专享任务  → t_task_config.target_audience = GRADE_GTE_N
--      · 专享活动/奖池    → 脚本里 member_gradeAtLeast(n)
--      · 等级价/专享商品  → 商城价格模型（还没做）
--    这张表回答的是另一个问题：<b>「用户在等级页看见自己在保什么」</b>。
--    没有它，用户没有任何理由去保级 —— 而保级正是这套机制要换的活跃与粘性。
--
-- ⚠️ 谁都不要给它加「生效引擎」。那一天来临时，应该是新立一个权益域，
--    而不是让这张展示表长出执行语义 —— 展示与执行混在一张表里之后，
--    改一句文案就有可能改掉一条业务规则。
--
-- 为什么不是 t_member_grade 上的一列 varchar：图标要 file_id、跳转要 url、
-- 排序要独立字段、以后还要多语言。这些塞进一个 varchar(500) 就是历史包袱本身。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_grade_privilege` (
    `id`             bigint      NOT NULL AUTO_INCREMENT COMMENT 'id',
    `grade_code`     int         NOT NULL COMMENT '等级值，关联 t_member_grade.grade_code。⚠️ 关联的是业务键不是自增 id —— 自增 id 换环境会变',
    `privilege_code` varchar(64) NOT NULL COMMENT '权益编码：EXCLUSIVE_TASK/EXCLUSIVE_POOL/BIRTHDAY_GIFT/MONTHLY_COUPON/PRIORITY_SERVICE…。🔴 它是【文档】，没有任何引擎读它',
    `privilege_name` varchar(64) NOT NULL COMMENT '权益名，直接展示给用户',
    `description`    varchar(255)         DEFAULT NULL COMMENT '权益说明，等级页的第二行小字',
    `icon_file_id`   bigint               DEFAULT NULL COMMENT '权益图标 file_id，走文件模块',
    `action_url`     varchar(255)         DEFAULT NULL COMMENT '点进去跳哪儿。为空表示纯展示、不可点',
    `sort`           int         NOT NULL DEFAULT '0' COMMENT '展示顺序，越大越靠前',
    `status`         tinyint     NOT NULL DEFAULT '1' COMMENT '状态：0-停用, 1-启用',
    `create_by`      varchar(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`    datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      varchar(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`    datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_t_gd_priv_code` (`grade_code`, `privilege_code`),
    KEY `idx_t_gd_priv_grade` (`grade_code`, `sort`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='等级权益【纯展示，不驱动逻辑】';


-- ---------------------------------------------------------------------------
-- 5.【新增】t_member_period_summary —— 期末结算快照
--
-- 🔴 它补的不是「历史查不到」，而是两个更具体的洞：
--
--   ① <b>等级没变的周期，全库一行记录都没有</b>。
--      MemberGradeChangeService.change() 在 old == new 且非 KEEP 时直接返回，
--      所以「平级过完一个周期」的人在 t_member_grade_log 里是空的 ——
--      而「我上周期到底攒了多少、差多少」正是客诉最常问的那句。
--
--   ② <b>对账任务没有锚点</b>。方案 §5.2 要求比对 current_period_value 与
--      SUM(流水)，但前者<b>一直在动</b>。有了快照，对账变成「比对一个封存的数」，
--      那才是能写出来的对账。
--
-- ⚠️ period_no 必须与 t_member_growth_log.period_tag <b>字节一致</b>
--    （都是 period_start 的 yyyyMMdd）。对账任务要靠它 JOIN 两张表；
--    一边写 yyyyMM、一边写 yyyyMMdd 的话，对账会安静地什么都对不上。
--
-- ⚠️ 千人千面周期：每天都有人到期。结算任务必须<b>分页捞 + 单人独立事务</b>
--    （形状照抄 MallOrderExpireJob），绝不能一个大事务扫全表。
--    uk(member_id, period_no) 让重跑安全 —— 那是分批处理的前提。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_member_period_summary` (
    `id`                 bigint      NOT NULL AUTO_INCREMENT COMMENT 'id',
    `member_id`          bigint      NOT NULL COMMENT '会员号：关联键',
    `period_no`          varchar(32) NOT NULL COMMENT '周期标识 = period_start 的 yyyyMMdd。🔴 必须与 t_member_growth_log.period_tag 同口径，对账靠它 JOIN',
    `period_start`       datetime    NOT NULL COMMENT '该周期实际开始时间',
    `period_end`         datetime    NOT NULL COMMENT '该周期实际结束时间',
    `final_growth_value` bigint      NOT NULL COMMENT '期末最终成长值。清零前的那个数，事后唯一凭证',
    `grade_before`       int         NOT NULL COMMENT '结算前的等级',
    `settled_grade`      int         NOT NULL COMMENT '期末结算定下的等级',
    `settle_result`      varchar(32) NOT NULL COMMENT '结算结果：UPGRADE-升级, KEEP-保持, DOWNGRADE-降级, PROTECT_START-进入保级缓冲, PROTECT_KEPT-缓冲期内保住了, PROTECT_FAILED-缓冲期满没保住',
    `next_grade`         int                  DEFAULT NULL COMMENT '结算时的下一档等级。已是最高档为空',
    `next_threshold`     bigint               DEFAULT NULL COMMENT '下一档门槛的【当时快照】。⚠️ 必须存：运营改过门槛之后，拿今天的配置回算会算出另一个答案',
    `protect_grade`      int                  DEFAULT NULL COMMENT '该周期若处于保级缓冲，保的是哪一档',
    `settled_at`         datetime    NOT NULL COMMENT '结算发生的时刻（不是周期结束时刻 —— job 可能晚跑）',
    `create_by`          varchar(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`        datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`          varchar(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`        datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_t_mbr_ps_period` (`member_id`, `period_no`),
    KEY `idx_t_mbr_ps_mbr` (`member_id`, `period_start`),
    KEY `idx_t_mbr_ps_settled` (`settled_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='会员周期结算快照';


-- ---------------------------------------------------------------------------
-- 6. 任务人群取值：LEVEL_GTE_N → GRADE_GTE_N
--
--    它引用的就是会员等级，留着 LEVEL_ 前缀等于同一个概念两个词。
--    ⚠️ 这一条改的是【存量数据】，不是表结构 —— 代码里的常量同步改成
--    TaskConst.AUDIENCE_GRADE_GTE_PREFIX。两边必须一起上线。
-- ---------------------------------------------------------------------------
UPDATE `t_task_config`
   SET `target_audience` = REPLACE(`target_audience`, 'LEVEL_GTE_', 'GRADE_GTE_')
 WHERE `target_audience` LIKE 'LEVEL\_GTE\_%';


-- ---------------------------------------------------------------------------
-- 7. 权益种子：把原先 benefits 那列的文案拆成行
--
--    ⚠️ 门槛与文案都是【示意值】，等跑过一个季度看到真实分布再定。
-- ---------------------------------------------------------------------------
INSERT INTO `t_grade_privilege` (`grade_code`, `privilege_code`, `privilege_name`, `description`, `sort`, `status`, `create_by`)
VALUES (0, 'PUBLIC_ACTIVITY', '全部公开活动', '参与平台所有公开活动', 100, 1, 'seed'),

       (1, 'EXCLUSIVE_TASK', '银卡专享任务', '解锁仅银卡及以上可做的任务', 100, 1, 'seed'),
       (1, 'ACTIVITY_PRIORITY', '活动优先参与', '热门活动优先开放', 90, 1, 'seed'),

       (2, 'EXCLUSIVE_TASK', '金卡专享任务', '解锁仅金卡及以上可做的任务', 100, 1, 'seed'),
       (2, 'EXCLUSIVE_POOL', '专享奖池', '抽奖走金卡专属奖池', 90, 1, 'seed'),
       (2, 'BIRTHDAY_GIFT', '生日礼', '生日当天领取专属礼包', 80, 1, 'seed'),

       (3, 'EXCLUSIVE_TASK', '白金专享任务', '解锁仅白金及以上可做的任务', 100, 1, 'seed'),
       (3, 'EXCLUSIVE_POOL', '专享奖池', '抽奖走白金专属奖池', 90, 1, 'seed'),
       (3, 'BIRTHDAY_GIFT', '生日礼', '生日当天领取专属礼包', 80, 1, 'seed'),
       (3, 'MONTHLY_COUPON', '每月专属券', '每月 1 号自动发放专属优惠券', 70, 1, 'seed'),

       (4, 'EXCLUSIVE_TASK', '钻石专享任务', '解锁仅钻石可做的任务', 100, 1, 'seed'),
       (4, 'EXCLUSIVE_POOL', '最高奖池', '抽奖走最高等级奖池', 90, 1, 'seed'),
       (4, 'BIRTHDAY_GIFT', '生日礼', '生日当天领取专属礼包', 80, 1, 'seed'),
       (4, 'MONTHLY_COUPON', '每月专属券', '每月 1 号自动发放专属优惠券', 70, 1, 'seed'),
       (4, 'PRIORITY_SERVICE', '专属客服', '专属通道，优先响应', 60, 1, 'seed')
ON DUPLICATE KEY UPDATE `privilege_name` = VALUES(`privilege_name`),
                        `description`    = VALUES(`description`),
                        `sort`           = VALUES(`sort`),
                        `status`         = VALUES(`status`);


-- ============================================================================
-- 自查
-- ============================================================================
--
--   SHOW CREATE TABLE t_member_grade;
--   SHOW CREATE TABLE t_member_growth;
--   SELECT grade_code, grade_name, threshold FROM t_member_grade ORDER BY grade_code;
--   SELECT grade_code, privilege_code, privilege_name FROM t_grade_privilege ORDER BY grade_code, sort DESC;
--   SELECT target_audience, COUNT(*) FROM t_task_config GROUP BY target_audience;
--
-- ⚠️ 还没有任何一行 t_member_period_summary —— 它由【期末结算 job】写入，
--    那是阶段 4。这次只是把表和约束先立起来。
-- ============================================================================
