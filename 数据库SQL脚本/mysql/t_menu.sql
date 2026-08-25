/*
 Navicat Premium Dump SQL

 Source Server         : 192.168.1.5
 Source Server Type    : MySQL
 Source Server Version : 80408 (8.4.8)
 Source Host           : 192.168.1.5:3306
 Source Schema         : solvela

 Target Server Type    : MySQL
 Target Server Version : 80408 (8.4.8)
 File Encoding         : 65001

 Date: 16/08/2026 13:33:59
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for t_menu
-- ----------------------------
DROP TABLE IF EXISTS `t_menu`;
CREATE TABLE `t_menu`  (
  `menu_id` bigint NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `menu_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '菜单名称',
  `menu_type` int NOT NULL COMMENT '类型',
  `parent_id` bigint NOT NULL COMMENT '父菜单ID',
  `sort` int NULL DEFAULT NULL COMMENT '显示顺序',
  `path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '路由地址',
  `component` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组件路径',
  `perms_type` int NULL DEFAULT NULL COMMENT '权限类型',
  `api_perms` varchar(5000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '后端权限字符串',
  `web_perms` varchar(5000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '前端权限字符串',
  `icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '菜单图标',
  `context_menu_id` bigint NULL DEFAULT NULL COMMENT '功能点关联菜单ID',
  `frame_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否为外链',
  `frame_url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '外链地址',
  `cache_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否缓存',
  `visible_flag` tinyint(1) NOT NULL DEFAULT 1 COMMENT '显示状态',
  `disabled_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT '禁用状态',
  `deleted_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除状态',
  `create_user_id` bigint NOT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user_id` bigint NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`menu_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 462 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '菜单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_menu
-- ----------------------------
INSERT INTO `t_menu` VALUES (26, '菜单管理', 2, 45, 1, '/menu/list', '/system/menu/menu-list.vue', NULL, NULL, NULL, 'CopyOutlined', NULL, 0, NULL, 1, 1, 0, 0, 2, '2021-08-09 15:04:35', 1, '2026-08-15 23:13:48');
INSERT INTO `t_menu` VALUES (40, '删除', 3, 26, NULL, NULL, NULL, 1, 'system:menu:batchDelete', 'system:menu:batchDelete', NULL, 26, 0, NULL, 0, 1, 0, 0, 1, '2021-08-12 09:45:56', 1, '2023-10-07 18:15:50');
INSERT INTO `t_menu` VALUES (45, '权限管理', 1, 0, 9, '/organization', NULL, NULL, NULL, NULL, 'UserSwitchOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2021-08-12 16:13:27', 1, '2026-08-15 23:13:30');
INSERT INTO `t_menu` VALUES (46, '员工管理', 2, 45, 3, '/organization/employee', '/system/employee/index.vue', NULL, NULL, NULL, 'AuditOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2021-08-12 16:21:50', 1, '2024-07-02 20:15:23');
INSERT INTO `t_menu` VALUES (47, '商品管理', 2, 48, 1, '/erp/goods/list', '/business/erp/goods/goods-list.vue', NULL, NULL, NULL, 'AliwangwangOutlined', NULL, 0, NULL, 1, 1, 0, 0, 1, '2021-08-12 17:58:39', 1, '2023-12-01 19:33:08');
INSERT INTO `t_menu` VALUES (48, '商品管理', 1, 138, 3, '/goods', NULL, NULL, NULL, NULL, 'BarcodeOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2021-08-12 18:02:59', 1, '2024-07-08 13:58:46');
INSERT INTO `t_menu` VALUES (50, '系统配置', 1, 0, 8, '/setting', NULL, NULL, NULL, NULL, 'SettingOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2021-08-13 16:41:33', 1, '2026-08-15 23:13:02');
INSERT INTO `t_menu` VALUES (76, '角色管理', 2, 45, 4, '/organization/role', '/system/role/index.vue', NULL, NULL, NULL, 'SlidersOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2021-08-26 10:31:00', 1, '2024-07-02 20:15:28');
INSERT INTO `t_menu` VALUES (78, '商品分类', 2, 48, 2, '/erp/catalog/goods', '/business/erp/catalog/goods-catalog.vue', NULL, NULL, NULL, 'ApartmentOutlined', NULL, 0, NULL, 1, 1, 0, 0, 1, '2022-05-18 23:34:14', 1, '2023-12-01 19:33:13');
INSERT INTO `t_menu` VALUES (79, '自定义分组', 2, 48, 3, '/erp/catalog/custom', '/business/erp/catalog/custom-catalog.vue', NULL, NULL, NULL, 'AppstoreAddOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-18 23:37:53', 1, '2023-12-01 19:33:16');
INSERT INTO `t_menu` VALUES (81, '用户操作记录', 2, 213, 6, '/support/operate-log/operate-log-list', '/support/operate-log/operate-log-list.vue', NULL, NULL, NULL, 'VideoCameraOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-20 12:37:24', 44, '2024-08-13 14:34:10');
INSERT INTO `t_menu` VALUES (85, '组件演示', 2, 84, NULL, '/demonstration/index', '/support/demonstration/index.vue', NULL, NULL, NULL, 'ClearOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-20 23:16:46', NULL, '2022-05-20 23:16:46');
INSERT INTO `t_menu` VALUES (86, '添加部门', 3, 46, 1, NULL, NULL, 1, 'system:department:add', 'system:department:add', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-26 23:33:37', 1, '2023-10-07 18:26:35');
INSERT INTO `t_menu` VALUES (87, '修改部门', 3, 46, 2, NULL, NULL, 1, 'system:department:update', 'system:department:update', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-26 23:34:11', 1, '2023-10-07 18:26:44');
INSERT INTO `t_menu` VALUES (88, '删除部门', 3, 46, 3, NULL, NULL, 1, 'system:department:delete', 'system:department:delete', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-26 23:34:49', 1, '2023-10-07 18:26:49');
INSERT INTO `t_menu` VALUES (91, '添加员工', 3, 46, NULL, NULL, NULL, 1, 'system:employee:add', 'system:employee:add', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:11:38', 1, '2023-10-07 18:27:46');
INSERT INTO `t_menu` VALUES (92, '编辑员工', 3, 46, NULL, NULL, NULL, 1, 'system:employee:update', 'system:employee:update', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:12:10', 1, '2023-10-07 18:27:49');
INSERT INTO `t_menu` VALUES (93, '禁用启用员工', 3, 46, NULL, NULL, NULL, 1, 'system:employee:disabled', 'system:employee:disabled', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:12:37', 1, '2023-10-07 18:27:53');
INSERT INTO `t_menu` VALUES (94, '调整员工部门', 3, 46, NULL, NULL, NULL, 1, 'system:employee:department:update', 'system:employee:department:update', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:12:59', 1, '2023-10-07 18:27:34');
INSERT INTO `t_menu` VALUES (95, '重置密码', 3, 46, NULL, NULL, NULL, 1, 'system:employee:password:reset', 'system:employee:password:reset', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:13:30', 1, '2023-10-07 18:27:57');
INSERT INTO `t_menu` VALUES (96, '删除员工', 3, 46, NULL, NULL, NULL, 1, 'system:employee:delete', 'system:employee:delete', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:14:08', 1, '2023-10-07 18:28:01');
INSERT INTO `t_menu` VALUES (97, '添加角色', 3, 76, NULL, NULL, NULL, 1, 'system:role:add', 'system:role:add', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:34:00', 1, '2023-10-07 18:42:31');
INSERT INTO `t_menu` VALUES (98, '删除角色', 3, 76, NULL, NULL, NULL, 1, 'system:role:delete', 'system:role:delete', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:34:19', 1, '2023-10-07 18:42:35');
INSERT INTO `t_menu` VALUES (99, '编辑角色', 3, 76, NULL, NULL, NULL, 1, 'system:role:update', 'system:role:update', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:34:55', 1, '2023-10-07 18:42:44');
INSERT INTO `t_menu` VALUES (100, '更新数据范围', 3, 76, NULL, NULL, NULL, 1, 'system:role:dataScope:update', 'system:role:dataScope:update', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:37:03', 1, '2023-10-07 18:41:49');
INSERT INTO `t_menu` VALUES (101, '批量移除员工', 3, 76, NULL, NULL, NULL, 1, 'system:role:employee:batch:delete', 'system:role:employee:batch:delete', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:39:05', 1, '2023-10-07 18:43:32');
INSERT INTO `t_menu` VALUES (102, '移除员工', 3, 76, NULL, NULL, NULL, 1, 'system:role:employee:delete', 'system:role:employee:delete', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:39:21', 1, '2023-10-07 18:43:37');
INSERT INTO `t_menu` VALUES (103, '添加员工', 3, 76, NULL, NULL, NULL, 1, 'system:role:employee:add', 'system:role:employee:add', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:39:38', 1, '2023-10-07 18:44:05');
INSERT INTO `t_menu` VALUES (104, '修改权限', 3, 76, NULL, NULL, NULL, 1, 'system:role:menu:update', 'system:role:menu:update', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:41:55', 1, '2023-10-07 18:44:11');
INSERT INTO `t_menu` VALUES (105, '添加', 3, 26, NULL, NULL, NULL, 1, 'system:menu:add', 'system:menu:add', NULL, 26, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:44:37', 1, '2023-10-07 17:35:35');
INSERT INTO `t_menu` VALUES (106, '编辑', 3, 26, NULL, NULL, NULL, 1, 'system:menu:update', 'system:menu:update', NULL, 26, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 00:44:59', 1, '2023-10-07 17:35:48');
INSERT INTO `t_menu` VALUES (109, '参数配置', 2, 50, 3, '/config/config-list', '/support/config/config-list.vue', NULL, NULL, NULL, 'AntDesignOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 13:34:41', 1, '2022-06-23 16:24:16');
INSERT INTO `t_menu` VALUES (110, '数据字典', 2, 50, 4, '/setting/dict', '/support/dict/index.vue', NULL, NULL, NULL, 'BarcodeOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-05-27 17:53:00', 1, '2022-05-27 18:09:14');
INSERT INTO `t_menu` VALUES (111, '监控服务', 1, 0, 100, '/monitor', NULL, NULL, NULL, NULL, 'BarChartOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-06-17 11:13:23', 1, '2023-11-28 17:43:56');
INSERT INTO `t_menu` VALUES (113, '查询', 3, 112, NULL, NULL, NULL, NULL, NULL, 'ad', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-06-17 11:31:36', NULL, '2022-06-17 11:31:36');
INSERT INTO `t_menu` VALUES (114, '运维工具', 1, 0, 200, NULL, NULL, NULL, NULL, NULL, 'NodeCollapseOutlined', NULL, 0, NULL, 0, 1, 0, 1, 1, '2022-06-20 10:09:16', 1, '2023-12-01 19:36:18');
INSERT INTO `t_menu` VALUES (117, 'Reload', 2, 50, 12, '/hook', '/support/reload/reload-list.vue', NULL, NULL, NULL, 'ReloadOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-06-20 10:16:49', 1, '2023-12-01 19:39:17');
INSERT INTO `t_menu` VALUES (122, '数据库监控', 2, 111, 4, '/support/druid/index', NULL, NULL, NULL, NULL, 'ConsoleSqlOutlined', NULL, 1, 'http://localhost:1024/druid', 1, 1, 0, 0, 1, '2022-06-20 14:49:33', 1, '2023-02-16 19:15:58');
INSERT INTO `t_menu` VALUES (130, '单号管理', 2, 50, 6, '/support/serial-number/serial-number-list', '/support/serial-number/serial-number-list.vue', NULL, NULL, NULL, 'NumberOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-06-24 14:45:22', 1, '2022-06-28 16:23:41');
INSERT INTO `t_menu` VALUES (132, '公告管理', 2, 138, 2, '/oa/notice/notice-list', '/business/oa/notice/notice-list.vue', NULL, NULL, NULL, 'SoundOutlined', NULL, 0, NULL, 1, 1, 0, 0, 1, '2022-06-24 18:23:09', 1, '2024-07-08 13:58:51');
INSERT INTO `t_menu` VALUES (133, '缓存管理', 2, 50, 11, '/support/cache/cache-list', '/support/cache/cache-list.vue', NULL, NULL, NULL, 'BorderInnerOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-06-24 18:52:25', 1, '2023-12-01 19:39:13');
INSERT INTO `t_menu` VALUES (138, '功能Demo', 1, 0, 1000, NULL, NULL, NULL, NULL, NULL, 'BankOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-06-24 20:09:18', 1, '2026-04-04 07:22:43');
INSERT INTO `t_menu` VALUES (142, '公告详情', 2, 132, NULL, '/oa/notice/notice-detail', '/business/oa/notice/notice-detail.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 0, 0, 0, 1, '2022-06-25 16:38:47', 1, '2022-09-14 19:46:17');
INSERT INTO `t_menu` VALUES (143, '登录登出记录', 2, 213, 5, '/support/login-log/login-log-list', '/support/login-log/login-log-list.vue', NULL, NULL, NULL, 'LoginOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-06-28 15:01:38', 44, '2024-08-13 14:33:49');
INSERT INTO `t_menu` VALUES (144, '企业管理', 2, 138, 1, '/oa/enterprise/enterprise-list', '/business/oa/enterprise/enterprise-list.vue', NULL, NULL, NULL, 'ShopOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-09-14 17:00:07', 1, '2024-07-08 13:48:24');
INSERT INTO `t_menu` VALUES (145, '企业详情', 2, 138, NULL, '/oa/enterprise/enterprise-detail', '/business/oa/enterprise/enterprise-detail.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 0, 0, 0, 1, '2022-09-14 18:52:52', 1, '2022-11-22 10:39:07');
INSERT INTO `t_menu` VALUES (147, '帮助文档', 2, 218, 1, '/help-doc/help-doc-manage-list', '/support/help-doc/management/help-doc-manage-list.vue', NULL, NULL, NULL, 'FolderViewOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-09-14 19:59:01', 1, '2023-12-01 19:38:23');
INSERT INTO `t_menu` VALUES (148, '意见反馈', 2, 218, 2, '/feedback/feedback-list', '/support/feedback/feedback-list.vue', NULL, NULL, NULL, 'CoffeeOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-09-14 19:59:52', 1, '2023-12-01 19:38:40');
INSERT INTO `t_menu` VALUES (149, '我的通知', 2, 132, NULL, '/oa/notice/notice-employee-list', '/business/oa/notice/notice-employee-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 0, 0, 0, 1, '2022-09-14 20:29:41', 1, '2022-09-14 20:31:23');
INSERT INTO `t_menu` VALUES (150, '我的通知公告详情', 2, 132, NULL, '/oa/notice/notice-employee-detail', '/business/oa/notice/notice-employee-detail.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 0, 0, 0, 1, '2022-09-14 20:30:25', 1, '2022-09-14 20:31:38');
INSERT INTO `t_menu` VALUES (151, '代码生成', 2, 0, 600, '/support/code-generator', '/support/code-generator/code-generator-list.vue', NULL, NULL, NULL, 'CoffeeOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-09-21 18:25:05', 1, '2022-10-22 11:27:58');
INSERT INTO `t_menu` VALUES (152, '更新日志', 2, 218, 3, '/support/change-log/change-log-list', '/support/change-log/change-log-list.vue', NULL, NULL, NULL, 'HeartOutlined', NULL, 0, NULL, 0, 1, 0, 0, 44, '2022-10-10 10:31:20', 1, '2023-12-01 19:38:51');
INSERT INTO `t_menu` VALUES (153, '清除缓存', 3, 133, NULL, NULL, NULL, 1, 'support:cache:delete', 'support:cache:delete', NULL, 133, 0, NULL, 0, 1, 1, 0, 1, '2022-10-15 22:45:13', 1, '2023-10-07 16:22:29');
INSERT INTO `t_menu` VALUES (154, '获取缓存key', 3, 133, NULL, NULL, NULL, 1, 'support:cache:keys', 'support:cache:keys', NULL, 133, 0, NULL, 0, 1, 1, 0, 1, '2022-10-15 22:45:48', 1, '2023-10-07 16:22:35');
INSERT INTO `t_menu` VALUES (156, '查看结果', 3, 117, NULL, NULL, NULL, 1, 'support:reload:result', 'support:reload:result', NULL, 117, 0, NULL, 0, 1, 0, 0, 1, '2022-10-15 23:17:23', 1, '2023-10-07 14:31:47');
INSERT INTO `t_menu` VALUES (157, '单号生成', 3, 130, NULL, NULL, NULL, 1, 'support:serialNumber:generate', 'support:serialNumber:generate', NULL, 130, 0, NULL, 0, 1, 0, 0, 1, '2022-10-15 23:21:06', 1, '2023-10-07 18:22:46');
INSERT INTO `t_menu` VALUES (158, '生成记录', 3, 130, NULL, NULL, NULL, 1, 'support:serialNumber:record', 'support:serialNumber:record', NULL, 130, 0, NULL, 0, 1, 0, 0, 1, '2022-10-15 23:21:34', 1, '2023-10-07 18:22:55');
INSERT INTO `t_menu` VALUES (159, '查询', 3, 110, NULL, NULL, NULL, 1, 'support:dict:query', 'support:dict:query', NULL, 110, 0, NULL, 0, 1, 0, 0, 1, '2022-10-15 23:23:51', 1, '2025-04-08 19:42:25');
INSERT INTO `t_menu` VALUES (160, '添加', 3, 110, NULL, NULL, NULL, 1, 'support:dict:add', 'support:dict:add', NULL, 110, 0, NULL, 0, 1, 0, 0, 1, '2022-10-15 23:24:05', 1, '2025-04-08 19:43:02');
INSERT INTO `t_menu` VALUES (161, '更新', 3, 110, NULL, NULL, NULL, 1, 'support:dict:update', 'support:dict:update', NULL, 110, 0, NULL, 0, 1, 0, 0, 1, '2022-10-15 23:24:34', 1, '2025-04-08 19:43:34');
INSERT INTO `t_menu` VALUES (162, '删除', 3, 110, NULL, NULL, NULL, 1, 'support:dict:delete', 'support:dict:delete', NULL, 110, 0, NULL, 0, 1, 0, 0, 1, '2022-10-15 23:24:55', 1, '2025-04-08 19:43:52');
INSERT INTO `t_menu` VALUES (163, '新建', 3, 109, NULL, NULL, NULL, 1, 'support:config:add', 'support:config:add', NULL, 109, 0, NULL, 0, 1, 0, 0, 1, '2022-10-15 23:26:56', 1, '2023-10-07 18:16:17');
INSERT INTO `t_menu` VALUES (164, '编辑', 3, 109, NULL, NULL, NULL, 1, 'support:config:update', 'support:config:update', NULL, 109, 0, NULL, 0, 1, 0, 0, 1, '2022-10-15 23:27:07', 1, '2023-10-07 18:16:24');
INSERT INTO `t_menu` VALUES (165, '查询', 3, 47, NULL, NULL, NULL, 1, 'goods:query', 'goods:query', NULL, 47, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 19:55:39', 1, '2023-10-07 13:58:28');
INSERT INTO `t_menu` VALUES (166, '新建', 3, 47, NULL, NULL, NULL, 1, 'goods:add', 'goods:add', NULL, 47, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 19:56:00', 1, '2023-10-07 13:58:32');
INSERT INTO `t_menu` VALUES (167, '批量删除', 3, 47, NULL, NULL, NULL, 1, 'goods:batchDelete', 'goods:batchDelete', NULL, 47, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 19:56:15', 1, '2023-10-07 13:58:35');
INSERT INTO `t_menu` VALUES (168, '查询', 3, 147, 11, NULL, NULL, 1, 'support:helpDoc:query', 'support:helpDoc:query', NULL, 147, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:12:13', 1, '2023-10-07 14:05:49');
INSERT INTO `t_menu` VALUES (169, '新建', 3, 147, 12, NULL, NULL, 1, 'support:helpDoc:add', 'support:helpDoc:add', NULL, 147, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:12:37', 1, '2023-10-07 14:05:56');
INSERT INTO `t_menu` VALUES (170, '新建目录', 3, 147, 1, NULL, NULL, 1, 'support:helpDocCatalog:addCategory', 'support:helpDocCatalog:addCategory', NULL, 147, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:12:57', 1, '2023-10-07 14:06:38');
INSERT INTO `t_menu` VALUES (171, '修改目录', 3, 147, 2, NULL, NULL, 1, 'support:helpDocCatalog:update', 'support:helpDocCatalog:update', NULL, 147, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:13:46', 1, '2023-10-07 14:06:49');
INSERT INTO `t_menu` VALUES (173, '新建', 3, 78, NULL, NULL, NULL, 1, 'category:add', 'category:add', NULL, 78, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:17:02', 1, '2023-10-07 13:54:01');
INSERT INTO `t_menu` VALUES (174, '查询', 3, 78, NULL, NULL, NULL, 1, 'category:tree', 'category:tree', NULL, 78, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:17:22', 1, '2023-10-07 13:54:33');
INSERT INTO `t_menu` VALUES (175, '编辑', 3, 78, NULL, NULL, NULL, 1, 'category:update', 'category:update', NULL, 78, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:17:38', 1, '2023-10-07 13:54:18');
INSERT INTO `t_menu` VALUES (176, '删除', 3, 78, NULL, NULL, NULL, 1, 'category:delete', 'category:delete', NULL, 78, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:17:50', 1, '2023-10-07 13:54:27');
INSERT INTO `t_menu` VALUES (177, '新建', 3, 79, NULL, NULL, NULL, 1, 'category:add', 'custom:category:add', NULL, 78, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:17:02', 1, '2023-10-07 13:57:32');
INSERT INTO `t_menu` VALUES (178, '查询', 3, 79, NULL, NULL, NULL, 1, 'category:tree', 'custom:category:tree', NULL, 78, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:17:22', 1, '2023-10-07 13:57:50');
INSERT INTO `t_menu` VALUES (179, '编辑', 3, 79, NULL, NULL, NULL, 1, 'category:update', 'custom:category:update', NULL, 78, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:17:38', 1, '2023-10-07 13:58:02');
INSERT INTO `t_menu` VALUES (180, '删除', 3, 79, NULL, NULL, NULL, 1, 'category:delete', 'custom:category:delete', NULL, 78, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:17:50', 1, '2023-10-07 13:58:12');
INSERT INTO `t_menu` VALUES (181, '查询', 3, 144, NULL, NULL, NULL, 1, 'oa:enterprise:query', 'oa:enterprise:query', NULL, 144, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:25:14', 1, '2023-10-07 12:00:09');
INSERT INTO `t_menu` VALUES (182, '新建', 3, 144, NULL, NULL, NULL, 1, 'oa:enterprise:add', 'oa:enterprise:add', NULL, 144, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:25:25', 1, '2023-10-07 12:00:17');
INSERT INTO `t_menu` VALUES (183, '编辑', 3, 144, NULL, NULL, NULL, 1, 'oa:enterprise:update', 'oa:enterprise:update', NULL, 144, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:25:36', 1, '2023-10-07 12:00:38');
INSERT INTO `t_menu` VALUES (184, '删除', 3, 144, NULL, NULL, NULL, 1, 'oa:enterprise:delete', 'oa:enterprise:delete', NULL, 144, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:25:53', 1, '2023-10-07 12:00:46');
INSERT INTO `t_menu` VALUES (185, '查询', 3, 132, NULL, NULL, NULL, 1, 'oa:notice:query', 'oa:notice:query', NULL, 132, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:26:38', 1, '2023-10-07 11:43:01');
INSERT INTO `t_menu` VALUES (186, '新建', 3, 132, NULL, NULL, NULL, 1, 'oa:notice:add', 'oa:notice:add', NULL, 132, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:27:04', 1, '2023-10-07 11:43:07');
INSERT INTO `t_menu` VALUES (187, '编辑', 3, 132, NULL, NULL, NULL, 1, 'oa:notice:update', 'oa:notice:update', NULL, 132, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:27:15', 1, '2023-10-07 11:43:12');
INSERT INTO `t_menu` VALUES (188, '删除', 3, 132, NULL, NULL, NULL, 1, 'oa:notice:delete', 'oa:notice:delete', NULL, 132, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:27:23', 1, '2023-10-07 11:43:18');
INSERT INTO `t_menu` VALUES (190, '查询', 3, 152, NULL, NULL, NULL, 1, '', 'support:changeLog:query', NULL, 152, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:28:33', 1, '2023-10-07 14:25:05');
INSERT INTO `t_menu` VALUES (191, '新建', 3, 152, NULL, NULL, NULL, 1, 'support:changeLog:add', 'support:changeLog:add', NULL, 152, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:28:46', 1, '2023-10-07 14:24:15');
INSERT INTO `t_menu` VALUES (192, '批量删除', 3, 152, NULL, NULL, NULL, 1, 'support:changeLog:batchDelete', 'support:changeLog:batchDelete', NULL, 152, 0, NULL, 0, 1, 0, 0, 1, '2022-10-16 20:29:10', 1, '2023-10-07 14:24:22');
INSERT INTO `t_menu` VALUES (193, '文件管理', 2, 50, 20, '/support/file/file-list', '/support/file/file-list.vue', NULL, NULL, NULL, 'FolderOpenOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-10-21 11:26:11', 1, '2022-10-22 11:29:22');
INSERT INTO `t_menu` VALUES (194, '删除', 3, 47, NULL, NULL, NULL, 1, 'goods:delete', 'goods:delete', NULL, 47, 0, NULL, 0, 1, 0, 0, 1, '2022-10-21 20:00:12', 1, '2023-10-07 13:58:39');
INSERT INTO `t_menu` VALUES (195, '修改', 3, 47, NULL, NULL, NULL, 1, 'goods:update', 'goods:update', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-10-21 20:05:23', 1, '2023-10-07 13:58:42');
INSERT INTO `t_menu` VALUES (196, '查看详情', 3, 145, NULL, NULL, NULL, 1, 'oa:enterprise:detail', 'oa:enterprise:detail', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-10-21 20:16:47', 1, '2023-10-07 11:48:59');
INSERT INTO `t_menu` VALUES (198, '删除', 3, 152, NULL, NULL, NULL, 1, 'support:changeLog:delete', 'support:changeLog:delete', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-10-21 20:42:34', 1, '2023-10-07 14:24:32');
INSERT INTO `t_menu` VALUES (199, '查询', 3, 109, NULL, NULL, NULL, 1, 'support:config:query', 'support:config:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-10-21 20:45:14', 1, '2023-10-07 18:16:27');
INSERT INTO `t_menu` VALUES (200, '查询', 3, 193, NULL, NULL, NULL, 1, 'support:file:query', 'support:file:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-10-21 20:47:23', 1, '2023-10-07 18:24:43');
INSERT INTO `t_menu` VALUES (201, '删除', 3, 147, 14, NULL, NULL, 1, 'support:helpDoc:delete', 'support:helpDoc:delete', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-10-21 21:03:20', 1, '2023-10-07 14:07:02');
INSERT INTO `t_menu` VALUES (202, '更新', 3, 147, 13, NULL, NULL, 1, 'support:helpDoc:update', 'support:helpDoc:update', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-10-21 21:03:32', 1, '2023-10-07 14:06:56');
INSERT INTO `t_menu` VALUES (203, '查询', 3, 143, NULL, NULL, NULL, 1, 'support:loginLog:query', 'support:loginLog:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-10-21 21:05:11', 1, '2023-10-07 14:27:23');
INSERT INTO `t_menu` VALUES (204, '查询', 3, 81, NULL, NULL, NULL, 1, 'support:operateLog:query', 'support:operateLog:query', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-10-22 10:33:31', 1, '2023-10-07 14:27:56');
INSERT INTO `t_menu` VALUES (205, '详情', 3, 81, NULL, NULL, NULL, 1, 'support:operateLog:detail', 'support:operateLog:detail', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-10-22 10:33:49', 1, '2023-10-07 14:28:04');
INSERT INTO `t_menu` VALUES (206, '心跳监控', 2, 111, 1, '/support/heart-beat/heart-beat-list', '/support/heart-beat/heart-beat-list.vue', 1, NULL, NULL, 'FallOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-10-22 10:47:03', 1, '2022-10-22 18:32:52');
INSERT INTO `t_menu` VALUES (207, '更新', 3, 152, NULL, NULL, NULL, 1, 'support:changeLog:update', 'support:changeLog:update', NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, '2022-10-22 11:51:32', 1, '2023-10-07 14:24:39');
INSERT INTO `t_menu` VALUES (212, '查询', 3, 117, NULL, NULL, NULL, 1, 'support:reload:query', 'support:reload:query', NULL, NULL, 0, NULL, 1, 1, 1, 0, 1, '2023-10-07 14:31:36', NULL, '2023-10-07 14:31:36');
INSERT INTO `t_menu` VALUES (213, '网络安全', 1, 0, 111, NULL, NULL, 1, NULL, NULL, 'SafetyCertificateOutlined', NULL, 0, NULL, 1, 1, 0, 0, 1, '2023-10-17 19:03:08', 1, '2026-04-18 03:38:58');
INSERT INTO `t_menu` VALUES (214, '登录失败锁定', 2, 213, 4, '/support/login-fail', '/support/login-fail/login-fail-list.vue', 1, NULL, NULL, 'LockOutlined', NULL, 0, NULL, 1, 1, 0, 0, 1, '2023-10-17 19:04:24', 44, '2024-08-13 14:16:26');
INSERT INTO `t_menu` VALUES (215, '接口加解密', 2, 213, 2, '/support/api-encrypt', '/support/api-encrypt/api-encrypt-index.vue', 1, NULL, NULL, 'CodepenCircleOutlined', NULL, 0, NULL, 1, 1, 0, 0, 1, '2023-10-24 11:49:28', 44, '2024-08-13 12:00:14');
INSERT INTO `t_menu` VALUES (216, '导出', 3, 47, NULL, NULL, NULL, 1, 'goods:exportGoods', 'goods:exportGoods', NULL, NULL, 0, NULL, 1, 1, 0, 0, 1, '2023-12-01 19:34:03', NULL, '2023-12-01 19:34:03');
INSERT INTO `t_menu` VALUES (217, '导入', 3, 47, 3, NULL, NULL, 1, 'goods:importGoods', 'goods:importGoods', NULL, NULL, 0, NULL, 1, 1, 0, 0, 1, '2023-12-01 19:34:22', NULL, '2023-12-01 19:34:22');
INSERT INTO `t_menu` VALUES (218, '文档中心', 1, 0, 8, NULL, NULL, 1, NULL, NULL, 'FileSearchOutlined', NULL, 0, NULL, 1, 1, 0, 0, 1, '2023-12-01 19:37:28', 1, '2026-04-18 03:39:16');
INSERT INTO `t_menu` VALUES (219, '部门管理', 2, 45, 1, '/organization/department', '/system/department/department-list.vue', 1, NULL, NULL, 'ApartmentOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2024-06-22 16:40:21', 1, '2024-07-02 20:15:17');
INSERT INTO `t_menu` VALUES (221, '定时任务', 2, 50, 25, '/job/list', '/support/job/job-list.vue', 1, NULL, NULL, 'AppstoreOutlined', NULL, 0, NULL, 1, 1, 0, 0, 2, '2024-06-25 17:57:40', 2, '2024-06-25 19:49:21');
INSERT INTO `t_menu` VALUES (228, '职务管理', 2, 45, 2, '/organization/position', '/system/position/position-list.vue', 1, NULL, NULL, 'ApartmentOutlined', NULL, 0, NULL, 1, 1, 0, 0, 1, '2024-06-29 11:11:09', 1, '2024-07-02 20:15:11');
INSERT INTO `t_menu` VALUES (229, '查询任务', 3, 221, NULL, NULL, NULL, 1, 'support:job:query', 'support:job:query', NULL, 221, 0, NULL, 1, 1, 0, 0, 2, '2024-06-29 11:14:15', 2, '2024-06-29 11:15:00');
INSERT INTO `t_menu` VALUES (230, '更新任务', 3, 221, NULL, NULL, NULL, 1, 'support:job:update', 'support:job:update', NULL, 221, 0, NULL, 1, 1, 0, 0, 2, '2024-06-29 11:15:40', NULL, '2024-06-29 11:15:40');
INSERT INTO `t_menu` VALUES (231, '执行任务', 3, 221, NULL, NULL, NULL, 1, 'support:job:execute', 'support:job:execute', NULL, 221, 0, NULL, 1, 1, 0, 0, 2, '2024-06-29 11:16:03', NULL, '2024-06-29 11:16:03');
INSERT INTO `t_menu` VALUES (232, '查询记录', 3, 221, NULL, NULL, NULL, 1, 'support:job:log:query', 'support:job:log:query', NULL, 221, 0, NULL, 1, 1, 0, 0, 2, '2024-06-29 11:16:37', NULL, '2024-06-29 11:16:37');
INSERT INTO `t_menu` VALUES (233, 'knife4j文档', 2, 218, 4, '/knife4j', NULL, 1, NULL, NULL, 'FileWordOutlined', NULL, 1, 'http://localhost:1024/doc.html', 1, 1, 0, 0, 1, '2024-07-02 20:23:50', 1, '2024-07-08 13:49:15');
INSERT INTO `t_menu` VALUES (234, 'swagger文档', 2, 218, 5, '/swagger', 'http://localhost:1024/swagger-ui/index.html', 1, NULL, NULL, 'ApiOutlined', NULL, 1, 'http://localhost:1024/swagger-ui/index.html', 1, 1, 0, 0, 1, '2024-07-02 20:35:43', 1, '2024-07-08 13:49:26');
INSERT INTO `t_menu` VALUES (250, '三级等保设置', 2, 213, 1, '/support/level3protect/level3-protect-config-index', '/support/level3protect/level3-protect-config-index.vue', 1, NULL, NULL, 'SafetyOutlined', NULL, 0, NULL, 1, 1, 0, 0, 44, '2024-08-13 11:41:02', 44, '2024-08-13 11:58:12');
INSERT INTO `t_menu` VALUES (251, '敏感数据脱敏', 2, 213, 3, '/support/level3protect/data-masking-list', '/support/level3protect/data-masking-list.vue', 1, NULL, NULL, 'FileProtectOutlined', NULL, 0, NULL, 1, 1, 0, 0, 44, '2024-08-13 11:58:00', 44, '2024-08-13 11:59:49');
INSERT INTO `t_menu` VALUES (252, '启用/禁用', 3, 110, NULL, NULL, NULL, 1, 'support:dict:updateDisabled', 'support:dict:updateDisabled', NULL, 110, 0, NULL, 0, 1, 0, 0, 1, '2025-04-08 19:44:12', 1, '2025-04-08 19:46:03');
INSERT INTO `t_menu` VALUES (253, '查询字典数据', 3, 110, NULL, NULL, NULL, 1, 'support:dictData:query', 'support:dictData:query', NULL, 110, 0, NULL, 0, 1, 0, 0, 1, '2025-04-08 19:46:47', NULL, '2025-04-08 19:46:47');
INSERT INTO `t_menu` VALUES (254, '添加字典数据', 3, 110, NULL, NULL, NULL, 1, 'support:dictData:add', 'support:dictData:add', NULL, 110, 0, NULL, 0, 1, 0, 0, 1, '2025-04-08 19:48:00', NULL, '2025-04-08 19:48:00');
INSERT INTO `t_menu` VALUES (255, '更新字典数据', 3, 110, NULL, NULL, NULL, 1, 'support:dictData:update', 'support:dictData:update', NULL, 110, 0, NULL, 0, 1, 0, 0, 1, '2025-04-08 19:48:19', NULL, '2025-04-08 19:48:19');
INSERT INTO `t_menu` VALUES (256, '删除字典数据', 3, 110, NULL, NULL, NULL, 1, 'support:dictData:delete', 'support:dictData:delete', NULL, 110, 0, NULL, 0, 1, 0, 0, 1, '2025-04-08 19:48:38', NULL, '2025-04-08 19:48:38');
INSERT INTO `t_menu` VALUES (257, '启用/禁用字典数据', 3, 110, NULL, NULL, NULL, 1, 'support:dictData:updateDisabled', 'support:dictData:updateDisabled', NULL, 110, 0, NULL, 0, 1, 0, 0, 1, '2025-04-08 19:48:57', NULL, '2025-04-08 19:48:57');
INSERT INTO `t_menu` VALUES (258, '查询企业员工', 3, 145, NULL, NULL, NULL, 1, 'oa:enterprise:queryEmployee', 'oa:enterprise:queryEmployee', NULL, 145, 0, NULL, 0, 1, 0, 0, 75, '2025-04-08 21:11:46', 75, '2025-04-08 21:12:24');
INSERT INTO `t_menu` VALUES (259, '查询银行信息', 3, 145, NULL, NULL, NULL, 1, 'oa:bank:query', 'oa:bank:query', NULL, 145, 0, NULL, 0, 1, 0, 0, 75, '2025-04-08 21:12:40', NULL, '2025-04-08 21:12:40');
INSERT INTO `t_menu` VALUES (260, '查询发票信息', 3, 145, NULL, NULL, NULL, 1, 'oa:invoice:query', 'oa:invoice:query', NULL, 145, 0, NULL, 0, 1, 0, 0, 75, '2025-04-08 21:12:56', NULL, '2025-04-08 21:12:56');
INSERT INTO `t_menu` VALUES (261, '添加企业员工', 3, 145, NULL, NULL, NULL, 1, 'oa:enterprise:addEmployee', 'oa:enterprise:addEmployee', NULL, 145, 0, NULL, 0, 1, 0, 0, 75, '2025-04-08 21:35:34', NULL, '2025-04-08 21:35:34');
INSERT INTO `t_menu` VALUES (262, '删除企业员工', 3, 145, NULL, NULL, NULL, 1, 'oa:enterprise:deleteEmployee', 'oa:enterprise:deleteEmployee', NULL, 145, 0, NULL, 0, 1, 0, 0, 75, '2025-04-08 21:40:17', NULL, '2025-04-08 21:40:17');
INSERT INTO `t_menu` VALUES (263, '添加银行信息', 3, 145, NULL, NULL, NULL, 1, 'oa:bank:add', 'oa:bank:add', NULL, 145, 0, NULL, 0, 1, 0, 0, 75, '2025-04-08 21:45:44', NULL, '2025-04-08 21:45:44');
INSERT INTO `t_menu` VALUES (264, '更新银行信息', 3, 145, NULL, NULL, NULL, 1, 'oa:bank:update', 'oa:bank:update', NULL, 145, 0, NULL, 0, 1, 0, 0, 75, '2025-04-08 21:46:02', NULL, '2025-04-08 21:46:02');
INSERT INTO `t_menu` VALUES (265, '删除银行信息', 3, 145, NULL, NULL, NULL, 1, 'oa:bank:delete', 'oa:bank:delete', NULL, 145, 0, NULL, 0, 1, 0, 0, 75, '2025-04-08 21:46:12', NULL, '2025-04-08 21:46:12');
INSERT INTO `t_menu` VALUES (266, '添加发票信息', 3, 145, NULL, NULL, NULL, 1, 'oa:invoice:add', 'oa:invoice:add', NULL, 145, 0, NULL, 0, 1, 0, 0, 75, '2025-04-08 21:46:30', NULL, '2025-04-08 21:46:30');
INSERT INTO `t_menu` VALUES (267, '更新发票信息', 3, 145, NULL, NULL, NULL, 1, 'oa:invoice:update', 'oa:invoice:update', NULL, 145, 0, NULL, 0, 1, 0, 0, 75, '2025-04-08 21:46:47', NULL, '2025-04-08 21:46:47');
INSERT INTO `t_menu` VALUES (268, '删除发票信息', 3, 145, NULL, NULL, NULL, 1, 'oa:invoice:delete', 'oa:invoice:delete', NULL, 145, 0, NULL, 0, 1, 0, 0, 75, '2025-04-08 21:46:59', NULL, '2025-04-08 21:46:59');
INSERT INTO `t_menu` VALUES (300, '消息管理', 2, 50, 30, '/message', '/support/message/message-list.vue', 1, NULL, NULL, 'MailOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2025-04-09 14:30:04', 1, '2025-04-10 20:19:36');
INSERT INTO `t_menu` VALUES (301, '在线编辑器', 2, 138, NULL, '/code-editor', '/business/code-editor/JsonEditor.vue', 1, NULL, NULL, 'BgColorsOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-03-19 16:23:25', 1, '2026-03-19 16:23:45');
INSERT INTO `t_menu` VALUES (302, '脚本文档', 2, 218, NULL, '/engine-doc', '/support/engine-doc/index.vue', 1, NULL, NULL, 'AuditOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-03-20 16:01:42', 1, '2026-03-20 16:02:05');
INSERT INTO `t_menu` VALUES (303, '会员钱包', 2, 308, 0, '/ledger/member-wallet/list', '/business/ledger/member-wallet/member-wallet-list.vue', 1, NULL, NULL, 'DollarCircleOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-03 16:22:26', 1, '2026-04-18 03:46:03');
INSERT INTO `t_menu` VALUES (304, '查询', 3, 303, NULL, NULL, NULL, 1, 'memberWallet:query', 'memberWallet:query', NULL, 303, 0, NULL, 0, 1, 0, 0, 1, '2026-04-03 16:22:26', NULL, '2026-04-03 16:22:26');
INSERT INTO `t_menu` VALUES (305, '添加', 3, 303, NULL, NULL, NULL, 1, 'memberWallet:add', 'memberWallet:add', NULL, 303, 0, NULL, 0, 1, 0, 0, 1, '2026-04-03 16:22:26', NULL, '2026-04-03 16:22:26');
INSERT INTO `t_menu` VALUES (306, '更新', 3, 303, NULL, NULL, NULL, 1, 'memberWallet:update', 'memberWallet:update', NULL, 303, 0, NULL, 0, 1, 0, 0, 1, '2026-04-03 16:22:26', NULL, '2026-04-03 16:22:26');
INSERT INTO `t_menu` VALUES (307, '删除', 3, 303, NULL, NULL, NULL, 1, 'memberWallet:delete', 'memberWallet:delete', NULL, 303, 0, NULL, 0, 1, 0, 0, 1, '2026-04-03 16:22:26', NULL, '2026-04-03 16:22:26');
INSERT INTO `t_menu` VALUES (308, '财务中心', 1, 0, 0, NULL, NULL, 1, NULL, NULL, 'AreaChartOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-03 16:33:31', 1, '2026-04-04 07:20:01');
INSERT INTO `t_menu` VALUES (309, '会员优惠券', 2, 308, 2, '/ledger/member-coupon/list', '/business/ledger/member-coupon/member-coupon-list.vue', 1, NULL, NULL, 'CopyOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 03:32:18', 1, '2026-04-18 03:46:20');
INSERT INTO `t_menu` VALUES (310, '查询', 3, 309, NULL, NULL, NULL, 1, 'memberCoupon:query', 'memberCoupon:query', NULL, 309, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 03:32:18', NULL, '2026-04-04 03:32:18');
INSERT INTO `t_menu` VALUES (311, '添加', 3, 309, NULL, NULL, NULL, 1, 'memberCoupon:add', 'memberCoupon:add', NULL, 309, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 03:32:18', NULL, '2026-04-04 03:32:18');
INSERT INTO `t_menu` VALUES (312, '更新', 3, 309, NULL, NULL, NULL, 1, 'memberCoupon:update', 'memberCoupon:update', NULL, 309, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 03:32:18', NULL, '2026-04-04 03:32:18');
INSERT INTO `t_menu` VALUES (313, '删除', 3, 309, NULL, NULL, NULL, 1, 'memberCoupon:delete', 'memberCoupon:delete', NULL, 309, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 03:32:18', NULL, '2026-04-04 03:32:18');
INSERT INTO `t_menu` VALUES (314, '交易明细表', 2, 308, 3, '/ledger/member-asset-transaction/list', '/business/ledger/member-asset-transaction/member-asset-transaction-list.vue', 1, NULL, NULL, 'ReadOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:13:51', 1, '2026-04-18 03:46:26');
INSERT INTO `t_menu` VALUES (315, '查询', 3, 314, NULL, NULL, NULL, 1, 'memberAssetTransaction:query', 'memberAssetTransaction:query', NULL, 314, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:13:51', NULL, '2026-04-04 07:13:51');
INSERT INTO `t_menu` VALUES (316, '添加', 3, 314, NULL, NULL, NULL, 1, 'memberAssetTransaction:add', 'memberAssetTransaction:add', NULL, 314, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:13:51', NULL, '2026-04-04 07:13:51');
INSERT INTO `t_menu` VALUES (317, '更新', 3, 314, NULL, NULL, NULL, 1, 'memberAssetTransaction:update', 'memberAssetTransaction:update', NULL, 314, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:13:51', NULL, '2026-04-04 07:13:51');
INSERT INTO `t_menu` VALUES (318, '删除', 3, 314, NULL, NULL, NULL, 1, 'memberAssetTransaction:delete', 'memberAssetTransaction:delete', NULL, 314, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:13:51', NULL, '2026-04-04 07:13:51');
INSERT INTO `t_menu` VALUES (319, '任务中心', 1, 0, 2, NULL, NULL, 1, NULL, NULL, 'AndroidOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:21:54', 1, '2026-04-18 03:49:37');
INSERT INTO `t_menu` VALUES (320, '奖品配置', 2, 370, 1, '/prize/prize-config/list', '/business/prize/prize-config/prize-config-list.vue', 1, NULL, NULL, 'GiftOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:27:14', 1, '2026-04-18 12:17:51');
INSERT INTO `t_menu` VALUES (321, '查询', 3, 320, NULL, NULL, NULL, 1, 'prizeConfig:query', 'prizeConfig:query', NULL, 320, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:27:14', NULL, '2026-04-04 07:27:14');
INSERT INTO `t_menu` VALUES (322, '添加', 3, 320, NULL, NULL, NULL, 1, 'prizeConfig:add', 'prizeConfig:add', NULL, 320, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:27:14', NULL, '2026-04-04 07:27:14');
INSERT INTO `t_menu` VALUES (323, '更新', 3, 320, NULL, NULL, NULL, 1, 'prizeConfig:update', 'prizeConfig:update', NULL, 320, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:27:14', NULL, '2026-04-04 07:27:14');
INSERT INTO `t_menu` VALUES (324, '删除', 3, 320, NULL, NULL, NULL, 1, 'prizeConfig:delete', 'prizeConfig:delete', NULL, 320, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:27:14', NULL, '2026-04-04 07:27:14');
INSERT INTO `t_menu` VALUES (325, '奖励分组', 2, 319, 2, '/marketing/prize-group/list', '/business/marketing/prize-group/prize-group-list.vue', 1, NULL, NULL, 'OrderedListOutlined', NULL, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 07:35:32', 1, '2026-04-18 03:41:55');
INSERT INTO `t_menu` VALUES (326, '查询', 3, 325, NULL, NULL, NULL, 1, 'prizeGroup:query', 'prizeGroup:query', NULL, 325, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 07:35:32', 1, '2026-04-18 03:41:55');
INSERT INTO `t_menu` VALUES (327, '添加', 3, 325, NULL, NULL, NULL, 1, 'prizeGroup:add', 'prizeGroup:add', NULL, 325, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 07:35:32', 1, '2026-04-18 03:41:55');
INSERT INTO `t_menu` VALUES (328, '更新', 3, 325, NULL, NULL, NULL, 1, 'prizeGroup:update', 'prizeGroup:update', NULL, 325, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 07:35:32', 1, '2026-04-18 03:41:55');
INSERT INTO `t_menu` VALUES (329, '删除', 3, 325, NULL, NULL, NULL, 1, 'prizeGroup:delete', 'prizeGroup:delete', NULL, 325, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 07:35:32', 1, '2026-04-18 03:41:55');
INSERT INTO `t_menu` VALUES (330, '奖励记录', 2, 370, 3, '/prize/prize-log/list', '/business/prize/prize-log/prize-log-list.vue', 1, NULL, NULL, 'BarsOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:37:44', 1, '2026-04-18 12:32:38');
INSERT INTO `t_menu` VALUES (331, '查询', 3, 330, NULL, NULL, NULL, 1, 'prizeLog:query', 'prizeLog:query', NULL, 330, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:37:44', NULL, '2026-04-04 07:37:44');
INSERT INTO `t_menu` VALUES (332, '添加', 3, 330, NULL, NULL, NULL, 1, 'prizeLog:add', 'prizeLog:add', NULL, 330, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:37:44', NULL, '2026-04-04 07:37:44');
INSERT INTO `t_menu` VALUES (333, '更新', 3, 330, NULL, NULL, NULL, 1, 'prizeLog:update', 'prizeLog:update', NULL, 330, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:37:44', NULL, '2026-04-04 07:37:44');
INSERT INTO `t_menu` VALUES (334, '删除', 3, 330, NULL, NULL, NULL, 1, 'prizeLog:delete', 'prizeLog:delete', NULL, 330, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:37:44', NULL, '2026-04-04 07:37:44');
INSERT INTO `t_menu` VALUES (335, '奖励包映射', 2, 319, 11, '/marketing/task-prize-mapping/list', '/business/marketing/task-prize-mapping/task-prize-mapping-list.vue', 1, NULL, NULL, 'ApiOutlined', NULL, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 07:45:08', 1, '2026-04-18 03:42:07');
INSERT INTO `t_menu` VALUES (336, '查询', 3, 335, NULL, NULL, NULL, 1, 'taskPrizeMapping:query', 'taskPrizeMapping:query', NULL, 335, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 07:45:08', 1, '2026-04-18 03:42:07');
INSERT INTO `t_menu` VALUES (340, '任务配置表', 2, 319, 4, '/task/task-config/list', '/business/task/task-config/task-config-list.vue', 1, NULL, NULL, 'SettingOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:51:58', 1, '2026-04-18 03:49:09');
INSERT INTO `t_menu` VALUES (341, '查询', 3, 340, NULL, NULL, NULL, 1, 'taskConfig:query', 'taskConfig:query', NULL, 340, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:51:58', NULL, '2026-04-04 07:51:58');
INSERT INTO `t_menu` VALUES (342, '添加', 3, 340, NULL, NULL, NULL, 1, 'taskConfig:add', 'taskConfig:add', NULL, 340, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:51:58', NULL, '2026-04-04 07:51:58');
INSERT INTO `t_menu` VALUES (343, '更新', 3, 340, NULL, NULL, NULL, 1, 'taskConfig:update', 'taskConfig:update', NULL, 340, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:51:58', NULL, '2026-04-04 07:51:58');
INSERT INTO `t_menu` VALUES (344, '删除', 3, 340, NULL, NULL, NULL, 1, 'taskConfig:delete', 'taskConfig:delete', NULL, 340, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:51:58', NULL, '2026-04-04 07:51:58');
INSERT INTO `t_menu` VALUES (345, '任务记录', 2, 319, 5, '/task/task-record/list', '/business/task/task-record/task-record-list.vue', 1, NULL, NULL, 'DatabaseOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:52:31', 1, '2026-04-18 03:49:16');
INSERT INTO `t_menu` VALUES (346, '查询', 3, 345, NULL, NULL, NULL, 1, 'taskRecord:query', 'taskRecord:query', NULL, 345, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:52:31', NULL, '2026-04-04 07:52:31');
INSERT INTO `t_menu` VALUES (347, '添加', 3, 345, NULL, NULL, NULL, 1, 'taskRecord:add', 'taskRecord:add', NULL, 345, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:52:31', NULL, '2026-04-04 07:52:31');
INSERT INTO `t_menu` VALUES (348, '更新', 3, 345, NULL, NULL, NULL, 1, 'taskRecord:update', 'taskRecord:update', NULL, 345, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:52:31', NULL, '2026-04-04 07:52:31');
INSERT INTO `t_menu` VALUES (349, '删除', 3, 345, NULL, NULL, NULL, 1, 'taskRecord:delete', 'taskRecord:delete', NULL, 345, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:52:31', NULL, '2026-04-04 07:52:31');
INSERT INTO `t_menu` VALUES (350, '任务模板', 2, 319, 6, '/task/task-template/list', '/business/task/task-template/task-template-list.vue', 1, NULL, NULL, 'BookOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:53:04', 1, '2026-04-18 03:49:22');
INSERT INTO `t_menu` VALUES (351, '查询', 3, 350, NULL, NULL, NULL, 1, 'taskTemplate:query', 'taskTemplate:query', NULL, 350, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:53:04', NULL, '2026-04-04 07:53:04');
INSERT INTO `t_menu` VALUES (352, '添加', 3, 350, NULL, NULL, NULL, 1, 'taskTemplate:add', 'taskTemplate:add', NULL, 350, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:53:04', NULL, '2026-04-04 07:53:04');
INSERT INTO `t_menu` VALUES (353, '更新', 3, 350, NULL, NULL, NULL, 1, 'taskTemplate:update', 'taskTemplate:update', NULL, 350, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:53:04', NULL, '2026-04-04 07:53:04');
INSERT INTO `t_menu` VALUES (354, '删除', 3, 350, NULL, NULL, NULL, 1, 'taskTemplate:delete', 'taskTemplate:delete', NULL, 350, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 07:53:04', NULL, '2026-04-04 07:53:04');
INSERT INTO `t_menu` VALUES (355, '优惠配置', 2, 371, 7, '/risk/promotion-config/list', '/business/risk/promotion-config/promotion-config-list.vue', 1, NULL, NULL, 'ConsoleSqlOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 08:07:17', 1, '2026-04-18 03:41:35');
INSERT INTO `t_menu` VALUES (356, '查询', 3, 355, NULL, NULL, NULL, 1, 'promotionConfig:query', 'promotionConfig:query', NULL, 355, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 08:07:17', NULL, '2026-04-04 08:07:17');
INSERT INTO `t_menu` VALUES (357, '添加', 3, 355, NULL, NULL, NULL, 1, 'promotionConfig:add', 'promotionConfig:add', NULL, 355, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 08:07:17', NULL, '2026-04-04 08:07:17');
INSERT INTO `t_menu` VALUES (358, '更新', 3, 355, NULL, NULL, NULL, 1, 'promotionConfig:update', 'promotionConfig:update', NULL, 355, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 08:07:17', NULL, '2026-04-04 08:07:17');
INSERT INTO `t_menu` VALUES (359, '删除', 3, 355, NULL, NULL, NULL, 1, 'promotionConfig:delete', 'promotionConfig:delete', NULL, 355, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 08:07:17', NULL, '2026-04-04 08:07:17');
INSERT INTO `t_menu` VALUES (360, '提案', 2, 371, 6, '/risk/promotion-proposal/list', '/business/risk/promotion-proposal/promotion-proposal-list.vue', 1, NULL, NULL, 'FileMarkdownOutlined', NULL, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 08:07:59', 1, '2026-04-18 15:21:28');
INSERT INTO `t_menu` VALUES (361, '查询', 3, 360, NULL, NULL, NULL, 1, 'promotionProposal:query', 'promotionProposal:query', NULL, 360, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 08:07:59', 1, '2026-04-18 15:21:28');
INSERT INTO `t_menu` VALUES (362, '添加', 3, 360, NULL, NULL, NULL, 1, 'promotionProposal:add', 'promotionProposal:add', NULL, 360, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 08:07:59', 1, '2026-04-18 15:21:28');
INSERT INTO `t_menu` VALUES (363, '更新', 3, 360, NULL, NULL, NULL, 1, 'promotionProposal:update', 'promotionProposal:update', NULL, 360, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 08:07:59', 1, '2026-04-18 15:21:28');
INSERT INTO `t_menu` VALUES (364, '删除', 3, 360, NULL, NULL, NULL, 1, 'promotionProposal:delete', 'promotionProposal:delete', NULL, 360, 0, NULL, 0, 1, 0, 1, 1, '2026-04-04 08:07:59', 1, '2026-04-18 15:21:28');
INSERT INTO `t_menu` VALUES (365, '发货物流表', 2, 308, 7, '/ledger/physical-delivery/list', '/business/ledger/physical-delivery/physical-delivery-list.vue', 1, NULL, NULL, 'RotateRightOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 08:18:03', 1, '2026-04-18 03:46:32');
INSERT INTO `t_menu` VALUES (366, '查询', 3, 365, NULL, NULL, NULL, 1, 'physicalDelivery:query', 'physicalDelivery:query', NULL, 365, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 08:18:03', NULL, '2026-04-04 08:18:03');
INSERT INTO `t_menu` VALUES (367, '添加', 3, 365, NULL, NULL, NULL, 1, 'physicalDelivery:add', 'physicalDelivery:add', NULL, 365, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 08:18:03', NULL, '2026-04-04 08:18:03');
INSERT INTO `t_menu` VALUES (368, '更新', 3, 365, NULL, NULL, NULL, 1, 'physicalDelivery:update', 'physicalDelivery:update', NULL, 365, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 08:18:03', NULL, '2026-04-04 08:18:03');
INSERT INTO `t_menu` VALUES (369, '删除', 3, 365, NULL, NULL, NULL, 1, 'physicalDelivery:delete', 'physicalDelivery:delete', NULL, 365, 0, NULL, 0, 1, 0, 0, 1, '2026-04-04 08:18:03', NULL, '2026-04-04 08:18:03');
INSERT INTO `t_menu` VALUES (370, '活动中心', 1, 0, 3, NULL, NULL, 1, NULL, NULL, 'AppstoreOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 03:36:40', NULL, '2026-04-18 03:36:40');
INSERT INTO `t_menu` VALUES (371, '风控管理', 1, 0, 1, NULL, NULL, 1, NULL, NULL, 'BellOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 03:37:28', NULL, '2026-04-18 03:37:28');
INSERT INTO `t_menu` VALUES (372, '活动配置', 2, 370, 0, '/activity/activity-config/list', '/business/activity/activity-config/activity-config-list.vue', 1, NULL, NULL, 'TableOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 11:43:59', 1, '2026-04-18 11:51:18');
INSERT INTO `t_menu` VALUES (373, '查询', 3, 372, NULL, NULL, NULL, 1, 'activityConfig:query', 'activityConfig:query', NULL, 372, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 11:43:59', NULL, '2026-04-18 11:43:59');
INSERT INTO `t_menu` VALUES (374, '添加', 3, 372, NULL, NULL, NULL, 1, 'activityConfig:add', 'activityConfig:add', NULL, 372, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 11:43:59', NULL, '2026-04-18 11:43:59');
INSERT INTO `t_menu` VALUES (375, '更新', 3, 372, NULL, NULL, NULL, 1, 'activityConfig:update', 'activityConfig:update', NULL, 372, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 11:43:59', NULL, '2026-04-18 11:43:59');
INSERT INTO `t_menu` VALUES (376, '删除', 3, 372, NULL, NULL, NULL, 1, 'activityConfig:delete', 'activityConfig:delete', NULL, 372, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 11:43:59', NULL, '2026-04-18 11:43:59');
INSERT INTO `t_menu` VALUES (377, '奖品配置', 2, 0, NULL, '/activity/prize-config/list', '/business/activity/prize-config/prize-config-list.vue', 1, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 1, 1, '2026-04-18 12:13:29', 1, '2026-04-18 12:14:47');
INSERT INTO `t_menu` VALUES (378, '任务奖励', 2, 319, 9, '/prize/task-prize-mapping/list', '/business/prize/task-prize-mapping/task-prize-mapping-list.vue', 1, NULL, NULL, 'BankOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 12:46:22', 1, '2026-04-18 12:50:14');
INSERT INTO `t_menu` VALUES (379, '查询', 3, 378, NULL, NULL, NULL, 1, 'taskPrizeMapping:query', 'taskPrizeMapping:query', NULL, 378, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 12:46:23', NULL, '2026-04-18 12:46:23');
INSERT INTO `t_menu` VALUES (383, '提案记录', 2, 371, 1, '/risk/proposal-record/list', '/business/risk/proposal-record/proposal-record-list.vue', 1, NULL, NULL, 'BarsOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 15:20:56', 1, '2026-08-16 13:08:45');
INSERT INTO `t_menu` VALUES (384, '查询', 3, 383, NULL, NULL, NULL, 1, 'proposalRecord:query', 'proposalRecord:query', NULL, 383, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 15:20:56', NULL, '2026-04-18 15:20:56');
INSERT INTO `t_menu` VALUES (385, '添加', 3, 383, NULL, NULL, NULL, 1, 'proposalRecord:add', 'proposalRecord:add', NULL, 383, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 15:20:56', NULL, '2026-04-18 15:20:56');
INSERT INTO `t_menu` VALUES (386, '更新', 3, 383, NULL, NULL, NULL, 1, 'proposalRecord:update', 'proposalRecord:update', NULL, 383, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 15:20:56', NULL, '2026-04-18 15:20:56');
INSERT INTO `t_menu` VALUES (387, '删除', 3, 383, NULL, NULL, NULL, 1, 'proposalRecord:delete', 'proposalRecord:delete', NULL, 383, 0, NULL, 0, 1, 0, 0, 1, '2026-04-18 15:20:56', NULL, '2026-04-18 15:20:56');
INSERT INTO `t_menu` VALUES (388, '抽奖记录', 2, 393, 99, '/draw/draw-prize-log/list', '/business/draw/draw-prize-log/draw-prize-log-list.vue', 1, NULL, NULL, 'BarsOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 01:27:23', 1, '2026-04-19 01:38:18');
INSERT INTO `t_menu` VALUES (389, '查询', 3, 388, NULL, NULL, NULL, 1, 'drawPrizeLog:query', 'drawPrizeLog:query', NULL, 388, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 01:27:23', NULL, '2026-04-19 01:27:23');
INSERT INTO `t_menu` VALUES (393, '抽奖管理', 1, 0, 7, NULL, NULL, 1, NULL, NULL, 'AppstoreAddOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 01:36:30', NULL, '2026-04-19 01:36:30');
INSERT INTO `t_menu` VALUES (394, '彩票管理', 1, 0, 6, NULL, NULL, 1, NULL, NULL, 'DropboxOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 01:36:53', 1, '2026-04-19 01:37:22');
INSERT INTO `t_menu` VALUES (395, '奖池配置', 2, 393, 0, '/draw/prize-pool-config/list', '/business/draw/prize-pool-config/prize-pool-config-list.vue', 1, NULL, NULL, 'AppstoreOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 01:50:10', 1, '2026-04-19 01:50:56');
INSERT INTO `t_menu` VALUES (396, '查询', 3, 395, NULL, NULL, NULL, 1, 'prizePoolConfig:query', 'prizePoolConfig:query', NULL, 395, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 01:50:10', NULL, '2026-04-19 01:50:10');
INSERT INTO `t_menu` VALUES (398, '更新', 3, 395, NULL, NULL, NULL, 1, 'prizePoolConfig:update', 'prizePoolConfig:update', NULL, 395, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 01:50:10', NULL, '2026-04-19 01:50:10');
INSERT INTO `t_menu` VALUES (400, '奖池奖项', 2, 393, 7, '/draw/prize-pool-item/list', '/business/draw/prize-pool-item/prize-pool-item-list.vue', 1, NULL, NULL, 'BarsOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 01:56:55', 1, '2026-04-19 02:05:44');
INSERT INTO `t_menu` VALUES (401, '查询', 3, 400, NULL, NULL, NULL, 1, 'prizePoolItem:query', 'prizePoolItem:query', NULL, 400, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 01:56:55', NULL, '2026-04-19 01:56:55');
INSERT INTO `t_menu` VALUES (405, '奖池奖项巡检', 2, 393, 7, '/draw/pool-prize-mapping/list', '/business/draw/pool-prize-mapping/pool-prize-mapping-list.vue', 1, NULL, NULL, 'DatabaseOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 02:09:02', 1, '2026-08-16 12:51:59');
INSERT INTO `t_menu` VALUES (406, '查询', 3, 405, NULL, NULL, NULL, 1, 'poolPrizeMapping:query', 'poolPrizeMapping:query', NULL, 405, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 02:09:02', NULL, '2026-04-19 02:09:02');
INSERT INTO `t_menu` VALUES (410, '彩票配置', 2, 394, 0, '/lottery/lottery-config/list', '/business/lottery/lottery-config/lottery-config-list.vue', 1, NULL, NULL, 'AntDesignOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 03:19:12', 1, '2026-04-19 03:22:32');
INSERT INTO `t_menu` VALUES (411, '查询', 3, 410, NULL, NULL, NULL, 1, 'lotteryConfig:query', 'lotteryConfig:query', NULL, 410, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 03:19:12', NULL, '2026-04-19 03:19:12');
INSERT INTO `t_menu` VALUES (413, '更新', 3, 410, NULL, NULL, NULL, 1, 'lotteryConfig:update', 'lotteryConfig:update', NULL, 410, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 03:19:12', NULL, '2026-04-19 03:19:12');
INSERT INTO `t_menu` VALUES (415, '期号巡检', 2, 394, 2, '/lottery/lottery-issue/list', '/business/lottery/lottery-issue/lottery-issue-list.vue', 1, NULL, NULL, 'FieldNumberOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 03:28:57', 1, '2026-08-15 23:42:46');
INSERT INTO `t_menu` VALUES (416, '查询', 3, 415, NULL, NULL, NULL, 1, 'lotteryIssue:query', 'lotteryIssue:query', NULL, 415, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 03:28:57', NULL, '2026-04-19 03:28:57');
INSERT INTO `t_menu` VALUES (417, '添加', 3, 415, NULL, NULL, NULL, 1, 'lotteryIssue:add', 'lotteryIssue:add', NULL, 415, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 03:28:57', NULL, '2026-04-19 03:28:57');
INSERT INTO `t_menu` VALUES (418, '更新', 3, 415, NULL, NULL, NULL, 1, 'lotteryIssue:update', 'lotteryIssue:update', NULL, 415, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 03:28:57', NULL, '2026-04-19 03:28:57');
INSERT INTO `t_menu` VALUES (419, '删除', 3, 415, NULL, NULL, NULL, 1, 'lotteryIssue:delete', 'lotteryIssue:delete', NULL, 415, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 03:28:57', NULL, '2026-04-19 03:28:57');
INSERT INTO `t_menu` VALUES (425, '彩票奖励分析', 2, 394, 4, '/lottery/lottery-prize-rule/list', '/business/lottery/lottery-prize-rule/lottery-prize-rule-list.vue', 1, NULL, NULL, 'CloudServerOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 03:52:47', 1, '2026-08-16 07:39:03');
INSERT INTO `t_menu` VALUES (426, '查询', 3, 425, NULL, NULL, NULL, 1, 'lotteryPrizeRule:query', 'lotteryPrizeRule:query', NULL, 425, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 03:52:47', NULL, '2026-04-19 03:52:47');
INSERT INTO `t_menu` VALUES (430, '用户号码记录', 2, 394, 8, '/lottery/lottery-record/list', '/business/lottery/lottery-record/lottery-record-list.vue', 1, NULL, NULL, 'BarsOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 04:00:02', 1, '2026-04-19 04:09:27');
INSERT INTO `t_menu` VALUES (431, '查询', 3, 430, NULL, NULL, NULL, 1, 'lotteryRecord:query', 'lotteryRecord:query', NULL, 430, 0, NULL, 0, 1, 0, 0, 1, '2026-04-19 04:00:02', NULL, '2026-04-19 04:00:02');
INSERT INTO `t_menu` VALUES (435, '任务配置向导', 2, 319, 0, '/business/task/task-wizard', '/business/task/task-wizard/TaskWizard.vue', 1, NULL, NULL, 'AppstoreAddOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:46', NULL, '2026-08-15 15:05:46');
INSERT INTO `t_menu` VALUES (436, '任务模板设计器', 2, 319, 1, '/business/task/task-template-designer', '/business/task/task-template/TaskTemplateDesigner.vue', 1, NULL, NULL, 'AppstoreAddOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:46', NULL, '2026-08-15 15:05:46');
INSERT INTO `t_menu` VALUES (437, '抽奖工作台', 2, 393, 0, '/business/draw/draw-workbench', '/business/draw/draw-workbench/DrawWorkbench.vue', 1, NULL, NULL, 'AppstoreAddOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:46', NULL, '2026-08-15 15:05:46');
INSERT INTO `t_menu` VALUES (438, '彩票配置工作台', 2, 394, 0, '/business/lottery/lottery-workbench', '/business/lottery/lottery-workbench/LotteryWorkbench.vue', 1, NULL, NULL, 'AppstoreAddOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (439, '查询', 3, 438, NULL, NULL, NULL, 1, 'lotteryConfig:query', 'lotteryConfig:query', NULL, 438, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (440, '保存配置', 3, 438, NULL, NULL, NULL, 1, 'lotteryConfig:update', 'lotteryConfig:update', NULL, 438, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (441, '活动创建向导', 2, 370, -1, '/activity/activity-wizard', '/business/activity/activity-wizard/ActivityWizard.vue', 1, NULL, NULL, 'RocketOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (442, '查询', 3, 441, NULL, NULL, NULL, 1, 'activityConfig:query', 'activityConfig:query', NULL, 441, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (443, '新建活动', 3, 441, NULL, NULL, NULL, 1, 'activityConfig:add', 'activityConfig:add', NULL, 441, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (444, '编辑活动/升级玩法', 3, 441, NULL, NULL, NULL, 1, 'activityConfig:update', 'activityConfig:update', NULL, 441, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (445, '删除活动', 3, 441, NULL, NULL, NULL, 1, 'activityConfig:delete', 'activityConfig:delete', NULL, 441, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (446, '向导提交', 3, 435, NULL, NULL, NULL, 1, 'taskConfig:wizard:submit', 'taskConfig:wizard:submit', NULL, 435, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (447, '设计器保存', 3, 436, NULL, NULL, NULL, 1, 'taskTemplate:save', 'taskTemplate:save', NULL, 436, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (448, '事件上报', 3, 345, NULL, NULL, NULL, 1, 'taskEvent:report', 'taskEvent:report', NULL, 345, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (449, '任务事件注册', 2, 319, 0, '/task/task-event/list', '/business/task/task-event/task-event-list.vue', 1, NULL, NULL, 'ApiOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (450, '查询', 3, 449, NULL, NULL, NULL, 1, 'taskEventDef:query', 'taskEventDef:query', NULL, 449, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (451, '新增', 3, 449, NULL, NULL, NULL, 1, 'taskEventDef:add', 'taskEventDef:add', NULL, 449, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (452, '编辑', 3, 449, NULL, NULL, NULL, 1, 'taskEventDef:update', 'taskEventDef:update', NULL, 449, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (453, '删除', 3, 449, NULL, NULL, NULL, 1, 'taskEventDef:delete', 'taskEventDef:delete', NULL, 449, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (454, '发奖审批', 3, 330, NULL, NULL, NULL, 1, 'prizeLog:approve', 'prizeLog:approve', NULL, 330, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (455, '提案审批', 3, 383, NULL, NULL, NULL, 1, 'proposalRecord:approve', 'proposalRecord:approve', NULL, 383, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (456, '执行抽奖', 3, 388, NULL, NULL, NULL, 1, 'drawPrizeLog:execute', 'drawPrizeLog:execute', NULL, 388, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (457, '工作台保存', 3, 437, NULL, NULL, NULL, 1, 'prizePoolConfig:workbench:save', 'prizePoolConfig:workbench:save', NULL, 437, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (458, '领号与验真', 3, 430, NULL, NULL, NULL, 1, 'lotteryTicket:query', 'lotteryTicket:query', NULL, 430, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (459, '首页·营销大屏', 2, 370, 9, '/home', '/system/home/index.vue', 1, NULL, NULL, 'DashboardOutlined', NULL, 0, NULL, 0, 0, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (460, '统计查询', 3, 459, NULL, NULL, NULL, 1, 'marketingStat:query', 'marketingStat:query', NULL, 459, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');
INSERT INTO `t_menu` VALUES (461, '导入', 3, 365, NULL, NULL, NULL, 1, 'physicalDelivery:import', 'physicalDelivery:import', NULL, 365, 0, NULL, 0, 1, 0, 0, 1, '2026-08-15 15:05:58', NULL, '2026-08-15 15:05:58');

SET FOREIGN_KEY_CHECKS = 1;
