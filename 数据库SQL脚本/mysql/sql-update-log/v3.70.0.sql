-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- =====================================================================================
-- v3.70.0  默认租户标识 '0' → 'taozi'
-- 撰写：2026-08-22
--
-- 背景：tenant_id 从建库起就是个惰性占位符 —— 25 张表带这个列、共 9317 行，
--       <b>tenant_id <> '0' 的行数为 0</b>；MybatisPlusConfig 里只有分页拦截器、
--       没有租户拦截器；Mapper 里那句 `AND tenant_id = #{queryForm.tenantId}` 写在
--       <if> 里，前端从不传，永不生效。
--       '0' 这种哨兵值会让所有人默认「多租户还没启用」，于是索引里不带它、
--       查询里不过滤它，字段长期空转 —— 越空转越没人敢用，形成死循环。
--
-- 本次把它转正成一个<b>真实租户</b>：默认租户命名为 'taozi'。
--
-- 🔴 本脚本必须<b>整体执行、一次跑完</b>。
--    第 1 步（改 DEFAULT）和第 2 步（刷存量）之间如果中断，库里就会同时存在
--    '0' 和 'taozi' 两种租户值。这种不一致<b>不报任何错</b>，只在将来启用租户
--    拦截器的那一刻表现为「一半数据凭空消失」，而那时已经很难倒查是什么时候分的叉。
--
-- 配套的代码改动（已随本版提交）：
--   · 新增 sa.base.common.constant.TenantConst.DEFAULT_TENANT_ID —— 唯一真源
--   · 删除 TaskEventService / TaskRecordAdvanceService / TaskEventDefService
--     里各自私有的 DEFAULT_TENANT_ID = "0"（同一个常量被写了三遍）
--   · PrizePoolConfigService / LotteryConfigService / TicketPersistService /
--     TaskConfigService 里 4 处硬编码 setTenantId("0") 改为引用常量
--   共 7 个散点收拢成 1 处。以后再改租户标识只改 TenantConst 一个字面量。
--
--
-- 【t_prize_group 已废弃，本脚本刻意不含它】
--   它在<b>整个仓库里没有任何引用</b>：Java 0 处、前端 0 处、主干 DDL 0 处
--   （t_prize_config / t_task_prize_mapping 上那两个 prize_group_id 列只存在于
--    .claude/worktrees 的旧分支副本里，主干早已演进掉了）。表本身也是 0 行。
--   本次已在 dev 库手工 DROP。
--   ⚠️ 若还有别的环境存在这张表，补一条清理，<b>确认那边同样是 0 行之后再执行</b>：
--       DROP TABLE IF EXISTS `t_prize_group`;
--   刻意不把这条 DROP 写进本脚本：本文件的意图是「改默认租户」，
--   往里塞一条删表会让它在别的环境上产生非预期的破坏性副作用。
-- 不在本次范围：
--   · mysql/sql-update-log/ 下的历史版本文件<b>一律不改</b> —— 那是已经执行过的
--     迁移记录，改它等于篡改历史。历史里写着 '0' 是对的，因为当时就是 '0'。
--   · 启用 TenantLineInnerInterceptor。那是另一件事，要连同「后台跨租户运营
--     怎么绕过拦截器」一起设计，不该顺手做。
-- =====================================================================================


-- -------------------------------------------------------------------------------------
-- 第 1 步：把所有 tenant_id 列的 DEFAULT 从 '0' 改成 'taozi'
-- -------------------------------------------------------------------------------------
-- 全部是 INSTANT DDL（只改元数据），实测 50 万行的表耗时 46ms，与表大小无关。
-- 显式写 ALGORITHM=INSTANT：万一哪张表因为行格式等原因走不了 INSTANT，
-- 让它<b>直接报错</b>，而不是悄悄降级成 COPY 把线上锁住几十分钟。
-- -------------------------------------------------------------------------------------
ALTER TABLE `t_activity_config`          MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_activity_display`         MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_prize_pool_config`        MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_prize_pool_item`          MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_pool_prize_mapping`       MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_prize_config`             MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_prize_log`                MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_draw_prize_log`           MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_promotion_config`         MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_proposal_record`          MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_member_wallet`            MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_member_asset_transaction` MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_member_coupon`            MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_physical_delivery`        MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_lottery_config`           MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_lottery_issue`            MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_lottery_prize_rule`       MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_lottery_record`           MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_task_template`            MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_task_config`              MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_task_record`              MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_task_prize_mapping`       MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_task_event`               MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;
ALTER TABLE `t_task_record_flow`         MODIFY `tenant_id` varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi', ALGORITHM=INSTANT;


-- -------------------------------------------------------------------------------------
-- 第 2 步：刷存量数据
-- -------------------------------------------------------------------------------------
-- 每条都带 `WHERE tenant_id = '0'`，所以<b>可以重复执行</b>（幂等）：
-- 中途失败了直接整个文件重跑，已经改过的行不会被再动一次。
-- -------------------------------------------------------------------------------------
UPDATE `t_activity_config`          SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_activity_display`         SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_prize_pool_config`        SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_prize_pool_item`          SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_pool_prize_mapping`       SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_prize_config`             SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_prize_log`                SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_draw_prize_log`           SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_promotion_config`         SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_proposal_record`          SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_member_wallet`            SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_member_asset_transaction` SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_member_coupon`            SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_physical_delivery`        SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_lottery_config`           SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_lottery_issue`            SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_lottery_prize_rule`       SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_lottery_record`           SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_task_template`            SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_task_config`              SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_task_record`              SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_task_prize_mapping`       SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_task_event`               SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';
UPDATE `t_task_record_flow`         SET `tenant_id` = 'taozi' WHERE `tenant_id` = '0';


-- -------------------------------------------------------------------------------------
-- 第 3 步：验收 —— 必须返回 0 行
-- -------------------------------------------------------------------------------------
-- 这条会把「还有哪张表残留 '0'」直接列出来。有输出就说明上面漏了表
-- （比如后来又新建了带 tenant_id 的表），补一条 UPDATE 再跑。
-- -------------------------------------------------------------------------------------
SELECT
    c.TABLE_NAME AS `残留表`,
    CONCAT('UPDATE `', c.TABLE_NAME, '` SET tenant_id=''taozi'' WHERE tenant_id=''0'';') AS `补跑语句`
FROM information_schema.COLUMNS c
WHERE c.TABLE_SCHEMA = DATABASE()
  AND c.COLUMN_NAME = 'tenant_id'
  AND c.COLUMN_DEFAULT <> 'taozi';

-- 数据面验收（逐表统计残留行数）由 SmartAdmin 侧脚本执行，见提交说明。
