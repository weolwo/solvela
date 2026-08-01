-- =====================================================================================
-- v3.46.0  任务模块权限串修正 + 补三个缺失的功能点
-- 撰写：2026-08-01
--
-- 背景：与活动域（v3.42）、奖品域是<b>同一处复制粘贴错误</b>的第三次出现。
--   四个生成器产出的任务 Controller 上写的是 ":query" / ":addProposal" / ":update" / ":delete"
--   —— <b>全都缺模块前缀</b>，而库里 menu 336~354 的串一直是对的
--   （taskConfig:query / taskRecord:query / taskTemplate:query / taskPrizeMapping:query）。
--   两边永远对不上 = <b>任何受限角色即便被正确授权也依然会被拒</b>。
--   长期没人发现是因为 admin 的 administratorFlag=true，超管绕过全部权限校验。
--
--   本次已把四个 Controller 的串补齐前缀（Java 侧改动见同一提交），本脚本只补数据侧缺的三行。
--
-- ⚠️ 执行前已核对：库里最大 menu_id = 445（v3.42 占用 441~445），446 起安全。
--    换环境执行前先跑：SELECT MAX(menu_id) FROM t_menu;
--
-- 可重复执行：先按 menu_id 删再插。
-- =====================================================================================

DELETE FROM `t_role_menu` WHERE `menu_id` IN (446, 447, 448);
DELETE FROM `t_menu`      WHERE `menu_id` IN (446, 447, 448);

INSERT INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`,
                      `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`,
                      `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`,
                      `create_user_id`)
VALUES
    -- ① 向导提交：挂在 435「任务配置向导」下。
    --    该页面此前<b>一个功能点都没有</b> —— 受限角色能看见页面、点保存必被拒，且界面上没有任何线索。
    --    刻意不复用 taskConfig:add：向导提交是主子表一次性事务（config + prizeMapping），
    --    与列表页的「新增」不是同一个动作，权限也该能分开授。
    (446, '向导提交', 3, 435, NULL, NULL, NULL, 1,
     'taskConfig:wizard:submit', 'taskConfig:wizard:submit', NULL, 435, 0, NULL, 0, 1, 0, 0, 1),

    -- ② 模板设计器保存：挂在 436「任务模板设计器」下，同样此前没有任何功能点。
    --    save 是<b>按 templateCode upsert</b>（新建与覆盖同一个入口），
    --    映射到 add 或 update 都会让另一半权限的人用不了设计器，故单列一个功能点。
    --    generateCode（生成模板编码）也用这个串 —— 它是保存流程的一步，不该单独授权。
    (447, '设计器保存', 3, 436, NULL, NULL, NULL, 1,
     'taskTemplate:save', 'taskTemplate:save', NULL, 436, 0, NULL, 0, 1, 0, 0, 1),

    -- ③ 事件上报：挂在 345「任务记录」下 —— 事件正是任务记录的来源。
    --    ⚠️ 这是<b>给上游业务系统调的接口</b>，不是给运营点的按钮。
    --    真接入时应给上游一个专用角色只授这一个功能点，而不是把它塞进运营角色。
    --    /taskEvent/flow/{recordId}（事件流水查询，客诉自证入口）复用已有的 taskRecord:query(346)，无需新增。
    (448, '事件上报', 3, 345, NULL, NULL, NULL, 1,
     'taskEvent:report', 'taskEvent:report', NULL, 345, 0, NULL, 0, 1, 0, 0, 1);

-- 给超级管理员角色授权，否则功能点建了也授不出去。只对已存在的角色补授权，不新建角色。
INSERT INTO `t_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, m.menu_id
FROM `t_role` r
         CROSS JOIN (SELECT 446 AS menu_id UNION ALL SELECT 447 UNION ALL SELECT 448) m
WHERE r.role_code = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM `t_role_menu` rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);


-- =====================================================================================
-- 🔴 改完权限必须登出重登，否则不生效（基座缺陷，非本次引入）
--
--   @SaCheckPermission 走 LoginManager.getUserPermission()，那是 @Cacheable(USER_PERMISSION)；
--   而清缓存的 clearLoginEmployeeCache 只在「登出」与「员工信息变更」时调用，
--   RoleMenuService.updateRoleMenu 不清缓存。
--   运营侧表现：管理员加了权限，对方刷新页面菜单都出来了，一点功能却还是「没有权限」，
--   界面上看不出任何线索。详见交接文档 §4.5。
-- =====================================================================================


-- =====================================================================================
-- 自查
-- =====================================================================================

-- 1. 新增的三个功能点是否就位
SELECT menu_id, menu_name, menu_type, parent_id, api_perms, web_perms
FROM t_menu WHERE menu_id BETWEEN 446 AND 448;

-- 2. 任务模块全部功能点一览（应与四个 Controller 上的串一一对得上）
SELECT m.menu_id, p.menu_name AS 所属页面, m.menu_name AS 功能点, m.api_perms
FROM t_menu m LEFT JOIN t_menu p ON p.menu_id = m.parent_id
WHERE m.menu_type = 3 AND m.api_perms LIKE 'task%'
ORDER BY m.api_perms;

-- 3. ⚠️ 已知的存量重复：taskPrizeMapping 的四个功能点在库里有两份
--    （menu 336~339 挂在 335「奖励配置映射」，menu 379~382 挂在 378「奖励包」），
--    两个页面指向同一张表。属历史遗留，本次不动 —— 权限判定按串匹配，重复不影响功能，
--    但清理时要两处一起清，别只删一份留下悬空菜单。
SELECT menu_id, parent_id, menu_name, api_perms FROM t_menu
WHERE api_perms LIKE 'taskPrizeMapping%' ORDER BY api_perms, menu_id;
