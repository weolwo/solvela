-- =====================================================================================
-- v3.62.0  任务奖励页改为只读：回收 taskPrizeMapping 的 增/改/删 三个功能点
-- 撰写：2026-08-15
--
-- 背景：t_task_prize_mapping 没有独立生命周期，它由任务向导整体托管 ——
--   TaskConfigService.wizardUpdate 每次保存都是「按 task_config_id 全删 + insertBatch 重插」。
--   所以在管理端单条新增/修改，只要运营下次在向导里点一次保存就被无声抹掉；
--   一个改了不生效、还不报错的入口，比没有入口更糟。
--
--   删除确实能让 TaskPrizeDispatcher 发不出奖，但那是<b>物理删除、不可逆</b>的停发方式
--   （这张表没有 deleted_flag，删了只能人肉去向导重配），
--   而且删掉阶梯中间的档位会把跑着的活动改坏：已拿过第 1 档的用户下次事件直接跨到第 3 档，
--   t_task_record.progress_data.dispatchedStages 与实际发放对不上。
--   停发的正确做法是把任务下线（taskConfig:update）—— 可逆、有状态可查、运行态不再订阅事件。
--
--   Java 侧对应的 add / update / batchDelete / delete 四个接口已在同一提交里删除，
--   本脚本只回收数据侧的权限点，避免菜单里留着点了必然 404 的按钮。
--
-- ⚠️ taskPrizeMapping 的功能点在库里<b>有两份</b>（336~339 与 379~382，v3.46.0 脚注已记录过），
--    所以这里按 api_perms 删而不是按 menu_id 删，两份一起回收，避免只清掉一份。
--    保留的只有 query（336 / 379）。
--
-- 可重复执行。
-- =====================================================================================

-- 先解绑角色授权，再删功能点：反过来会留下指向不存在菜单的 t_role_menu 脏行
DELETE FROM `t_role_menu`
WHERE `menu_id` IN (SELECT `menu_id`
                    FROM `t_menu`
                    WHERE `api_perms` IN ('taskPrizeMapping:add',
                                          'taskPrizeMapping:update',
                                          'taskPrizeMapping:delete'));

DELETE FROM `t_menu`
WHERE `api_perms` IN ('taskPrizeMapping:add',
                      'taskPrizeMapping:update',
                      'taskPrizeMapping:delete');

-- 自查：
-- 应只剩 query，且（因存量重复）是两行
-- SELECT menu_id, parent_id, menu_name, api_perms FROM t_menu WHERE api_perms LIKE 'taskPrizeMapping%';
--
-- 应为 0 行
-- SELECT rm.* FROM t_role_menu rm LEFT JOIN t_menu m ON m.menu_id = rm.menu_id WHERE m.menu_id IS NULL;

-- =====================================================================================
-- 🔴 改完权限必须登出重登，否则不生效（基座缺陷，非本次引入）
-- =====================================================================================
