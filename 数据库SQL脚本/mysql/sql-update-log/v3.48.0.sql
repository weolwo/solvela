-- =====================================================================================
-- v3.48.0  全工程权限串修正收尾：补 5 个缺失的功能点
-- 撰写：2026-08-01
--
-- 背景：v3.46.0 修了任务模块的 4 个 Controller，本轮修完剩下的 17 个
--   （draw / lottery / ledger / risk / prize 各域，共 96 处 @SaCheckPermission 缺模块前缀）。
--   至此全工程再无「以冒号开头」的裸权限串。
--
-- 对照结果：17 个 Controller 一共需要 64 个权限串，库里已有 59 个 ——
--   生成器产出的 CRUD 功能点是齐的，**缺的 5 个全都是后来手写的业务动作**。
--   这个分布本身很说明问题：功能点是跟着生成器走的，凡是人手加的接口都没人记得补功能点。
--
-- 🔴 其中两条最要命的是**两套审批**：
--   prizeLog:approve（运营视角：这个奖该不该发给这个人）
--   proposalRecord:approve（财务视角：这笔钱该不该出）
--   在此之前，**审批动作在库里连功能点都不存在** —— 也就是说除了超管，
--   没有任何角色能被授予审批权限，审批工作台对受限角色是彻底不可用的。
--
-- ⚠️ 执行前已核对：库里最大 menu_id = 453（v3.47 占用 449~453），454 起安全。
--    换环境执行前先跑：SELECT MAX(menu_id) FROM t_menu;
--
-- 可重复执行：先按 menu_id 删再插。
-- =====================================================================================

DELETE FROM `t_role_menu` WHERE `menu_id` IN (454, 455, 456, 457, 458);
DELETE FROM `t_menu`      WHERE `menu_id` IN (454, 455, 456, 457, 458);

INSERT INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`,
                      `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`,
                      `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`,
                      `create_user_id`)
VALUES
    -- ① 发奖审批（prize 域）：挂在 330「发奖记录」下。
    --    approve 与 reject 共用一个功能点：能批就能驳，拆开授权没有实际场景，
    --    反而会配出「只能通过不能驳回」这种把人逼到只能点通过的角色。
    (454, '发奖审批', 3, 330, NULL, NULL, NULL, 1,
     'prizeLog:approve', 'prizeLog:approve', NULL, 330, 0, NULL, 0, 1, 0, 0, 1),

    -- ② 提案审批（提案域，财务视角）：挂在 383「提案表」下。
    --    ⚠️ 与 ① 是**两套并存的审批**，语义不同、可叠加：
    --      prize 域看 t_prize_config.approve_mode，提案域看 t_promotion_config.review_level。
    --    两层都配了审批就需要运营和财务各批一次，因此权限也该分开授。
    (455, '提案审批', 3, 383, NULL, NULL, NULL, 1,
     'proposalRecord:approve', 'proposalRecord:approve', NULL, 383, 0, NULL, 0, 1, 0, 0, 1),

    -- ③ 执行抽奖（运行态入口）：挂在 388「抽奖记录」下。
    --    ⚠️ 这是给 C 端/上游调的接口，不是运营按钮。真接入时应给调用方一个专用角色只授这一个，
    --    而不是塞进运营角色 —— 否则运营账号可以直接replay抽奖。
    (456, '执行抽奖', 3, 388, NULL, NULL, NULL, 1,
     'drawPrizeLog:execute', 'drawPrizeLog:execute', NULL, 388, 0, NULL, 0, 1, 0, 0, 1),

    -- ④ 抽奖工作台保存：挂在 437「抽奖配置工作台」下 —— 那个页面此前**一个功能点都没有**，
    --    与任务模块的 435/436 一模一样的问题（受限角色能看见页面、点保存必被拒、界面无线索）。
    --    刻意不复用 prizePoolConfig:update：工作台保存是「物资 + 奖池 + 坑位映射」的
    --    主子表一次性事务，与列表页改一行不是同一个动作。
    --    /generateCode（生成奖池编码）也用这个串，它是保存流程的一步，不该单独授权。
    --    ⚠️ 工作台的读取走 prizePoolConfig:query（已存在，挂在 395「奖池配置」下）——
    --       给受限角色开工作台时，这两个要一起授。
    (457, '工作台保存', 3, 437, NULL, NULL, NULL, 1,
     'prizePoolConfig:workbench:save', 'prizePoolConfig:workbench:save', NULL, 437, 0, NULL, 0, 1, 0, 0, 1),

    -- ⑤ 彩票领号/查号/验真：挂在 430「用户购彩记录」下。
    --    ⚠️ 三个方法（/obtain 领号、/myTickets 我的号码、/verify 验真）在代码里共用 :query。
    --       其中 /obtain 语义上是**写**（发号），归在 query 下并不合适 ——
    --       但本轮只做「补前缀」这一件事，不顺手改动作语义（那会改变已定案的彩票模块行为）。
    --       已记入交接文档待办，需要时单独拆成 lotteryTicket:obtain。
    (458, '领号与验真', 3, 430, NULL, NULL, NULL, 1,
     'lotteryTicket:query', 'lotteryTicket:query', NULL, 430, 0, NULL, 0, 1, 0, 0, 1);

INSERT INTO `t_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, m.menu_id
FROM `t_role` r
         CROSS JOIN (SELECT 454 AS menu_id UNION ALL SELECT 455 UNION ALL SELECT 456
                     UNION ALL SELECT 457 UNION ALL SELECT 458) m
WHERE r.role_code = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM `t_role_menu` rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);


-- =====================================================================================
-- 🔴 改完权限必须登出重登（基座缺陷，非本次引入）：
--   @SaCheckPermission 走 @Cacheable(USER_PERMISSION)，而 RoleMenuService.updateRoleMenu 不清缓存。
--   表现是「菜单出来了但一点功能还是没权限」，界面上看不出任何线索。详见交接文档 §4.5。
-- =====================================================================================


-- =====================================================================================
-- 自查
-- =====================================================================================

-- 1. 新增的五个功能点
SELECT m.menu_id, p.menu_name AS 宿主页面, m.menu_name AS 功能点, m.api_perms
FROM t_menu m LEFT JOIN t_menu p ON p.menu_id = m.parent_id
WHERE m.menu_id BETWEEN 454 AND 458;

-- 2. 营销中台全部功能点（应与各 Controller 上的串一一对得上，共 64 + 任务域的）
SELECT m.api_perms, COUNT(*) AS 行数
FROM t_menu m
WHERE m.menu_type = 3 AND m.deleted_flag = 0
  AND (m.api_perms LIKE 'task%' OR m.api_perms LIKE 'draw%' OR m.api_perms LIKE 'lottery%'
       OR m.api_perms LIKE 'member%' OR m.api_perms LIKE 'physical%' OR m.api_perms LIKE 'pool%'
       OR m.api_perms LIKE 'prize%' OR m.api_perms LIKE 'promotion%' OR m.api_perms LIKE 'proposal%')
GROUP BY m.api_perms
ORDER BY m.api_perms;
