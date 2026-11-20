-- =============================================================================
-- 生产升级：安全事件改名 incident → event，第 ① 步（【发版前】执行）
-- =============================================================================
-- 全流程（三步，顺序不能乱）见 docs/架构/安全事件改名-实施方案.md：
--   ① 本脚本（发版前）→ ② 发新版后端与管理端 → ③ 「②发版后」脚本 → （以后单独）「③收口」脚本 + 删 TEMP 代码
--
-- 本步做什么：
--   1. t_security_incident  → t_security_event，列 incident_type → event_type，4 个索引 idx_sec_inc_* → idx_sec_evt_*
--   2. 建同名兼容视图 t_security_incident，把新表原样映射回旧表名、旧列名
--      —— 🔴 发版间隙里旧版后台还在跑：它的落库任务往 t_security_incident 里 INSERT、安全事件页从里面 SELECT。
--         没有这个视图，从本脚本执行完到新版后台上线的这段时间，安全事件全部落库失败（留在 Redis 处理中，不丢，但会堆积报错）。
--   3. t_security_list 加列 last_event_id，并把 last_incident_id 回填过去（新代码两列都写，旧代码只写旧列）
--   4. 分区维护任务的参数里如果手工写过 t_security_incident，换成新表名（对一个视图做分区维护会报错）
--
-- 本步刻意【不做】的事（它们放在「②发版后」脚本里，理由见那个文件头）：
--   · 不改 t_solvela_job 里落库任务的 handler_name
--   · 不改 t_menu 里「安全事件」页的路径
--
-- 可重复执行：每一步都先查 information_schema，已经做过就跳过。
--
-- 执行方式（在项目根目录）：
--   docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 solvela' \
--     < "数据库SQL脚本/mysql/生产升级-20261120-安全事件改名-①发版前.sql"
--
-- ⚠️ 跑之前先备份：docker compose exec -T mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --no-tablespaces solvela' > 备份.sql
-- ⚠️ RENAME TABLE 要拿表的元数据锁：有长事务在读这张表时它会等，等待期间后续读写也排队。挑低峰执行。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. 表改名：只有 t_security_incident 还是一张【真表】、且 t_security_event 不存在时才改
--    （重复执行时 t_security_incident 已经是视图，跳过）
-- -----------------------------------------------------------------------------
SET @ddl := (SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.tables
             WHERE table_schema = DATABASE() AND table_name = 't_security_incident' AND table_type = 'BASE TABLE')
    AND NOT EXISTS (SELECT 1 FROM information_schema.tables
             WHERE table_schema = DATABASE() AND table_name = 't_security_event'),
    'RENAME TABLE `t_security_incident` TO `t_security_event`',
    'DO 0'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 列改名（RENAME COLUMN 保留原有类型、约束与注释）
SET @ddl := (SELECT IF(COUNT(*) = 1,
    'ALTER TABLE `t_security_event` RENAME COLUMN `incident_type` TO `event_type`',
    'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_security_event' AND column_name = 'incident_type');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4 个索引改名（RENAME INDEX 只改元数据，不重建）
SET @ddl := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_security_event` RENAME INDEX `idx_sec_inc_ip` TO `idx_sec_evt_ip`',
    'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 't_security_event' AND index_name = 'idx_sec_inc_ip');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_security_event` RENAME INDEX `idx_sec_inc_device` TO `idx_sec_evt_device`',
    'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 't_security_event' AND index_name = 'idx_sec_inc_device');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_security_event` RENAME INDEX `idx_sec_inc_member` TO `idx_sec_evt_member`',
    'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 't_security_event' AND index_name = 'idx_sec_inc_member');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_security_event` RENAME INDEX `idx_sec_inc_type` TO `idx_sec_evt_type`',
    'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 't_security_event' AND index_name = 'idx_sec_inc_type');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 2. 兼容视图：旧表名、旧列名 → 新表
--    · 每一列都是对基表列的直接引用，所以这个视图可插入（旧版落库任务的 INSERT 能穿过去，自增 id 照常返回）
--    · SQL SECURITY INVOKER：按调用者自己的权限访问基表，不依赖建视图那个账号以后还在不在
--    · 只在 t_security_incident 这个名字没被真表占着时才建（表改名失败的话这里不会把它盖掉）
--    TEMP(安全事件改名-收口)：由「③收口」脚本删除
-- -----------------------------------------------------------------------------
SET @ddl := (SELECT IF(
    NOT EXISTS (SELECT 1 FROM information_schema.tables
                 WHERE table_schema = DATABASE() AND table_name = 't_security_incident' AND table_type = 'BASE TABLE')
    AND EXISTS (SELECT 1 FROM information_schema.tables
                 WHERE table_schema = DATABASE() AND table_name = 't_security_event' AND table_type = 'BASE TABLE'),
    'CREATE OR REPLACE ALGORITHM = MERGE SQL SECURITY INVOKER VIEW `t_security_incident` AS
       SELECT `id`, `event_type` AS `incident_type`, `rule_code`, `action`, `suppress_reason`, `penalty_level`,
              `client_ip`, `device_id`, `member_id`, `identity_hash`, `http_method`, `request_path`, `matched_field`,
              `matched_param`, `matched_sample`, `user_agent`, `penalty_until`, `trace_id`, `create_time`
         FROM `t_security_event`',
    'DO 0'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 3. t_security_list：加 last_event_id，回填
-- -----------------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `t_security_list` ADD COLUMN `last_event_id` bigint DEFAULT NULL COMMENT ''最近一次证据，指向 t_security_event.id'' AFTER `last_hit_time`',
    'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_security_list' AND column_name = 'last_event_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 旧列的注释改成说明它是过渡列（类型与默认值不变）
SET @ddl := (SELECT IF(COUNT(*) = 1,
    'ALTER TABLE `t_security_list` MODIFY COLUMN `last_incident_id` bigint DEFAULT NULL COMMENT ''改名过渡列，与 last_event_id 同值；收口脚本删除'' AFTER `last_event_id`',
    'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_security_list' AND column_name = 'last_incident_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `t_security_list`
   SET `last_event_id` = `last_incident_id`
 WHERE `last_event_id` IS NULL AND `last_incident_id` IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 4. 分区维护任务：参数里手工写过旧表名的，换成新表名（param 为空时用代码里的默认清单，不受影响）
-- -----------------------------------------------------------------------------
UPDATE `t_solvela_job`
   SET `param` = REPLACE(`param`, 't_security_incident', 't_security_event')
 WHERE `handler_name` = 'partitionMaintain' AND `param` LIKE '%t_security_incident%';

-- -----------------------------------------------------------------------------
-- 执行结果自检：应当看到 t_security_event 是 BASE TABLE、t_security_incident 是 VIEW
-- -----------------------------------------------------------------------------
SELECT table_name, table_type
  FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name IN ('t_security_event', 't_security_incident')
 ORDER BY table_name;
