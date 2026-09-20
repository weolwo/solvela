-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- ============================================================================
-- 会员等级 · 等级权益后台菜单  2026-09-21
--
-- 前置：会员等级-Grade改名与5表模型.sql（建 t_grade_privilege）
--      会员等级-Grade改名之菜单与权限.sql（建 menu 616~621）
--
-- 【它补的是什么】
--   t_grade_privilege 这张表一直是拿 SQL 灌进去的 —— C 端等级页老老实实
--   把它渲染出来了，可管理端<b>没有任何维护入口</b>，运营想改一句权益文案
--   只能找人改库。而权益文案恰恰是最常改的那类内容：它是用户
--   「为什么要保级」的唯一答案，措辞本身就是运营动作。
--
-- 🔴 新菜单【不带新权限点】，沿用 memberGrade:query / memberGrade:config。
--   理由：「改权益文案」和「改等级门槛」是同一类运营动作，都不涉及改某一个人的账
--   （那是 memberGrade:adjust，本来就是单独的）。多开一个权限点的唯一效果，
--   是让已经授过 memberGrade:config 的角色<b>看得见菜单却点不动按钮</b> ——
--   而那种「有入口没权限」的状态没人会主动来报，只会被当成系统坏了。
--
-- 【执行方式】
--   mysql> SOURCE 数据库SQL脚本/mysql/会员等级-权益后台菜单.sql;
--   按 path 判存，可重复执行。
--
-- ⚠️ 这里<b>不碰 t_role_menu</b>。没有新权限点要授，已有角色的 memberGrade:config
--   授权自动覆盖新页面 —— 这也正是不新开权限点换来的好处。
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. 等级权益菜单
--
-- 排在「等级配置」后面：运营的动线是先定档位再写这一档有什么，
-- 把它夹在配置和留痕之间，比缀在最后更贴近他实际的操作顺序。
-- ---------------------------------------------------------------------------
INSERT INTO `t_menu`
    (`menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`,
     `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`,
     `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`,
     `deleted_flag`, `create_user_id`, `create_time`)
SELECT * FROM (
    SELECT '等级权益' AS menu_name, 2 AS menu_type, 462 AS parent_id, 7 AS sort,
           '/member/member-grade/privilege-list' AS path,
           '/business/member/member-grade/member-grade-privilege-list.vue' AS component,
           1 AS perms_type, NULL AS api_perms, NULL AS web_perms,
           'GiftOutlined' AS icon, NULL AS context_menu_id,
           0 AS frame_flag, NULL AS frame_url, 0 AS cache_flag,
           1 AS visible_flag, 0 AS disabled_flag, 0 AS deleted_flag,
           1 AS create_user_id, NOW() AS create_time
) AS m
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT `path` FROM `t_menu` WHERE `deleted_flag` = 0) AS existed
     WHERE existed.`path` = m.path
);


-- ---------------------------------------------------------------------------
-- 2. 把「等级变更留痕」往后挪一位，给上面那条腾出 sort=7
--
-- ⚠️ 用 UPDATE 而不是删掉重建。t_role_menu 是按 menu_id 关联的，
--    重建会把已有角色的授权<b>静默丢掉</b> —— 那是改名那一版踩过的坑，
--    见 会员等级-Grade改名之菜单与权限.sql。
-- ---------------------------------------------------------------------------
UPDATE `t_menu` SET `sort` = 8
 WHERE `path` = '/member/member-grade/grade-log-list' AND `deleted_flag` = 0;


-- ============================================================================
-- 自查
-- ============================================================================
--
--   -- 会员中心下的菜单顺序，应当是 …成长值(5) 配置(6) 权益(7) 留痕(8)
--   SELECT menu_id, menu_name, sort, path FROM t_menu
--    WHERE parent_id = 462 AND deleted_flag = 0 ORDER BY sort;
--
--   -- 权益挂错档的存量数据（C 端永远不会展示它们，且不报错）：
--   SELECT p.id, p.grade_code, p.privilege_code, p.privilege_name
--     FROM t_grade_privilege p
--    WHERE NOT EXISTS (SELECT 1 FROM t_member_grade g WHERE g.grade_code = p.grade_code);
--   -- 新增时服务端会拦，但等级被删改之后存量仍可能变成这样，列表页上会标红提示
-- ============================================================================
