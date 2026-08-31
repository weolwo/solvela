-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文注释会整片乱码）。
SET NAMES utf8mb4;

-- =====================================================================================
-- solvela 全量表结构基线（只有结构，没有数据）
--
-- 🔴 <b>新环境部署：执行本文件即可，不需要再翻 sql-update-log。</b>
--
-- 生成方式：从开发库用 SHOW CREATE TABLE 逐表导出（见 scratchpad/DumpSchema.java），
--          所以它<b>就是库里真实的样子</b>，不是人工维护的近似版本。
--
-- 【为什么要有这个文件】
--   在它出现之前，77 张表的建表语句散落在 6 个 schema 文件 + 49 个版本迁移文件里：
--     · 3 张只存在于迁移日志（t_activity_display / t_file_category / t_file_relation）
--     · 5 张<b>任何文件里都找不到建表语句</b>（t_task_event / t_task_record_flow /
--       t_lottery_number_pool + 两张手工备份表）
--   也就是说「新环境要执行哪些 SQL」这个问题，在今天是<b>没有答案</b>的 ——
--   照 mysql/README.md 说的只跑 smart_admin_v3.sql，会缺掉整个营销域和会员域。
--   这也是铁律 22 的根因：表清单靠 grep 文件永远是不全的。
--
-- 【维护约定（重要，不遵守它这个文件三个月后就又失真了）】
--   改表结构时<b>两个地方都要动</b>：
--     ① 本文件 —— 让新环境建出来就是最新的
--     ② mysql/sql-update-log/vX.sql —— 让已有环境能升上来
--   然后重新跑一次 DumpSchema 覆盖本文件，用 git diff 核对是否与预期一致。
--   🔴 只改迁移不改基线 = 新环境和老环境结构不一样，而且没人会发现。
--
-- 生成时间：2026-08-23（DumpSchema 导出）
-- 最后核对：2026-08-31（手工，见下）
-- 表数量：64 张
--
-- ⚠️ 2026-08-31 核对结果：本文件自 2026-08-23 导出之后【被手工改过】，
--    但头部与分组的计数没跟着改 —— 曾写着 84 张，实际只有 64 张。
--    差的 23 张（另有 2 张是后加的）已逐个查过：t_notice / t_help_doc / t_feedback /
--    t_message / t_oa_* / t_goods / t_category 等 16 张办公内容表，
--    加上 t_change_log / t_heart_beat_record / t_serial_number* / t_reload_* 6 张、
--    t_prize_group 1 张 —— 全仓【零代码引用】，是随功能一起下掉的，不是掉了。
--    本次只修计数，一张表都没动。
--
--    🔴 教训写在这儿：这个文件的价值全在「它就是库里真实的样子」。
--    手工改它而不重新导出，它就退化成一份人工维护的近似版本 —— 正是它当初要取代的东西。
-- =====================================================================================

-- 刻意排除（手工备份表，不属于系统结构）：
--   t_menu_26081523
--   t_menu_2608169


-- =====================================================================================
-- 系统底座（上游 Solvela）（21 张）
-- =====================================================================================

DROP TABLE IF EXISTS `t_employee`;
CREATE TABLE `t_employee` (
  `employee_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `employee_uid` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '员工uuid',
  `login_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '登录帐号',
  `login_pwd` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '登录密码',
  `actual_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '员工名称',
  `avatar` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gender` tinyint(1) NOT NULL DEFAULT '0' COMMENT '性别',
  `phone` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '手机号码',
  `department_id` bigint NOT NULL COMMENT '部门id',
  `position_id` bigint DEFAULT NULL COMMENT '职务ID',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '邮箱',
  `disabled_flag` tinyint unsigned NOT NULL COMMENT '是否被禁用 0否1是',
  `deleted_flag` tinyint unsigned NOT NULL COMMENT '是否删除0否 1是',
  `administrator_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否为超级管理员: 0 不是，1是',
  `remark` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`employee_id`) USING BTREE,
  UNIQUE KEY `employee_uid_index` (`employee_uid`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='员工表';

DROP TABLE IF EXISTS `t_department`;
CREATE TABLE `t_department` (
  `department_id` bigint NOT NULL AUTO_INCREMENT COMMENT '部门主键id',
  `department_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '部门名称',
  `manager_id` bigint DEFAULT NULL COMMENT '部门负责人id',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '部门的父级id',
  `sort` int NOT NULL COMMENT '部门排序',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`department_id`) USING BTREE,
  KEY `parent_id` (`parent_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='部门';

DROP TABLE IF EXISTS `t_position`;
CREATE TABLE `t_position` (
  `position_id` bigint NOT NULL AUTO_INCREMENT COMMENT '职务ID',
  `position_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '职务名称',
  `position_level` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '职级',
  `sort` int DEFAULT '0' COMMENT '排序',
  `remark` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `deleted_flag` tinyint(1) DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`position_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='职务表';

DROP TABLE IF EXISTS `t_role`;
CREATE TABLE `t_role` (
  `role_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色名称',
  `role_code` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '角色编码',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '角色描述',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '创建时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`role_id`) USING BTREE,
  UNIQUE KEY `role_code_uni` (`role_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='角色表';

DROP TABLE IF EXISTS `t_role_employee`;
CREATE TABLE `t_role_employee` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL COMMENT '角色id',
  `employee_id` bigint NOT NULL COMMENT '员工id',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_role_employee` (`role_id`,`employee_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='角色员工功能表';

DROP TABLE IF EXISTS `t_role_menu`;
CREATE TABLE `t_role_menu` (
  `role_menu_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `role_id` bigint NOT NULL COMMENT '角色id',
  `menu_id` bigint NOT NULL COMMENT '菜单id',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`role_menu_id`) USING BTREE,
  KEY `idx_role_id` (`role_id`) USING BTREE,
  KEY `idx_menu_id` (`menu_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='角色-菜单\n';

DROP TABLE IF EXISTS `t_role_data_scope`;
CREATE TABLE `t_role_data_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `data_scope_type` int NOT NULL COMMENT '数据范围类型',
  `view_type` int NOT NULL COMMENT '数据可见范围类型',
  `role_id` bigint NOT NULL COMMENT '角色id',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='角色的数据范围';

DROP TABLE IF EXISTS `t_menu`;
CREATE TABLE `t_menu` (
  `menu_id` bigint NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `menu_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '菜单名称',
  `menu_type` int NOT NULL COMMENT '类型',
  `parent_id` bigint NOT NULL COMMENT '父菜单ID',
  `sort` int DEFAULT NULL COMMENT '显示顺序',
  `path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '路由地址',
  `component` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '组件路径',
  `perms_type` int DEFAULT NULL COMMENT '权限类型',
  `api_perms` varchar(5000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '后端权限字符串',
  `web_perms` varchar(5000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '前端权限字符串',
  `icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '菜单图标',
  `context_menu_id` bigint DEFAULT NULL COMMENT '功能点关联菜单ID',
  `frame_flag` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否为外链',
  `frame_url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '外链地址',
  `cache_flag` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否缓存',
  `visible_flag` tinyint(1) NOT NULL DEFAULT '1' COMMENT '显示状态',
  `disabled_flag` tinyint(1) NOT NULL DEFAULT '0' COMMENT '禁用状态',
  `deleted_flag` tinyint(1) NOT NULL DEFAULT '0' COMMENT '删除状态',
  `create_user_id` bigint NOT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user_id` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`menu_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='菜单表';

DROP TABLE IF EXISTS `t_login_log`;
CREATE TABLE `t_login_log` (
  `login_log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` int NOT NULL COMMENT '用户id',
  `user_type` int NOT NULL COMMENT '用户类型',
  `user_name` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户名',
  `login_ip` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户ip',
  `login_ip_region` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户ip地区',
  `user_agent` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT 'user-agent信息',
  `login_device` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '登录设备',
  `login_result` int NOT NULL COMMENT '登录结果：0成功 1失败 2 退出',
  `remark` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`login_log_id`) USING BTREE,
  KEY `customer_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='用户登录日志';

DROP TABLE IF EXISTS `t_login_fail`;
CREATE TABLE `t_login_fail` (
  `login_fail_id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `user_type` int NOT NULL COMMENT '用户类型',
  `login_name` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '登录名',
  `login_fail_count` int DEFAULT NULL COMMENT '连续登录失败次数',
  `lock_flag` tinyint DEFAULT '0' COMMENT '锁定状态:1锁定，0未锁定',
  `login_lock_begin_time` datetime DEFAULT NULL COMMENT '连续登录失败锁定开始时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`login_fail_id`) USING BTREE,
  UNIQUE KEY `uid_and_utype` (`user_id`,`user_type`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='登录失败次数记录表';

DROP TABLE IF EXISTS `t_password_log`;
CREATE TABLE `t_password_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `user_type` tinyint NOT NULL COMMENT '用户类型',
  `old_password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '旧密码',
  `new_password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '新密码',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '创建时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `user_and_type_index` (`user_id`,`user_type`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='密码修改记录';

DROP TABLE IF EXISTS `t_operate_log`;
CREATE TABLE `t_operate_log` (
  `operate_log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `operate_user_id` bigint NOT NULL COMMENT '用户id',
  `operate_user_type` int NOT NULL COMMENT '用户类型',
  `operate_user_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户名称',
  `module` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作模块',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '操作内容',
  `url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '请求路径',
  `method` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '请求方法',
  `param` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '请求参数',
  `response` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '返回值',
  `ip` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '请求ip',
  `ip_region` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '请求ip地区',
  `user_agent` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '请求user-agent',
  `success_flag` tinyint DEFAULT NULL COMMENT '请求结果 0失败 1成功',
  `fail_reason` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '失败原因',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`operate_log_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='操作记录';

DROP TABLE IF EXISTS `t_data_tracer`;
CREATE TABLE `t_data_tracer` (
  `data_tracer_id` bigint NOT NULL AUTO_INCREMENT,
  `data_id` bigint NOT NULL COMMENT '各种单据的id',
  `type` int NOT NULL COMMENT '单据类型',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '操作内容',
  `diff_old` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '差异：旧的数据',
  `diff_new` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '差异：新的数据',
  `extra_data` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '额外信息',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `user_type` int NOT NULL COMMENT '用户类型：1 后管用户 ',
  `user_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户名称',
  `ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'ip',
  `ip_region` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'ip地区',
  `user_agent` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户ua',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`data_tracer_id`) USING BTREE,
  KEY `order_id_order_type` (`data_id`,`type`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='各种单据操作记录';

DROP TABLE IF EXISTS `t_config`;
CREATE TABLE `t_config` (
  `config_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `config_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '参数名字',
  `config_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '参数key',
  `config_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '上次修改时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`config_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='系统配置';

DROP TABLE IF EXISTS `t_dict`;
CREATE TABLE `t_dict` (
  `dict_id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典id',
  `dict_name` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典名字',
  `dict_code` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典编码',
  `remark` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字典备注',
  `disabled_flag` tinyint NOT NULL DEFAULT '0' COMMENT '禁用状态',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`dict_id`),
  UNIQUE KEY `unique_code` (`dict_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典表';

DROP TABLE IF EXISTS `t_dict_data`;
CREATE TABLE `t_dict_data` (
  `dict_data_id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典数据id',
  `dict_id` bigint NOT NULL COMMENT '字典id',
  `data_value` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典项值',
  `data_label` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典项显示名称',
  `data_style` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字典项样式',
  `remark` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `sort_order` int NOT NULL COMMENT '排序（越大越靠前）',
  `disabled_flag` tinyint NOT NULL DEFAULT '0' COMMENT '禁用状态',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`dict_data_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典数据表';

DROP TABLE IF EXISTS `t_solvela_job`;
CREATE TABLE `t_solvela_job`
(
  `job_id` int NOT NULL AUTO_INCREMENT COMMENT '任务id',
  `job_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务编码：10位大写字母+数字，全局唯一',
  `job_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `handler_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '执行器名称，对应 @SolvelaJobHandler#name()',
  `job_group` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'BUSINESS' COMMENT '分组：SYSTEM/DATA/ACTIVITY/OPS/BUSINESS',
  `trigger_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发类型',
  `trigger_value` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发配置',
  `next_trigger_time` datetime DEFAULT NULL COMMENT '下次触发时间：调度的唯一真源，由数据库时钟产生',
  `prev_trigger_time` datetime DEFAULT NULL COMMENT '上次触发时间',
  `trigger_version` bigint NOT NULL DEFAULT '0' COMMENT '抢占乐观锁版本号',
  `jitter_seconds` int NOT NULL DEFAULT '0' COMMENT '打散秒数：按 job_id 确定性偏移，防整点惊群；ONE_TIME 强制 0',
  `enabled_flag` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否开启',
  `param` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '参数',
  `preset_code` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'NORMAL' COMMENT '预设档位：LIGHT/NORMAL/HEAVY/CUSTOM，仅记录来源，落库的是展开后的值',
  `timeout_seconds` int NOT NULL DEFAULT '0' COMMENT '超时秒数，0=取执行器声明值',
  `retry_times` int NOT NULL DEFAULT '0' COMMENT '失败重试次数，仅幂等执行器允许 > 0',
  `retry_interval` int NOT NULL DEFAULT '30' COMMENT '重试间隔秒数',
  `misfire_strategy` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'SKIP' COMMENT '错过调度策略：SKIP/FIRE_ONCE',
  `misfire_threshold_sec` int NOT NULL DEFAULT '300' COMMENT '判定错过调度的阈值秒数，随档位联动',
  `block_strategy` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DISCARD' COMMENT '阻塞策略：DISCARD/SERIAL/OVERRIDE',
  `last_execute_time` datetime DEFAULT NULL COMMENT '最后一次执行时间',
  `last_execute_log_id` int DEFAULT NULL COMMENT '最后一次执行记录id',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '描述',
  `deleted_flag` tinyint(1) NOT NULL DEFAULT '0' COMMENT '删除状态',
  `update_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '更新人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `app_env` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'dev' COMMENT '环境标识：只有 env 匹配的节点才会抢这个任务',
  `alarm_receiver` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '告警接收人，多个逗号分隔',
  `continuous_fail_count` int NOT NULL DEFAULT '0' COMMENT '连续失败次数，成功时清零；用于告警阈值',
  `handler_missing_flag` tinyint NOT NULL DEFAULT '0' COMMENT '执行器在代码中不存在：该任务不会被执行，列表需标红',
  `terminal_flag` tinyint NOT NULL DEFAULT '0' COMMENT 'ONE_TIME 任务执行完置 1，列表默认折叠',
  `owner_biz_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '归属业务类型：SYSTEM/ACTIVITY（第三档用）',
  `owner_biz_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '归属业务编码，如活动编码（第三档用）',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL 人工创建 / SYSTEM 向导生成（第三档用）',
  `manual_modified_flag` tinyint NOT NULL DEFAULT '0' COMMENT '衍生任务是否被人工改过：改过的向导不再覆盖（第三档用）',
  PRIMARY KEY (`job_id`) USING BTREE,
  UNIQUE KEY `uk_job_code` (`job_code`),
  KEY `idx_handler_name` (`handler_name`),
  KEY `idx_next_trigger` (`app_env`,`enabled_flag`,`deleted_flag`,`next_trigger_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='定时任务配置 @listen';

DROP TABLE IF EXISTS `t_solvela_job_log`;
CREATE TABLE `t_solvela_job_log`
(
  `log_id` bigint NOT NULL AUTO_INCREMENT,
  `job_id` int NOT NULL COMMENT '任务id',
  `job_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `app_env` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'dev' COMMENT '环境标识：冗余列，避免日志表扫描每秒 join t_solvela_job',
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '链路追踪id',
  `trigger_source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'SCHEDULE' COMMENT '触发来源：SCHEDULE 定时 / MANUAL 手动。无 RETRY —— 重试继承原值，靠 retry_seq 区分',
  `trigger_time` datetime NOT NULL COMMENT '本次调度的原定触发时刻（不是执行时刻）',
  `retry_seq` int NOT NULL DEFAULT '0' COMMENT '同一触发点的第几次尝试，0 为首次',
  `biz_date` date DEFAULT NULL COMMENT '业务日期：正常调度=触发日+bizDateOffset，重跑时可指定历史日期',
  `param_snapshot` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '执行时的参数快照（不是当前配置）：重试与重跑都复现这一份',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '执行状态：0-待执行 1-执行中 2-成功 3-失败 4-超时中断 5-阻塞丢弃 6-错过调度 7-中断',
  `execute_start_time` datetime DEFAULT NULL COMMENT '开始执行时间。PENDING（待执行/待重试）记录为 NULL —— 它还没开始',
  `execute_time_millis` int DEFAULT NULL COMMENT '执行时长',
  `execute_end_time` datetime DEFAULT NULL COMMENT '执行结束时间',
  `result_summary` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '执行结果摘要：执行器返回的人话，给运营看',
  `error_detail` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '失败时的异常堆栈（已截断）',
  `ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '执行节点 ip。抢占到该记录时才知道，PENDING 为 NULL',
  `process_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '执行进程 id，PENDING 为 NULL',
  `program_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '执行程序目录，PENDING 为 NULL',
  `create_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fire_time` datetime DEFAULT NULL COMMENT '何时该被捞起执行。仅 PENDING 有值，其余状态恒 NULL',
  `retry_of_log_id` bigint DEFAULT NULL COMMENT '本次是哪条记录的重试',
  `schedule_delay_ms` bigint DEFAULT NULL COMMENT '调度延迟 = 实际开始 - 原定触发。持续增长即为扩容信号',
  PRIMARY KEY (`log_id`) USING BTREE,
  UNIQUE KEY `uk_job_trigger` (`job_id`,`trigger_time`,`retry_seq`,`trigger_source`),
  KEY `idx_job_id` (`job_id`) USING BTREE,
  KEY `idx_job_time` (`job_id`,`execute_start_time`),
  KEY `idx_pending` (`app_env`,`status`,`fire_time`),
  KEY `idx_running` (`status`,`execute_start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='定时任务-执行记录 @listen';

DROP TABLE IF EXISTS `t_table_column`;
CREATE TABLE `t_table_column` (
  `table_column_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户id',
  `user_type` int NOT NULL COMMENT '用户类型',
  `table_id` int NOT NULL COMMENT '表格id',
  `columns` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '具体的表格列，存入的json',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`table_column_id`) USING BTREE,
  UNIQUE KEY `uni_employee_table` (`user_id`,`table_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='表格的自定义列存储';

DROP TABLE IF EXISTS `t_code_generator_config`;
CREATE TABLE `t_code_generator_config` (
  `table_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '表名',
  `basic` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '基础命名信息',
  `fields` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '字段列表',
  `insert_and_update` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '新建、修改',
  `delete_info` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '删除',
  `query_fields` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '查询',
  `table_fields` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '列表',
  `detail` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '详情',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`table_name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='代码生成器的每个表的配置';

DROP TABLE IF EXISTS `t_mail_template`;
CREATE TABLE `t_mail_template` (
  `template_code` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `template_subject` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板名称',
  `template_content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板内容',
  `template_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '解析类型 string，freemarker',
  `disable_flag` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否禁用',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '创建时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`template_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC;


-- =====================================================================================
-- 文件 / 素材库（3 张）
-- =====================================================================================

DROP TABLE IF EXISTS `t_file`;
CREATE TABLE `t_file` (
  `file_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `category_id` bigint NOT NULL COMMENT '分类',
  `original_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户上传时的原始文件名',
  `file_size` bigint DEFAULT NULL COMMENT '字节数',
  `storage_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '存储键，系统生成，不可变',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `storage_kind` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'LOCAL' COMMENT '存储介质：LOCAL / S3',
  `content_type` varchar(100) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'application/octet-stream' COMMENT '嗅探出的真实MIME',
  `extension` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '扩展名，从嗅探MIME反推',
  `content_hash` char(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'SHA-256，去重预留',
  `image_width` int DEFAULT NULL COMMENT '图片宽，非图片为NULL',
  `image_height` int DEFAULT NULL COMMENT '图片高',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1临时 2已确认',
  `tags` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '标签，前后各带逗号：,双十一,banner,',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '删除标记',
  `create_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人（用户名）',
  `update_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人（用户名）',
  PRIMARY KEY (`file_id`) USING BTREE,
  UNIQUE KEY `uk_file_key` (`storage_key`) USING BTREE,
  KEY `idx_category_time` (`category_id`,`create_time` DESC),
  KEY `idx_status_time` (`status`,`create_time`),
  KEY `idx_hash` (`content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='文件';

DROP TABLE IF EXISTS `t_file_category`;
CREATE TABLE `t_file_category` (
  `category_id` bigint NOT NULL AUTO_INCREMENT,
  `category_code` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '稳定标识，代码引用它而非ID',
  `category_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '显示名称，可随时改',
  `category_tag` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '标签，选择器里分组用',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序，小的在前',
  `create_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人（用户名）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人（用户名）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `uk_code` (`category_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文件分类';

DROP TABLE IF EXISTS `t_file_relation`;
CREATE TABLE `t_file_relation` (
  `relation_id` bigint NOT NULL AUTO_INCREMENT,
  `file_id` bigint NOT NULL,
  `biz_type` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '业务类型：NOTICE / HELP_DOC / ACTIVITY_DISPLAY / ...',
  `biz_id` bigint NOT NULL,
  `sort` int NOT NULL DEFAULT '0' COMMENT '附件顺序，轮播图必需',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`relation_id`),
  UNIQUE KEY `uk_biz_file` (`biz_type`,`biz_id`,`file_id`),
  KEY `idx_file` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文件业务关联';


-- =====================================================================================
-- 会员域（5 张）
-- =====================================================================================

DROP TABLE IF EXISTS `t_member`;
CREATE TABLE `t_member` (
  `member_id` bigint NOT NULL COMMENT '会员号：10位数字(1000000000~9999999999)。全链路关联键+迁移锚点，永不可变',
  `member_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '账号：微信号风格，字母开头6~20位[A-Za-z][A-Za-z0-9_-]。全局唯一(大小写不敏感)，用户可改',
  `name_update_time` datetime DEFAULT NULL COMMENT '上次修改账号的时间：改名限频判据(建议一年一次)。为空表示从未改过',
  `nickname` varchar(64) NOT NULL COMMENT '昵称：中文随意，用户可改。?任何地方都不许拿它做关联键',
  `avatar_file_id` bigint DEFAULT NULL COMMENT '头像 file_id（走文件模块，同商城图片）',
  `gender` tinyint NOT NULL DEFAULT '0' COMMENT '性别：0-未知, 1-男, 2-女',
  `birthday` date DEFAULT NULL COMMENT '生日：生日营销用，可空',
  `phone` varchar(255) DEFAULT NULL COMMENT '手机号密文（AES/SM4，密钥走配置）',
  `phone_hash` binary(32) DEFAULT NULL COMMENT '手机号HMAC-SHA256原始字节(32B)：唯一约束与登录查询走它。注销时置NULL以释放号码。查看用HEX()',
  `email` varchar(255) DEFAULT NULL COMMENT '邮箱密文，可空',
  `email_hash` binary(32) DEFAULT NULL COMMENT '邮箱HMAC-SHA256原始字节(32B)，可空',
  `password` varchar(255) DEFAULT NULL COMMENT '登录密码：Argon2id PHC串(盐已内嵌，不要再开salt列)。验证码登录可为空',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-正常, 2-冻结(风控/违规), 3-已注销',
  `register_source` varchar(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT '注册来源渠道：H5/APP/WECHAT/INVITE/IMPORT...',
  `register_ip` varchar(64) DEFAULT NULL COMMENT '注册IP：批量注册的识别依据',
  `invite_id` bigint DEFAULT NULL COMMENT '邀请人member_id：没有邀请体系时恒为空，留着比事后加表便宜',
  `remark` varchar(255) DEFAULT NULL COMMENT '运营备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人：后台导入时有值，自主注册为空',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`member_id`),
  UNIQUE KEY `uk_mbr_name` (`member_name`),
  UNIQUE KEY `uk_mbr_phone_hash` (`phone_hash`),
  UNIQUE KEY `uk_mbr_email_hash` (`email_hash`),
  KEY `idx_mbr_status_time` (`status`,`create_time`),
  KEY `idx_mbr_source_time` (`register_source`,`create_time`),
  KEY `idx_mbr_invite` (`invite_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员主表';

DROP TABLE IF EXISTS `t_member_operation_limit`;
CREATE TABLE `t_member_operation_limit`
(
    `id`             bigint   NOT NULL AUTO_INCREMENT COMMENT '自增id',
    `member_id`      bigint   NOT NULL COMMENT '会员号：关联键',
    `operation_type` int      NOT NULL COMMENT '受限操作：1-登录, 2-修改密码。见 MemberOperationTypeEnum',
    `lock_time`      datetime NOT NULL COMMENT '冻结开始时间',
    `expire_time`    datetime NOT NULL COMMENT '自动到期时间：到点即视为解除，不依赖回写',
    `unlock_time`    datetime          DEFAULT NULL COMMENT '实际解冻时间：status=1 时必填',
    `unlock_type`    tinyint           DEFAULT NULL COMMENT '解冻方式：1-自动到期, 2-重置密码, 3-人工。status=0 时为 NULL',
    `operator`       varchar(64)       DEFAULT NULL COMMENT '人工解冻的操作人：unlock_type=3 时必填，用于追溯',
    `status`         tinyint  NOT NULL DEFAULT '0' COMMENT '状态：0-冻结中, 1-已解冻',
    `reason`         varchar(128)      DEFAULT NULL COMMENT '触发原因：给客服看的人话，如「连续登录失败」',
    `remark`         varchar(256)      DEFAULT NULL COMMENT '备注',
    `create_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY              `idx_mbr_limit_active` (`member_id`, `operation_type`, `status`),
    KEY              `idx_mbr_limit_expire` (`status`, `expire_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='会员操作限制（功能级、带到期、可解冻）';

DROP TABLE IF EXISTS `t_member_verify`;
CREATE TABLE `t_member_verify` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `member_id` bigint NOT NULL COMMENT '会员号',
  `real_name` varchar(255) DEFAULT NULL COMMENT '真实姓名密文',
  `id_card` varchar(255) DEFAULT NULL COMMENT '身份证号密文',
  `id_card_hash` binary(32) DEFAULT NULL COMMENT '身份证HMAC-SHA256原始字节(32B)：查重与唯一约束走它',
  `verify_status` tinyint NOT NULL DEFAULT '0' COMMENT '认证状态：0-未认证, 1-认证中, 2-已认证, 3-认证失败',
  `verify_time` datetime DEFAULT NULL COMMENT '认证通过时间',
  `fail_reason` varchar(255) DEFAULT NULL COMMENT '认证失败原因',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mbr_vrf_member` (`member_id`),
  UNIQUE KEY `uk_mbr_vrf_idcard` (`id_card_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员实名信息（敏感，与主表分离）';

DROP TABLE IF EXISTS `t_member_id_seq`;
CREATE TABLE `t_member_id_seq` (
  `id` tinyint NOT NULL DEFAULT '1' COMMENT '恒为1，本表只有一行',
  `next_seq` bigint NOT NULL DEFAULT '0' COMMENT '内部序号分配水位（已批发到此），只增不减',
  `step` int NOT NULL DEFAULT '1000' COMMENT '号段大小：一次批发多少个内部序号',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员号发号序列（单行，号段模式）';

DROP TABLE IF EXISTS `t_member_login_log`;
CREATE TABLE `t_member_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `member_id` bigint NOT NULL COMMENT '会员号',
  `client_ip` varchar(39) DEFAULT NULL COMMENT '客户端IP（兼容IPv6，39位足够）',
  `ip_region` varchar(64) DEFAULT NULL COMMENT 'IP归属地（ip2region 解析，SolvelaIpUtil 已有）',
  `device_type` varchar(16) DEFAULT NULL COMMENT '设备端：APP/H5/WECHAT/PC',
  `os_name` varchar(32) DEFAULT NULL COMMENT '操作系统：iOS/Android/Windows',
  `browser_name` varchar(32) DEFAULT NULL COMMENT '浏览器：Chrome/Safari',
  `status` tinyint NOT NULL COMMENT '登录结果：0-成功, 1-失败, 2-登出。与 t_login_log.login_result 同口径，共用 LoginLogResultEnum',
  `remark` varchar(128) DEFAULT NULL COMMENT '提示信息：成功可为空，失败写具体原因',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '全链路追踪ID，对应 LogTraceFilter 的 MDC traceId',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间（即登录时间）',
  PRIMARY KEY (`id`,`create_time`),
  KEY `idx_mbr_log_member` (`member_id`),
  KEY `idx_mbr_log_time` (`create_time`),
  KEY `idx_mbr_log_ip` (`client_ip`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员登录日志（append-only，按月分区）'
/*!50500 PARTITION BY RANGE  COLUMNS(create_time)
(PARTITION p202608 VALUES LESS THAN ('2026-09-01') ENGINE = InnoDB,
 PARTITION p202609 VALUES LESS THAN ('2026-10-01') ENGINE = InnoDB,
 PARTITION p202610 VALUES LESS THAN ('2026-11-01') ENGINE = InnoDB,
 PARTITION p202611 VALUES LESS THAN ('2026-12-01') ENGINE = InnoDB,
 PARTITION p202612 VALUES LESS THAN ('2027-01-01') ENGINE = InnoDB,
 PARTITION p202701 VALUES LESS THAN ('2027-02-01') ENGINE = InnoDB,
 PARTITION pmax VALUES LESS THAN (MAXVALUE) ENGINE = InnoDB) */;


-- =====================================================================================
-- 账务 / 履约（6 张）
-- =====================================================================================

DROP TABLE IF EXISTS `t_member_wallet`;
CREATE TABLE `t_member_wallet` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `member_id` bigint NOT NULL COMMENT '会员号：关联键',
  `asset_type` varchar(32) NOT NULL COMMENT '资产类型：SCORE-积分, BALANCE-现金，取值对齐 PrizeTypeEnum，与流水表 asset_type 同一字典',
  `balance` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '余额',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-冻结, 1-正常',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_asset` (`member_id`,`asset_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员钱包表（一行一种资产，扩展新资产只需新增 asset_type 取值，无需加字段）';

DROP TABLE IF EXISTS `t_member_asset_transaction`;
CREATE TABLE `t_member_asset_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `member_id` bigint NOT NULL COMMENT '会员号：关联键',
  `member_name` varchar(32) DEFAULT NULL COMMENT '会员账号【展示快照，非关联键，不要用于查询】',
  `asset_type` varchar(32) NOT NULL COMMENT '资产类型：SCORE, BALANCE',
  `transaction_type` tinyint NOT NULL COMMENT '资金流向：1-收入, 2-支出',
  `change_amount` decimal(18,4) NOT NULL COMMENT '变动绝对值',
  `balance_after` decimal(18,4) NOT NULL COMMENT '变动后最新余额',
  `biz_type` varchar(64) NOT NULL COMMENT '业务类型：TASK_PRIZE, CONSUME, MANUAL_ADJUST',
  `biz_ref_id` varchar(64) NOT NULL COMMENT '关联外部业务ID(如 prize_code)',
  `remark` varchar(255) DEFAULT NULL COMMENT 'C端展示摘要',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_t_biz_mbr_ast_txn_ref` (`biz_ref_id`,`asset_type`),
  KEY `idx_t_biz_mbr_ast_txn_time` (`member_id`,`asset_type`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易明细表';

DROP TABLE IF EXISTS `t_member_coupon`;
CREATE TABLE `t_member_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `member_id` bigint NOT NULL COMMENT '会员号：关联键',
  `member_name` varchar(32) DEFAULT NULL COMMENT '会员账号【展示快照，非关联键，不要用于查询】',
  `coupon_code` varchar(64) NOT NULL COMMENT '券模编码',
  `coupon_type` varchar(16) NOT NULL COMMENT '券类型',
  `coupon_name` varchar(128) NOT NULL COMMENT '券名称',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0-未使用, 1-已使用, 2-已过期, 3-已作废',
  `source_type` varchar(32) NOT NULL COMMENT '来源：DRAW, TASK, MANUAL_SEND',
  `source_biz_id` varchar(64) NOT NULL COMMENT '关联单号',
  `valid_start_time` datetime NOT NULL COMMENT '有效期开始',
  `valid_end_time` datetime NOT NULL COMMENT '有效期结束',
  `used_time` datetime DEFAULT NULL COMMENT '核销时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_source` (`source_type`,`source_biz_id`),
  KEY `idx_mbr_sts` (`member_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员优惠券';

DROP TABLE IF EXISTS `t_physical_delivery`;
CREATE TABLE `t_physical_delivery` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `member_id` bigint NOT NULL COMMENT '会员号：关联键',
  `member_name` varchar(32) DEFAULT NULL COMMENT '会员账号【展示快照，非关联键，不要用于查询】',
  `source_biz_id` varchar(64) NOT NULL COMMENT '来源单号：PROPOSAL 存提案ID / MALL 存订单号。只认单号，不认上游业务',
  `source_type` varchar(64) NOT NULL COMMENT '来源类型',
  `receiver_name` varchar(255) DEFAULT NULL COMMENT '收件人姓名【密文】：中奖时未知，由用户后续补填',
  `receiver_phone` varchar(255) DEFAULT NULL COMMENT '收件人电话【密文】：中奖时未知，由用户后续补填',
  `receiver_address` varchar(512) DEFAULT NULL COMMENT '收件详细地址【密文】：中奖时未知，由用户后续补填',
  `logistics_company` varchar(64) DEFAULT NULL COMMENT '物流公司',
  `logistics_no` varchar(128) DEFAULT NULL COMMENT '物流单号',
  `status` tinyint DEFAULT '0' COMMENT '状态：-1-已取消, 0-待发货, 1-已发货, 2-已签收, 3-异常退回',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_t_biz_phy_dlv_src` (`source_biz_id`,`source_type`),
  KEY `idx_delivery_status` (`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发货物流表';

DROP TABLE IF EXISTS `t_proposal_record`;
CREATE TABLE `t_proposal_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `member_id` bigint NOT NULL COMMENT '会员号：关联键',
  `trade_no` varchar(32) NOT NULL COMMENT '提案单号，服务端生成，对外唯一标识',
  `member_name` varchar(32) DEFAULT NULL COMMENT '会员账号【展示快照，非关联键，不要用于查询】',
  `asset_type` varchar(16) NOT NULL COMMENT 'SCORE/BALANCE/COUPON/PHYSICAL',
  `asset_ref` varchar(64) DEFAULT NULL COMMENT '资产引用：券模/SKU，值类资产为空',
  `asset_name` varchar(128) DEFAULT NULL COMMENT '资产展示名（券名/商品名）：由营销侧传入，避免账务域反查营销域',
  `amount` decimal(13,4) NOT NULL COMMENT '发放金额/积分数',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '发放数量，扣 used_quota 用',
  `source_type` varchar(32) NOT NULL COMMENT '来源：TASK(任务), DRAW(抽奖), LOTTERY(彩票), MANUAL(人工)',
  `source_biz_id` varchar(64) NOT NULL COMMENT '来源单号',
  `promotion_config_id` bigint NOT NULL COMMENT '优惠配置ID',
  `status` int NOT NULL DEFAULT '0' COMMENT '0-等待中, 10-待一审, 11-待二审, 20-驳回, 30-待执行, 40-执行中, 50-成功, 60-部分成功, 70-彻底失败, 80-风控拦截',
  `remark` varchar(255) DEFAULT NULL COMMENT '执行失败/风控拦截原因，或调用方传入的场景说明',
  `risk_code` varchar(32) DEFAULT NULL COMMENT '风控拦截分类(给漏斗聚类)：SINGLE_MAX_AMOUNT_LIMIT/USER_FREQUENCY_LIMIT/GLOBAL_BUDGET_LIMIT，仅 status=80 有值',
  `first_reviewer` varchar(64) DEFAULT NULL COMMENT '一审人',
  `first_review_time` datetime DEFAULT NULL COMMENT '一审时间',
  `second_reviewer` varchar(64) DEFAULT NULL COMMENT '二审人',
  `second_review_time` datetime DEFAULT NULL COMMENT '二审时间',
  `review_comment` varchar(255) DEFAULT NULL COMMENT '审核意见/驳回理由',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_no` (`trade_no`),
  UNIQUE KEY `uk_prop_source` (`source_type`,`asset_type`,`source_biz_id`),
  KEY `idx_prop_cfg_sts` (`promotion_config_id`,`status`),
  KEY `idx_prop_risk_stat` (`create_time`,`status`,`risk_code`),
  KEY `idx_prop_member` (`member_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提案表';

DROP TABLE IF EXISTS `t_promotion_config`;
CREATE TABLE `t_promotion_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `promo_name` varchar(128) NOT NULL COMMENT '优惠配置名称',
  `prize_type` varchar(32) NOT NULL COMMENT '资产类型：SCORE(积分), BALANCE(现金), COUPON(优惠券), PHYSICAL(实物)',
  `total_quota` int NOT NULL DEFAULT '-1' COMMENT '总库存(个数)：-1为不限制(适用于券/实物)',
  `used_quota` int NOT NULL DEFAULT '0' COMMENT '已消耗库存(个数)',
  `total_amount` decimal(18,4) NOT NULL DEFAULT '-1.0000' COMMENT '总预算(金额)：-1为不限制(适用于积分/现金)',
  `used_amount` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '已消耗预算(金额)',
  `review_level` tinyint NOT NULL DEFAULT '0' COMMENT '审核层级控制：0-无需审核, 1-单层审批, 2-双层审批',
  `first_review_threshold` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '一审触发阈值：动账金额 >= 此值必须一审(值为0代表笔笔一审)',
  `second_review_threshold` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '二审触发阈值：动账金额 >= 此值必须二审(前提 review_level=2)',
  `single_max_quota` int NOT NULL DEFAULT '1' COMMENT '单次最大数量兜底，超限阻断',
  `single_max_amount` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '单次最大金额兜底，超限阻断',
  `limit_period` varchar(32) NOT NULL DEFAULT 'LIFETIME' COMMENT '限制周期：LIFETIME(终身), DAILY(每日), WEEKLY(每周), MONTHLY(每月), CUSTOM',
  `identify_limit` int DEFAULT '-1' COMMENT '同周期内，单会员ID最多领取次数 (-1为不限)',
  `phone_limit` int DEFAULT '-1' COMMENT '同周期内，单手机号最多领取次数 (-1为不限)',
  `ip_limit` int DEFAULT '-1' COMMENT '同周期内，单IP地址最多领取次数 (-1为不限)',
  `device_limit` int DEFAULT '-1' COMMENT '同周期内，单设备硬件号(IMEI/IDFA)最多领取次数 (-1为不限)',
  `fingerprint_limit` int DEFAULT '-1' COMMENT '同周期内，单客户端指纹最多领取次数 (-1为不限)',
  `mutex_rule` json DEFAULT NULL COMMENT '互斥规则：存互斥的优惠配置ID数组',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-停用, 1-启用',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠配置表';


-- =====================================================================================
-- 营销 - 活动与奖品（9 张）
-- =====================================================================================

DROP TABLE IF EXISTS `t_activity_config`;
CREATE TABLE `t_activity_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `activity_code` varchar(32) NOT NULL COMMENT '活动编码',
  `activity_name` varchar(64) NOT NULL COMMENT '活动名称',
  `activity_type` varchar(32) NOT NULL COMMENT '活动类型：BASIC-基础活动(仅外壳,不挂玩法) / DRAW-奖池抽奖 / TASK-任务驱动 / LOTTERY-FPE彩票',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0-未开始, 1-上线, 2-下线',
  `start_time` datetime NOT NULL COMMENT '活动开始时间',
  `end_time` datetime NOT NULL COMMENT '活动结束时间',
  `data_end_time` datetime DEFAULT NULL COMMENT '数据截止时间：此刻起不再受理参与（抽奖/任务累计），但活动仍可见、奖品仍可领到 end_time。为空表示与 end_time 相同',
  `create_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_act_code` (`activity_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动配置';

DROP TABLE IF EXISTS `t_activity_display`;
CREATE TABLE `t_activity_display` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `activity_id` bigint NOT NULL COMMENT '关联 t_activity_config.id',
  `main_image_id` bigint DEFAULT NULL COMMENT '主视觉 file_id',
  `bg_image_id` bigint DEFAULT NULL COMMENT '背景图 file_id',
  `share_image_id` bigint DEFAULT NULL COMMENT '分享图 file_id',
  `share_title` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '分享标题',
  `share_desc` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '分享描述',
  `sub_title` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '副标题',
  `theme_color` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '主题色 #RRGGBB',
  `rule_content` mediumtext COLLATE utf8mb4_general_ci COMMENT '活动规则，富文本HTML。禁止 base64 内联图片',
  `extra_config` json DEFAULT NULL COMMENT '按 activity_type 各自定义，后端只存不解析',
  `create_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人（用户名）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人（用户名）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='活动C端展示配置';

DROP TABLE IF EXISTS `t_prize_config`;
CREATE TABLE `t_prize_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `activity_code` varchar(32) NOT NULL COMMENT '活动编码',
  `promotion_config_id` bigint NOT NULL COMMENT '优惠配置ID',
  `prize_type` varchar(32) NOT NULL COMMENT '资产类型：SCORE, BALANCE, COUPON, PHYSICAL, LOTTERY, CUSTOM',
  `prize_name` varchar(128) NOT NULL COMMENT '奖品名称',
  `prize_code` varchar(64) NOT NULL COMMENT '奖品编码',
  `prize_level` int DEFAULT '0' COMMENT '奖品级别',
  `prize_value` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '奖励价值',
  `approve_mode` tinyint NOT NULL DEFAULT '0' COMMENT '审批模式：0-自动免审, 1-人工审批',
  `sort_weight` int NOT NULL DEFAULT '0' COMMENT '排序权重',
  `ext` json DEFAULT NULL COMMENT '扩展信息：如奖品图片URL、跳转链接等',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-停用, 1-启用',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prize_code` (`prize_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='奖品配置表';

DROP TABLE IF EXISTS `t_mq_message_log`;
CREATE TABLE `t_mq_message_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `message_id` varchar(64) NOT NULL COMMENT '消息唯一标识（发送方生成）。唯一索引即消费幂等',
  `exchange` varchar(64) NOT NULL COMMENT '交换机',
  `routing_key` varchar(64) NOT NULL COMMENT '路由键。将来活动挂事件监听就是按它路由的',
  `consumer_key` varchar(64) NOT NULL DEFAULT '' COMMENT '消费者标识：活动事件填活动编码，固定消费者填 handler 名。后台重试按它隔离',
  `queue` varchar(64) NOT NULL COMMENT '队列名：同一条消息可能被多个队列消费，队列名参与定位',
  `payload` mediumtext NOT NULL COMMENT '消息 JSON 原文',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0-已接收, 1-处理成功, 2-处理失败',
  `fail_reason` varchar(255) DEFAULT NULL COMMENT '处理失败原因',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数',
  `receive_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
  `handle_time` datetime DEFAULT NULL COMMENT '处理完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mq_msg` (`message_id`,`consumer_key`),
  KEY `idx_mq_retry` (`consumer_key`,`status`,`receive_time`),
  KEY `idx_mq_status` (`status`,`receive_time`),
  KEY `idx_mq_receive_time` (`receive_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息接收记录：唯一索引即消费幂等，只保留 7 天';

DROP TABLE IF EXISTS `t_prize_log`;
CREATE TABLE `t_prize_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `member_id` bigint NOT NULL COMMENT '会员号：关联键',
  `member_name` varchar(32) DEFAULT NULL COMMENT '会员账号【展示快照，非关联键，不要用于查询】',
  `prize_code` varchar(64) NOT NULL COMMENT '奖品编码',
  `activity_code` varchar(32) NOT NULL COMMENT '活动编码',
  `activity_type` varchar(32) DEFAULT NULL COMMENT '玩法类型 BASIC/DRAW/TASK/LOTTERY：发奖时由发放方写入。派发链路据它归类提案来源，不再回头查活动表 —— 拆服务后活动域与资产域不在同一个进程',
  `prize_level` int DEFAULT '0' COMMENT '奖品级别',
  `prize_name` varchar(128) NOT NULL COMMENT '奖品名称',
  `prize_type` varchar(32) NOT NULL COMMENT '奖励类型：SCORE, BALANCE, COUPON, PHYSICAL',
  `prize_value` varchar(128) NOT NULL COMMENT '奖励体值(积分数/券ID)',
  `fail_reason` varchar(128) DEFAULT NULL COMMENT '异常原因：发奖失败时才有值',
  `approve_status` tinyint NOT NULL DEFAULT '0' COMMENT '审批状态：0-无需审批, 1-待审批, 2-已批准, 3-已驳回',
  `approve_by` varchar(64) DEFAULT NULL COMMENT '审批人',
  `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  `valid_until` datetime DEFAULT NULL COMMENT '过期时间',
  `status` tinyint DEFAULT '0' COMMENT '执行状态：0-等待, 1-成功, 2-失败',
  `proposal_status` tinyint NOT NULL DEFAULT '0' COMMENT '提案侧结果：0-待提交, 1-已受理, 2-被拒绝。与 status 是两件事：本列说「会员服务收没收下」，status 说「用户最终有没有拿到」',
  `proposal_id` bigint DEFAULT NULL COMMENT '会员服务返回的提案 id，对账与人工排查用',
  `external_biz_no` varchar(128) DEFAULT NULL COMMENT '外部单号',
  `remark` varchar(255) DEFAULT NULL COMMENT '异常原因',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_external_biz` (`external_biz_no`),
  KEY `idx_prize_log_` (`member_id`,`activity_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='奖励记录表';

DROP TABLE IF EXISTS `t_prize_pool_config`;
CREATE TABLE `t_prize_pool_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `activity_code` varchar(32) NOT NULL COMMENT '活动编码',
  `pool_code` varchar(32) NOT NULL COMMENT '奖池唯一编码 (如: VIP_POOL)',
  `pool_name` varchar(128) NOT NULL COMMENT '奖池名称',
  `reset_period` varchar(32) NOT NULL DEFAULT 'DAY' COMMENT '重置周期，天，周，月，活动期间',
  `draw_mode` tinyint DEFAULT '1' COMMENT '抽奖算法: 1-按概率(probability), 2-按库存比例(stock_ratio)',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0关闭，1开启',
  `create_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pool_code` (`pool_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='奖池配置';

DROP TABLE IF EXISTS `t_prize_pool_item`;
CREATE TABLE `t_prize_pool_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `activity_code` varchar(32) NOT NULL COMMENT '活动编码',
  `prize_code` varchar(64) NOT NULL COMMENT '奖品编码',
  `user_max_count` int NOT NULL DEFAULT '-1' COMMENT '单人限领次数: -1不限, 1表示每人最多中一次',
  `total_stock` int NOT NULL DEFAULT '-1' COMMENT '总库存',
  `used_stock` int NOT NULL DEFAULT '0' COMMENT '已用库存',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  `white_list` json DEFAULT NULL COMMENT '白名单：指定用户必中',
  `create_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_activity` (`activity_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='奖池奖项';

DROP TABLE IF EXISTS `t_pool_prize_mapping`;
CREATE TABLE `t_pool_prize_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `pool_code` varchar(32) NOT NULL COMMENT '奖池编码',
  `prize_item_id` bigint NOT NULL COMMENT '奖项id',
  `probability` decimal(8,4) NOT NULL DEFAULT '0.0000' COMMENT '中奖概率(万分位)',
  `is_fallback` tinyint NOT NULL DEFAULT '0' COMMENT '是否兜底奖项：1-兜底(自动吃掉剩余概率/库存不足时降级命中)，每池最多一个',
  `sort_weight` int NOT NULL DEFAULT '0' COMMENT '序号',
  `create_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pool_prize` (`pool_code`,`prize_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='奖池奖项映射';

DROP TABLE IF EXISTS `t_draw_prize_log`;
CREATE TABLE `t_draw_prize_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `member_id` bigint NOT NULL COMMENT '会员号：关联键',
  `trace_id` varchar(64) NOT NULL COMMENT '请求ID',
  `activity_code` varchar(32) NOT NULL COMMENT '活动编码',
  `pool_code` varchar(32) NOT NULL COMMENT '奖池编码',
  `member_name` varchar(32) DEFAULT NULL COMMENT '会员账号【展示快照，非关联键，不要用于查询】',
  `prize_item_id` bigint NOT NULL COMMENT '奖项ID',
  `prize_code` varchar(32) NOT NULL COMMENT '奖品code',
  `status` tinyint DEFAULT '0' COMMENT '状态: 0-未中奖, 1-已中奖, 2-库存不足, 3-异常',
  `remark` varchar(64) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_mem_act` (`member_id`,`activity_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽奖记录';


-- =====================================================================================
-- 营销 - 任务（6 张）
-- =====================================================================================

DROP TABLE IF EXISTS `t_task_template`;
CREATE TABLE `t_task_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `template_code` varchar(64) NOT NULL COMMENT '模板编码',
  `template_name` varchar(128) NOT NULL COMMENT '模板名称',
  `task_type` varchar(32) NOT NULL COMMENT '流转类型：SIMPLE(单次节点型), COUNT(计次型), AMOUNT(计额型)',
  `trigger_event` varchar(32) DEFAULT NULL COMMENT '默认触发事件：模板建议值，向导中可覆盖',
  `ui_schema` json NOT NULL COMMENT '前端渲染规则',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-禁用, 1-启用',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_tpl_code` (`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务模板表';

DROP TABLE IF EXISTS `t_task_config`;
CREATE TABLE `t_task_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `activity_code` varchar(16) NOT NULL COMMENT '活动编码',
  `task_name` varchar(128) NOT NULL COMMENT '任务名称',
  `template_code` varchar(64) NOT NULL COMMENT '模板Code',
  `trigger_event` varchar(64) NOT NULL COMMENT '触发事件：ORDER_PAID(支付), MEMBER_REGISTER(注册), DAILY_SIGN(签到), PAGE_VIEW(浏览), CUSTOM(自定义)',
  `task_group` varchar(32) DEFAULT 'DEFAULT' COMMENT '任务分组：NEWBIE(新手), DAILY(日常), PROMO(大促), VIP(会员专属)',
  `target_audience` varchar(32) DEFAULT 'ALL' COMMENT '目标人群：ALL(全部), NEW_MEMBER(新会员), OLD_MEMBER(老会员)。非ALL时上游上报事件必须带 isNewMember，否则事件被丢弃',
  `limit_type` varchar(32) NOT NULL DEFAULT 'ONCE' COMMENT '参与频次：ONCE(终身一次), DAILY(每日重复), WEEKLY(每周重复), UNLIMITED(无限制)',
  `limit_count` int NOT NULL DEFAULT '1' COMMENT '周期内可完成的轮数，仅 DAILY/WEEKLY 生效；>1 时 period_key 追加轮次后缀(如 20260801#2)，第1轮不带后缀',
  `rule_config` json NOT NULL COMMENT '规则配置',
  `sort_weight` int DEFAULT '0' COMMENT '排序权重，越大越靠前',
  `action_url` varchar(255) DEFAULT NULL COMMENT '跳转地址',
  `ui_config` json DEFAULT NULL COMMENT '展示UI(图标/角标等)',
  `status` tinyint DEFAULT '0' COMMENT '任务状态 1-待生效, 2-生效中, 3-已下线',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_t_biz_tsk_cfg_grp_aud` (`task_group`,`target_audience`),
  KEY `idx_t_biz_tsk_cfg_evt_sts` (`trigger_event`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务配置表';

DROP TABLE IF EXISTS `t_task_record`;
CREATE TABLE `t_task_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `member_id` bigint NOT NULL COMMENT '会员号：关联键',
  `task_config_id` bigint NOT NULL COMMENT '任务配置ID',
  `activity_code` varchar(32) NOT NULL COMMENT '活动编码',
  `period_key` varchar(32) NOT NULL DEFAULT 'NONE' COMMENT '业务期数标识(防重用)：NONE / 日期(20260402) / 周(2026W14)；limit_count>1 时第2轮起带后缀(20260402#2)',
  `valid_start_time` datetime NOT NULL COMMENT '开始时间',
  `valid_end_time` datetime NOT NULL COMMENT '过期时间',
  `current_metric` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '当前进度值：如已签到 3.0000 天',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号：仅 STREAK 的读-改-写路径使用，累加型走条件更新不需要它',
  `status` tinyint DEFAULT '0' COMMENT '状态：0-进行中, 1-已完成, 2-已发奖, 3-已过期',
  `progress_data` json DEFAULT NULL COMMENT '进度详情',
  `rule_snapshot` json NOT NULL COMMENT '接取任务时的规则快照',
  `prize_snapshot` json NOT NULL COMMENT '接取任务时的奖励快照',
  `complete_time` datetime DEFAULT NULL COMMENT '达标时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_t_tsk_rec_mbr_cfg_prd` (`member_id`,`task_config_id`,`period_key`),
  KEY `idx_t_tsk_rec_expire` (`status`,`valid_end_time`),
  KEY `idx_t_tsk_rec_mbr_sts` (`member_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务记录表';

DROP TABLE IF EXISTS `t_task_record_flow`;
CREATE TABLE `t_task_record_flow` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `member_id` bigint NOT NULL COMMENT '会员号：关联键',
  `member_name` varchar(32) DEFAULT NULL COMMENT '会员账号【展示快照，非关联键，不要用于查询】',
  `task_config_id` bigint NOT NULL COMMENT '任务配置ID',
  `record_id` bigint DEFAULT NULL COMMENT '任务记录ID：被丢弃的事件可能还没建记录，故可空',
  `event_code` varchar(64) NOT NULL COMMENT '事件编码：DAILY_SIGN / ORDER_PAID ...',
  `event_biz_id` varchar(128) NOT NULL COMMENT '幂等键：上游单号；无天然单号的事件用 D+yyyyMMdd(事件日) 兜底',
  `flow_type` tinyint NOT NULL DEFAULT '1' COMMENT '1-进度推进(已生效), 2-事件丢弃(未生效)',
  `delta_metric` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '本次增量',
  `after_metric` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '推进后进度值，便于按时间轴复盘',
  `discard_code` varchar(32) DEFAULT NULL COMMENT '丢弃原因分类(给大屏聚类)：AMOUNT_MISSING/AMOUNT_BELOW_MIN/STREAK_SAME_DAY/RECORD_NOT_RUNNING/AUDIENCE_MISMATCH/AUDIENCE_UNKNOWN/ROUND_LIMIT_EXCEEDED/CONFIG_INVALID/POOL_REJECTED',
  `discard_reason` varchar(255) DEFAULT NULL COMMENT '丢弃原因：flow_type=2 时必填',
  `event_payload` json DEFAULT NULL COMMENT '事件原文快照，供客诉复盘',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_t_tsk_flw_evt` (`task_config_id`,`member_id`,`event_biz_id`),
  KEY `idx_t_tsk_flw_rec` (`record_id`,`create_time`),
  KEY `idx_t_tsk_flw_stat` (`create_time`,`flow_type`,`discard_code`),
  KEY `idx_t_tsk_flw_mbr` (`member_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务事件流水表：幂等防重 + 客诉自证';

DROP TABLE IF EXISTS `t_task_event`;
CREATE TABLE `t_task_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `event_code` varchar(64) NOT NULL COMMENT '事件编码：DAILY_SIGN / ORDER_PAID / GOODS_SHARE',
  `event_name` varchar(64) NOT NULL COMMENT '展示名：签到 / 支付成功 / 分享商品',
  `metric_source` varchar(32) NOT NULL DEFAULT 'NONE' COMMENT '计量来源：NONE(计次) 或 payload 里的字段名(计额)',
  `payload_schema` json DEFAULT NULL COMMENT '该事件会带哪些字段，供模板设计器提示与校验',
  `biz_id_required` tinyint NOT NULL DEFAULT '0' COMMENT '上游是否必须带幂等单号：1-必须, 0-可按事件日兜底',
  `is_high_frequency` tinyint NOT NULL DEFAULT '0' COMMENT '是否高频事件：1-是（预留给路由优化，本期未实现）',
  `discard_log_flag` tinyint NOT NULL DEFAULT '1' COMMENT '是否记录被丢弃事件的流水：1-记录, 0-不记录（高频事件建议关）',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注：上游由谁埋点、什么时机触发',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-停用, 1-启用',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_t_tsk_evt_code` (`event_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务事件注册表';

DROP TABLE IF EXISTS `t_task_prize_mapping`;
CREATE TABLE `t_task_prize_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `task_config_id` bigint NOT NULL COMMENT '任务配置ID',
  `stage_condition` json NOT NULL COMMENT '阶段达标条件：如 {"min": 10, "max": 99} 或 {"action": "share"}',
  `stage_level` int NOT NULL DEFAULT '1' COMMENT '任务阶段：单次任务填1，阶梯任务填 1, 2, 3...',
  `prize_code` varchar(64) NOT NULL COMMENT '奖励编码',
  `prize_mode` varchar(32) NOT NULL DEFAULT 'FIXED' COMMENT '计算类型：FIXED(固定), RATIO(比例), FORMULA(公式)',
  `prize_strategy` json DEFAULT NULL COMMENT '动态发奖策略JSON：如 {"amount": 20} 或 {"ratio": 0.05}',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_stage` (`task_config_id`,`stage_level`),
  KEY `idx_prize_code` (`prize_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务阶段与奖励映射表';


-- =====================================================================================
-- 营销 - 彩票（5 张）
-- =====================================================================================

DROP TABLE IF EXISTS `t_lottery_config`;
CREATE TABLE `t_lottery_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `activity_code` varchar(32) NOT NULL COMMENT '活动编码',
  `lottery_code` varchar(32) NOT NULL COMMENT '彩票编码',
  `lottery_name` varchar(128) NOT NULL COMMENT '彩票名称',
  `number_charset` varchar(32) NOT NULL DEFAULT '0-9' COMMENT '发号字符集',
  `number_length` tinyint NOT NULL DEFAULT '5' COMMENT '号码长度',
  `total_count` int NOT NULL COMMENT '单期发行总数上限',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-下线, 1-上线',
  `create_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lottery_code` (`lottery_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='彩票配置';

DROP TABLE IF EXISTS `t_lottery_issue`;
CREATE TABLE `t_lottery_issue` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `lottery_code` varchar(32) NOT NULL COMMENT '彩票编码',
  `issue_no` varchar(32) NOT NULL COMMENT '期号',
  `sold_count` int NOT NULL DEFAULT '0' COMMENT '已售/已派发数量',
  `sale_start_time` datetime NOT NULL COMMENT '售卖开始时间',
  `sale_end_time` datetime NOT NULL COMMENT '售卖结束时间',
  `plan_draw_time` datetime DEFAULT NULL COMMENT '计划开奖时间：对外承诺的开奖时刻，与实际执行的 settle_time 区分',
  `settle_time` datetime DEFAULT NULL COMMENT '开奖时间',
  `winning_number` varchar(32) DEFAULT NULL COMMENT '开奖号码',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0-待开奖, 1-部分开奖, 2-已开奖',
  `create_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_issue_no` (`lottery_code`,`issue_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='期号配置';

DROP TABLE IF EXISTS `t_lottery_prize_rule`;
CREATE TABLE `t_lottery_prize_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `lottery_code` varchar(32) NOT NULL COMMENT '彩票编码',
  `prize_level` int NOT NULL COMMENT '奖品奖级',
  `match_rule` varchar(16) NOT NULL COMMENT '匹配规则,EXACT:全号, TAIL:尾号匹配, HEAD:首号匹配',
  `match_length` int NOT NULL COMMENT '匹配长度',
  `prize_code` varchar(64) NOT NULL COMMENT '奖品编码',
  `create_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lottery_level` (`lottery_code`,`prize_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='彩票匹配与资产路由规则表';

DROP TABLE IF EXISTS `t_lottery_record`;
CREATE TABLE `t_lottery_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `member_id` bigint NOT NULL COMMENT '会员号：关联键',
  `lottery_code` varchar(32) NOT NULL COMMENT '彩票编码',
  `issue_no` varchar(32) NOT NULL COMMENT '期号',
  `sequence_no` int NOT NULL COMMENT 'FPE算号基数',
  `ticket_number` varchar(32) NOT NULL COMMENT '彩票号码',
  `member_name` varchar(32) DEFAULT NULL COMMENT '会员账号【展示快照，非关联键，不要用于查询】',
  `obtain_time` datetime DEFAULT NULL COMMENT '领号时间',
  `win_status` tinyint NOT NULL DEFAULT '0' COMMENT '中奖状态: 0-未开奖, 1-未中奖, 2-已中奖',
  `prize_level` int NOT NULL DEFAULT '99' COMMENT '奖励等级：1..N 为中奖奖级(数字越小奖越大)，99-未中奖/未开奖',
  `prize_code` varchar(64) DEFAULT NULL COMMENT '中奖奖品编码：核销时从规则表快照，防规则被改后历史中奖结果漂移',
  `dispatch_status` tinyint NOT NULL DEFAULT '0' COMMENT '派发状态：0-待派发/无需派发, 1-已投递, 2-投递失败',
  `security_sign` varchar(32) NOT NULL COMMENT '防篡改签名',
  `create_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_issue_ticket` (`lottery_code`,`issue_no`,`ticket_number`),
  KEY `idx_status` (`issue_no`,`win_status`),
  KEY `idx_dispatch` (`issue_no`,`dispatch_status`),
  KEY `idx_member` (`member_id`,`issue_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户号码记录';

DROP TABLE IF EXISTS `t_lottery_number_pool`;
CREATE TABLE `t_lottery_number_pool` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `lottery_code` varchar(32) NOT NULL COMMENT '彩票编码',
  `ticket_number` varchar(32) NOT NULL COMMENT '彩票号码',
  `sequence_no` int NOT NULL COMMENT '发号序列号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lottery_num` (`lottery_code`,`ticket_number`),
  KEY `idx_number_pool_member` (`sequence_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='彩票号码池';


-- =====================================================================================
-- 脚本引擎（2 张）
-- =====================================================================================

DROP TABLE IF EXISTS `t_script`;
CREATE TABLE `t_script` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `script_code` varchar(64) NOT NULL COMMENT '脚本唯一编码，由文件相对路径去掉后缀推导，如 task/streak_sign_7d',
  `script_name` varchar(128) NOT NULL COMMENT '脚本名称，来自文件头 @name',
  `domain` varchar(32) NOT NULL COMMENT '业务域，对应 ScriptDomain 枚举。由 scene 推导，不单独声明',
  `scene` varchar(32) NOT NULL COMMENT '场景，对应 ScriptScene 枚举，来自文件头 @scene。决定入参与返回值契约',
  `file_path` varchar(255) NOT NULL COMMENT 'classpath 下的路径，如 scripts/task/streak_sign_7d.ql',
  `content` mediumtext NOT NULL COMMENT '脚本内容。只读镜像：权威在文件，启动时由加载器覆盖写入，改这里不生效',
  `content_hash` varchar(64) NOT NULL COMMENT 'content 的 SHA-256，加载器据此判断内容是否变化',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本号，内容变化时 +1',
  `params_schema` json DEFAULT NULL COMMENT '入参契约快照，由 ScriptScene.getParams() 生成，供前端渲染',
  `return_type` varchar(32) NOT NULL COMMENT '返回值类型，由 ScriptScene 决定',
  `description` varchar(500) DEFAULT NULL COMMENT '用途说明，来自文件头 @desc',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-停用, 1-启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_script_code` (`script_code`),
  KEY `idx_script_scene` (`scene`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='脚本注册表。文件的只读镜像，权威在 common-api/src/main/resources/scripts/，无 create_by/update_by 是因为这张表只由加载器写';

DROP TABLE IF EXISTS `t_script_ref`;
CREATE TABLE `t_script_ref` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `script_code` varchar(64) NOT NULL COMMENT '引用的脚本编码，关联 t_script.script_code',
  `ref_type` varchar(32) NOT NULL COMMENT '引用方类型：TASK_TEMPLATE / PRIZE_POOL / ACTIVITY，见 ScriptRefPoint 枚举',
  `ref_id` varchar(64) NOT NULL COMMENT '引用方业务编码（template_code / pool_code / activity_code），不用自增id',
  `ref_slot` varchar(32) NOT NULL COMMENT '挂载槽位：RULE / ENTRY 等。同一个业务对象可以挂多个不同用途的脚本',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-停用, 1-启用',
  `create_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_script_ref_point` (`ref_type`,`ref_id`,`ref_slot`),
  KEY `idx_script_ref_code` (`script_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='脚本引用关系表。存在的唯一理由：回答「改这个脚本会影响哪些业务对象」';


-- =====================================================================================
-- 积分商城（7 张）
-- =====================================================================================

DROP TABLE IF EXISTS `t_mall_category`;
CREATE TABLE `t_mall_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父级id：0-顶级分类。业务上限死两级',
  `category_name` varchar(50) NOT NULL COMMENT '分类名称：如 数码3C / 虚拟权益',
  `icon_file_id` bigint DEFAULT NULL COMMENT '分类图标 file_id（C端宫格导航用）',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序：从小到大',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-禁用, 1-启用',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_cat_parent_name` (`parent_id`,`category_name`),
  KEY `idx_mall_cat_status_sort` (`status`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商城-商品分类';

DROP TABLE IF EXISTS `t_mall_commodity`;
CREATE TABLE `t_mall_commodity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `commodity_code` varchar(32) NOT NULL COMMENT '商品编码：10位大写字母+数字，全局唯一，创建后不可改',
  `category_id` bigint NOT NULL COMMENT '分类id',
  `commodity_type` varchar(32) NOT NULL DEFAULT 'PHYSICAL' COMMENT '商品类型：PHYSICAL-实物(走t_physical_delivery), COUPON-优惠券(走t_member_coupon), BALANCE-现金/红包(走钱包入账)',
  `asset_ref` varchar(64) DEFAULT NULL COMMENT '资产引用：COUPON 存券模编码，PHYSICAL 为空。语义对齐 t_proposal_record.asset_ref',
  `commodity_name` varchar(128) NOT NULL COMMENT '商品名称',
  `commodity_intro` varchar(255) DEFAULT NULL COMMENT '副标题/一句话卖点',
  `cover_file_id` bigint NOT NULL COMMENT '封面主图 file_id（建议 800x800）',
  `detail_content` mediumtext COMMENT '图文详情，富文本HTML。禁止 base64 内联图片（对齐 t_activity_display.rule_content）',
  `exchange_notice` varchar(1024) DEFAULT NULL COMMENT '兑换须知：券的核销说明、实物的发货时效等。C端下单页固定展示',
  `pay_type` tinyint NOT NULL DEFAULT '1' COMMENT '支付方式：1-纯积分, 2-积分+现金',
  `original_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '划线原价：仅前端展示「价值￥199」，纯积分商品可留 0',
  `points_price` int NOT NULL DEFAULT '0' COMMENT '基准兑换积分',
  `cash_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '基准兑换现金：pay_type=1 时恒为 0',
  `limit_period` varchar(32) NOT NULL DEFAULT 'LIFETIME' COMMENT '限兑周期：LIFETIME-终身, DAILY-每日, WEEKLY-每周, MONTHLY-每月',
  `limit_count` int NOT NULL DEFAULT '0' COMMENT '周期内单会员限兑件数：0-不限制',
  `start_time` datetime NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '上架开始时间：默认值代表不限。不是秒杀场次',
  `end_time` datetime NOT NULL DEFAULT '2099-12-31 23:59:59' COMMENT '上架结束时间：默认值代表不限。不是秒杀场次',
  `status` tinyint NOT NULL DEFAULT '2' COMMENT '状态：0-下架, 1-上架, 2-草稿。新建默认落草稿',
  `is_home` tinyint NOT NULL DEFAULT '0' COMMENT '是否首页推荐：0-否, 1-是',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序权重：从小到大',
  `sold_count` int NOT NULL DEFAULT '0' COMMENT '累计已兑件数（各SKU之和的冗余，用于列表按热销排序）',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_cmd_code` (`commodity_code`),
  KEY `idx_mall_cmd_cat_status_sort` (`category_id`,`status`,`sort`),
  KEY `idx_mall_cmd_home` (`is_home`,`status`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商城-商品主表';

DROP TABLE IF EXISTS `t_mall_sku`;
CREATE TABLE `t_mall_sku` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `commodity_id` bigint NOT NULL COMMENT '关联 t_mall_commodity.id',
  `sku_code` varchar(32) NOT NULL COMMENT 'SKU编码：10位大写字母+数字，全局唯一',
  `sku_attrs` json NOT NULL COMMENT '规格组合：{"颜色":"星空灰","尺码":"XL"}。无规格商品填 {}',
  `sku_cover_file_id` bigint DEFAULT NULL COMMENT '该规格专属图 file_id：C端切换规格时换主图，为空则用商品封面',
  `sku_points_price` int DEFAULT NULL COMMENT '本规格所需积分：为空则继承 t_mall_commodity.points_price',
  `sku_cash_price` decimal(10,2) DEFAULT NULL COMMENT '本规格所需现金：为空则继承 t_mall_commodity.cash_price',
  `total_stock` int NOT NULL DEFAULT '0' COMMENT '总库存：运营投放量，恒定不变，补货改这里',
  `locked_stock` int NOT NULL DEFAULT '0' COMMENT '锁定库存：已下单未履约（仅 pay_type=2 会悬挂）',
  `sold_count` int NOT NULL DEFAULT '0' COMMENT '已售数量：履约成功累加',
  `available_stock` int GENERATED ALWAYS AS (((`total_stock` - `locked_stock`) - `sold_count`)) VIRTUAL COMMENT '可用库存（虚拟列，勿写入）',
  `sku_status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-停用, 1-启用',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_sku_code` (`sku_code`),
  KEY `idx_mall_sku_cmd` (`commodity_id`,`sku_status`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商城-SKU与库存';

DROP TABLE IF EXISTS `t_mall_order`;
CREATE TABLE `t_mall_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `order_no` varchar(32) NOT NULL COMMENT '订单号：服务端生成，对外唯一标识，同时作为扣积分的幂等键',
  `member_id` bigint NOT NULL COMMENT '会员号：关联键',
  `member_name` varchar(32) DEFAULT NULL COMMENT '下单时的会员账号【展示快照，非关联键，不要用于查询】',
  `commodity_id` bigint NOT NULL COMMENT '商品id',
  `commodity_code` varchar(32) NOT NULL COMMENT '商品编码（跨环境稳定的那个）',
  `sku_id` bigint NOT NULL COMMENT 'SKUid',
  `sku_code` varchar(32) NOT NULL COMMENT 'SKU编码',
  `commodity_type` varchar(32) NOT NULL COMMENT '商品类型快照：PHYSICAL / COUPON / BALANCE，履约分派靠它',
  `asset_ref` varchar(64) DEFAULT NULL COMMENT '资产引用快照：券模编码等',
  `commodity_name` varchar(128) NOT NULL COMMENT '商品名称快照',
  `cover_file_id` bigint DEFAULT NULL COMMENT '封面图快照 file_id',
  `sku_attrs` json NOT NULL COMMENT '规格快照',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '兑换件数',
  `points_price` int NOT NULL DEFAULT '0' COMMENT '单件积分单价快照',
  `cash_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '单件现金单价快照',
  `pay_points` int NOT NULL DEFAULT '0' COMMENT '实付积分合计',
  `pay_cash` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '实付现金合计',
  `address_id` bigint DEFAULT NULL COMMENT '收货地址id(软引用t_mall_address)，仅PHYSICAL有值。收件信息快照在t_physical_delivery，不在本表',
  `status` int NOT NULL DEFAULT '0' COMMENT '状态：0-待支付, 10-待履约, 20-履约中, 30-已完成, 40-已取消, 50-已退款, 60-履约失败',
  `expire_time` datetime DEFAULT NULL COMMENT '待支付超时时间：到点由 job 取消并释放锁定库存。纯积分订单为空',
  `pay_time` datetime DEFAULT NULL COMMENT '支付/扣分完成时间',
  `finish_time` datetime DEFAULT NULL COMMENT '履约完成时间',
  `cancel_time` datetime DEFAULT NULL COMMENT '取消时间',
  `source_type` varchar(32) NOT NULL DEFAULT 'NORMAL' COMMENT '订单来源：NORMAL-日常兑换, FLASH_SALE-限时抢购场次',
  `source_biz_id` varchar(64) DEFAULT NULL COMMENT '来源单号：FLASH_SALE 时存场次编码，NORMAL 为空',
  `fulfill_ref_id` varchar(64) DEFAULT NULL COMMENT '履约单引用：发货单id / 券id',
  `fail_reason` varchar(255) DEFAULT NULL COMMENT '履约失败原因（status=60 时有值）',
  `remark` varchar(255) DEFAULT NULL COMMENT '用户备注 / 运营备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_ord_no` (`order_no`),
  KEY `idx_mall_ord_member` (`member_id`,`create_time`),
  KEY `idx_mall_ord_expire` (`status`,`expire_time`),
  KEY `idx_mall_ord_cmd` (`commodity_id`,`status`,`create_time`),
  KEY `idx_mall_ord_source` (`source_type`,`source_biz_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商城-兑换订单';

DROP TABLE IF EXISTS `t_mall_exchange_limit`;
CREATE TABLE `t_mall_exchange_limit` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `member_id` bigint NOT NULL COMMENT '会员号：关联键',
  `commodity_id` bigint NOT NULL COMMENT '商品id',
  `period_key` varchar(32) NOT NULL DEFAULT 'NONE' COMMENT '周期标识：NONE(终身) / 20260819(日) / 2026W34(周) / 202608(月)。取值口径对齐 t_task_record.period_key',
  `used_count` int NOT NULL DEFAULT '0' COMMENT '该周期内已兑件数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_lmt_mbr_cmd_prd` (`member_id`,`commodity_id`,`period_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商城-会员限兑计数';

DROP TABLE IF EXISTS `t_mall_address`;
CREATE TABLE `t_mall_address` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `member_id` bigint NOT NULL COMMENT '会员号：关联键',
  `receiver_name` varchar(255) NOT NULL COMMENT '收件人姓名【密文】',
  `receiver_phone` varchar(255) NOT NULL COMMENT '收件人电话【密文】',
  `detail_address` varchar(512) NOT NULL COMMENT '详细门牌地址【密文】',
  `province` varchar(32) DEFAULT NULL COMMENT '省【明文，可统计】',
  `city` varchar(32) DEFAULT NULL COMMENT '市【明文，可统计】',
  `district` varchar(32) DEFAULT NULL COMMENT '区/县【明文，可统计】',
  `is_default` tinyint NOT NULL DEFAULT '0' COMMENT '是否默认地址：0-否, 1-是。设默认时先把该会员其余行置0',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_mall_addr_member` (`member_id`,`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商城-会员收货地址簿';

DROP TABLE IF EXISTS `t_mall_favorite`;
CREATE TABLE `t_mall_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `member_id` bigint NOT NULL COMMENT '会员号：关联键',
  `commodity_id` bigint NOT NULL COMMENT '商品id（商品粒度，不是SKU粒度）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_fav_mbr_cmd` (`member_id`,`commodity_id`),
  KEY `idx_mall_fav_mbr_time` (`member_id`,`create_time`),
  KEY `idx_mall_fav_cmd` (`commodity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商城-商品收藏';

