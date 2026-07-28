-- 彩票配置工作台菜单（2026-07-27，彩票中台 P2）
--
-- 挂在 394「彩票管理」下，与抽奖工作台（menu_id=437，挂在 393 抽奖管理下）同构。
-- 沿用抽奖工作台那条记录的字段口径：menu_type=2(菜单)、perms_type=1、frame/cache 关闭、可见。
--
-- ⚠️ path 与 component 必须与前端 router 的约定一致：
--    SmartAdmin 是「菜单驱动路由」，component 填 src/views 下的相对路径，
--    写错不会报错，只会在点开时白屏 —— 号码池菜单当初就是这么变成白屏入口的。
--
-- 可重复执行：先按 menu_id 删再插，避免重复跑出两条。

DELETE FROM `t_role_menu` WHERE `menu_id` IN (438, 439, 440);
DELETE FROM `t_menu` WHERE `menu_id` IN (438, 439, 440);

INSERT INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`,
                      `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`,
                      `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`,
                      `create_user_id`)
VALUES
    (438, '彩票配置工作台', 2, 394, 0, '/business/lottery/lottery-workbench',
     '/business/lottery/lottery-workbench/LotteryWorkbench.vue', 1, NULL, NULL, 'AppstoreAddOutlined', NULL,
     0, NULL, 0, 1, 0, 0, 1),
    -- 工作台的两个功能点：回显走 query，聚合保存走 update。
    -- 与 Controller 上的 @SaCheckPermission(":query") / (":update") 对应
    (439, '查询', 3, 438, NULL, NULL, NULL, 1, 'lotteryConfig:query', 'lotteryConfig:query', NULL, 438,
     0, NULL, 0, 1, 0, 0, 1),
    (440, '保存配置', 3, 438, NULL, NULL, NULL, 1, 'lotteryConfig:update', 'lotteryConfig:update', NULL, 438,
     0, NULL, 0, 1, 0, 0, 1);

-- 自查：应返回 3 行；且 394 下的子菜单里能看到「彩票配置工作台」
-- SELECT menu_id, menu_name, parent_id, path, component FROM t_menu WHERE menu_id IN (438,439,440);
-- SELECT menu_id, menu_name, sort FROM t_menu WHERE parent_id = 394 ORDER BY sort, menu_id;
