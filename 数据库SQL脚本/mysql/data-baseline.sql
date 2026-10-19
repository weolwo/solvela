-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- =====================================================================================
-- solvela 系统种子数据
--
-- 🔴 <b>新环境部署第 2 步</b>：先执行 schema-baseline.sql（建结构），再执行本文件（灌数据）。
--    两个文件跑完，系统就能登录并正常使用。
--
-- 【本文件<b>只有配置数据</b>，没有任何业务数据】
--   会员、活动、任务记录、积分流水、各类日志 —— 一律不在这里。
--   那些要么是真实使用产生的，要么跑 *造数*.sql 生成，进基线只会让新环境
--   一开张就带着别人的测试数据。
--
-- 【为什么需要这个文件】
--   schema-baseline.sql 只有结构。而系统能不能跑起来还依赖一批配置数据，
--   它们原先散落在 sql-update-log 的 17 个版本文件里 —— 光 t_menu 的增量插入
--   就横跨 v3.18 / v3.41 / v3.42 / v3.46 / v3.47 / v3.48 / v3.52 / v3.60 八个文件。
--   靠人按版本号顺序执行才能拼出完整菜单，漏一个就少一块功能入口，而且不报错。
--
-- ⚠️ <b>t_employee 含 Argon2 密码哈希与手机号</b>。上游 smart_admin_v3.sql 本来也带，
--    所以这不是新增的暴露面；但本文件如果要发到公开仓库/交付给外部，
--    先把非必要账号删掉，只留一个 admin。
--
-- 生成方式：数据库SQL脚本/tools/DumpSeedData.java
-- 生成时间：2026-10-17
-- =====================================================================================

SET FOREIGN_KEY_CHECKS = 0;


-- -----------------------------------------------------------------------------------
-- t_menu  菜单树。没有它后台登录进去是空白（319 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_menu`;
INSERT INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES
(26, '菜单管理', 2, 45, 1, '/menu/list', '/system/menu/menu-list.vue', NULL, NULL, NULL, 'CopyOutlined', NULL, 0, NULL, 1, 1, 0, 0, 2, '2021-08-09 15:04:35', 1, '2026-08-15 23:13:48'),
(40, '删除', 3, 26, NULL, NULL, NULL, 1, 'system:menu:batchDelete', 'system:menu:batchDelete', NULL, 26, 0, NULL, 0, 1, 0, 0, 1, '2021-08-12 09:45:56', 1, '2023-10-07 18:15:50'),
(45, '权限管理', 1, 0, 9, '/organization', NULL, NULL, NULL, NULL, 'UserSwitchOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2021-08-12 16:13:27', 1, '2026-08-15 23:13:30'),
(46, '员工管理', 2, 45, 3, '/organization/employee', '/system/employee/index.vue', NULL, NULL, NULL, 'AuditOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2021-08-12 16:21:50', 1, '2024-07-02 20:15:23'),
(50, '系统配置', 1, 0, 8, '/setting', NULL, NULL, NULL, NULL, 'SettingOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2021-08-13 16:41:33', 1, '2026-08-15 23:13:02'),
(76, '角色管理', 2, 45, 4, '/organization/role', '/system/role/index.vue', NULL, NULL, NULL, 'SlidersOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2021-08-26 10:31:00', 1, '2024-07-02 20:15:28'),
(81, '用户操作记录', 2, 213, 6, '/support/operate-log/operate-log-list', '/support/operate-log/operate-log-list.vue', NULL, NULL, NULL, 'VideoCameraOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-20 12:37:24', 44, '2024-08-13 14:34:10'),
(85, '组件演示', 2, 84, NULL, '/demonstration/index', '/support/demonstration/index.vue', NULL, NULL, NULL, 'ClearOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-20 23:16:46', NULL, '2022-05-20 23:16:46'),
(86, '添加部门', 3, 46, 1, NULL, NULL, 1, 'system:department:add', 'system:department:add', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-26 23:33:37', 1, '2023-10-07 18:26:35'),
(87, '修改部门', 3, 46, 2, NULL, NULL, 1, 'system:department:update', 'system:department:update', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-26 23:34:11', 1, '2023-10-07 18:26:44'),
(88, '删除部门', 3, 46, 3, NULL, NULL, 1, 'system:department:delete', 'system:department:delete', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-26 23:34:49', 1, '2023-10-07 18:26:49'),
(91, '添加员工', 3, 46, NULL, NULL, NULL, 1, 'system:employee:add', 'system:employee:add', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:11:38', 1, '2023-10-07 18:27:46'),
(92, '编辑员工', 3, 46, NULL, NULL, NULL, 1, 'system:employee:update', 'system:employee:update', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:12:10', 1, '2023-10-07 18:27:49'),
(93, '禁用启用员工', 3, 46, NULL, NULL, NULL, 1, 'system:employee:disabled', 'system:employee:disabled', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:12:37', 1, '2023-10-07 18:27:53'),
(94, '调整员工部门', 3, 46, NULL, NULL, NULL, 1, 'system:employee:department:update', 'system:employee:department:update', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:12:59', 1, '2023-10-07 18:27:34'),
(95, '重置密码', 3, 46, NULL, NULL, NULL, 1, 'system:employee:password:reset', 'system:employee:password:reset', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:13:30', 1, '2023-10-07 18:27:57'),
(96, '删除员工', 3, 46, NULL, NULL, NULL, 1, 'system:employee:delete', 'system:employee:delete', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:14:08', 1, '2023-10-07 18:28:01'),
(97, '添加角色', 3, 76, NULL, NULL, NULL, 1, 'system:role:add', 'system:role:add', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:34:00', 1, '2023-10-07 18:42:31'),
(98, '删除角色', 3, 76, NULL, NULL, NULL, 1, 'system:role:delete', 'system:role:delete', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:34:19', 1, '2023-10-07 18:42:35'),
(99, '编辑角色', 3, 76, NULL, NULL, NULL, 1, 'system:role:update', 'system:role:update', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:34:55', 1, '2023-10-07 18:42:44'),
(100, '更新数据范围', 3, 76, NULL, NULL, NULL, 1, 'system:role:dataScope:update', 'system:role:dataScope:update', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:37:03', 1, '2023-10-07 18:41:49'),
(101, '批量移除员工', 3, 76, NULL, NULL, NULL, 1, 'system:role:employee:batch:delete', 'system:role:employee:batch:delete', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:39:05', 1, '2023-10-07 18:43:32'),
(102, '移除员工', 3, 76, NULL, NULL, NULL, 1, 'system:role:employee:delete', 'system:role:employee:delete', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:39:21', 1, '2023-10-07 18:43:37'),
(103, '添加员工', 3, 76, NULL, NULL, NULL, 1, 'system:role:employee:add', 'system:role:employee:add', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:39:38', 1, '2023-10-07 18:44:05'),
(104, '修改权限', 3, 76, NULL, NULL, NULL, 1, 'system:role:menu:update', 'system:role:menu:update', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:41:55', 1, '2023-10-07 18:44:11'),
(105, '添加', 3, 26, NULL, NULL, NULL, 1, 'system:menu:add', 'system:menu:add', NULL, 26, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:44:37', 1, '2023-10-07 17:35:35'),
(106, '编辑', 3, 26, NULL, NULL, NULL, 1, 'system:menu:update', 'system:menu:update', NULL, 26, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:44:59', 1, '2023-10-07 17:35:48'),
(109, '参数配置', 2, 50, 3, '/config/config-list', '/support/config/config-list.vue', NULL, NULL, NULL, 'AntDesignOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 13:34:41', 1, '2022-06-23 16:24:16'),
(110, '数据字典', 2, 50, 4, '/setting/dict', '/support/dict/index.vue', NULL, NULL, NULL, 'BarcodeOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 17:53:00', 1, '2022-05-27 18:09:14'),
(111, '监控服务', 1, 0, 100, '/monitor', NULL, NULL, NULL, NULL, 'BarChartOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-06-17 11:13:23', 1, '2023-11-28 17:43:56'),
(113, '查询', 3, 112, NULL, NULL, NULL, NULL, NULL, 'ad', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-06-17 11:31:36', NULL, '2022-06-17 11:31:36'),
(114, '运维工具', 1, 0, 200, NULL, NULL, NULL, NULL, NULL, 'NodeCollapseOutlined', NULL, 0, NULL, 0, 1, 0, 1, 1, '2022-06-20 10:09:16', 1, '2023-12-01 19:36:18'),
(122, '数据库监控', 2, 111, 4, '/support/druid/index', NULL, NULL, NULL, NULL, 'ConsoleSqlOutlined', NULL, 1, 'http://localhost:1024/druid', 1, 1, 0, 0, 1, '2022-06-20 14:49:33', 1, '2023-02-16 19:15:58'),
(138, '功能Demo', 1, 0, 1000, NULL, NULL, NULL, NULL, NULL, 'BankOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-06-24 20:09:18', 1, '2026-04-04 07:22:43'),
(143, '登录登出记录', 2, 213, 5, '/support/login-log/login-log-list', '/support/login-log/login-log-list.vue', NULL, NULL, NULL, 'LoginOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-06-28 15:01:38', 44, '2024-08-13 14:33:49'),
(159, '查询', 3, 110, NULL, NULL, NULL, 1, 'support:dict:query', 'support:dict:query', NULL, 110, 0, NULL, 0, 1, 0, 0, 1, '2022-10-15 23:23:51', 1, '2025-04-08 19:42:25'),
(160, '添加', 3, 110, NULL, NULL, NULL, 1, 'support:dict:add', 'support:dict:add', NULL, 110, 0, NULL, 0, 1, 0, 0, 1, '2022-10-15 23:24:05', 1, '2025-04-08 19:43:02'),
(161, '更新', 3, 110, NULL, NULL, NULL, 1, 'support:dict:update', 'support:dict:update', NULL, 110, 0, NULL, 0, 1, 0, 0, 1, '2022-10-15 23:24:34', 1, '2025-04-08 19:43:34'),
(162, '删除', 3, 110, NULL, NULL, NULL, 1, 'support:dict:delete', 'support:dict:delete', NULL, 110, 0, NULL, 0, 1, 0, 0, 1, '2022-10-15 23:24:55', 1, '2025-04-08 19:43:52'),
(163, '新建', 3, 109, NULL, NULL, NULL, 1, 'support:config:add', 'support:config:add', NULL, 109, 0, NULL, 0, 1, 0, 0, 1, '2022-10-15 23:26:56', 1, '2023-10-07 18:16:17'),
(164, '编辑', 3, 109, NULL, NULL, NULL, 1, 'support:config:update', 'support:config:update', NULL, 109, 0, NULL, 0, 1, 0, 0, 1, '2022-10-15 23:27:07', 1, '2023-10-07 18:16:24'),
(193, '文件管理', 2, 50, 20, '/support/file/file-list', '/support/file/file-list.vue', NULL, NULL, NULL, 'FolderOpenOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-10-21 11:26:11', 1, '2022-10-22 11:29:22'),
(199, '查询', 3, 109, NULL, NULL, NULL, 1, 'support:config:query', 'support:config:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-10-21 20:45:14', 1, '2023-10-07 18:16:27'),
(200, '查询', 3, 193, NULL, NULL, NULL, 1, 'support:file:query', 'support:file:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-10-21 20:47:23', 1, '2023-10-07 18:24:43'),
(203, '查询', 3, 143, NULL, NULL, NULL, 1, 'support:loginLog:query', 'support:loginLog:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-10-21 21:05:11', 1, '2023-10-07 14:27:23'),
(204, '查询', 3, 81, NULL, NULL, NULL, 1, 'support:operateLog:query', 'support:operateLog:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-10-22 10:33:31', 1, '2023-10-07 14:27:56'),
(205, '详情', 3, 81, NULL, NULL, NULL, 1, 'support:operateLog:detail', 'support:operateLog:detail', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-10-22 10:33:49', 1, '2023-10-07 14:28:04'),
(213, '网络安全', 1, 0, 111, NULL, NULL, 1, NULL, NULL, 'SafetyCertificateOutlined', NULL, 0, NULL, 1, 1, 0, 0, 1, '2023-10-17 19:03:08', 1, '2026-04-18 03:38:58'),
(214, '登录失败锁定', 2, 213, 4, '/support/login-fail', '/support/login-fail/login-fail-list.vue', 1, NULL, NULL, 'LockOutlined', NULL, 0, NULL, 1, 1, 0, 0, 1, '2023-10-17 19:04:24', 44, '2024-08-13 14:16:26'),
(215, '接口加解密', 2, 213, 2, '/support/api-encrypt', '/support/api-encrypt/api-encrypt-index.vue', 1, NULL, NULL, 'CodepenCircleOutlined', NULL, 0, NULL, 1, 1, 0, 0, 1, '2023-10-24 11:49:28', 44, '2024-08-13 12:00:14'),
(218, '文档中心', 1, 0, 8, NULL, NULL, 1, NULL, NULL, 'FileSearchOutlined', NULL, 0, NULL, 1, 1, 0, 0, 1, '2023-12-01 19:37:28', 1, '2026-04-18 03:39:16'),
(219, '部门管理', 2, 45, 1, '/organization/department', '/system/department/department-list.vue', 1, NULL, NULL, 'ApartmentOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2024-06-22 16:40:21', 1, '2024-07-02 20:15:17'),
(221, '定时任务', 2, 50, 25, '/job/list', '/support/job/job-list.vue', 1, NULL, NULL, 'AppstoreOutlined', NULL, 0, NULL, 1, 1, 0, 0, 2, '2024-06-25 17:57:40', 2, '2024-06-25 19:49:21'),
(228, '职务管理', 2, 45, 2, '/organization/position', '/system/position/position-list.vue', 1, NULL, NULL, 'ApartmentOutlined', NULL, 0, NULL, 1, 1, 0, 0, 1, '2024-06-29 11:11:09', 1, '2024-07-02 20:15:11'),
(229, '查询任务', 3, 221, NULL, NULL, NULL, 1, 'support:job:query', 'support:job:query', NULL, 221, 0, NULL, 1, 1, 0, 0, 2, '2024-06-29 11:14:15', 2, '2024-06-29 11:15:00'),
(230, '更新任务', 3, 221, NULL, NULL, NULL, 1, 'support:job:update', 'support:job:update', NULL, 221, 0, NULL, 1, 1, 0, 0, 2, '2024-06-29 11:15:40', NULL, '2024-06-29 11:15:40'),
(231, '执行任务', 3, 221, NULL, NULL, NULL, 1, 'support:job:execute', 'support:job:execute', NULL, 221, 0, NULL, 1, 1, 0, 0, 2, '2024-06-29 11:16:03', NULL, '2024-06-29 11:16:03'),
(232, '查询记录', 3, 221, NULL, NULL, NULL, 1, 'support:job:log:query', 'support:job:log:query', NULL, 221, 0, NULL, 1, 1, 0, 0, 2, '2024-06-29 11:16:37', NULL, '2024-06-29 11:16:37'),
(233, 'knife4j文档', 2, 218, 4, '/knife4j', NULL, 1, NULL, NULL, 'FileWordOutlined', NULL, 1, 'http://localhost:1024/doc.html', 1, 1, 0, 0, 1, '2024-07-02 20:23:50', 1, '2024-07-08 13:49:15'),
(234, 'swagger文档', 2, 218, 5, '/swagger', 'http://localhost:1024/swagger-ui/index.html', 1, NULL, NULL, 'ApiOutlined', NULL, 1, 'http://localhost:1024/swagger-ui/index.html', 1, 1, 0, 0, 1, '2024-07-02 20:35:43', 1, '2024-07-08 13:49:26'),
(250, '三级等保设置', 2, 213, 1, '/support/level3protect/level3-protect-config-index', '/support/level3protect/level3-protect-config-index.vue', 1, NULL, NULL, 'SafetyOutlined', NULL, 0, NULL, 1, 1, 0, 0, 44, '2024-08-13 11:41:02', 44, '2024-08-13 11:58:12'),
(252, '启用/禁用', 3, 110, NULL, NULL, NULL, 1, 'support:dict:updateDisabled', 'support:dict:updateDisabled', NULL, 110, 0, NULL, 0, 1, 0, 0, 1, '2025-04-08 19:44:12', 1, '2025-04-08 19:46:03'),
(253, '查询字典数据', 3, 110, NULL, NULL, NULL, 1, 'support:dictData:query', 'support:dictData:query', NULL, 110, 0, NULL, 0, 1, 0, 0, 1, '2025-04-08 19:46:47', NULL, '2025-04-08 19:46:47'),
(254, '添加字典数据', 3, 110, NULL, NULL, NULL, 1, 'support:dictData:add', 'support:dictData:add', NULL, 110, 0, NULL, 0, 1, 0, 0, 1, '2025-04-08 19:48:00', NULL, '2025-04-08 19:48:00'),
(255, '更新字典数据', 3, 110, NULL, NULL, NULL, 1, 'support:dictData:update', 'support:dictData:update', NULL, 110, 0, NULL, 0, 1, 0, 0, 1, '2025-04-08 19:48:19', NULL, '2025-04-08 19:48:19'),
(256, '删除字典数据', 3, 110, NULL, NULL, NULL, 1, 'support:dictData:delete', 'support:dictData:delete', NULL, 110, 0, NULL, 0, 1, 0, 0, 1, '2025-04-08 19:48:38', NULL, '2025-04-08 19:48:38'),
(257, '启用/禁用字典数据', 3, 110, NULL, NULL, NULL, 1, 'support:dictData:updateDisabled', 'support:dictData:updateDisabled', NULL, 110, 0, NULL, 0, 1, 0, 0, 1, '2025-04-08 19:48:57', NULL, '2025-04-08 19:48:57'),
(301, '在线编辑器', 2, 138, NULL, '/code-editor', '/business/code-editor/JsonEditor.vue', 1, NULL, NULL, 'BgColorsOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-03-19 16:23:25', 1, '2026-03-19 16:23:45'),
(302, '脚本文档', 2, 218, NULL, '/engine-doc', '/support/engine-doc/index.vue', 1, NULL, NULL, 'AuditOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-03-20 16:01:42', 1, '2026-03-20 16:02:05'),
(303, '会员钱包', 2, 308, 0, '/ledger/member-wallet/list', '/business/ledger/member-wallet/member-wallet-list.vue', 1, NULL, NULL, 'DollarCircleOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-03 16:22:26', 1, '2026-04-18 03:46:03'),
(304, '查询', 3, 303, NULL, NULL, NULL, 1, 'memberWallet:query', 'memberWallet:query', NULL, 303, 0, NULL, 0, 1, 0, 0, 1, '2026-04-03 16:22:26', NULL, '2026-04-03 16:22:26'),
(305, '添加', 3, 303, NULL, NULL, NULL, 1, 'memberWallet:add', 'memberWallet:add', NULL, 303, 0, NULL, 0, 1, 0, 0, 1, '2026-04-03 16:22:26', NULL, '2026-04-03 16:22:26'),
(306, '更新', 3, 303, NULL, NULL, NULL, 1, 'memberWallet:update', 'memberWallet:update', NULL, 303, 0, NULL, 0, 1, 0, 0, 1, '2026-04-03 16:22:26', NULL, '2026-04-03 16:22:26'),
(307, '删除', 3, 303, NULL, NULL, NULL, 1, 'memberWallet:delete', 'memberWallet:delete', NULL, 303, 0, NULL, 0, 1, 0, 0, 1, '2026-04-03 16:22:26', NULL, '2026-04-03 16:22:26'),
(308, '财务中心', 1, 0, 1, NULL, NULL, 1, NULL, NULL, 'AreaChartOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-03 16:33:31', 1, '2026-08-22 17:35:31'),
(309, '会员优惠券', 2, 308, 2, '/ledger/member-coupon/list', '/business/ledger/member-coupon/member-coupon-list.vue', 1, NULL, NULL, 'CopyOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 03:32:18', 1, '2026-04-18 03:46:20'),
(310, '查询', 3, 309, NULL, NULL, NULL, 1, 'memberCoupon:query', 'memberCoupon:query', NULL, 309, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 03:32:18', NULL, '2026-04-04 03:32:18'),
(311, '添加', 3, 309, NULL, NULL, NULL, 1, 'memberCoupon:add', 'memberCoupon:add', NULL, 309, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 03:32:18', NULL, '2026-04-04 03:32:18'),
(312, '更新', 3, 309, NULL, NULL, NULL, 1, 'memberCoupon:update', 'memberCoupon:update', NULL, 309, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 03:32:18', NULL, '2026-04-04 03:32:18'),
(313, '删除', 3, 309, NULL, NULL, NULL, 1, 'memberCoupon:delete', 'memberCoupon:delete', NULL, 309, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 03:32:18', NULL, '2026-04-04 03:32:18'),
(314, '交易明细表', 2, 308, 3, '/ledger/member-asset-transaction/list', '/business/ledger/member-asset-transaction/member-asset-transaction-list.vue', 1, NULL, NULL, 'ReadOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:13:51', 1, '2026-04-18 03:46:26'),
(315, '查询', 3, 314, NULL, NULL, NULL, 1, 'memberAssetTransaction:query', 'memberAssetTransaction:query', NULL, 314, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:13:51', NULL, '2026-04-04 07:13:51'),
(316, '添加', 3, 314, NULL, NULL, NULL, 1, 'memberAssetTransaction:add', 'memberAssetTransaction:add', NULL, 314, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:13:51', NULL, '2026-04-04 07:13:51'),
(317, '更新', 3, 314, NULL, NULL, NULL, 1, 'memberAssetTransaction:update', 'memberAssetTransaction:update', NULL, 314, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:13:51', NULL, '2026-04-04 07:13:51'),
(318, '删除', 3, 314, NULL, NULL, NULL, 1, 'memberAssetTransaction:delete', 'memberAssetTransaction:delete', NULL, 314, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:13:51', NULL, '2026-04-04 07:13:51'),
(319, '任务中心', 1, 0, 2, NULL, NULL, 1, NULL, NULL, 'AndroidOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:21:54', 1, '2026-04-18 03:49:37'),
(320, '奖品配置', 2, 370, 1, '/prize/prize-config/list', '/business/prize/prize-config/prize-config-list.vue', 1, NULL, NULL, 'GiftOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:27:14', 1, '2026-04-18 12:17:51'),
(321, '查询', 3, 320, NULL, NULL, NULL, 1, 'prizeConfig:query', 'prizeConfig:query', NULL, 320, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:27:14', NULL, '2026-04-04 07:27:14'),
(322, '添加', 3, 320, NULL, NULL, NULL, 1, 'prizeConfig:add', 'prizeConfig:add', NULL, 320, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:27:14', NULL, '2026-04-04 07:27:14'),
(323, '更新', 3, 320, NULL, NULL, NULL, 1, 'prizeConfig:update', 'prizeConfig:update', NULL, 320, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:27:14', NULL, '2026-04-04 07:27:14'),
(324, '删除', 3, 320, NULL, NULL, NULL, 1, 'prizeConfig:delete', 'prizeConfig:delete', NULL, 320, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:27:14', NULL, '2026-04-04 07:27:14'),
(325, '奖励分组', 2, 319, 2, '/marketing/prize-group/list', '/business/marketing/prize-group/prize-group-list.vue', 1, NULL, NULL, 'OrderedListOutlined', NULL, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 07:35:32', 1, '2026-04-18 03:41:55'),
(326, '查询', 3, 325, NULL, NULL, NULL, 1, 'prizeGroup:query', 'prizeGroup:query', NULL, 325, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 07:35:32', 1, '2026-04-18 03:41:55'),
(327, '添加', 3, 325, NULL, NULL, NULL, 1, 'prizeGroup:add', 'prizeGroup:add', NULL, 325, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 07:35:32', 1, '2026-04-18 03:41:55'),
(328, '更新', 3, 325, NULL, NULL, NULL, 1, 'prizeGroup:update', 'prizeGroup:update', NULL, 325, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 07:35:32', 1, '2026-04-18 03:41:55'),
(329, '删除', 3, 325, NULL, NULL, NULL, 1, 'prizeGroup:delete', 'prizeGroup:delete', NULL, 325, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 07:35:32', 1, '2026-04-18 03:41:55'),
(330, '奖励记录', 2, 370, 3, '/prize/prize-log/list', '/business/prize/prize-log/prize-log-list.vue', 1, NULL, NULL, 'BarsOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:37:44', 1, '2026-04-18 12:32:38'),
(331, '查询', 3, 330, NULL, NULL, NULL, 1, 'prizeLog:query', 'prizeLog:query', NULL, 330, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:37:44', NULL, '2026-04-04 07:37:44'),
(332, '添加', 3, 330, NULL, NULL, NULL, 1, 'prizeLog:add', 'prizeLog:add', NULL, 330, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:37:44', NULL, '2026-04-04 07:37:44'),
(333, '更新', 3, 330, NULL, NULL, NULL, 1, 'prizeLog:update', 'prizeLog:update', NULL, 330, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:37:44', NULL, '2026-04-04 07:37:44'),
(334, '删除', 3, 330, NULL, NULL, NULL, 1, 'prizeLog:delete', 'prizeLog:delete', NULL, 330, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:37:44', NULL, '2026-04-04 07:37:44'),
(335, '奖励包映射', 2, 319, 11, '/marketing/task-prize-mapping/list', '/business/marketing/task-prize-mapping/task-prize-mapping-list.vue', 1, NULL, NULL, 'ApiOutlined', NULL, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 07:45:08', 1, '2026-04-18 03:42:07'),
(336, '查询', 3, 335, NULL, NULL, NULL, 1, 'taskPrizeMapping:query', 'taskPrizeMapping:query', NULL, 335, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 07:45:08', 1, '2026-04-18 03:42:07'),
(340, '任务配置表', 2, 319, 4, '/task/task-config/list', '/business/task/task-config/task-config-list.vue', 1, NULL, NULL, 'SettingOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:51:58', 1, '2026-04-18 03:49:09'),
(341, '查询', 3, 340, NULL, NULL, NULL, 1, 'taskConfig:query', 'taskConfig:query', NULL, 340, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:51:58', NULL, '2026-04-04 07:51:58'),
(342, '添加', 3, 340, NULL, NULL, NULL, 1, 'taskConfig:add', 'taskConfig:add', NULL, 340, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:51:58', NULL, '2026-04-04 07:51:58'),
(343, '更新', 3, 340, NULL, NULL, NULL, 1, 'taskConfig:update', 'taskConfig:update', NULL, 340, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:51:58', NULL, '2026-04-04 07:51:58'),
(344, '删除', 3, 340, NULL, NULL, NULL, 1, 'taskConfig:delete', 'taskConfig:delete', NULL, 340, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:51:58', NULL, '2026-04-04 07:51:58'),
(345, '任务记录', 2, 319, 5, '/task/task-record/list', '/business/task/task-record/task-record-list.vue', 1, NULL, NULL, 'DatabaseOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:52:31', 1, '2026-04-18 03:49:16'),
(346, '查询', 3, 345, NULL, NULL, NULL, 1, 'taskRecord:query', 'taskRecord:query', NULL, 345, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:52:31', NULL, '2026-04-04 07:52:31'),
(347, '添加', 3, 345, NULL, NULL, NULL, 1, 'taskRecord:add', 'taskRecord:add', NULL, 345, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:52:31', NULL, '2026-04-04 07:52:31'),
(348, '更新', 3, 345, NULL, NULL, NULL, 1, 'taskRecord:update', 'taskRecord:update', NULL, 345, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:52:31', NULL, '2026-04-04 07:52:31'),
(349, '删除', 3, 345, NULL, NULL, NULL, 1, 'taskRecord:delete', 'taskRecord:delete', NULL, 345, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:52:31', NULL, '2026-04-04 07:52:31'),
(350, '任务模板', 2, 319, 6, '/task/task-template/list', '/business/task/task-template/task-template-list.vue', 1, NULL, NULL, 'BookOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:53:04', 1, '2026-04-18 03:49:22'),
(351, '查询', 3, 350, NULL, NULL, NULL, 1, 'taskTemplate:query', 'taskTemplate:query', NULL, 350, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:53:04', NULL, '2026-04-04 07:53:04'),
(352, '添加', 3, 350, NULL, NULL, NULL, 1, 'taskTemplate:add', 'taskTemplate:add', NULL, 350, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:53:04', NULL, '2026-04-04 07:53:04'),
(353, '更新', 3, 350, NULL, NULL, NULL, 1, 'taskTemplate:update', 'taskTemplate:update', NULL, 350, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:53:04', NULL, '2026-04-04 07:53:04'),
(354, '删除', 3, 350, NULL, NULL, NULL, 1, 'taskTemplate:delete', 'taskTemplate:delete', NULL, 350, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:53:04', NULL, '2026-04-04 07:53:04'),
(355, '优惠配置', 2, 371, 7, '/risk/promotion-config/list', '/business/risk/promotion-config/promotion-config-list.vue', 1, NULL, NULL, 'ConsoleSqlOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 08:07:17', 1, '2026-04-18 03:41:35'),
(356, '查询', 3, 355, NULL, NULL, NULL, 1, 'promotionConfig:query', 'promotionConfig:query', NULL, 355, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 08:07:17', NULL, '2026-04-04 08:07:17'),
(357, '添加', 3, 355, NULL, NULL, NULL, 1, 'promotionConfig:add', 'promotionConfig:add', NULL, 355, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 08:07:17', NULL, '2026-04-04 08:07:17'),
(358, '更新', 3, 355, NULL, NULL, NULL, 1, 'promotionConfig:update', 'promotionConfig:update', NULL, 355, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 08:07:17', NULL, '2026-04-04 08:07:17'),
(359, '删除', 3, 355, NULL, NULL, NULL, 1, 'promotionConfig:delete', 'promotionConfig:delete', NULL, 355, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 08:07:17', NULL, '2026-04-04 08:07:17'),
(360, '提案', 2, 371, 6, '/risk/promotion-proposal/list', '/business/risk/promotion-proposal/promotion-proposal-list.vue', 1, NULL, NULL, 'FileMarkdownOutlined', NULL, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 08:07:59', 1, '2026-04-18 15:21:28'),
(361, '查询', 3, 360, NULL, NULL, NULL, 1, 'promotionProposal:query', 'promotionProposal:query', NULL, 360, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 08:07:59', 1, '2026-04-18 15:21:28'),
(362, '添加', 3, 360, NULL, NULL, NULL, 1, 'promotionProposal:add', 'promotionProposal:add', NULL, 360, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 08:07:59', 1, '2026-04-18 15:21:28'),
(363, '更新', 3, 360, NULL, NULL, NULL, 1, 'promotionProposal:update', 'promotionProposal:update', NULL, 360, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 08:07:59', 1, '2026-04-18 15:21:28'),
(364, '删除', 3, 360, NULL, NULL, NULL, 1, 'promotionProposal:delete', 'promotionProposal:delete', NULL, 360, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 08:07:59', 1, '2026-04-18 15:21:28'),
(365, '发货物流表', 2, 308, 7, '/ledger/physical-delivery/list', '/business/ledger/physical-delivery/physical-delivery-list.vue', 1, NULL, NULL, 'RotateRightOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 08:18:03', 1, '2026-04-18 03:46:32'),
(366, '查询', 3, 365, NULL, NULL, NULL, 1, 'physicalDelivery:query', 'physicalDelivery:query', NULL, 365, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 08:18:03', NULL, '2026-04-04 08:18:03'),
(367, '添加', 3, 365, NULL, NULL, NULL, 1, 'physicalDelivery:add', 'physicalDelivery:add', NULL, 365, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 08:18:03', NULL, '2026-04-04 08:18:03'),
(368, '更新', 3, 365, NULL, NULL, NULL, 1, 'physicalDelivery:update', 'physicalDelivery:update', NULL, 365, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 08:18:03', NULL, '2026-04-04 08:18:03'),
(369, '删除', 3, 365, NULL, NULL, NULL, 1, 'physicalDelivery:delete', 'physicalDelivery:delete', NULL, 365, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 08:18:03', NULL, '2026-04-04 08:18:03'),
(370, '活动中心', 1, 0, 3, NULL, NULL, 1, NULL, NULL, 'AppstoreOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 03:36:40', NULL, '2026-04-18 03:36:40'),
(371, '风控管理', 1, 0, 1, NULL, NULL, 1, NULL, NULL, 'BellOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 03:37:28', NULL, '2026-04-18 03:37:28'),
(372, '活动配置', 2, 370, 0, '/activity/activity-config/list', '/business/activity/activity-config/activity-config-list.vue', 1, NULL, NULL, 'TableOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 11:43:59', 1, '2026-04-18 11:51:18'),
(373, '查询', 3, 372, NULL, NULL, NULL, 1, 'activityConfig:query', 'activityConfig:query', NULL, 372, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 11:43:59', NULL, '2026-04-18 11:43:59'),
(374, '添加', 3, 372, NULL, NULL, NULL, 1, 'activityConfig:add', 'activityConfig:add', NULL, 372, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 11:43:59', NULL, '2026-04-18 11:43:59'),
(375, '更新', 3, 372, NULL, NULL, NULL, 1, 'activityConfig:update', 'activityConfig:update', NULL, 372, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 11:43:59', NULL, '2026-04-18 11:43:59'),
(376, '删除', 3, 372, NULL, NULL, NULL, 1, 'activityConfig:delete', 'activityConfig:delete', NULL, 372, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 11:43:59', NULL, '2026-04-18 11:43:59'),
(377, '奖品配置', 2, 0, NULL, '/activity/prize-config/list', '/business/activity/prize-config/prize-config-list.vue', 1, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 1, 1, '2026-04-18 12:13:29', 1, '2026-04-18 12:14:47'),
(378, '任务奖励', 2, 319, 9, '/prize/task-prize-mapping/list', '/business/prize/task-prize-mapping/task-prize-mapping-list.vue', 1, NULL, NULL, 'BankOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 12:46:22', 1, '2026-04-18 12:50:14'),
(379, '查询', 3, 378, NULL, NULL, NULL, 1, 'taskPrizeMapping:query', 'taskPrizeMapping:query', NULL, 378, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 12:46:23', NULL, '2026-04-18 12:46:23'),
(383, '提案记录', 2, 371, 1, '/risk/proposal-record/list', '/business/risk/proposal-record/proposal-record-list.vue', 1, NULL, NULL, 'BarsOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 15:20:56', 1, '2026-08-16 13:08:45'),
(384, '查询', 3, 383, NULL, NULL, NULL, 1, 'proposalRecord:query', 'proposalRecord:query', NULL, 383, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 15:20:56', NULL, '2026-04-18 15:20:56'),
(385, '添加', 3, 383, NULL, NULL, NULL, 1, 'proposalRecord:add', 'proposalRecord:add', NULL, 383, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 15:20:56', NULL, '2026-04-18 15:20:56'),
(386, '更新', 3, 383, NULL, NULL, NULL, 1, 'proposalRecord:update', 'proposalRecord:update', NULL, 383, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 15:20:56', NULL, '2026-04-18 15:20:56'),
(387, '删除', 3, 383, NULL, NULL, NULL, 1, 'proposalRecord:delete', 'proposalRecord:delete', NULL, 383, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 15:20:56', NULL, '2026-04-18 15:20:56'),
(388, '抽奖记录', 2, 393, 99, '/draw/draw-prize-log/list', '/business/draw/draw-prize-log/draw-prize-log-list.vue', 1, NULL, NULL, 'BarsOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 01:27:23', 1, '2026-04-19 01:38:18'),
(389, '查询', 3, 388, NULL, NULL, NULL, 1, 'drawPrizeLog:query', 'drawPrizeLog:query', NULL, 388, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 01:27:23', NULL, '2026-04-19 01:27:23'),
(393, '抽奖管理', 1, 0, 7, NULL, NULL, 1, NULL, NULL, 'AppstoreAddOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 01:36:30', NULL, '2026-04-19 01:36:30'),
(394, '彩票管理', 1, 0, 6, NULL, NULL, 1, NULL, NULL, 'DropboxOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 01:36:53', 1, '2026-04-19 01:37:22'),
(395, '奖池配置', 2, 393, 3, '/draw/prize-pool-config/list', '/business/draw/prize-pool-config/prize-pool-config-list.vue', 1, NULL, NULL, 'AppstoreOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 01:50:10', 1, '2026-08-31 22:39:47'),
(396, '查询', 3, 395, NULL, NULL, NULL, 1, 'prizePoolConfig:query', 'prizePoolConfig:query', NULL, 395, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 01:50:10', NULL, '2026-04-19 01:50:10'),
(398, '更新', 3, 395, NULL, NULL, NULL, 1, 'prizePoolConfig:update', 'prizePoolConfig:update', NULL, 395, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 01:50:10', NULL, '2026-04-19 01:50:10'),
(400, '奖池奖项', 2, 393, 7, '/draw/prize-pool-item/list', '/business/draw/prize-pool-item/prize-pool-item-list.vue', 1, NULL, NULL, 'BarsOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 01:56:55', 1, '2026-04-19 02:05:44'),
(401, '查询', 3, 400, NULL, NULL, NULL, 1, 'prizePoolItem:query', 'prizePoolItem:query', NULL, 400, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 01:56:55', NULL, '2026-04-19 01:56:55'),
(405, '奖池奖项巡检', 2, 393, 7, '/draw/pool-prize-mapping/list', '/business/draw/pool-prize-mapping/pool-prize-mapping-list.vue', 1, NULL, NULL, 'DatabaseOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 02:09:02', 1, '2026-08-16 12:51:59'),
(406, '查询', 3, 405, NULL, NULL, NULL, 1, 'poolPrizeMapping:query', 'poolPrizeMapping:query', NULL, 405, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 02:09:02', NULL, '2026-04-19 02:09:02'),
(410, '彩票配置', 2, 394, 0, '/lottery/lottery-config/list', '/business/lottery/lottery-config/lottery-config-list.vue', 1, NULL, NULL, 'AntDesignOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 03:19:12', 1, '2026-04-19 03:22:32'),
(411, '查询', 3, 410, NULL, NULL, NULL, 1, 'lotteryConfig:query', 'lotteryConfig:query', NULL, 410, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 03:19:12', NULL, '2026-04-19 03:19:12'),
(413, '更新', 3, 410, NULL, NULL, NULL, 1, 'lotteryConfig:update', 'lotteryConfig:update', NULL, 410, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 03:19:12', NULL, '2026-04-19 03:19:12'),
(415, '期号巡检', 2, 394, 2, '/lottery/lottery-issue/list', '/business/lottery/lottery-issue/lottery-issue-list.vue', 1, NULL, NULL, 'FieldNumberOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 03:28:57', 1, '2026-08-15 23:42:46'),
(416, '查询', 3, 415, NULL, NULL, NULL, 1, 'lotteryIssue:query', 'lotteryIssue:query', NULL, 415, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 03:28:57', NULL, '2026-04-19 03:28:57'),
(417, '添加', 3, 415, NULL, NULL, NULL, 1, 'lotteryIssue:add', 'lotteryIssue:add', NULL, 415, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 03:28:57', NULL, '2026-04-19 03:28:57'),
(418, '更新', 3, 415, NULL, NULL, NULL, 1, 'lotteryIssue:update', 'lotteryIssue:update', NULL, 415, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 03:28:57', NULL, '2026-04-19 03:28:57'),
(419, '删除', 3, 415, NULL, NULL, NULL, 1, 'lotteryIssue:delete', 'lotteryIssue:delete', NULL, 415, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 03:28:57', NULL, '2026-04-19 03:28:57'),
(425, '彩票奖励分析', 2, 394, 4, '/lottery/lottery-prize-rule/list', '/business/lottery/lottery-prize-rule/lottery-prize-rule-list.vue', 1, NULL, NULL, 'CloudServerOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 03:52:47', 1, '2026-08-16 07:39:03'),
(426, '查询', 3, 425, NULL, NULL, NULL, 1, 'lotteryPrizeRule:query', 'lotteryPrizeRule:query', NULL, 425, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 03:52:47', NULL, '2026-04-19 03:52:47'),
(430, '用户号码记录', 2, 394, 8, '/lottery/lottery-record/list', '/business/lottery/lottery-record/lottery-record-list.vue', 1, NULL, NULL, 'BarsOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 04:00:02', 1, '2026-04-19 04:09:27'),
(431, '查询', 3, 430, NULL, NULL, NULL, 1, 'lotteryRecord:query', 'lotteryRecord:query', NULL, 430, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 04:00:02', NULL, '2026-04-19 04:00:02'),
(435, '任务配置向导', 2, 319, 0, '/business/task/task-wizard', '/business/task/task-wizard/TaskWizard.vue', 1, NULL, NULL, 'AppstoreAddOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:46', NULL, '2026-08-15 15:05:46'),
(436, '任务模板设计器', 2, 319, 1, '/business/task/task-template-designer', '/business/task/task-template/TaskTemplateDesigner.vue', 1, NULL, NULL, 'AppstoreAddOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:46', NULL, '2026-08-15 15:05:46'),
(437, '抽奖工作台', 2, 393, 1, '/business/draw/draw-workbench', '/business/draw/draw-workbench/DrawWorkbench.vue', 1, NULL, NULL, 'AppstoreAddOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:46', 1, '2026-08-31 22:39:57'),
(438, '彩票配置工作台', 2, 394, 0, '/business/lottery/lottery-workbench', '/business/lottery/lottery-workbench/LotteryWorkbench.vue', 1, NULL, NULL, 'AppstoreAddOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(439, '查询', 3, 438, NULL, NULL, NULL, 1, 'lotteryConfig:query', 'lotteryConfig:query', NULL, 438, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(440, '保存配置', 3, 438, NULL, NULL, NULL, 1, 'lotteryConfig:update', 'lotteryConfig:update', NULL, 438, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(441, '活动创建向导', 2, 370, -1, '/activity/activity-wizard', '/business/activity/activity-wizard/ActivityWizard.vue', 1, NULL, NULL, 'RocketOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(442, '查询', 3, 441, NULL, NULL, NULL, 1, 'activityConfig:query', 'activityConfig:query', NULL, 441, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(443, '新建活动', 3, 441, NULL, NULL, NULL, 1, 'activityConfig:add', 'activityConfig:add', NULL, 441, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(444, '编辑活动/升级玩法', 3, 441, NULL, NULL, NULL, 1, 'activityConfig:update', 'activityConfig:update', NULL, 441, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(445, '删除活动', 3, 441, NULL, NULL, NULL, 1, 'activityConfig:delete', 'activityConfig:delete', NULL, 441, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(446, '向导提交', 3, 435, NULL, NULL, NULL, 1, 'taskConfig:wizard:submit', 'taskConfig:wizard:submit', NULL, 435, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(447, '设计器保存', 3, 436, NULL, NULL, NULL, 1, 'taskTemplate:save', 'taskTemplate:save', NULL, 436, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(448, '事件上报', 3, 345, NULL, NULL, NULL, 1, 'taskEvent:report', 'taskEvent:report', NULL, 345, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(449, '任务事件注册', 2, 319, 0, '/task/task-event/list', '/business/task/task-event/task-event-list.vue', 1, NULL, NULL, 'ApiOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(450, '查询', 3, 449, NULL, NULL, NULL, 1, 'taskEventDef:query', 'taskEventDef:query', NULL, 449, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(451, '新增', 3, 449, NULL, NULL, NULL, 1, 'taskEventDef:add', 'taskEventDef:add', NULL, 449, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(452, '编辑', 3, 449, NULL, NULL, NULL, 1, 'taskEventDef:update', 'taskEventDef:update', NULL, 449, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(453, '删除', 3, 449, NULL, NULL, NULL, 1, 'taskEventDef:delete', 'taskEventDef:delete', NULL, 449, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(454, '发奖审批', 3, 330, NULL, NULL, NULL, 1, 'prizeLog:approve', 'prizeLog:approve', NULL, 330, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(455, '提案审批', 3, 383, NULL, NULL, NULL, 1, 'proposalRecord:approve', 'proposalRecord:approve', NULL, 383, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(456, '执行抽奖', 3, 388, NULL, NULL, NULL, 1, 'drawPrizeLog:execute', 'drawPrizeLog:execute', NULL, 388, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(457, '工作台保存', 3, 437, NULL, NULL, NULL, 1, 'prizePoolConfig:workbench:save', 'prizePoolConfig:workbench:save', NULL, 437, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(458, '领号与验真', 3, 430, NULL, NULL, NULL, 1, 'lotteryTicket:query', 'lotteryTicket:query', NULL, 430, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(459, '首页·营销大屏', 2, 370, 9, '/home', '/system/home/index.vue', 1, NULL, NULL, 'DashboardOutlined', NULL, 0, NULL, 0, 0, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(460, '统计查询', 3, 459, NULL, NULL, NULL, 1, 'marketingStat:query', 'marketingStat:query', NULL, 459, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(461, '导入', 3, 365, NULL, NULL, NULL, 1, 'physicalDelivery:import', 'physicalDelivery:import', NULL, 365, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58'),
(462, '会员中心', 1, 0, 0, NULL, NULL, 1, NULL, NULL, 'UsergroupDeleteOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 17:34:56', NULL, '2026-08-22 17:34:56');
INSERT INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES
(463, '积分商城', 1, 0, 7, NULL, NULL, 1, NULL, NULL, 'MediumOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 19:20:09', 1, '2026-08-22 19:20:39'),
(464, '会员管理', 2, 462, 0, '/member/list', '/business/member/member-list.vue', 1, NULL, NULL, 'CalendarOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 12:01:37', 1, '2026-08-22 20:55:58'),
(465, '查询', 3, 464, NULL, NULL, NULL, 1, 'member:query', 'member:query', NULL, 464, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 12:01:37', NULL, '2026-08-22 12:01:37'),
(466, '收货地址簿', 2, 463, 9, '/mall/mall-address/list', '/business/mall/mall-address/mall-address-list.vue', 1, NULL, NULL, 'AlignRightOutlined', NULL, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 12:20:32', 1, '2026-08-23 05:08:51'),
(467, '查询', 3, 466, NULL, NULL, NULL, 1, 'mallAddress:query', 'mallAddress:query', NULL, 466, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 12:20:32', NULL, '2026-08-23 05:08:51'),
(468, '添加', 3, 466, NULL, NULL, NULL, 1, 'mallAddress:add', 'mallAddress:add', NULL, 466, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 12:20:32', NULL, '2026-08-23 05:08:51'),
(469, '更新', 3, 466, NULL, NULL, NULL, 1, 'mallAddress:update', 'mallAddress:update', NULL, 466, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 12:20:32', NULL, '2026-08-23 05:08:51'),
(470, '删除', 3, 466, NULL, NULL, NULL, 1, 'mallAddress:delete', 'mallAddress:delete', NULL, 466, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 12:20:32', NULL, '2026-08-23 05:08:51'),
(471, '商品分类', 2, 0, NULL, '/msll/mall-category/list', '/business/mall/mall-category/mall-category-list.vue', 1, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 12:23:09', 1, '2026-08-22 20:31:47'),
(472, '商品分类', 2, 0, NULL, '/msll/mall-category/list', '/business/mall/mall-category/mall-category-list.vue', 1, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 12:23:16', 1, '2026-08-22 20:31:45'),
(473, '商品分类', 2, 463, 0, '/mall/mall-category/list', '/business/mall/mall-category/mall-category-list.vue', 1, NULL, NULL, 'BarsOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 12:23:49', 1, '2026-08-22 20:32:34'),
(474, '商品管理', 2, 0, NULL, '/mall/mall-commodity/list', '/business/mall/mall-commodity/mall-commodity-list.vue', 1, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 12:28:01', 1, '2026-08-22 20:31:52'),
(475, '商品管理', 2, 0, NULL, '/mall/mall-commodity/list', '/business/mall/mall-commodity/mall-commodity-list.vue', 1, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 12:29:33', 1, '2026-08-22 20:31:55'),
(476, '商品管理', 2, 0, NULL, '/mall/mall-commodity/list', '/business/mall/mall-commodity/mall-commodity-list.vue', 1, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 12:29:33', 1, '2026-08-22 20:31:58'),
(477, '商品管理', 2, 463, 2, '/mall/mall-commodity/list', '/business/mall/mall-commodity/mall-commodity-list.vue', 1, NULL, NULL, 'SaveOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 12:30:50', 1, '2026-08-22 20:33:44'),
(478, '查询', 3, 477, NULL, NULL, NULL, 1, 'mallCommodity:query', 'mallCommodity:query', NULL, 477, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 12:37:36', NULL, '2026-08-22 12:37:36'),
(479, '添加', 3, 477, NULL, NULL, NULL, 1, 'mallCommodity:add', 'mallCommodity:add', NULL, 477, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 12:37:36', NULL, '2026-08-22 12:37:36'),
(480, '更新', 3, 477, NULL, NULL, NULL, 1, 'mallCommodity:update', 'mallCommodity:update', NULL, 477, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 12:37:36', NULL, '2026-08-22 12:37:36'),
(481, '删除', 3, 477, NULL, NULL, NULL, 1, 'mallCommodity:delete', 'mallCommodity:delete', NULL, 477, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 12:37:36', NULL, '2026-08-22 12:37:36'),
(482, '会员限兑计数', 2, 463, 7, '/mall/mall-exchange-limit/list', '/business/mall/mall-exchange-limit/mall-exchange-limit-list.vue', 1, NULL, NULL, 'AppstoreOutlined', NULL, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 12:39:52', 1, '2026-08-23 05:08:51'),
(483, '查询', 3, 482, NULL, NULL, NULL, 1, 'mallExchangeLimit:query', 'mallExchangeLimit:query', NULL, 482, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 12:40:47', NULL, '2026-08-23 05:08:51'),
(484, '添加', 3, 482, NULL, NULL, NULL, 1, 'mallExchangeLimit:add', 'mallExchangeLimit:add', NULL, 482, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 12:40:47', NULL, '2026-08-23 05:08:51'),
(485, '更新', 3, 482, NULL, NULL, NULL, 1, 'mallExchangeLimit:update', 'mallExchangeLimit:update', NULL, 482, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 12:40:47', NULL, '2026-08-23 05:08:51'),
(486, '删除', 3, 482, NULL, NULL, NULL, 1, 'mallExchangeLimit:delete', 'mallExchangeLimit:delete', NULL, 482, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 12:40:47', NULL, '2026-08-23 05:08:51'),
(487, '收藏统计', 2, 463, 4, '/mall/mall-favorite/list', '/business/mall/mall-favorite/mall-favorite-list.vue', 1, NULL, NULL, 'AntCloudOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 12:41:58', 1, '2026-08-23 05:08:51'),
(488, '兑换订单', 2, 463, 3, '/mall/mall-order/list', '/business/mall/mall-order/mall-order-list.vue', 1, NULL, NULL, 'BarsOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 12:44:03', 1, '2026-08-23 09:42:16'),
(489, '查询', 3, 488, NULL, NULL, NULL, 1, 'mallOrder:query', 'mallOrder:query', NULL, 488, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 12:44:43', NULL, '2026-08-22 12:44:43'),
(490, '添加', 3, 488, NULL, NULL, NULL, 1, 'mallOrder:add', 'mallOrder:add', NULL, 488, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 12:44:43', NULL, '2026-08-23 05:08:51'),
(491, '更新', 3, 488, NULL, NULL, NULL, 1, 'mallOrder:update', 'mallOrder:update', NULL, 488, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 12:44:43', NULL, '2026-08-22 12:44:43'),
(492, '删除', 3, 488, NULL, NULL, NULL, 1, 'mallOrder:delete', 'mallOrder:delete', NULL, 488, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 12:44:43', NULL, '2026-08-23 05:08:51'),
(493, '库存总览', 2, 463, 6, '/mall/mall-sku/list', '/business/mall/mall-sku/mall-sku-list.vue', 1, NULL, NULL, 'DatabaseOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 12:45:19', 1, '2026-08-23 05:08:51'),
(494, '查询', 3, 493, NULL, NULL, NULL, 1, 'mallSku:query', 'mallSku:query', NULL, 493, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 12:45:51', NULL, '2026-08-22 12:45:51'),
(495, '添加', 3, 493, NULL, NULL, NULL, 1, 'mallSku:add', 'mallSku:add', NULL, 493, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 12:45:51', NULL, '2026-08-23 05:08:51'),
(496, '更新', 3, 493, NULL, NULL, NULL, 1, 'mallSku:update', 'mallSku:update', NULL, 493, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 12:45:51', NULL, '2026-08-23 05:08:51'),
(497, '删除', 3, 493, NULL, NULL, NULL, 1, 'mallSku:delete', 'mallSku:delete', NULL, 493, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 12:45:51', NULL, '2026-08-23 05:08:51'),
(498, '会员实名信息', 2, 462, 3, '/member/member-verify/list', '/business/member/member-verify/member-verify-list.vue', 1, NULL, NULL, 'BoxPlotOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 13:08:52', 1, '2026-08-22 21:29:28'),
(499, '查询', 3, 498, NULL, NULL, NULL, 1, 'memberVerify:query', 'memberVerify:query', NULL, 498, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 13:09:43', NULL, '2026-08-22 13:09:43'),
(500, '添加', 3, 498, NULL, NULL, NULL, 1, 'memberVerify:add', 'memberVerify:add', NULL, 498, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 13:09:43', NULL, '2026-08-22 13:09:43'),
(501, '更新', 3, 498, NULL, NULL, NULL, 1, 'memberVerify:update', 'memberVerify:update', NULL, 498, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 13:09:43', NULL, '2026-08-22 13:09:43'),
(502, '删除', 3, 498, NULL, NULL, NULL, 1, 'memberVerify:delete', 'memberVerify:delete', NULL, 498, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 13:09:43', NULL, '2026-08-22 13:09:43'),
(503, '会员登录日志', 2, 462, 2, '/member/member-login-log/list', '/business/member/member-login-log/member-login-log-list.vue', 1, NULL, NULL, 'ApiOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 13:10:41', 1, '2026-08-22 21:30:48'),
(504, '查询', 3, 503, NULL, NULL, NULL, 1, 'memberLoginLog:query', 'memberLoginLog:query', NULL, 503, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 13:11:11', NULL, '2026-08-22 13:11:11'),
(505, '添加', 3, 503, NULL, NULL, NULL, 1, 'memberLoginLog:add', 'memberLoginLog:add', NULL, 503, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 13:11:11', NULL, '2026-08-22 13:11:11'),
(506, '更新', 3, 503, NULL, NULL, NULL, 1, 'memberLoginLog:update', 'memberLoginLog:update', NULL, 503, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 13:11:11', NULL, '2026-08-22 13:11:11'),
(507, '删除', 3, 503, NULL, NULL, NULL, 1, 'memberLoginLog:delete', 'memberLoginLog:delete', NULL, 503, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 13:11:11', NULL, '2026-08-22 13:11:11'),
(536, '商品新增', 2, 463, 2, '/mall/mall-commodity/editor', '/business/mall/mall-commodity/mall-commodity-editor.vue', 1, NULL, NULL, 'FormOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 16:42:37', 1, '2026-08-23 09:42:43'),
(537, '查询', 3, 473, NULL, NULL, NULL, 1, 'mallCategory:query', 'mallCategory:query', NULL, 473, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 16:42:37', NULL, '2026-08-22 16:42:37'),
(538, '添加', 3, 473, NULL, NULL, NULL, 1, 'mallCategory:add', 'mallCategory:add', NULL, 473, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 16:42:37', NULL, '2026-08-22 16:42:37'),
(539, '更新', 3, 473, NULL, NULL, NULL, 1, 'mallCategory:update', 'mallCategory:update', NULL, 473, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 16:42:37', NULL, '2026-08-22 16:42:37'),
(540, '删除', 3, 473, NULL, NULL, NULL, 1, 'mallCategory:delete', 'mallCategory:delete', NULL, 473, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 16:42:37', NULL, '2026-08-22 16:42:37'),
(541, '查询', 3, 487, NULL, NULL, NULL, 1, 'mallFavorite:query', 'mallFavorite:query', NULL, 487, 0, NULL, 0, 1, 0, 0, 1, '2026-08-22 16:42:37', NULL, '2026-08-22 16:42:37'),
(542, '添加', 3, 487, NULL, NULL, NULL, 1, 'mallFavorite:add', 'mallFavorite:add', NULL, 487, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 16:42:37', NULL, '2026-08-23 05:08:51'),
(543, '更新', 3, 487, NULL, NULL, NULL, 1, 'mallFavorite:update', 'mallFavorite:update', NULL, 487, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 16:42:37', NULL, '2026-08-23 05:08:51'),
(544, '删除', 3, 487, NULL, NULL, NULL, 1, 'mallFavorite:delete', 'mallFavorite:delete', NULL, 487, 0, NULL, 0, 1, 0, 1, 1, '2026-08-22 16:42:37', NULL, '2026-08-23 05:08:51'),
(552, '脚本管理', 2, 50, 26, '/support/script/script-list', '/support/script/script-list.vue', 1, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-23 10:09:13', NULL, '2026-08-23 10:09:13'),
(553, '查询', 3, 552, NULL, NULL, NULL, 1, 'script:query', 'script:query', NULL, 552, 0, NULL, 0, 1, 0, 0, 1, '2026-08-23 14:27:15', NULL, '2026-08-23 14:27:15'),
(554, '挂载脚本', 3, 552, NULL, NULL, NULL, 1, 'script:bind', 'script:bind', NULL, 552, 0, NULL, 0, 1, 0, 0, 1, '2026-08-23 14:27:15', NULL, '2026-08-23 14:27:15'),
(555, '在线试跑', 3, 552, NULL, NULL, NULL, 1, 'script:test', 'script:test', NULL, 552, 0, NULL, 0, 1, 0, 0, 1, '2026-08-23 14:27:15', NULL, '2026-08-23 14:27:15'),
(556, '优惠配置分组', 2, 371, 8, '/risk/promotion-group/list', '/business/risk/promotion-group/promotion-group-list.vue', 1, NULL, NULL, 'ClusterOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-30 02:33:28', NULL, '2026-08-30 02:33:28'),
(557, '优惠配置工作台', 2, 371, 9, '/risk/promotion-group/workbench', '/business/risk/promotion-group/PromotionGroupWorkbench.vue', 1, NULL, NULL, 'AppstoreAddOutlined', NULL, 0, NULL, 0, 0, 0, 0, 1, '2026-08-30 02:33:28', NULL, '2026-08-30 02:33:28'),
(558, '查询', 3, 556, NULL, NULL, NULL, 1, 'promotionGroup:query', 'promotionGroup:query', NULL, 556, 0, NULL, 0, 1, 0, 0, 1, '2026-08-30 02:33:28', NULL, '2026-08-30 02:33:28'),
(559, '更新', 3, 556, NULL, NULL, NULL, 1, 'promotionGroup:update', 'promotionGroup:update', NULL, 556, 0, NULL, 0, 1, 0, 0, 1, '2026-08-30 02:33:28', NULL, '2026-08-30 02:33:28'),
(560, '删除', 3, 556, NULL, NULL, NULL, 1, 'promotionGroup:delete', 'promotionGroup:delete', NULL, 556, 0, NULL, 0, 1, 0, 0, 1, '2026-08-30 02:33:28', NULL, '2026-08-30 02:33:28'),
(561, '脚本保存', 3, 552, 4, NULL, NULL, 1, 'script:edit', 'script:edit', NULL, 552, 0, NULL, 0, 1, 0, 0, 1, '2026-08-31 10:57:05', NULL, '2026-08-31 10:57:05'),
(562, '激活', 3, 552, 5, NULL, NULL, 1, 'script:publish', 'script:publish', NULL, 552, 0, NULL, 0, 1, 0, 0, 1, '2026-08-31 10:57:33', NULL, '2026-08-31 10:57:33'),
(563, '抽奖配置', 2, 393, 0, '/business/draw/draw-config', '/business/draw/draw-config/draw-config-list.vue', 1, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-31 06:23:09', 1, '2026-08-31 22:39:36'),
(564, '查询', 3, 563, NULL, NULL, NULL, 1, 'drawConfig:query', 'drawConfig:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-31 06:23:09', NULL, '2026-08-31 06:23:09'),
(565, '新增', 3, 563, NULL, NULL, NULL, 1, 'drawConfig:add', 'drawConfig:add', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-31 06:23:09', NULL, '2026-08-31 06:23:09'),
(566, '更新', 3, 563, NULL, NULL, NULL, 1, 'drawConfig:update', 'drawConfig:update', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-31 06:23:09', NULL, '2026-08-31 06:23:09'),
(567, '删除', 3, 563, NULL, NULL, NULL, 1, 'drawConfig:delete', 'drawConfig:delete', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-31 06:23:09', NULL, '2026-08-31 06:23:09'),
(600, '设备', 2, 462, 3, '/member/device/list', '/business/member/device/device-list.vue', 1, NULL, NULL, 'MobileOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-09-10 10:00:00', NULL, '2026-09-10 10:00:00'),
(601, '消息中心', 1, 0, 80, NULL, NULL, 1, NULL, NULL, 'MessageOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-09-14 22:42:53', NULL, '2026-09-14 22:42:53'),
(602, '通知模板', 2, 601, 1, '/notification/notification-template/list', '/business/notification/notification-template-list.vue', 1, NULL, NULL, NULL, NULL, 0, NULL, 1, 1, 0, 0, 1, '2026-09-14 22:42:53', NULL, '2026-09-14 22:42:53'),
(603, '查询', 3, 602, 1, NULL, NULL, 1, 'notificationTemplate:query', 'notificationTemplate:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-09-14 22:42:53', NULL, '2026-09-14 22:42:53'),
(604, '新增版本 / 停用', 3, 602, 2, NULL, NULL, 1, 'notificationTemplate:save', 'notificationTemplate:save', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-09-14 22:42:53', NULL, '2026-09-14 22:42:53'),
(605, '公告管理', 2, 601, 2, '/notification/announcement/list', '/business/notification/announcement-list.vue', 1, NULL, NULL, NULL, NULL, 0, NULL, 1, 1, 0, 0, 1, '2026-09-14 22:42:53', NULL, '2026-09-14 22:42:53'),
(606, '查询', 3, 605, 1, NULL, NULL, 1, 'announcement:query', 'announcement:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-09-14 22:42:53', NULL, '2026-09-14 22:42:53'),
(607, '编辑 / 下架', 3, 605, 2, NULL, NULL, 1, 'announcement:save', 'announcement:save', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-09-14 22:42:53', NULL, '2026-09-14 22:42:53'),
(608, '删除（含确认留痕）', 3, 605, 3, NULL, NULL, 1, 'announcement:delete', 'announcement:delete', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-09-14 22:42:53', NULL, '2026-09-14 22:42:53'),
(609, '人工发送', 2, 601, 3, '/notification/manual-notify/send', '/business/notification/manual-notify.vue', 1, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-09-15 00:04:04', NULL, '2026-09-15 00:04:04'),
(610, '发送', 3, 609, 1, NULL, NULL, 1, 'manualNotify:send', 'manualNotify:send', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-09-15 00:04:04', NULL, '2026-09-15 00:04:04'),
(611, '券模板', 2, 308, 4, '/ledger/coupon-template/list', '/business/ledger/coupon-template/coupon-template-list.vue', 1, NULL, NULL, 'TagsOutlined', NULL, 0, NULL, 1, 1, 0, 0, 1, '2026-09-15 04:14:03', NULL, '2026-09-15 04:14:03'),
(612, '查询', 3, 611, 1, NULL, NULL, 1, 'couponTemplate:query', 'couponTemplate:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-09-15 04:14:03', NULL, '2026-09-15 04:14:03'),
(613, '新增版本 / 停用', 3, 611, 2, NULL, NULL, 1, 'couponTemplate:save', 'couponTemplate:save', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-09-15 04:14:03', NULL, '2026-09-15 04:14:03'),
(614, '人工发券', 2, 308, 5, '/ledger/manual-coupon', '/business/ledger/coupon-template/manual-coupon.vue', 1, NULL, NULL, 'SendOutlined', NULL, 0, NULL, 1, 1, 0, 0, 1, '2026-09-15 18:32:20', NULL, '2026-09-15 18:32:20'),
(615, '发券', 3, 614, 1, NULL, NULL, 1, 'manualCoupon:send', 'manualCoupon:send', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-09-15 18:32:20', NULL, '2026-09-15 18:32:20'),
(616, '会员成长值', 2, 462, 5, '/member/member-grade/growth-list', '/business/member/member-grade/member-growth-list.vue', 1, NULL, NULL, 'RiseOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-09-18 08:52:12', NULL, '2026-09-20 02:38:32'),
(617, '等级配置', 2, 462, 6, '/member/member-grade/config-list', '/business/member/member-grade/member-grade-config-list.vue', 1, NULL, NULL, 'TrophyOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-09-18 08:52:13', NULL, '2026-09-20 02:38:32'),
(618, '等级变更留痕', 2, 462, 8, '/member/member-grade/grade-log-list', '/business/member/member-grade/member-grade-log-list.vue', 1, NULL, NULL, 'HistoryOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-09-18 08:52:13', NULL, '2026-09-20 23:42:56'),
(619, '查询', 3, 616, NULL, NULL, NULL, 1, 'memberGrade:query', 'memberGrade:query', NULL, 616, 0, NULL, 0, 1, 0, 0, 1, '2026-09-18 08:52:13', NULL, '2026-09-20 02:38:32'),
(620, '等级配置', 3, 616, NULL, NULL, NULL, 1, 'memberGrade:config', 'memberGrade:config', NULL, 616, 0, NULL, 0, 1, 0, 0, 1, '2026-09-18 08:52:13', NULL, '2026-09-20 02:38:32'),
(621, '人工调级', 3, 616, NULL, NULL, NULL, 1, 'memberGrade:adjust', 'memberGrade:adjust', NULL, 616, 0, NULL, 0, 1, 0, 0, 1, '2026-09-18 08:52:13', NULL, '2026-09-20 02:38:32'),
(622, '等级权益', 2, 462, 7, '/member/member-grade/privilege-list', '/business/member/member-grade/member-grade-privilege-list.vue', 1, NULL, NULL, 'GiftOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-09-20 23:42:56', NULL, '2026-09-20 23:42:56'),
(623, '权益发放配置', 2, 462, 8, '/member/member-entitlement/list', '/business/member/member-entitlement/member-entitlement-list.vue', 1, NULL, NULL, 'RedEnvelopeOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-09-23 00:38:05', NULL, '2026-09-23 00:38:05'),
(624, '查询', 3, 623, NULL, NULL, NULL, 1, 'memberEntitlement:query', 'memberEntitlement:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-09-23 00:38:05', NULL, '2026-09-23 00:38:05'),
(625, '配置（会真的发出东西）', 3, 623, NULL, NULL, NULL, 1, 'memberEntitlement:config', 'memberEntitlement:config', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-09-23 00:38:05', NULL, '2026-09-23 00:38:05'),
(626, '风控规则', 2, 636, 2, '/security/risk-rule', '/business/security/risk-rule-list.vue', 1, NULL, NULL, 'SafetyOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-15 16:39:12', NULL, '2026-10-17 11:48:31'),
(627, '查询', 3, 626, 1, NULL, NULL, 1, 'security:query', 'security:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-15 16:39:12', NULL, '2026-10-17 11:48:31'),
(628, '改档位 / 分值 / 参数', 3, 626, 2, NULL, NULL, 1, 'security:rule', 'security:rule', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-15 16:39:12', NULL, '2026-10-17 11:48:31'),
(629, '风控决策流水', 2, 636, 6, '/security/risk-event', '/business/security/risk-event-list.vue', 1, NULL, NULL, 'FileSearchOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-15 16:39:12', NULL, '2026-10-17 11:48:31'),
(630, '查询', 3, 629, 1, NULL, NULL, 1, 'security:query', 'security:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-15 16:39:12', NULL, '2026-10-17 11:48:31'),
(631, '安全名单', 2, 636, 5, '/security/list', '/business/security/security-list-list.vue', 1, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-15 23:35:17', NULL, '2026-10-17 11:48:31'),
(632, '查询', 3, 631, 1, NULL, NULL, 1, 'security:query', 'security:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-15 23:35:17', NULL, '2026-10-17 11:48:31'),
(633, '新增 / 撤销', 3, 631, 2, NULL, NULL, 1, 'security:list', 'security:list', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-15 23:35:17', NULL, '2026-10-17 11:48:31'),
(634, '风控看板', 2, 636, 8, '/security/stat', '/business/security/risk-stat.vue', 1, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-15 23:48:59', NULL, '2026-10-17 11:48:31'),
(635, '查询', 3, 634, 1, NULL, NULL, 1, 'security:query', 'security:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-15 23:48:59', NULL, '2026-10-17 11:48:31'),
(636, '安全中心', 1, 0, 1, NULL, NULL, 1, NULL, NULL, 'SafetyCertificateOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-17 11:48:31', NULL, '2026-10-17 11:48:31'),
(637, '限流规则', 2, 636, 1, '/security/rate-limit', '/business/security/rate-limit-rule.vue', 1, NULL, NULL, 'DashboardOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-17 11:48:31', NULL, '2026-10-17 11:48:31'),
(638, '查询', 3, 637, 1, NULL, NULL, 1, 'security:query', 'security:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-17 11:48:31', NULL, '2026-10-17 11:48:31'),
(639, '改阈值 / 接口覆盖', 3, 637, 2, NULL, NULL, 1, 'security:rule', 'security:rule', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-17 11:48:31', NULL, '2026-10-17 11:48:31'),
(640, '处罚查询', 2, 636, 3, '/security/penalty', '/business/security/penalty-query.vue', 1, NULL, NULL, 'StopOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-17 11:48:31', NULL, '2026-10-17 11:48:31'),
(641, '查询', 3, 640, 1, NULL, NULL, 1, 'security:query', 'security:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-17 11:48:31', NULL, '2026-10-17 11:48:31'),
(642, '解除处罚', 3, 640, 2, NULL, NULL, 1, 'security:penalty', 'security:penalty', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-17 11:48:31', NULL, '2026-10-17 11:48:31'),
(643, '蜜罐与关键字', 2, 636, 4, '/security/gate', '/business/security/security-gate.vue', 1, NULL, NULL, 'BugOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-17 11:48:31', NULL, '2026-10-17 11:48:31'),
(644, '查询', 3, 643, 1, NULL, NULL, 1, 'security:query', 'security:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-17 11:48:31', NULL, '2026-10-17 11:48:31'),
(645, '增删 / 停用', 3, 643, 2, NULL, NULL, 1, 'security:rule', 'security:rule', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-17 11:48:31', NULL, '2026-10-17 11:48:31'),
(646, '建蜜罐账号', 3, 643, 3, NULL, NULL, 1, 'security:list', 'security:list', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-17 11:48:31', NULL, '2026-10-17 11:48:31'),
(647, '安全事件', 2, 636, 7, '/security/incident', '/business/security/security-incident-list.vue', 1, NULL, NULL, 'AlertOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-17 11:48:31', NULL, '2026-10-17 11:48:31'),
(648, '查询', 3, 647, 1, NULL, NULL, 1, 'security:query', 'security:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-10-17 11:48:31', NULL, '2026-10-17 11:48:31');

-- -----------------------------------------------------------------------------------
-- t_role  角色（5 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_role`;
INSERT INTO `t_role` (`role_id`, `role_name`, `role_code`, `remark`, `update_time`, `create_time`) VALUES
(1, '技术总监', NULL, '', '2022-10-19 20:24:09', '2019-06-21 12:09:34'),
(34, '销售总监', 'cto', '', '2023-09-06 19:10:34', '2019-08-30 09:30:50'),
(35, '总经理', NULL, '', '2019-08-30 09:31:05', '2019-08-30 09:31:05'),
(36, '董事长', NULL, '', '2019-08-30 09:31:11', '2019-08-30 09:31:11'),
(37, '财务', NULL, '', '2019-08-30 09:31:16', '2019-08-30 09:31:16');

-- -----------------------------------------------------------------------------------
-- t_role_menu  角色-菜单授权（97 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_role_menu`;
INSERT INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES
(1051, 1, 137, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1052, 1, 220, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1053, 1, 45, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1054, 1, 219, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1055, 1, 46, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1056, 1, 91, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1057, 1, 92, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1058, 1, 93, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1059, 1, 94, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1060, 1, 95, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1061, 1, 96, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1062, 1, 86, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1063, 1, 87, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1064, 1, 88, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1065, 1, 76, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1066, 1, 97, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1067, 1, 98, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1068, 1, 99, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1069, 1, 100, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1070, 1, 101, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1071, 1, 102, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1072, 1, 103, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1073, 1, 104, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1074, 1, 218, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1089, 1, 111, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1091, 1, 122, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1092, 1, 228, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1093, 1, 138, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1107, 1, 301, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1140, 1, 213, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1141, 1, 250, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1142, 1, 215, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1144, 1, 214, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1145, 1, 143, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1146, 1, 203, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1147, 1, 81, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1148, 1, 204, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1149, 1, 205, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1150, 1, 50, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1151, 1, 26, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1152, 1, 40, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1153, 1, 105, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1154, 1, 106, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1155, 1, 109, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1156, 1, 163, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1157, 1, 164, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1158, 1, 199, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1159, 1, 110, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1160, 1, 159, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1161, 1, 160, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1162, 1, 161, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1163, 1, 162, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1164, 1, 252, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1165, 1, 253, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1166, 1, 254, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1167, 1, 255, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1168, 1, 256, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1169, 1, 257, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1176, 1, 193, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1177, 1, 200, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1178, 1, 221, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1179, 1, 229, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1180, 1, 230, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1181, 1, 231, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1182, 1, 232, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1185, 1, 302, '2026-03-20 16:03:25', '2026-03-20 16:03:25'),
(1187, 1, 552, '2026-08-23 10:09:13', '2026-08-23 10:09:13'),
(1188, 1, 553, '2026-08-23 14:27:15', '2026-08-23 14:27:15'),
(1189, 1, 554, '2026-08-23 14:27:15', '2026-08-23 14:27:15'),
(1190, 1, 555, '2026-08-23 14:27:15', '2026-08-23 14:27:15'),
(1191, 37, 384, '2026-09-05 15:53:46', '2026-09-05 15:53:46'),
(1192, 37, 455, '2026-09-05 15:53:46', '2026-09-05 15:53:46'),
(1300, 1, 600, '2026-09-10 10:00:00', '2026-09-10 10:00:00'),
(1301, 1, 626, '2026-10-15 16:39:12', '2026-10-15 16:39:12'),
(1302, 1, 627, '2026-10-15 16:39:12', '2026-10-15 16:39:12'),
(1303, 1, 628, '2026-10-15 16:39:12', '2026-10-15 16:39:12'),
(1304, 1, 629, '2026-10-15 16:39:12', '2026-10-15 16:39:12'),
(1305, 1, 630, '2026-10-15 16:39:12', '2026-10-15 16:39:12'),
(1306, 1, 631, '2026-10-15 23:35:28', '2026-10-15 23:35:28'),
(1307, 1, 632, '2026-10-15 23:35:28', '2026-10-15 23:35:28'),
(1308, 1, 633, '2026-10-15 23:35:28', '2026-10-15 23:35:28'),
(1309, 1, 634, '2026-10-15 23:48:59', '2026-10-15 23:48:59'),
(1310, 1, 635, '2026-10-15 23:48:59', '2026-10-15 23:48:59'),
(1311, 1, 636, '2026-10-17 11:48:31', '2026-10-17 11:48:31'),
(1312, 1, 637, '2026-10-17 11:48:31', '2026-10-17 11:48:31'),
(1313, 1, 638, '2026-10-17 11:48:31', '2026-10-17 11:48:31'),
(1314, 1, 639, '2026-10-17 11:48:31', '2026-10-17 11:48:31'),
(1315, 1, 640, '2026-10-17 11:48:31', '2026-10-17 11:48:31'),
(1316, 1, 641, '2026-10-17 11:48:31', '2026-10-17 11:48:31'),
(1317, 1, 642, '2026-10-17 11:48:31', '2026-10-17 11:48:31'),
(1318, 1, 643, '2026-10-17 11:48:31', '2026-10-17 11:48:31'),
(1319, 1, 644, '2026-10-17 11:48:31', '2026-10-17 11:48:31'),
(1320, 1, 645, '2026-10-17 11:48:31', '2026-10-17 11:48:31'),
(1321, 1, 646, '2026-10-17 11:48:31', '2026-10-17 11:48:31'),
(1322, 1, 647, '2026-10-17 11:48:31', '2026-10-17 11:48:31'),
(1323, 1, 648, '2026-10-17 11:48:31', '2026-10-17 11:48:31');

-- -----------------------------------------------------------------------------------
-- t_role_employee  角色-员工（11 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_role_employee`;
INSERT INTO `t_role_employee` (`id`, `role_id`, `employee_id`, `update_time`, `create_time`) VALUES
(325, 36, 63, '2022-10-19 20:25:26', '2022-10-19 20:25:26'),
(329, 34, 72, '2022-11-05 10:56:54', '2022-11-05 10:56:54'),
(330, 36, 72, '2022-11-05 10:56:54', '2022-11-05 10:56:54'),
(333, 1, 44, '2023-10-07 18:53:29', '2023-10-07 18:53:29'),
(334, 1, 47, '2023-10-07 18:55:00', '2023-10-07 18:55:00'),
(341, 1, 48, '2024-09-02 23:03:28', '2024-09-02 23:03:28'),
(342, 1, 75, '2026-07-30 17:00:16', '2026-07-30 17:00:16'),
(343, 34, 75, '2026-07-30 17:00:16', '2026-07-30 17:00:16'),
(344, 35, 75, '2026-07-30 17:00:16', '2026-07-30 17:00:16'),
(345, 36, 75, '2026-07-30 17:00:16', '2026-07-30 17:00:16'),
(346, 37, 75, '2026-07-30 17:00:16', '2026-07-30 17:00:16');

-- -----------------------------------------------------------------------------------
-- t_role_data_scope  角色数据范围（1 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_role_data_scope`;
INSERT INTO `t_role_data_scope` (`id`, `data_scope_type`, `view_type`, `role_id`, `update_time`, `create_time`) VALUES
(67, 1, 2, 1, '2024-03-18 20:41:00', '2024-03-18 20:41:00');

-- -----------------------------------------------------------------------------------
-- t_department  部门（8 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_department`;
INSERT INTO `t_department` (`department_id`, `department_name`, `manager_id`, `parent_id`, `sort`, `update_time`, `create_time`) VALUES
(1, '1024创新实验室', 1, 0, 1, '2022-10-19 20:17:09', '2022-10-19 20:17:09'),
(2, '开发部', 44, 1, 1000, '2022-10-19 20:22:23', '2022-10-19 20:22:23'),
(3, '产品部', 2, 1, 99, '2022-10-21 10:25:30', '2022-10-21 10:25:30'),
(4, '销售部', 64, 1, 9, '2022-10-21 10:25:47', '2022-10-21 10:25:47'),
(5, '测试部', 48, 1, 0, '2022-11-05 10:54:18', '2022-11-05 10:54:18'),
(7, '直播组', 44, 1, 1111, '2024-07-02 19:38:15', '2024-07-02 19:38:15'),
(8, '抖音组', 47, 7, 0, '2024-07-02 19:39:11', '2024-07-02 19:39:11'),
(9, '测试', 1, 1, 0, '2026-03-17 15:32:47', '2026-03-17 15:32:47');

-- -----------------------------------------------------------------------------------
-- t_position  职务（5 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_position`;
INSERT INTO `t_position` (`position_id`, `position_name`, `position_level`, `sort`, `remark`, `deleted_flag`, `create_time`, `update_time`) VALUES
(3, '技术P7', 'L1', 3, '', 0, '2024-06-29 15:57:07', '2024-07-15 23:34:35'),
(4, '技术P8', 'L2', 1, NULL, 0, '2024-07-15 23:34:14', '2024-07-15 23:34:23'),
(5, '管理M5', 'L1', 4, NULL, 0, '2024-07-15 23:34:48', '2024-07-15 23:34:48'),
(6, '管理M6', 'L2', 5, NULL, 0, '2024-07-15 23:35:00', '2024-07-15 23:35:00'),
(7, '测试', '1', 0, NULL, 0, '2026-03-17 15:39:52', '2026-03-17 15:39:52');

-- -----------------------------------------------------------------------------------
-- t_employee  员工账号。⚠️ 含 Argon2 密码哈希与手机号，见文件头提示（12 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_employee`;
INSERT INTO `t_employee` (`employee_id`, `employee_uid`, `login_name`, `login_pwd`, `actual_name`, `avatar`, `gender`, `phone`, `department_id`, `position_id`, `email`, `disabled_flag`, `deleted_flag`, `administrator_flag`, `remark`, `update_time`, `create_time`) VALUES
(1, 'cf1e361fd46741f5b2a09335cef50db8', 'admin', '$argon2id$v=19$m=16384,t=2,p=1$d9yQEAhck+haKxP2ZtXocg$NEnw3D2Ly8UbYpy2odATLA4ZflZ1FKJjWCuOGrVE4PM', '管理员', 'public/common/f36e59b20faa4720b225edf81d15727a_20250713220349.jpeg', 0, '13500000000', 1, 3, NULL, 0, 0, 1, NULL, '2025-07-15 10:19:23', '2022-10-04 21:33:50'),
(2, '3c253628b4cb4302a7bb83008a82a415', 'huke', '$argon2id$v=19$m=16384,t=2,p=1$3N9HxhPdydtmqXmTmBUxcw$Yh2jMqQ5qmCC1cgezKtFd5vuH8WirHZh6FPnFS0clEY', '胡克', NULL, 0, '13123123121', 1, 4, NULL, 0, 0, 0, NULL, '2025-07-15 10:19:23', '2022-10-04 21:33:50'),
(44, '5e2a57cd8eff4346be03dc2acfed0d7c', 'zhuoda', '$argon2id$v=19$m=16384,t=2,p=1$Mt02VdlsDNrteY/sBOs2uw$0gI5gfb/D4iLGi6RRlEq/4Qo71cseuz5YZrwiCj3VQI', '卓大', NULL, 1, '18637925892', 1, 6, NULL, 0, 0, 0, NULL, '2025-07-15 10:19:23', '2022-10-04 21:33:50'),
(47, 'b031a061076a4732aa0d63989adb1fbc', 'shanyi', '$argon2id$v=19$m=16384,t=2,p=1$lsqZF68KCPkPaF2ShNhtNQ$Zpsv0GLBeau3x0hL0JzpWtnIlNf0hh3+P6Zu5fM6gJw', '善逸', 'public/common/f823b00873684f0a9d31f0d62316cc8e_20240630015141.jpg', 1, '17630506613', 2, 5, NULL, 0, 0, 0, '这个是备注', '2025-07-15 10:19:23', '2022-10-04 21:33:50'),
(48, 'e29327485b784211aa9677a9436d2e00', 'qinjiu', '$argon2id$v=19$m=16384,t=2,p=1$ga8Ww+zlLShAzC8o54qftg$3Ete1M8/zzepZqiEV1yNu/U7svMI0EuDWVKZ9X5M1uQ', '琴酒', NULL, 1, '14112343212', 2, 6, NULL, 0, 0, 0, NULL, '2025-07-15 10:19:23', '2022-10-04 21:33:50'),
(63, 'cab6922aeeb949a997c93c043b909b05', 'kaiyun', '$argon2id$v=19$m=16384,t=2,p=1$5TZB3BWsbv0FXrgA60+7ag$pnDVVvjE/J0kOet3xLq19fyv1+a/KGqN6B+xsvDluYc', '开云', NULL, 0, '13112312346', 2, 5, 'ss@qq.com', 0, 0, 0, NULL, '2025-07-15 10:19:23', '2022-10-04 21:33:50'),
(64, '02ce19c1c707448a81159834a60bbd94', 'qingye', '$argon2id$v=19$m=16384,t=2,p=1$X+M3CF1557PGfLavpWXCPQ$2LsEiOgLFP+VbGA/7TPAbLnkyiLollova6iETB9S/ds', '清野', NULL, 1, '13123123111', 2, 4, NULL, 0, 0, 0, NULL, '2025-07-15 10:19:23', '2022-10-04 21:33:50'),
(65, 'e791135b86c34435873f4c9068c0e9ba', 'feiye', '$argon2id$v=19$m=16384,t=2,p=1$cPMw0Xu3dgu4lFX1x+qUvQ$Ol6NktMqi2fGn4Djv+m5ha/DyARWkXA/y784hFVa0rQ', '飞叶', NULL, 1, '13123123112', 4, 3, NULL, 0, 0, 0, NULL, '2025-07-15 10:19:23', '2022-10-04 21:33:50'),
(66, '2954e985557745df844e4c88532cd8a6', 'luoyi', '$argon2id$v=19$m=16384,t=2,p=1$D0lXN4LyhLhtHaKFbS3DRw$0FK9A8F1oT38xqIZvNcu1eWsB5C5vXkwULXhvLxYmK8', '罗伊', NULL, 1, '13123123142', 4, 2, NULL, 1, 0, 0, NULL, '2025-07-15 10:19:23', '2022-10-04 21:33:50'),
(67, '39cb2c7de94141d6824e9a167912c23d', 'chuxiao', '$argon2id$v=19$m=16384,t=2,p=1$/BdtVk/U5utWvple9bfCQw$eK8JjH+cei7gNQwPDDdP5ACQT3qkYvz5Qk4k016jRpU', '初晓', NULL, 1, '13123123123', 1, 2, NULL, 1, 0, 0, NULL, '2025-07-15 10:19:23', '2022-10-04 21:33:50'),
(68, '2aaf8c8c393c46b080aca86179388d7e', 'xuanpeng', '$argon2id$v=19$m=16384,t=2,p=1$ldHEjEwCWur/RnSy0JmFJQ$nlhVYiFMELToZ9nXI5QxG4maTV/L7pyPU0GRv3+s+tg', '玄朋', NULL, 1, '13123123124', 1, 3, NULL, 0, 0, 0, NULL, '2025-07-15 10:19:23', '2022-10-04 21:33:50'),
(75, '02a4f9d977c7461990d9317e773597b9', 'claude', '$argon2id$v=19$m=16384,t=2,p=1$ucx4Er/a/O2Ist2F7Jj4Yg$rbMz+WwkZ9gxzvPWHQC7e99cBjslGYpVCeOUCb5IITo', 'claude', NULL, 1, '13012341234', 1, 6, 'claude@qq.com', 0, 0, 0, NULL, '2026-07-30 17:00:16', '2026-07-30 17:00:16');

-- -----------------------------------------------------------------------------------
-- t_dict  字典（1 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_dict`;
INSERT INTO `t_dict` (`dict_id`, `dict_name`, `dict_code`, `remark`, `disabled_flag`, `create_time`, `update_time`) VALUES
(1, '商品地区', 'GOODS_PLACE', '用于商品管理中的商品地区1', 0, '2025-03-27 14:42:26', '2025-03-31 11:23:03');

-- -----------------------------------------------------------------------------------
-- t_dict_data  字典项（4 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_dict_data`;
INSERT INTO `t_dict_data` (`dict_data_id`, `dict_id`, `data_value`, `data_label`, `data_style`, `remark`, `sort_order`, `disabled_flag`, `create_time`, `update_time`) VALUES
(2, 1, 'LUO_YANG', '洛阳', '', 'sad', 2, 0, '2025-03-27 15:52:39', '2025-03-27 20:53:21'),
(3, 1, 'ZHENG_ZHOU', '郑州', '', '', 0, 0, '2025-03-27 18:58:16', '2025-03-27 20:53:32'),
(7, 1, 'BEI_JING', '北京', '', '', 0, 0, '2025-03-27 20:53:45', '2025-03-27 20:53:45'),
(8, 1, 'SHANG_HAI', '上海', '', '', 0, 0, '2025-03-27 20:53:45', '2025-03-27 20:53:45');

-- -----------------------------------------------------------------------------------
-- t_config  系统配置（4 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_config`;
INSERT INTO `t_config` (`config_id`, `config_name`, `config_key`, `config_value`, `remark`, `update_time`, `create_time`) VALUES
(1, '万能密码', 'super_password', '1024ok', '执行示例任务2', '2026-03-17 13:18:14', '2021-12-16 23:32:46'),
(2, '三级等保', 'level3_protect_config', '{"fileDetectFlag":true,"loginActiveTimeoutMinutes":30,"loginFailLockMinutes":30,"loginFailMaxTimes":5,"maxUploadFileSizeMb":50,"passwordComplexityEnabled":true,"regularChangePasswordMonths":3,"regularChangePasswordNotAllowRepeatTimes":3,"twoFactorLoginEnabled":false}', 'SmartJob Sample2 update', '2026-09-05 23:27:54', '2024-08-13 11:44:49'),
(3, '网关限流', 'gateway_rate_limit', '{"ladder":["60s","5m","30m","1h","2h","8h"],"levelResetAfter":"24h","anonAccountCap":"30m","ipSegmentCap":"30m","oldDeviceDays":7,"greyFactor":0.5,"rateLimitGreyLevel":4,"rateLimitGreyDays":7}', '阶梯处罚、清零时间、匿名账号维度与 IP 段维度的封顶、老设备豁免天数、灰名单阈值系数、限流重罚转灰。见 docs/架构/网关阶梯限流-技术方案.md §4、§5.5', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(4, '网关安全闸', 'gateway_security_gate', '{"ban":"24h","escalateGreyDays":30,"segmentGrey":{"minIps":3,"within":"24h","days":7},"breaker":{"window":"10m","distinctIps":50},"honeypotPaths":[{"code":"ENV","path":"/.env","enabled":true},{"code":"GIT","path":"/.git/**","enabled":true},{"code":"ACTUATOR","path":"/actuator/**","enabled":true},{"code":"WP_LOGIN","path":"/wp-login.php","enabled":true},{"code":"WP_ADMIN","path":"/wp-admin/**","enabled":true},{"code":"PHPMYADMIN","path":"/phpmyadmin/**","enabled":true},{"code":"TOMCAT_MANAGER","path":"/manager/html","enabled":true}],"keywords":[{"code":"SQL_SLEEP","scope":"PARAM","pattern":"\\\\b(?:pg_)?sleep\\\\s*\\\\(\\\\s*\\\\d+(?:\\\\.\\\\d+)?\\\\s*\\\\)","enabled":true},{"code":"SQL_BENCHMARK","scope":"PARAM","pattern":"\\\\bbenchmark\\\\s*\\\\(\\\\s*\\\\d+\\\\s*,","enabled":true},{"code":"SQL_WAITFOR","scope":"PARAM","pattern":"\\\\bwaitfor\\\\s+delay\\\\s+\'","enabled":true},{"code":"SQL_UNION_SELECT","scope":"PARAM","pattern":"\\\\bunion\\\\b[\\\\s/*]+(?:all[\\\\s/*]+)?select\\\\b[\\\\s/*]+(?:\\\\d|null\\\\b|@@|\\\\w+\\\\s*\\\\()","enabled":true},{"code":"SQL_TAUTOLOGY","scope":"PARAM","pattern":"\'\\\\s*(?:or|and)\\\\s+\'?\\\\d+\'?\\\\s*=\\\\s*\'?\\\\d+","enabled":true},{"code":"SQL_OR_PAREN","scope":"PARAM","pattern":"\\\\b(?:or|and)\\\\s*\\\\(\\\\s*\\\\d+\\\\s*=\\\\s*\\\\d+\\\\s*\\\\)","enabled":true},{"code":"SQL_INFO_SCHEMA","scope":"PARAM","pattern":"\\\\binformation_schema\\\\s*\\\\.","enabled":true},{"code":"XSS_SCRIPT","scope":"PARAM","pattern":"<\\\\s*script\\\\b","enabled":true},{"code":"XSS_EVENT","scope":"PARAM","pattern":"<[^>]*\\\\bon(?:error|load)\\\\s*=","enabled":true},{"code":"PATH_TRAVERSAL","scope":"PARAM","pattern":"(?:\\\\.\\\\.[/\\\\\\\\]){2,}","enabled":true},{"code":"FILE_PROBE","scope":"PARAM","pattern":"/etc/(?:passwd|shadow)\\\\b","enabled":true},{"code":"JNDI","scope":"PARAM","pattern":"\\\\$\\\\{\\\\s*jndi\\\\s*:","enabled":true},{"code":"UA_SCANNER","scope":"UA","pattern":"\\\\b(?:sqlmap|nikto|nmap|masscan|zgrab|nuclei|acunetix|wpscan|dirbuster|gobuster|ffuf)\\\\b","enabled":true}]}', '蜜罐与关键字的封禁时长、黑到期转灰、同段连坐、熔断阈值。honeypotPaths 是扫描器才会探的路径（真实客户端永远不调，与真实接口重叠时网关拒绝启动）；keywords 是探测特征（scope=PARAM 扫路径、查询串、JSON 请求体的值，密码与验证码字段不扫；scope=UA 只扫 User-Agent），保存时每条都要过反例集（KeywordRules.NORMAL_INPUTS），命中任何一条正常输入就存不进去。每条都能单独停用。见 docs/架构/网关阶梯限流-技术方案.md §5', '2026-10-17 19:57:45', '2026-10-17 12:00:00');
-- [跳过] t_serial_number : Table 'solvela.t_serial_number' doesn't exist

-- -----------------------------------------------------------------------------------
-- t_file_category  文件分类。代码按 category_code 引用，缺了直接抛异常（7 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_file_category`;
INSERT INTO `t_file_category` (`category_id`, `category_code`, `category_name`, `category_tag`, `sort`, `public_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
(1, 'COMMON', '通用', '系统', 10, 1, NULL, '2026-08-09 16:10:36', NULL, '2026-09-05 02:32:13'),
(2, 'NOTICE', '公告', '系统', 20, 1, NULL, '2026-08-09 16:10:36', NULL, '2026-09-05 02:32:13'),
(3, 'HELP_DOC', '帮助中心', '系统', 30, 1, NULL, '2026-08-09 16:10:36', NULL, '2026-09-05 02:32:13'),
(4, 'FEEDBACK', '意见反馈', '系统', 40, 1, NULL, '2026-08-09 16:10:36', NULL, '2026-09-05 02:32:13'),
(6, 'ACTIVITY', '活动素材', '营销', 5, 1, NULL, '2026-08-10 10:20:21', NULL, '2026-09-05 02:32:13'),
(7, 'CONTENT', '正文配图', '营销', 6, 1, NULL, '2026-08-10 11:01:30', NULL, '2026-09-05 02:32:13'),
(8, 'MALL_COMMODITY', '商城商品图', '商城', 100, 1, NULL, '2026-08-22 11:18:13', NULL, '2026-09-05 02:32:13');

-- -----------------------------------------------------------------------------------
-- t_solvela_job  定时任务定义。缺了任务不会注册（23 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_solvela_job`;
INSERT INTO `t_solvela_job` (`job_id`, `job_code`, `job_name`, `handler_name`, `job_group`, `trigger_type`, `trigger_value`, `next_trigger_time`, `prev_trigger_time`, `trigger_version`, `jitter_seconds`, `enabled_flag`, `param`, `preset_code`, `timeout_seconds`, `retry_times`, `retry_interval`, `misfire_strategy`, `misfire_threshold_sec`, `block_strategy`, `last_execute_time`, `last_execute_log_id`, `sort`, `remark`, `deleted_flag`, `update_name`, `create_time`, `update_time`, `app_env`, `alarm_receiver`, `continuous_fail_count`, `handler_missing_flag`, `terminal_flag`, `owner_biz_type`, `owner_biz_code`, `source`, `manual_modified_flag`) VALUES
(4, '2MA0ZV9ZM3', '【系统】清理定时任务执行日志', '_jobLogClean', 'SYSTEM', 'cron', '0 0 3 * * *', '2026-10-18 03:00:04', '2026-10-17 03:00:04', 15, 60, 1, NULL, 'NORMAL', 300, 1, 30, 'SKIP', 300, 'DISCARD', '2026-09-24 03:00:04', 13272, 0, '自动创建：每天 03:00 清理过期执行日志', 0, '管理员', '2026-08-12 07:57:06', '2026-10-17 04:31:40', 'dev', NULL, 0, 0, 0, 'SYSTEM', NULL, 'SYSTEM', 0),
(5, 'T0UK8ALDZG', '【系统】回收僵尸执行记录', '_jobZombieScan', 'SYSTEM', 'cron', '0 */5 * * * *', '2026-10-17 21:45:05', '2026-10-17 21:40:05', 950, 30, 1, NULL, 'LIGHT', 30, 0, 30, 'SKIP', 60, 'DISCARD', '2026-10-17 21:40:05', 47297, 0, '自动创建：每 5 分钟回收「执行中」僵尸记录', 0, '管理员', '2026-08-12 07:57:06', '2026-10-17 21:40:05', 'dev', NULL, 0, 0, 0, 'SYSTEM', NULL, 'SYSTEM', 0),
(6, 'OKHKUQ5G6K', '【系统】调度健康自检与告警', '_jobHealthCheck', 'SYSTEM', 'cron', '0 */5 * * * *', '2026-10-17 21:45:06', '2026-10-17 21:40:06', 950, 30, 1, NULL, 'LIGHT', 30, 0, 30, 'SKIP', 60, 'DISCARD', '2026-10-17 21:40:06', 47298, 0, '自动创建：每 5 分钟检查调度器是否仍在正常工作', 0, '管理员', '2026-08-12 07:57:06', '2026-10-17 21:40:06', 'dev', NULL, 0, 0, 0, 'SYSTEM', NULL, 'SYSTEM', 0),
(10, 'JOBCOUPEXP', '【账务】优惠券过期收口', 'couponExpire', 'BUSINESS', 'cron', '0 10 3 * * *', '2026-10-18 03:10:00', '2026-10-17 03:10:00', 38, 0, 1, NULL, 'NORMAL', 0, 0, 30, 'SKIP', 300, 'DISCARD', '2026-09-24 03:10:00', 13273, 0, '每天 03:10 把过了有效期仍未使用的券置为已过期；支持 dryRun 试运行', 0, 'system', '2026-08-18 14:44:11', '2026-10-17 04:31:40', 'dev', NULL, 0, 0, 0, NULL, NULL, 'MANUAL', 0),
(11, 'JOBTASKEXP', '【任务】任务记录过期收口', 'taskRecordExpire', 'BUSINESS', 'cron', '0 20 3 * * *', '2026-10-18 03:20:00', '2026-10-17 03:20:00', 38, 0, 1, NULL, 'NORMAL', 0, 0, 30, 'SKIP', 300, 'DISCARD', '2026-09-24 03:20:00', 13274, 0, '每天 03:20 把过了有效期仍在进行中的任务记录置为已过期；支持 dryRun 试运行', 0, 'system', '2026-08-18 14:44:11', '2026-10-17 04:31:40', 'dev', NULL, 0, 0, 0, NULL, NULL, 'MANUAL', 0),
(12, 'JOBPROPSTK', '【风控】提案卡单扫描', 'proposalStuckScan', 'OPS', 'cron', '0 */10 * * * *', '2026-10-17 21:50:00', '2026-10-17 21:40:00', 1519, 0, 1, '{"stuckMinutes": 30, "warnThreshold": 0}', 'NORMAL', 0, 0, 30, 'SKIP', 300, 'DISCARD', '2026-10-17 21:40:00', 47296, 0, '每 10 分钟扫一次卡在下发的提案，只报不修（自动重发需要下发侧先有幂等键）', 0, 'system', '2026-08-18 14:44:11', '2026-10-17 21:40:00', 'dev', NULL, 0, 0, 0, NULL, NULL, 'MANUAL', 0),
(13, 'JOBCOUPLCK', '【账务】优惠券卡单释放', 'couponStuckLockRelease', 'BUSINESS', 'cron', '0 5/30 * * * *', '2026-10-17 22:05:00', '2026-10-17 21:35:00', 240, 0, 1, NULL, 'NORMAL', 300, 0, 30, 'SKIP', 300, 'DISCARD', '2026-10-17 21:35:00', 47283, 0, '每 30 分钟把锁定超过 120 分钟仍未确认的券放回未使用；支持 dryRun 试运行。阈值不要调到 60 分钟以下', 0, 'system', '2026-09-15 05:47:06', '2026-10-17 21:35:00', 'dev', NULL, 0, 0, 0, NULL, NULL, 'MANUAL', 0),
(16, 'JOBMALLEXP', '【商城】超时未支付订单释放', 'mallOrderExpire', 'BUSINESS', 'cron', '0 3/5 * * * *', '2026-10-17 21:43:00', '2026-10-17 21:38:00', 890, 0, 1, NULL, 'NORMAL', 300, 0, 30, 'SKIP', 300, 'DISCARD', '2026-10-17 21:38:00', 47292, 10, '到点取消并放回积分/库存/限购额度', 0, 'system', '2026-09-20 07:45:00', '2026-10-17 21:38:00', 'dev', NULL, 0, 0, 0, NULL, NULL, 'MANUAL', 0),
(17, 'JOBPRZRECN', '【发奖】跨服务对账与重投', 'prizeDispatchReconcile', 'BUSINESS', 'cron', '0 7/15 * * * *', '2026-10-17 21:52:00', '2026-10-17 21:37:00', 337, 0, 1, NULL, 'NORMAL', 600, 0, 30, 'SKIP', 300, 'DISCARD', '2026-10-17 21:37:00', 47289, 20, '只处理 10 分钟前的记录 —— 刚落库那条可能正被线程池处理，捞它等于和自己抢', 0, 'system', '2026-09-20 07:45:00', '2026-10-17 21:37:00', 'dev', NULL, 0, 0, 0, NULL, NULL, 'MANUAL', 0),
(18, 'JOBBIZRECN', '【任务打点】漏投反查与补推', 'bizActionReconcile', 'BUSINESS', 'cron', '0 15/30 * * * *', '2026-10-17 21:45:00', '2026-10-17 21:15:00', 199, 0, 1, NULL, 'NORMAL', 600, 0, 30, 'SKIP', 300, 'DISCARD', '2026-10-17 21:15:00', 47248, 30, '往回看 24 小时，与流水求差集补推。重推被唯一键兜着，安全', 0, 'system', '2026-09-20 07:45:00', '2026-10-17 21:15:00', 'dev', NULL, 0, 0, 0, NULL, NULL, 'MANUAL', 0),
(19, 'JOBNOTICLN', '【通知】站内信保留期清理', 'memberNotificationClean', 'BUSINESS', 'cron', '0 30 3 * * *', '2026-10-18 03:30:00', '2026-10-17 03:30:00', 12, 0, 1, NULL, 'NORMAL', 600, 0, 30, 'SKIP', 300, 'DISCARD', '2026-09-24 03:30:00', 13275, 40, '默认保留 180 天', 0, 'system', '2026-09-20 07:45:00', '2026-10-17 04:31:40', 'dev', NULL, 0, 0, 0, NULL, NULL, 'MANUAL', 0),
(20, 'JOBMQLOGCL', '【基础】消息接收记录清理', 'mqMessageLogClean', 'SYSTEM', 'cron', '0 40 3 * * *', '2026-10-18 03:40:00', '2026-10-17 03:40:00', 12, 0, 1, NULL, 'NORMAL', 300, 0, 30, 'SKIP', 300, 'DISCARD', '2026-09-24 03:40:00', 13276, 50, '默认保留 7 天', 0, 'system', '2026-09-20 07:45:00', '2026-10-17 04:31:40', 'dev', NULL, 0, 0, 0, NULL, NULL, 'MANUAL', 0),
(21, 'JOBCOUPNTF', '【通知】优惠券即将过期提醒', 'couponExpiringNotify', 'BUSINESS', 'cron', '0 0 10 * * *', '2026-10-18 10:00:00', '2026-10-17 10:00:00', 12, 0, 1, '{"dryRun": true}', 'NORMAL', 600, 0, 30, 'SKIP', 300, 'DISCARD', '2026-09-27 10:00:00', 13308, 60, '⚠️ 非幂等且会发站内信。首次挂载为 dryRun，确认量级后把 param 置空', 0, 'system', '2026-09-20 07:45:00', '2026-10-17 10:41:16', 'dev', NULL, 0, 0, 0, NULL, NULL, 'MANUAL', 0),
(23, 'JOBGRDSETL', '【会员】等级期末结算与保级判定', 'memberGradeSettle', 'BUSINESS', 'cron', '0 25 * * * *', '2026-10-17 22:25:00', '2026-10-17 21:25:00', 125, 0, 1, NULL, 'NORMAL', 900, 0, 30, 'SKIP', 300, 'DISCARD', '2026-10-17 21:25:00', 47257, 5, '⚠️ 首次为 dryRun。确认待结算人数后把 param 置空放开 —— 它会真降级', 0, 'system', '2026-09-20 07:55:46', '2026-10-17 21:25:00', 'dev', NULL, 0, 0, 0, NULL, NULL, 'MANUAL', 0),
(24, 'JOBGRWRECN', '【会员】成长值对账', 'memberGrowthReconcile', 'BUSINESS', 'cron', '0 50 * * * *', '2026-10-17 21:50:00', '2026-10-17 20:50:00', 119, 0, 1, NULL, 'NORMAL', 900, 0, 30, 'SKIP', 300, 'DISCARD', '2026-10-17 17:53:32', 47243, 6, '只报不改。发现漂移会打 ERROR 并带样本；autoFix 是手工动作，不要常开', 0, 'system', '2026-09-20 23:16:02', '2026-10-17 21:08:25', 'dev', NULL, 0, 0, 0, NULL, NULL, 'MANUAL', 0),
(25, 'JOBGDENTGR', '【会员】等级权益生成与过期', 'gradeEntitlementGrant', 'BUSINESS', 'cron', '0 10 4 * * *', '2026-09-23 07:16:54', NULL, 0, 0, 1, NULL, 'NORMAL', 1800, 0, 60, 'SKIP', 600, 'DISCARD', NULL, 10408, 7, '每天扫全量生成待领取，靠唯一键幂等（自带补跑）；同时把到期未领的置为过期', 0, 'system', '2026-09-22 23:15:54', '2026-09-23 08:44:41', 'dev', NULL, 0, 1, 0, NULL, NULL, 'MANUAL', 0),
(26, 'JOBFILEBKF', '【基础】文件引用存量回填', 'fileRelationBackfill', 'SYSTEM', 'cron', '0 0 4 1 1 ?', NULL, NULL, 0, 0, 0, NULL, 'NORMAL', 600, 0, 30, 'SKIP', 300, 'DISCARD', NULL, 11754, 0, '手动跑一次即可：把商城分类图标 / 任务规则说明内嵌图 / 奖品扩展图的存量引用补进 t_file_relation。开 fileOrphanClean 之前【必须】先跑这个，否则那些图会被当垃圾删掉。幂等，重复跑无害。', 0, 'system', '2026-09-25 12:02:00', '2026-09-25 20:06:08', 'dev', NULL, 0, 0, 0, 'SYSTEM', NULL, 'MANUAL', 0),
(27, 'JOBFILEORP', '【基础】孤儿文件清理', 'fileOrphanClean', 'SYSTEM', 'cron', '0 40 3 * * *', NULL, NULL, 0, 60, 0, NULL, 'NORMAL', 600, 0, 30, 'SKIP', 300, 'DISCARD', NULL, 11759, 0, '每天 03:40 删掉「上传了但从来没有业务引用」的临时文件（默认保护期 7 天）。⚠️ 开之前必须先跑 fileRelationBackfill，并用 dryRun 看一眼数量。支持 retainDays / dryRun 两个参数。', 0, 'system', '2026-09-25 12:02:00', '2026-09-25 20:09:36', 'dev', NULL, 1, 0, 0, 'SYSTEM', NULL, 'MANUAL', 0),
(28, 'JOBPARTMNT', '【基础】按月分区维护', 'partitionMaintain', 'SYSTEM', 'cron', '0 50 3 * * *', '2026-10-18 03:50:28', '2026-10-17 21:26:33', 1, 60, 1, NULL, 'NORMAL', 600, 1, 60, 'SKIP', 300, 'DISCARD', '2026-10-17 21:26:33', 47260, 0, '每天 03:50 给按月分区的表补下 2 个月的分区、删掉保留期（6 个月）之外的。🔴 不跑的后果是静默的：四张表（登录日志、风控决策流水与命中明细、安全事件流水）的分区只建到 2027-01，之后数据全落进 pmax，而它永不过期 —— 保留期形同虚设，而且之后只能靠 DELETE 清（长事务、主从延迟、碎片）。单次最多删 3 个分区，保留期误配时不会一口气抹掉几年数据。', 0, '管理员', '2026-10-15 16:17:37', '2026-10-17 21:26:33', 'dev', NULL, 0, 0, 0, 'SYSTEM', NULL, 'SYSTEM', 0),
(29, 'JOBGWSNAPS', '【安全】网关规则快照重发', 'gatewaySnapshotPublish', 'SYSTEM', 'cron', '0 1/5 * * * *', '2026-10-17 21:41:09', '2026-10-17 21:36:09', 3, 10, 1, NULL, 'NORMAL', 30, 0, 60, 'SKIP', 300, 'DISCARD', '2026-10-17 21:36:09', 47287, 0, '每 5 分钟把限流规则、两条全局参数、IP 名单拼成快照重发到 Redis（gw:rl:snapshot）。主路径是后台保存后立即发布，本任务是自愈：Redis 被清、被内存淘汰、保存时发布失败，都在这里补回来。内容没变时版本不变，网关不会重载。见 docs/架构/网关阶梯限流-技术方案.md §6', 0, '管理员', '2026-10-17 12:00:00', '2026-10-17 21:36:09', 'dev', NULL, 0, 0, 0, 'SYSTEM', NULL, 'SYSTEM', 0),
(30, 'JOBDEVBANM', '【安全】设备封禁镜像重建', 'deviceBanMirrorRebuild', 'SYSTEM', 'cron', '0 3/5 * * * *', '2026-10-17 21:43:00', '2026-10-17 21:38:00', 4, 10, 1, NULL, 'NORMAL', 30, 0, 60, 'SKIP', 300, 'DISCARD', '2026-10-17 21:38:00', 47293, 0, '每 5 分钟按 t_device.status 全量重建 Redis 里的设备封禁镜像（sec:dev:banned），网关安全闸靠它挡被封的设备。主路径是后台人工处置时立即同步，本任务是自愈：同步失败、Redis 被清或被内存淘汰，都在这里补回来。🔴 镜像丢了的后果是静默的：被封的设备照常能用。见 docs/架构/网关阶梯限流-技术方案.md §5.5', 0, '管理员', '2026-10-17 12:00:00', '2026-10-17 21:38:00', 'dev', NULL, 0, 0, 0, 'SYSTEM', NULL, 'SYSTEM', 0),
(31, 'JOBSECINCD', '【安全】安全事件落库', 'securityIncidentDrain', 'SYSTEM', 'cron', '30 * * * * *', '2026-10-17 21:40:31', '2026-10-17 21:39:31', 14, 5, 1, NULL, 'NORMAL', 30, 0, 60, 'SKIP', 300, 'DISCARD', '2026-10-17 21:39:31', 47295, 0, '每分钟把 Redis 队列 sec:incident 里的安全事件（蜜罐、关键字、限流判罚）写进 t_security_incident，并按事件里的处置自动拉黑 / 拉灰 IP、把设备推进观察档；名单变了立即重发网关快照。取走的事件先挪到 sec:incident:processing，落完库才删，任务崩了下一轮接着做。处罚本身早已在 Redis 里生效，这个任务停了只影响留痕与名单。见 docs/架构/网关阶梯限流-技术方案.md §6.4', 0, '管理员', '2026-10-17 12:00:00', '2026-10-17 21:39:31', 'dev', NULL, 0, 0, 0, 'SYSTEM', NULL, 'SYSTEM', 0),
(32, 'JOBSECLIST', '【安全】安全名单维护', 'securityListMaintain', 'SYSTEM', 'cron', '0 2/5 * * * *', '2026-10-17 21:42:02', '2026-10-17 21:37:02', 4, 10, 1, NULL, 'NORMAL', 30, 0, 60, 'SKIP', 300, 'DISCARD', '2026-10-17 21:37:02', 47290, 0, '每 5 分钟：到期的自动黑名单转灰 30 天（ESCALATE）；24 小时内同一 /24（IPv6 /48）有 3 个以上 IP 被自动拉黑则整段拉灰 7 天（SEGMENT，只拉灰、从不自动拉黑整段）；把网关攒在 Redis 的被挡次数（sec:blocked:*）加到黑名单的 blocked_count。参数在 t_config.gateway_security_gate。见 docs/架构/网关阶梯限流-技术方案.md §5.5、§6.3', 0, '管理员', '2026-10-17 12:00:00', '2026-10-17 21:37:02', 'dev', NULL, 0, 0, 0, 'SYSTEM', NULL, 'SYSTEM', 0);

-- -----------------------------------------------------------------------------------
-- t_task_event  任务事件定义（v3.47.0 灌入）（10 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_task_event`;
INSERT INTO `t_task_event` (`id`, `event_code`, `event_name`, `metric_source`, `payload_schema`, `biz_id_required`, `is_high_frequency`, `discard_log_flag`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
(1, 'DAILY_SIGN', '每日签到', 'NONE', '{"fields": []}', 0, 0, 1, '天然无单号，按事件自然日兜底幂等（一天算一次）。生产者：MemberSignService.sign（C 端 POST /task/sign）', 1, NULL, '2026-08-01 05:12:16', NULL, '2026-09-20 23:27:29'),
(2, 'ORDER_PAID', '订单支付成功', 'NONE', '{"fields": [{"key": "orderNo", "desc": "订单号", "type": "string"}, {"key": "payPoints", "desc": "实付积分", "type": "decimal"}, {"key": "payCash", "desc": "实付现金", "type": "decimal"}, {"key": "quantity", "desc": "件数", "type": "int"}, {"key": "commodityCode", "desc": "商品编码", "type": "string"}, {"key": "skuCode", "desc": "SKU 编码", "type": "string"}]}', 1, 0, 1, '🔴 必须带 eventBizId=订单号，否则同一天多笔订单只会算一笔。生产者：MallPayService.pay（混合单）与 MallRedeemService.redeem（纯积分单）', 1, NULL, '2026-08-01 05:12:16', NULL, '2026-09-20 23:27:29'),
(3, 'MEMBER_REGISTER', '会员注册', 'NONE', '{"fields": []}', 0, 0, 1, '一个会员一辈子一次。生产者：MemberRegisterService.createMember，幂等键=会员号', 1, NULL, '2026-08-01 05:12:16', NULL, '2026-09-20 23:27:29'),
(4, 'PAGE_VIEW', '页面浏览', 'NONE', '{"fields": [{"key": "pageId", "desc": "页面标识", "type": "string"}]}', 0, 1, 0, '高频事件：discard_log_flag 关掉，否则不匹配的浏览会把流水表写爆', 1, NULL, '2026-08-01 05:12:16', NULL, '2026-08-22 09:49:51'),
(5, 'GOODS_SHARE', '分享商品', 'NONE', '{"fields": [{"key": "goodsId", "desc": "商品ID", "type": "string"}]}', 0, 0, 1, '本次新增，用来演示「加事件只加一行数据、前端零改动」', 1, NULL, '2026-08-01 05:12:16', NULL, '2026-08-22 09:49:51'),
(6, 'ORDER_AMOUNT', '订单金额累计', 'payPoints', '{"fields": [{"key": "orderNo", "desc": "订单号", "type": "string"}, {"key": "payPoints", "desc": "实付积分", "type": "decimal"}, {"key": "payCash", "desc": "实付现金", "type": "decimal"}, {"key": "quantity", "desc": "件数", "type": "int"}, {"key": "commodityCode", "desc": "商品编码", "type": "string"}, {"key": "skuCode", "desc": "SKU 编码", "type": "string"}]}', 1, 0, 1, 'AMOUNT 类任务用。生产者：MallPayService.pay（混合单）与 MallRedeemService.redeem（纯积分单）——🔴 两个产生点，改一处必须想另一处', 1, NULL, '2026-08-01 05:12:16', NULL, '2026-09-20 23:27:29'),
(7, 'CONCURRENT_ADD', '并发累加(验收专用)', 'payAmount', '{"fields": [{"key": "payAmount", "desc": "金额", "type": "decimal"}]}', 1, 0, 1, 'P0 判据4 专用，生产环境可停用', 1, NULL, '2026-08-01 05:12:16', NULL, '2026-08-22 09:49:51'),
(32, 'AUDIENCE_TEST', '人群过滤验收', 'NONE', '{"fields": []}', 0, 0, 1, 'P0 验收专用：同一个事件同时命中「限新会员」和「限老会员」两个任务，一次验两个分支', 1, NULL, '2026-09-20 08:12:56', NULL, '2026-09-20 08:12:56'),
(33, 'ROUND_TEST', '参与轮次验收', 'NONE', '{"fields": []}', 1, 0, 1, 'P0 验收专用：每轮用不同 eventBizId，验 limit_count 轮次推进与用尽', 1, NULL, '2026-09-20 08:12:56', NULL, '2026-09-20 08:12:56'),
(34, 'RECHARGE_PAID', '充值成功', 'NONE', '{"fields": [{"key": "orderNo", "desc": "外部消费单号", "type": "string"}, {"key": "sceneCode", "desc": "场景编码", "type": "string"}, {"key": "payAmount", "desc": "实付金额", "type": "decimal"}, {"key": "originalAmount", "desc": "原价", "type": "decimal"}]}', 1, 0, 1, '🔴 必须带 eventBizId=外部消费单号。生产者：ExternalRechargeService.payAndExecute —— 打点在 markSuccess【之后】，收了钱不等于充值成功', 1, NULL, '2026-09-20 23:27:29', NULL, '2026-09-20 23:27:29');
-- [跳过] t_notice_type : Table 'solvela.t_notice_type' doesn't exist

-- -----------------------------------------------------------------------------------
-- t_notification_template  通知模板。缺了发不出任何站内信，而且不报错（12 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_notification_template`;
INSERT INTO `t_notification_template` (`template_code`, `version`, `category`, `title_template`, `content_template`, `param_keys`, `status`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('ACCOUNT_LIMITED', 1, 'SYSTEM', '账号使用受限提醒', '您的账号因「${limitType}」被限制部分功能，预计 ${unlockTime} 自动恢复。如有疑问请联系客服。', '["limitType", "unlockTime"]', 1, 'system', '2026-09-14 22:42:48', NULL, '2026-09-14 22:42:48'),
('CONTACT_BOUND', 1, 'SYSTEM', '账号绑定变更提醒', '你的账号刚刚绑定了${contactType} ${maskedContact}。如果不是你本人操作，请立即修改密码，并在「我的登录设备」中下线其他设备。', '["contactType", "maskedContact"]', 1, 'system', '2026-10-16 10:00:00', NULL, '2026-10-15 23:38:07'),
('COUPON_EXPIRING', 1, 'TRADE', '您有优惠券即将过期', '您有 ${count} 张优惠券即将过期，最近一张将于 ${nearestExpireTime} 失效，别忘了使用哦~', '["count", "nearestExpireTime"]', 1, 'system', '2026-09-14 22:42:48', NULL, '2026-09-14 22:42:48'),
('COUPON_GRANTED', 1, 'MARKETING', '您收到一张优惠券', '您收到一张「${couponName}」。${reason}有效期至 ${validEndTime}，记得在券包里查看。', '["couponName", "reason", "validEndTime"]', 1, 'system', '2026-09-15 18:32:20', NULL, '2026-09-15 18:32:20'),
('DELIVERY_SHIPPED', 1, 'TRADE', '您的商品已发货', '您的订单 ${sourceBizId} 已发货。承运：${logisticsCompany}，运单号：${logisticsNo}。请留意物流信息。', '["sourceBizId", "logisticsCompany", "logisticsNo"]', 1, 'system', '2026-09-14 22:42:48', NULL, '2026-09-14 22:42:48'),
('LEAKED_PASSWORD', 1, 'SYSTEM', '你的登录密码存在风险', '你当前使用的登录密码出现在公开泄露的密码库中，这意味着它很可能已经被收录进撞库字典。你的账号目前没有发现异常，但建议你尽快把密码改成一个没有在别处用过的。我们不会在任何提醒里放链接，请自行打开 App 的「账号与安全」修改密码。', '[]', 1, 'seed', '2026-10-17 10:00:00', NULL, '2026-10-16 20:26:42'),
('MANUAL', 1, 'SYSTEM', '${title}', '${content}', '["title", "content"]', 1, 'system', '2026-09-15 00:04:04', NULL, '2026-09-15 00:04:04'),
('NEW_DEVICE_LOGIN', 1, 'SYSTEM', '新设备登录提醒', '你的账号于 ${loginTime} 在一台新设备（${deviceType}，${location}）上登录。如果不是你本人操作，请立即修改密码，并在「我的登录设备」中下线其他设备。', '["loginTime", "deviceType", "location"]', 1, 'system', '2026-09-27 00:26:16', NULL, '2026-09-27 00:26:16'),
('ORDER_CANCELLED', 1, 'TRADE', '订单已取消，积分已退回', '您的订单 ${orderNo}（${commodityName}）因超时未支付已取消，${refundPoints} 积分已原路退回您的账户。', '["orderNo", "commodityName", "refundPoints"]', 1, 'system', '2026-09-14 22:42:48', NULL, '2026-09-14 22:42:48'),
('ORDER_FULFILL_FAILED', 1, 'TRADE', '兑换未能完成', '很抱歉，您的订单 ${orderNo}（${commodityName}）暂时未能完成兑换：${failReason}。我们会尽快处理，您的权益不受影响。', '["orderNo", "commodityName", "failReason"]', 1, 'system', '2026-09-14 22:42:48', NULL, '2026-09-14 22:42:48'),
('PRIZE_WON', 1, 'MARKETING', '恭喜您中奖啦', '您获得的 ${prizeName} ×${amount} 已发放到您的账户，快去看看吧~', '["prizeName", "amount"]', 1, 'system', '2026-09-14 22:42:48', NULL, '2026-09-14 22:42:48'),
('SUSPICIOUS_LOGIN', 1, 'SYSTEM', '可疑登录已被拦下', '你的账号于 ${loginTime} 在 ${location} 的 ${deviceType} 上有一次可疑的登录尝试，我们没有让它直接进去。能走到这一步说明对方知道你的密码，建议你立即修改密码，并在「我的登录设备」中下线其他设备。', '["loginTime", "deviceType", "location"]', 1, 'system', '2026-10-16 00:24:00', NULL, '2026-10-16 00:24:00');

-- -----------------------------------------------------------------------------------
-- t_mail_template  邮件模板。缺了邮箱验证码发不出去（8 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_mail_template`;
INSERT INTO `t_mail_template` (`template_code`, `template_subject`, `template_content`, `template_type`, `disable_flag`, `update_time`, `create_time`) VALUES
('login_verification_code', '登录验证码', '<!DOCTYPE HTML><html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/></head><body>\n<div style="margin:0 auto;width:690px;font-family:Helvetica,Arial,sans-serif;line-height:28px;">\n  <h2>登录验证码</h2>\n  <p>请在登录页面输入此验证码：</p>\n  <p style="font-size:28px;letter-spacing:6px;"><b>${code}</b></p>\n  <p>验证码将于此邮件发出 5 分钟后过期。</p>\n  <p>如果你未曾提出此请求，可以忽略这封邮件。</p>\n</div></body></html>', 'freemarker', 0, '2026-09-09 04:25:48', '2026-09-09 04:25:48'),
('member_bind_email_code', '邮箱绑定验证码', '<!DOCTYPE HTML><html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/></head><body>\n<div style="margin:0 auto;width:690px;font-family:Helvetica,Arial,sans-serif;line-height:28px;">\n  <h2>邮箱绑定验证码</h2>\n  <p>你正在把这个邮箱<b>绑定到一个账号</b>上。请在绑定页面输入：</p>\n  <p style="font-size:28px;letter-spacing:6px;"><b>${code}</b></p>\n  <p>验证码 ${minutes} 分钟内有效。</p>\n  <p><b>这串数字只用于绑定邮箱。</b>如果有人以别的理由（中奖、退款、客服核验）向你索要它，那是诈骗。</p>\n  <p>如果这不是你本人的操作，忽略这封邮件即可，你的邮箱不会被绑定。</p>\n</div></body></html>', 'freemarker', 0, '2026-09-09 04:25:48', '2026-09-09 04:25:48'),
('member_login_code', '登录验证码', '<!DOCTYPE HTML><html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/></head><body>\n<div style="margin:0 auto;width:690px;font-family:Helvetica,Arial,sans-serif;line-height:28px;">\n  <h2>登录验证码</h2>\n  <p>你正在<b>登录</b>。请在登录页面输入：</p>\n  <p style="font-size:28px;letter-spacing:6px;"><b>${code}</b></p>\n  <p>验证码 ${minutes} 分钟内有效。</p>\n  <p><b>不要把这串数字告诉任何人</b>，包括自称客服的人 —— 我们不会向你索要验证码。</p>\n  <p>如果这不是你本人的操作，可能有人正在尝试登录你的账号，建议尽快修改密码。</p>\n</div></body></html>', 'freemarker', 0, '2026-09-09 04:25:48', '2026-09-09 04:25:48'),
('member_new_device_login', '新设备登录提醒', '<!DOCTYPE HTML><html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/></head><body>\n<div style="margin:0 auto;width:690px;font-family:Helvetica,Arial,sans-serif;line-height:28px;">\n  <h2>新设备登录提醒</h2>\n  <p>你的账号刚刚在一台<b>新设备</b>上登录：</p>\n  <p>时间：${loginTime}<br/>设备：${deviceType}<br/>地点：${location}</p>\n  <p>如果是你本人，无需任何操作。</p>\n  <p style="color:#b00;"><b>如果不是你本人</b>，说明有人知道了你的密码。请立即打开 App 修改密码，并在「我的登录设备」中点「下线其他设备」。</p>\n  <p style="color:#888;font-size:13px;">这封信里没有任何链接。收到带链接、要你点击登录或验证的「安全提醒」，那是钓鱼邮件。</p>\n</div></body></html>', 'freemarker', 0, '2026-09-27 00:26:16', '2026-09-27 00:26:16'),
('member_register_code', '注册验证码', '<!DOCTYPE HTML><html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/></head><body>\n<div style="margin:0 auto;width:690px;font-family:Helvetica,Arial,sans-serif;line-height:28px;">\n  <h2>注册验证码</h2>\n  <p>你正在用这个邮箱<b>注册账号</b>。请在注册页面输入：</p>\n  <p style="font-size:28px;letter-spacing:6px;"><b>${code}</b></p>\n  <p>验证码 ${minutes} 分钟内有效。</p>\n  <p>如果这不是你本人的操作，说明有人填错了邮箱地址，忽略这封邮件即可 —— 不会有账号被创建。</p>\n</div></body></html>', 'freemarker', 0, '2026-09-09 04:25:48', '2026-09-09 04:25:48'),
('member_reset_password_code', '重置密码验证码', '<!DOCTYPE HTML><html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/></head><body>\n<div style="margin:0 auto;width:690px;font-family:Helvetica,Arial,sans-serif;line-height:28px;">\n  <h2>重置密码验证码</h2>\n  <p>有人正在用这个邮箱<b>重置账号密码</b>。请在重置页面输入：</p>\n  <p style="font-size:28px;letter-spacing:6px;"><b>${code}</b></p>\n  <p>验证码 ${minutes} 分钟内有效。</p>\n  <p><b>拿到这串数字就能改掉你的密码。</b>不要告诉任何人，包括自称客服的人。</p>\n  <p style="color:#b00;"><b>如果这不是你本人的操作，请立即登录并修改密码</b> ——\n     有人知道你的邮箱地址，并且正在尝试接管你的账号。</p>\n</div></body></html>', 'freemarker', 0, '2026-09-09 04:25:48', '2026-09-09 04:25:48'),
('member_step_up_code', '账号安全验证码', '<!DOCTYPE HTML><html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/></head><body>\n<div style="margin:0 auto;width:690px;font-family:Helvetica,Arial,sans-serif;line-height:28px;">\n  <h2>账号安全验证码</h2>\n  <p>你的账号正在一台<b>新设备</b>上<b>新增或修改收货地址 / 充值话费</b>。请在页面上输入：</p>\n  <p style="font-size:28px;letter-spacing:6px;"><b>${code}</b></p>\n  <p>验证码 ${minutes} 分钟内有效。验证通过后，这台设备以后做同类操作不再需要验证码。</p>\n  <p><b>这串数字只用于这一次安全验证。</b>如果有人以别的理由（中奖、退款、客服核验）向你索要它，那是诈骗。</p>\n  <p style="color:#b00;"><b>如果这不是你本人的操作，说明有人已经登录了你的账号</b> ——\n     请不要输入这个验证码，立即用「忘记密码」重置密码，所有设备都会被强制下线。</p>\n</div></body></html>', 'freemarker', 0, '2026-09-26 04:54:26', '2026-09-26 04:54:26'),
('member_suspicious_login', '可疑登录已被拦下', '<!DOCTYPE HTML><html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/></head><body>\n<div style="margin:0 auto;width:690px;font-family:Helvetica,Arial,sans-serif;line-height:28px;">\n  <h2>可疑登录已被拦下</h2>\n  <p>你的账号刚刚有一次<b>可疑的登录尝试</b>，我们没有让它直接进去：</p>\n  <p>时间：${loginTime}<br/>设备：${deviceType}<br/>地点：${location}</p>\n  <p style="color:#b00;"><b>能走到这一步，说明对方知道你的密码。</b>请立即打开 App 修改密码，并在「我的登录设备」中点「下线其他设备」。</p>\n  <p>如果这就是你本人（比如换了网络或设备），按页面提示完成验证即可，无需其它操作。</p>\n  <p style="color:#888;font-size:13px;">这封信里没有任何链接。收到带链接、要你点击登录或验证的「安全提醒」，那是钓鱼邮件。</p>\n</div></body></html>', 'freemarker', 0, '2026-10-16 00:24:00', '2026-10-16 00:24:00');

-- -----------------------------------------------------------------------------------
-- t_coupon_template  优惠券模板。缺了发券查不到规则（6 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_coupon_template`;
INSERT INTO `t_coupon_template` (`coupon_code`, `version`, `coupon_name`, `discount_type`, `discount_value`, `min_amount`, `max_discount`, `deduct_target`, `scope_type`, `scope_refs`, `valid_days`, `valid_end_time`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('0ZXXLZ0RZ1', 1, '满100减20优惠券', 'FIXED', 20.00, 100.00, NULL, 'CASH', 'ALL', NULL, 30, NULL, '按券名反推，上线前请运营核对', 1, 'system', '2026-09-15 04:04:30', NULL, '2026-09-15 04:04:30'),
('CU4XN6VTLQ', 1, '满100减20优惠券', 'FIXED', 20.00, 100.00, NULL, 'CASH', 'ALL', NULL, 30, NULL, '按券名反推，上线前请运营核对', 1, 'system', '2026-09-15 04:04:30', NULL, '2026-09-15 04:04:30'),
('PK144782FR', 1, '商城优惠券100', 'FIXED', 100.00, 0.00, NULL, 'SCORE', 'ALL', NULL, 30, NULL, '按券名反推，上线前请运营核对', 1, 'system', '2026-09-15 04:04:30', NULL, '2026-09-15 04:04:30'),
('PNIX3HHMDN', 1, '商城优惠券100', 'FIXED', 100.00, 0.00, NULL, 'SCORE', 'ALL', NULL, 30, NULL, '按券名反推，上线前请运营核对', 1, 'system', '2026-09-15 04:04:30', NULL, '2026-09-15 04:04:30'),
('PP0COUPON1', 1, 'P0-20元券', 'FIXED', 20.00, 0.00, NULL, 'CASH', 'ALL', NULL, 30, NULL, '按券名反推，上线前请运营核对', 1, 'system', '2026-09-15 04:04:30', NULL, '2026-09-15 04:04:30'),
('RECHARGE10', 1, '话费充值满100减10', 'FIXED', 10.00, 100.00, NULL, 'CASH', 'EXTERNAL', '["MOBILE_RECHARGE"]', 30, NULL, '阶段 7 种子：外部场景券的第一张。上线前请运营核对面额与门槛', 1, 'system', '2026-09-15 20:20:40', NULL, '2026-09-15 20:20:40');

-- -----------------------------------------------------------------------------------
-- t_member_grade  会员等级定义。缺了所有人恒为 0 级，且只有告警不报错（5 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_member_grade`;
INSERT INTO `t_member_grade` (`id`, `grade_code`, `grade_name`, `threshold`, `points_discount`, `icon_file_id`, `status`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
(1, 0, '普通会员', 0, 100, NULL, 1, 'seed', '2026-09-18 05:00:41', NULL, '2026-09-23 04:18:46'),
(2, 1, '银卡会员', 1000, 98, NULL, 1, 'seed', '2026-09-18 05:00:41', '管理员', '2026-09-23 04:18:46'),
(3, 2, '金卡会员', 5000, 95, NULL, 1, 'seed', '2026-09-18 05:00:41', NULL, '2026-09-23 04:18:46'),
(4, 3, '白金会员', 20000, 92, NULL, 1, 'seed', '2026-09-18 05:00:41', NULL, '2026-09-23 04:18:46'),
(5, 4, '钻石会员', 60000, 88, NULL, 1, 'seed', '2026-09-18 05:00:41', NULL, '2026-09-23 04:18:46');

-- -----------------------------------------------------------------------------------
-- t_grade_privilege  等级权益【纯展示】。缺了等级页空白，用户不知道自己在保什么（15 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_grade_privilege`;
INSERT INTO `t_grade_privilege` (`id`, `grade_code`, `privilege_code`, `privilege_name`, `description`, `icon_file_id`, `action_url`, `sort`, `status`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
(1, 0, 'PUBLIC_ACTIVITY', '全部公开活动', '参与平台所有公开活动', NULL, NULL, 100, 1, 'seed', '2026-09-20 02:23:14', NULL, '2026-09-20 02:23:14'),
(2, 1, 'EXCLUSIVE_TASK', '银卡专享任务', '解锁仅银卡及以上可做的任务', NULL, NULL, 100, 1, 'seed', '2026-09-20 02:23:14', NULL, '2026-09-20 02:23:14'),
(3, 1, 'ACTIVITY_PRIORITY', '活动优先参与', '热门活动优先开放', NULL, NULL, 90, 1, 'seed', '2026-09-20 02:23:14', NULL, '2026-09-20 02:23:14'),
(4, 2, 'EXCLUSIVE_TASK', '金卡专享任务', '解锁仅金卡及以上可做的任务', NULL, NULL, 100, 1, 'seed', '2026-09-20 02:23:14', NULL, '2026-09-20 02:23:14'),
(5, 2, 'EXCLUSIVE_POOL', '专享奖池', '抽奖走金卡专属奖池', NULL, NULL, 90, 1, 'seed', '2026-09-20 02:23:14', NULL, '2026-09-20 02:23:14'),
(6, 2, 'BIRTHDAY_GIFT', '生日礼', '生日当天领取专属礼包', NULL, NULL, 80, 1, 'seed', '2026-09-20 02:23:14', NULL, '2026-09-20 02:23:14'),
(7, 3, 'EXCLUSIVE_TASK', '白金专享任务', '解锁仅白金及以上可做的任务', NULL, NULL, 100, 1, 'seed', '2026-09-20 02:23:14', NULL, '2026-09-20 02:23:14'),
(8, 3, 'EXCLUSIVE_POOL', '专享奖池', '抽奖走白金专属奖池', NULL, NULL, 90, 1, 'seed', '2026-09-20 02:23:14', NULL, '2026-09-20 02:23:14'),
(9, 3, 'BIRTHDAY_GIFT', '生日礼', '生日当天领取专属礼包', NULL, NULL, 80, 1, 'seed', '2026-09-20 02:23:14', '管理员', '2026-09-21 07:54:37'),
(10, 3, 'MONTHLY_COUPON', '每月专属券', '每月 1 号自动发放专属优惠券', NULL, NULL, 70, 1, 'seed', '2026-09-20 02:23:14', NULL, '2026-09-20 02:23:14'),
(11, 4, 'EXCLUSIVE_TASK', '钻石专享任务', '解锁仅钻石可做的任务', NULL, NULL, 100, 1, 'seed', '2026-09-20 02:23:14', NULL, '2026-09-20 02:23:14'),
(12, 4, 'EXCLUSIVE_POOL', '最高奖池', '抽奖走最高等级奖池', NULL, NULL, 90, 1, 'seed', '2026-09-20 02:23:14', NULL, '2026-09-20 02:23:14'),
(13, 4, 'BIRTHDAY_GIFT', '生日礼', '生日当天领取专属礼包', NULL, NULL, 80, 1, 'seed', '2026-09-20 02:23:14', NULL, '2026-09-20 02:23:14'),
(14, 4, 'MONTHLY_COUPON', '每月专属券', '每月 1 号自动发放专属优惠券', NULL, NULL, 70, 1, 'seed', '2026-09-20 02:23:14', NULL, '2026-09-20 02:23:14'),
(15, 4, 'PRIORITY_SERVICE', '专属客服', '专属通道，优先响应', NULL, NULL, 60, 1, 'seed', '2026-09-20 02:23:14', NULL, '2026-09-20 02:23:14');

-- -----------------------------------------------------------------------------------
-- t_member_risk_rule  风控规则：登录 18 + 注册 2 + 重置 2 + 资产出口 3 + 换绑 3（纯计次的 6 条 2026-10-17 移交网关限流）。缺了 app-biz 启动失败（规则表与代码双向校验）（19 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_member_risk_rule`;
INSERT INTO `t_member_risk_rule` (`id`, `rule_code`, `rule_name`, `event_type`, `phase`, `rule_type`, `direct_decision`, `score`, `rule_group`, `mode`, `bypass_by_trust`, `params`, `description`, `create_time`, `update_time`) VALUES
(1, 'GLOBAL_CAPTCHA', '全局滑块', 1, 1, 1, 2, 0, NULL, 2, 0, NULL, '开着时所有密码登录都要滑块，熟悉网络免滑块不起作用。上线初期开着（= 改造前的行为），之后作为应急档', '2026-10-14 23:19:28', '2026-10-17 19:57:42'),
(4, 'IP_FAIL_LIMIT', 'IP 登录失败', 1, 1, 2, NULL, 0, NULL, 2, 0, '{"tiers": [{"do": 2, "over": 5}, {"do": 6, "over": 20}], "window": "1h"}', '单 IP 每小时登录失败。20 迁自 LoginIpGuard；5 的滑块档在全局滑块关掉后才起作用', '2026-10-14 23:19:28', '2026-10-17 19:57:35'),
(5, 'IP_UNKNOWN_LIMIT', 'IP 试不存在账号', 1, 1, 2, NULL, 0, NULL, 2, 0, '{"tiers": [{"do": 2, "over": 3}, {"do": 6, "over": 10}], "window": "1h"}', '单 IP 每小时尝试不存在的账号。信号最强的一条：真人偶尔记错邮箱，一小时试十个只可能是脚本', '2026-10-14 23:19:28', '2026-10-17 19:57:33'),
(6, 'IP_ACCOUNT_FANOUT', 'IP 尝试的不同账号数', 1, 1, 2, NULL, 0, NULL, 1, 0, '{"tiers": [{"do": 2, "over": 5}, {"do": 6, "over": 30}], "window": "1h"}', '单 IP 每小时尝试过的不同账号（不论存在与否）。撞库特征；出口 NAT 天然账号多，所以第一档是滑块', '2026-10-14 23:19:28', '2026-10-14 23:19:28'),
(9, 'DEVICE_FAIL_LIMIT', '设备登录失败', 1, 1, 2, NULL, 0, NULL, 1, 0, '{"tiers": [{"do": 6, "over": 10}], "window": "1h", "observe": true}', '单设备每小时登录失败，命中生效时进观察档。迁自 DeviceGuard（原 dry-run）', '2026-10-14 23:19:28', '2026-10-14 23:19:28'),
(10, 'PWD_COOLDOWN', '密码阶梯冷却', 1, 1, 2, NULL, 0, NULL, 2, 0, '{"free": 2, "steps": ["60s", "5m", "10m", "60m", "24h"], "idleReset": "24h", "recordFrom": "60m"}', '按身份摘要计数（防枚举），可信设备单独一桶（防恶意锁人），只锁密码通道。取代旧账号锁', '2026-10-14 23:19:28', '2026-10-17 19:57:33'),
(11, 'CAPTCHA_MATRIX', '前置矩阵（熟悉网络免滑块）', 1, 1, 2, NULL, 0, NULL, 2, 0, '{"v4Prefix": 24, "v6Prefix": 64, "recentIps": 10, "lookbackDays": 90}', '默认要滑块；账号存在且 IP 与最近成功登录同网段时放宽。只能放宽不能收紧。全局滑块开着时无效果', '2026-10-14 23:19:28', '2026-10-17 19:57:42'),
(12, 'NO_DEVICE', '没带设备令牌', 1, 1, 3, NULL, 10, NULL, 1, 0, NULL, '老客户端或直接打接口的脚本。弱信号', '2026-10-14 23:19:28', '2026-10-17 19:57:34'),
(13, 'IP_DATACENTER', '机房 IP', 1, 1, 3, NULL, 30, NULL, 1, 0, '{"keywords": ["阿里云", "腾讯云", "华为云", "百度云", "京东云", "Amazon", "Google", "Microsoft", "DigitalOcean"]}', 'ip2region 的 ISP 段命中机房关键字。关键字级，覆盖不全', '2026-10-14 23:19:28', '2026-10-14 23:19:28'),
(14, 'IP_GREYLIST', 'IP 灰名单', 1, 1, 3, NULL, 0, NULL, 1, 0, NULL, 'IP / IP 段在灰名单，加名单条目上设的分', '2026-10-14 23:19:28', '2026-10-14 23:19:28'),
(15, 'DEVICE_OBSERVE', '设备观察档', 1, 2, 1, 3, 0, NULL, 2, 0, NULL, '设备处在观察档，登录要多验一道码。迁自 checkDeviceChallenge', '2026-10-14 23:19:28', '2026-10-14 23:19:28'),
(16, 'DEVICE_FANOUT', '一机多号', 1, 2, 2, NULL, 0, NULL, 1, 0, '{"tiers": [{"do": 6, "over": 3}], "window": "24h", "observe": true, "sliding": true}', '单设备最后一次活动起 24h 内碰过的不同账号。养号、买号的最强信号。迁自 DeviceGuard（原 dry-run）', '2026-10-14 23:19:28', '2026-10-14 23:19:28'),
(17, 'LOGIN_MATRIX', '后置矩阵', 1, 2, 2, NULL, 0, NULL, 2, 0, '{"cells": {"TRUSTED": 1, "NEW_UNFAMILIAR": 3, "NEW_FAMILIAR_ACTIVE": 1, "NEW_FAMILIAR_DORMANT": 3}, "dormantDays": 30}', '设备信任 × IP 熟悉度（宽松口径）× 活跃度。可信设备放行；新设备遇到陌生网络或沉睡回来要 OTP', '2026-10-14 23:19:28', '2026-10-17 19:57:42'),
(18, 'NEW_COUNTRY', '新国家', 1, 2, 3, NULL, 50, 'GEO', 1, 1, '{"minSuccess": 3, "lookbackDays": 90}', '国家不在近 90 天成功登录过的国家里。成功登录少于 3 次不判', '2026-10-14 23:19:28', '2026-10-14 23:19:28'),
(19, 'IMPOSSIBLE_TRAVEL', '不可能旅行', 1, 2, 3, NULL, 40, 'GEO', 1, 0, '{"crossCountryMinutes": 120, "crossProvinceMinutes": 30}', '与上次成功登录跨国且 < 2h，或跨省且 < 30min。可信设备也不跳过：唯一能抓老设备令牌被复制的规则', '2026-10-14 23:19:28', '2026-10-14 23:19:28'),
(20, 'ACCOUNT_FAIL_BURST', '账号失败爆发', 1, 2, 3, NULL, 40, NULL, 1, 1, '{"minIps": 2, "window": "1h", "minFails": 3}', '本账号近 1h 失败 ≥ 3 次且来自 ≥ 2 个 IP。补旧账号锁的盲区：只数次数、不数来源', '2026-10-14 23:19:28', '2026-10-14 23:19:28'),
(31, 'ASSET_DEVICE_FANOUT', '资产出口-一机多号', 5, 1, 2, NULL, 0, NULL, 2, 0, '{"tiers": [{"do": 3, "over": 3}], "window": "1d"}', '一台设备一天替几个不同账号搬东西。登录侧的碰号数数不到这件事（买号的人每个号只登一次）。给 OTP 档所以敢直接生效：误判一次只是多收一封信', '2026-10-16 20:00:00', '2026-10-16 20:00:00'),
(34, 'BIND_DEVICE_FANOUT', '换绑-一机多号', 6, 1, 2, NULL, 0, NULL, 1, 0, '{"tiers": [{"do": 6, "over": 2}], "window": "1d"}', '一台设备一天替几个账号换联系方式。换完原主连找回密码都走不通，只剩 7 天冷静期在挡。新拍的阈值 + 硬拦处置，所以先陪跑', '2026-10-16 21:00:00', '2026-10-16 21:00:00'),
(35, 'CANARY_ACCOUNT', '蜜罐账号', 1, 1, 1, 1, 0, NULL, 2, 0, NULL, '登录的是蜜罐账号（register_source = CANARY）→ IP 与设备罚 24 小时、投安全事件、告警「用户名单可能已泄露」。固定处置是放行：不改变登录的回答，登录照常在密码比对失败，与真实账号输错密码逐字节相同。蜜罐账号由后台接口建，邮箱不进代码与种子。见 docs/架构/网关阶梯限流-技术方案.md §5.1', '2026-10-17 09:36:22', '2026-10-17 09:36:22');

-- -----------------------------------------------------------------------------------
-- t_rate_limit_rule  网关限流规则：9 个场景 × 维度的默认阈值。缺了网关用内置默认值，后台无法调参（28 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_rate_limit_rule`;
INSERT INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES
(1, 1, 'LOGIN', 2, 5, 10, 15, 20, NULL, NULL, 2, '同一登录身份。匿名场景：处罚封顶 30 分钟，老设备豁免（方案 §2.4）', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(2, 1, 'LOGIN', 3, 5, 10, 15, 20, '1d', 30, 2, '单设备。长窗口迁自登录风控 DEVICE_LOGIN_LIMIT（1 天 30 次）', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(3, 1, 'LOGIN', 1, 10, 30, 45, 60, '1h', 60, 2, '单 IP。长窗口迁自登录风控 IP_ATTEMPT_LIMIT（1 小时 60 次）', '2026-10-17 12:00:00', '2026-10-17 19:57:45'),
(4, 1, 'LOGIN', 4, 50, 150, 225, 300, NULL, NULL, 2, 'IP 段（/24、/48）：约为 IP 维度的 5 倍，处罚封顶 30 分钟（方案 §2.5）', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(5, 1, 'REGISTER', 2, 2, 3, 4, 5, NULL, NULL, 2, '同一注册身份', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(6, 1, 'REGISTER', 3, 2, 3, 4, 5, '1d', 2, 2, '单设备。长窗口迁自登录风控 REG_DEVICE_LIMIT（1 天 2 个）', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(7, 1, 'REGISTER', 1, 3, 6, 8, 10, '1h', 10, 2, '单 IP。长窗口迁自登录风控 REG_IP_LIMIT（1 小时 10 次）', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(8, 1, 'REGISTER', 4, 15, 30, 40, 50, NULL, NULL, 2, 'IP 段：约为 IP 维度的 5 倍', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(9, 1, 'CODE_SEND', 2, 2, 4, 5, 6, NULL, NULL, 2, '同一收码目标 / 会员。发码的日配额仍在业务域（VerificationCodeProperties）', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(10, 1, 'CODE_SEND', 3, 2, 4, 5, 6, NULL, NULL, 2, '单设备', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(11, 1, 'CODE_SEND', 1, 3, 8, 12, 15, NULL, NULL, 2, '单 IP', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(12, 1, 'CODE_SEND', 4, 15, 40, 60, 75, NULL, NULL, 2, 'IP 段：约为 IP 维度的 5 倍', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(13, 1, 'PASSWORD_RESET', 2, 3, 8, 10, 12, NULL, NULL, 2, '同一重置身份', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(14, 1, 'PASSWORD_RESET', 3, 3, 8, 10, 12, '1d', 5, 2, '单设备。长窗口迁自登录风控 RESET_DEVICE_LIMIT（1 天 5 次）', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(15, 1, 'PASSWORD_RESET', 1, 5, 15, 20, 30, '1h', 20, 2, '单 IP。长窗口迁自登录风控 RESET_IP_LIMIT（1 小时 20 次）', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(16, 1, 'PASSWORD_RESET', 4, 25, 75, 100, 150, NULL, NULL, 2, 'IP 段：约为 IP 维度的 5 倍', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(17, 1, 'REDEEM', 2, 3, 10, 15, 20, NULL, NULL, 2, '会员。资产流出：兑换、支付、充值、领奖', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(18, 1, 'REDEEM', 1, 10, 40, 60, 100, NULL, NULL, 2, '单 IP', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(19, 1, 'SIGN', 2, 3, 5, 6, 8, NULL, NULL, 2, '会员。签到一天本来就只成功一次', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(20, 1, 'SIGN', 1, 20, 100, 150, 200, NULL, NULL, 2, '单 IP', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(21, 1, 'CAPTCHA', 3, 5, 20, 30, 40, NULL, NULL, 2, '单设备：出图 + 判题', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(22, 1, 'CAPTCHA', 1, 10, 40, 60, 80, NULL, NULL, 2, '单 IP。取代 CaptchaService 里原来的同 IP 每分钟出图 20 张', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(23, 1, 'DEFAULT_WRITE', 2, 10, 100, 150, 200, NULL, NULL, 2, '其余写接口：会员', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(24, 1, 'DEFAULT_WRITE', 3, 10, 100, 150, 200, NULL, NULL, 2, '其余写接口：设备', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(25, 1, 'DEFAULT_WRITE', 1, 30, 500, 750, 1000, NULL, NULL, 2, '其余写接口：IP', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(26, 1, 'DEFAULT_READ', 2, 20, 300, 450, 600, NULL, NULL, 2, '其余读接口：会员。阈值很宽，只挡爬虫级别的量', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(27, 1, 'DEFAULT_READ', 3, 20, 300, 450, 600, NULL, NULL, 2, '其余读接口：设备', '2026-10-17 12:00:00', '2026-10-17 12:00:00'),
(28, 1, 'DEFAULT_READ', 1, 50, 1000, 1500, 2000, NULL, NULL, 2, '其余读接口：IP', '2026-10-17 12:00:00', '2026-10-17 12:00:00');

-- -----------------------------------------------------------------------------------
-- t_table_column  列配置（开发工具，可选）（3 行）
-- -----------------------------------------------------------------------------------
DELETE FROM `t_table_column`;
INSERT INTO `t_table_column` (`table_column_id`, `user_id`, `user_type`, `table_id`, `columns`, `create_time`, `update_time`) VALUES
(7, 1, 1, 50042, '[{"columnKey":"memberName","showFlag":true,"sort":1,"width":120},{"columnKey":"prizeName","showFlag":true,"sort":2,"width":140},{"columnKey":"prizeType","showFlag":true,"sort":3,"width":110},{"columnKey":"prizeValue","showFlag":true,"sort":4,"width":90},{"columnKey":"prizeLevel","showFlag":true,"sort":5,"width":60},{"columnKey":"activityCode","showFlag":true,"sort":6,"width":120},{"columnKey":"approveStatus","showFlag":true,"sort":7,"width":130},{"columnKey":"status","showFlag":true,"sort":8,"width":110},{"columnKey":"failReason","showFlag":false,"sort":9,"width":180},{"columnKey":"externalBizNo","showFlag":true,"sort":10,"width":140},{"columnKey":"createTime","showFlag":true,"sort":11,"width":160},{"columnKey":"action","showFlag":true,"sort":12,"width":110}]', '2026-07-30 21:54:04', '2026-07-30 21:54:04'),
(8, 1, 1, 20009, '[{"columnKey":"jobId","showFlag":false,"sort":1,"width":50},{"columnKey":"jobName","showFlag":true,"sort":2},{"columnKey":"handlerName","showFlag":false,"sort":3},{"columnKey":"triggerType","showFlag":true,"sort":4,"width":110},{"columnKey":"triggerValue","showFlag":true,"sort":5,"width":150},{"columnKey":"lastJob","showFlag":true,"sort":6,"width":180},{"columnKey":"nextJob","showFlag":true,"sort":7,"width":150},{"columnKey":"enabledFlag","showFlag":true,"sort":8,"width":100},{"columnKey":"param","showFlag":false,"sort":9},{"columnKey":"remark","showFlag":true,"sort":10},{"columnKey":"sort","showFlag":false,"sort":11,"width":65},{"columnKey":"updateName","showFlag":true,"sort":12,"width":90},{"columnKey":"updateTime","showFlag":false,"sort":13,"width":150},{"columnKey":"action","showFlag":false,"sort":14,"width":170}]', '2026-08-12 19:05:40', '2026-08-12 19:05:40'),
(9, 1, 1, 50032, '[{"columnKey":"id","showFlag":false,"sort":1},{"columnKey":"memberId","showFlag":false,"sort":3,"width":130},{"columnKey":"memberName","showFlag":true,"sort":4},{"columnKey":"taskConfigId","showFlag":true,"sort":5},{"columnKey":"activityCode","showFlag":true,"sort":6},{"columnKey":"periodKey","showFlag":true,"sort":7},{"columnKey":"validStartTime","showFlag":true,"sort":8},{"columnKey":"validEndTime","showFlag":true,"sort":9},{"columnKey":"currentMetric","showFlag":true,"sort":10},{"columnKey":"status","showFlag":true,"sort":11},{"columnKey":"progressData","showFlag":true,"sort":12},{"columnKey":"ruleSnapshot","showFlag":true,"sort":13},{"columnKey":"prizeSnapshot","showFlag":true,"sort":14},{"columnKey":"completeTime","showFlag":true,"sort":15},{"columnKey":"createBy","showFlag":false,"sort":16},{"columnKey":"createTime","showFlag":true,"sort":17},{"columnKey":"updateBy","showFlag":false,"sort":18},{"columnKey":"updateTime","showFlag":false,"sort":19},{"columnKey":"action","showFlag":true,"sort":20,"width":190}]', '2026-08-22 17:28:22', '2026-08-22 11:08:27');

-- =====================================================================================
-- 会员号发号序列的唯一一行。
--
-- 🔴 这行是【必须】的，不是可选种子数据。t_member_id_seq 是单行表，发号靠
--    UPDATE ... WHERE id = 1 推进水位。这一行不存在时，UPDATE 匹配 0 行、
--    LAST_INSERT_ID() 返回 0，算出的号段是 [-1000, 0) —— 于是【第一个注册的
--    用户】就撞上 BusinessException「会员号已耗尽或序号非法：-1000」，注册全线失败。
--
-- 🔴 本块由 DumpSeedData 【固定输出】，不是从库里 dump 的：
--    开发库的 next_seq 是运行态水位（早就几万了），而基线要的是种子值 0。
--    历史上它是手工粘进基线的，于是每次重新导出都被丢掉一次。
--
--    next_seq=0：第一次批发得到号段 [0, 1000)，起始内部序号 0（MemberIdCodec 接受 [0,CAPACITY)）。
--    step=1000：与 schema 默认值一致，库里的值优先于应用配置。
-- =====================================================================================
DELETE FROM `t_member_id_seq`;
INSERT INTO `t_member_id_seq` (`id`, `next_seq`, `step`) VALUES (1, 0, 1000);

SET FOREIGN_KEY_CHECKS = 1;
