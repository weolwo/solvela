-- =============================================================================
-- 生产升级：安全事件改名 incident → event，第 ③ 步「收口」（【以后单独执行】，与删 TEMP 代码同一次发版）
-- =============================================================================
-- 全流程见 docs/架构/安全事件改名-实施方案.md。
--
-- 🔴 执行前必须同时满足（任何一条不满足都不要跑）：
--   1. 所有进程都已是改名后的版本，没有任何旧版网关 / 后台 / 会员服务在跑；
--   2. 生产 Redis 里 LLEN sec:incident 与 LLEN sec:incident:processing 都为 0；
--   3. 「②发版后」脚本已执行（本脚本开头会核对，不满足就报错退出）；
--   4. 准备随本脚本一起发布的代码，已经删掉了所有 TEMP(安全事件改名-收口) 的兼容代码
--      —— 尤其是 SecurityListWriter 里对 last_incident_id 的双写：列删了而代码还在写，名单写入会全部失败。
--
-- 本步做什么：
--   1. 再回填一次 last_event_id（发版间隙里旧版后台只写了旧列）
--   2. 删 t_security_list.last_incident_id
--   3. 删兼容视图 t_security_incident
--
-- 可重复执行：每一步都先查 information_schema。
-- ⚠️ 删列不可逆。跑之前先备份。
-- =============================================================================

SET NAMES utf8mb4;

-- 0. 前置核对：②没跑过就停下（SIGNAL 会让 mysql 客户端以非 0 退出，后面的语句不再执行）
DROP PROCEDURE IF EXISTS `_sec_event_rename_guard`;
DELIMITER //
CREATE PROCEDURE `_sec_event_rename_guard`()
BEGIN
    IF EXISTS (SELECT 1 FROM `t_solvela_job` WHERE `handler_name` = 'securityIncidentDrain') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '「②发版后」脚本还没执行：t_solvela_job 里仍有 securityIncidentDrain';
    END IF;
    IF EXISTS (SELECT 1 FROM `t_menu` WHERE `component` = '/business/security/security-incident-list.vue') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '「②发版后」脚本还没执行：t_menu 里仍指向 security-incident-list.vue';
    END IF;
END //
DELIMITER ;
CALL `_sec_event_rename_guard`();
DROP PROCEDURE IF EXISTS `_sec_event_rename_guard`;

-- 1. 回填
SET @has_old := (SELECT COUNT(*) FROM information_schema.columns
                  WHERE table_schema = DATABASE() AND table_name = 't_security_list' AND column_name = 'last_incident_id');
SET @dml := IF(@has_old = 1,
    'UPDATE `t_security_list` SET `last_event_id` = `last_incident_id` WHERE `last_event_id` IS NULL AND `last_incident_id` IS NOT NULL',
    'DO 0');
PREPARE stmt FROM @dml;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 删旧列
SET @ddl := IF(@has_old = 1, 'ALTER TABLE `t_security_list` DROP COLUMN `last_incident_id`', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 删兼容视图（只删视图；万一同名的是一张真表，不动它）
SET @ddl := (SELECT IF(COUNT(*) = 1, 'DROP VIEW `t_security_incident`', 'DO 0')
  FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 't_security_incident' AND table_type = 'VIEW');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 执行结果自检：应当没有任何一行
SELECT table_name, table_type FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name = 't_security_incident'
UNION ALL
SELECT CONCAT(table_name, '.', column_name), 'COLUMN' FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 't_security_list' AND column_name = 'last_incident_id';
