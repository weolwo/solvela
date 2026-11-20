-- =============================================================================
-- 生产升级：安全事件改名 incident → event，第 ② 步（【新版后台与管理端都上线之后】执行）
-- =============================================================================
-- 全流程见 docs/架构/安全事件改名-实施方案.md。前置：「①发版前」脚本已执行，新版后端与管理端已全部上线。
--
-- 本步做什么：
--   1. 安全事件落库任务的 handler_name：securityIncidentDrain → securityEventDrain，并清掉 handler_missing_flag
--   2. 「安全事件」菜单：路径 /security/incident → /security/event，组件换成 security-event-list.vue
--   3. 两行的描述文字里旧的键名、表名一并换掉
--
-- 🔴 为什么这两件事不能放进「①发版前」：
--   · 调度器按 handler_name 找处理器，找不到就标 handler_missing_flag = 1，而扫描只挑 = 0 的行 ——
--     【标上之后处理器后来出现了也不会自己恢复】。发版前就改名，旧版后台不认新名字，落库任务当场永久停掉。
--     新版后台两个名字都认（LegacySecurityIncidentDrainJob），所以只能在它上线之后改。
--   · 菜单的组件路径要指向一个前端里真实存在的文件。旧版管理端没有 security-event-list.vue；
--     新版管理端两个文件都有（旧文件名是一个转发壳），所以同样只能在它上线之后改。
--
-- 可重复执行：WHERE 里带着旧值，改过一次就匹配不到了。
--
-- 执行方式（在项目根目录）：
--   docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 solvela' \
--     < "数据库SQL脚本/mysql/生产升级-20261120-安全事件改名-②发版后.sql"
-- =============================================================================

SET NAMES utf8mb4;

-- 1. 落库任务改名。handler_missing_flag 一并清零：万一发版过程中被误标过，这里把它救回来
UPDATE `t_solvela_job`
   SET `handler_name` = 'securityEventDrain',
       `handler_missing_flag` = 0,
       `remark` = REPLACE(REPLACE(`remark`, 'sec:incident', 'sec:event'), 't_security_incident', 't_security_event')
 WHERE `handler_name` = 'securityIncidentDrain';

-- 2. 菜单改路径（按组件路径匹配，不按 menu_id：各环境的 id 未必一致）
UPDATE `t_menu`
   SET `path` = '/security/event',
       `component` = '/business/security/security-event-list.vue'
 WHERE `component` = '/business/security/security-incident-list.vue';

-- 执行结果自检：应当各看到一行新值，且 handler_missing_flag = 0
SELECT `job_id`, `job_name`, `handler_name`, `handler_missing_flag` FROM `t_solvela_job` WHERE `handler_name` LIKE 'security%Drain';
SELECT `menu_id`, `menu_name`, `path`, `component` FROM `t_menu` WHERE `component` LIKE '/business/security/security-%-list.vue' AND `path` LIKE '/security/%';
