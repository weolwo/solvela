-- t_activity_config.status  状态：0-未开始, 1-上线, 2-下线
SELECT 't_activity_config.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_activity_config` GROUP BY `status`
UNION ALL
-- t_data_tracer.type  单据类型
SELECT 't_data_tracer.type' AS col, CAST(`type` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_data_tracer` GROUP BY `type`
UNION ALL
-- t_data_tracer.user_type  用户类型：1 后管用户
SELECT 't_data_tracer.user_type' AS col, CAST(`user_type` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_data_tracer` GROUP BY `user_type`
UNION ALL
-- t_dict.disabled_flag  禁用状态
SELECT 't_dict.disabled_flag' AS col, CAST(`disabled_flag` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_dict` GROUP BY `disabled_flag`
UNION ALL
-- t_dict_data.disabled_flag  禁用状态
SELECT 't_dict_data.disabled_flag' AS col, CAST(`disabled_flag` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_dict_data` GROUP BY `disabled_flag`
UNION ALL
-- t_draw_prize_log.status  状态: 0-未中奖, 1-已中奖, 2-库存不足, 3-异常
SELECT 't_draw_prize_log.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_draw_prize_log` GROUP BY `status`
UNION ALL
-- t_employee.administrator_flag  是否为超级管理员: 0 不是，1是
SELECT 't_employee.administrator_flag' AS col, CAST(`administrator_flag` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_employee` GROUP BY `administrator_flag`
UNION ALL
-- t_employee.deleted_flag  是否删除0否 1是
SELECT 't_employee.deleted_flag' AS col, CAST(`deleted_flag` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_employee` GROUP BY `deleted_flag`
UNION ALL
-- t_employee.disabled_flag  是否被禁用 0否1是
SELECT 't_employee.disabled_flag' AS col, CAST(`disabled_flag` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_employee` GROUP BY `disabled_flag`
UNION ALL
-- t_file.category_id  分类
SELECT 't_file.category_id' AS col, CAST(`category_id` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_file` GROUP BY `category_id`
UNION ALL
-- t_file.deleted_flag  删除标记
SELECT 't_file.deleted_flag' AS col, CAST(`deleted_flag` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_file` GROUP BY `deleted_flag`
UNION ALL
-- t_file.status  1临时 2已确认
SELECT 't_file.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_file` GROUP BY `status`
UNION ALL
-- t_file_category.category_id
SELECT 't_file_category.category_id' AS col, CAST(`category_id` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_file_category` GROUP BY `category_id`
UNION ALL
-- t_login_fail.lock_flag  锁定状态:1锁定，0未锁定
SELECT 't_login_fail.lock_flag' AS col, CAST(`lock_flag` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_login_fail` GROUP BY `lock_flag`
UNION ALL
-- t_login_fail.user_type  用户类型
SELECT 't_login_fail.user_type' AS col, CAST(`user_type` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_login_fail` GROUP BY `user_type`
UNION ALL
-- t_login_log.login_result  登录结果：0成功 1失败 2 退出
SELECT 't_login_log.login_result' AS col, CAST(`login_result` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_login_log` GROUP BY `login_result`
UNION ALL
-- t_login_log.user_type  用户类型
SELECT 't_login_log.user_type' AS col, CAST(`user_type` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_login_log` GROUP BY `user_type`
UNION ALL
-- t_lottery_config.status  状态：0-下线, 1-上线
SELECT 't_lottery_config.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_lottery_config` GROUP BY `status`
UNION ALL
-- t_lottery_issue.status  状态: 0-待开奖, 1-部分开奖, 2-已开奖
SELECT 't_lottery_issue.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_lottery_issue` GROUP BY `status`
UNION ALL
-- t_lottery_prize_rule.prize_level  奖品奖级
SELECT 't_lottery_prize_rule.prize_level' AS col, CAST(`prize_level` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_lottery_prize_rule` GROUP BY `prize_level`
UNION ALL
-- t_lottery_record.dispatch_status  派发状态：0-待派发/无需派发, 1-已投递, 2-投递失败
SELECT 't_lottery_record.dispatch_status' AS col, CAST(`dispatch_status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_lottery_record` GROUP BY `dispatch_status`
UNION ALL
-- t_lottery_record.prize_level  奖励等级：1..N 为中奖奖级(数字越小奖越大)，99-未中奖/未开奖
SELECT 't_lottery_record.prize_level' AS col, CAST(`prize_level` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_lottery_record` GROUP BY `prize_level`
UNION ALL
-- t_lottery_record.win_status  中奖状态: 0-未开奖, 1-未中奖, 2-已中奖
SELECT 't_lottery_record.win_status' AS col, CAST(`win_status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_lottery_record` GROUP BY `win_status`
UNION ALL
-- t_mail_template.disable_flag  是否禁用
SELECT 't_mail_template.disable_flag' AS col, CAST(`disable_flag` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_mail_template` GROUP BY `disable_flag`
UNION ALL
-- t_mall_category.status  状态：0-禁用, 1-启用
SELECT 't_mall_category.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_mall_category` GROUP BY `status`
UNION ALL
-- t_mall_commodity.category_id  分类id
SELECT 't_mall_commodity.category_id' AS col, CAST(`category_id` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_mall_commodity` GROUP BY `category_id`
UNION ALL
-- t_mall_commodity.pay_type  支付方式：1-纯积分, 2-积分+现金
SELECT 't_mall_commodity.pay_type' AS col, CAST(`pay_type` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_mall_commodity` GROUP BY `pay_type`
UNION ALL
-- t_mall_commodity.status  状态：0-下架, 1-上架, 2-草稿。新建默认落草稿
SELECT 't_mall_commodity.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_mall_commodity` GROUP BY `status`
UNION ALL
-- t_mall_order.status  状态：0-待支付, 10-待履约, 20-履约中, 30-已完成, 40-已取消, 50-已退款, 60-履约失败
SELECT 't_mall_order.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_mall_order` GROUP BY `status`
UNION ALL
-- t_mall_sku.sku_status  状态：0-停用, 1-启用
SELECT 't_mall_sku.sku_status' AS col, CAST(`sku_status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_mall_sku` GROUP BY `sku_status`
UNION ALL
-- t_member.status  状态：1-正常, 2-冻结(风控/违规), 3-已注销
SELECT 't_member.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_member` GROUP BY `status`
UNION ALL
-- t_member_asset_transaction.transaction_type  资金流向：1-收入, 2-支出
SELECT 't_member_asset_transaction.transaction_type' AS col, CAST(`transaction_type` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_member_asset_transaction` GROUP BY `transaction_type`
UNION ALL
-- t_member_coupon.status  状态：0-未使用, 1-已使用, 2-已过期, 3-已作废
SELECT 't_member_coupon.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_member_coupon` GROUP BY `status`
UNION ALL
-- t_member_login_log.status  状态：0-失败, 1-成功, 2-登出。⚠️与t_login_log.login_result取值相反
SELECT 't_member_login_log.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_member_login_log` GROUP BY `status`
UNION ALL
-- t_member_operation_limit.operation_type  受限操作：1-登录, 2-修改密码。见 MemberOperationTypeEnum
SELECT 't_member_operation_limit.operation_type' AS col, CAST(`operation_type` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_member_operation_limit` GROUP BY `operation_type`
UNION ALL
-- t_member_operation_limit.status  状态：0-冻结中, 1-已解冻
SELECT 't_member_operation_limit.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_member_operation_limit` GROUP BY `status`
UNION ALL
-- t_member_operation_limit.unlock_type  解冻方式：1-自动到期, 2-重置密码, 3-人工。status=0 时为 NULL
SELECT 't_member_operation_limit.unlock_type' AS col, CAST(`unlock_type` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_member_operation_limit` GROUP BY `unlock_type`
UNION ALL
-- t_member_verify.verify_status  认证状态：0-未认证, 1-认证中, 2-已认证, 3-认证失败
SELECT 't_member_verify.verify_status' AS col, CAST(`verify_status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_member_verify` GROUP BY `verify_status`
UNION ALL
-- t_member_wallet.status  状态：0-冻结, 1-正常
SELECT 't_member_wallet.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_member_wallet` GROUP BY `status`
UNION ALL
-- t_menu.cache_flag  是否缓存
SELECT 't_menu.cache_flag' AS col, CAST(`cache_flag` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_menu` GROUP BY `cache_flag`
UNION ALL
-- t_menu.deleted_flag  删除状态
SELECT 't_menu.deleted_flag' AS col, CAST(`deleted_flag` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_menu` GROUP BY `deleted_flag`
UNION ALL
-- t_menu.disabled_flag  禁用状态
SELECT 't_menu.disabled_flag' AS col, CAST(`disabled_flag` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_menu` GROUP BY `disabled_flag`
UNION ALL
-- t_menu.frame_flag  是否为外链
SELECT 't_menu.frame_flag' AS col, CAST(`frame_flag` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_menu` GROUP BY `frame_flag`
UNION ALL
-- t_menu.menu_type  类型
SELECT 't_menu.menu_type' AS col, CAST(`menu_type` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_menu` GROUP BY `menu_type`
UNION ALL
-- t_menu.perms_type  权限类型
SELECT 't_menu.perms_type' AS col, CAST(`perms_type` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_menu` GROUP BY `perms_type`
UNION ALL
-- t_menu.visible_flag  显示状态
SELECT 't_menu.visible_flag' AS col, CAST(`visible_flag` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_menu` GROUP BY `visible_flag`
UNION ALL
-- t_operate_log.operate_user_type  用户类型
SELECT 't_operate_log.operate_user_type' AS col, CAST(`operate_user_type` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_operate_log` GROUP BY `operate_user_type`
UNION ALL
-- t_operate_log.success_flag  请求结果 0失败 1成功
SELECT 't_operate_log.success_flag' AS col, CAST(`success_flag` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_operate_log` GROUP BY `success_flag`
UNION ALL
-- t_password_log.user_type  用户类型
SELECT 't_password_log.user_type' AS col, CAST(`user_type` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_password_log` GROUP BY `user_type`
UNION ALL
-- t_physical_delivery.status  状态：0-待发货, 1-已发货, 2-已签收, 3-异常退回
SELECT 't_physical_delivery.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_physical_delivery` GROUP BY `status`
UNION ALL
-- t_position.deleted_flag
SELECT 't_position.deleted_flag' AS col, CAST(`deleted_flag` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_position` GROUP BY `deleted_flag`
UNION ALL
-- t_prize_config.approve_mode  审批模式：0-自动免审, 1-人工审批
SELECT 't_prize_config.approve_mode' AS col, CAST(`approve_mode` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_prize_config` GROUP BY `approve_mode`
UNION ALL
-- t_prize_config.prize_level  奖品级别
SELECT 't_prize_config.prize_level' AS col, CAST(`prize_level` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_prize_config` GROUP BY `prize_level`
UNION ALL
-- t_prize_config.status  状态：0-停用, 1-启用
SELECT 't_prize_config.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_prize_config` GROUP BY `status`
UNION ALL
-- t_prize_log.approve_status  审批状态：0-无需审批, 1-待审批, 2-已批准, 3-已驳回
SELECT 't_prize_log.approve_status' AS col, CAST(`approve_status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_prize_log` GROUP BY `approve_status`
UNION ALL
-- t_prize_log.prize_level  奖品级别
SELECT 't_prize_log.prize_level' AS col, CAST(`prize_level` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_prize_log` GROUP BY `prize_level`
UNION ALL
-- t_prize_log.status  执行状态：0-等待, 1-成功, 2-失败
SELECT 't_prize_log.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_prize_log` GROUP BY `status`
UNION ALL
-- t_prize_pool_config.draw_mode  抽奖算法: 1-按概率(probability), 2-按库存比例(stock_ratio)
SELECT 't_prize_pool_config.draw_mode' AS col, CAST(`draw_mode` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_prize_pool_config` GROUP BY `draw_mode`
UNION ALL
-- t_prize_pool_config.status  0关闭，1开启
SELECT 't_prize_pool_config.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_prize_pool_config` GROUP BY `status`
UNION ALL
-- t_promotion_config.review_level  审核层级控制：0-无需审核, 1-单层审批, 2-双层审批
SELECT 't_promotion_config.review_level' AS col, CAST(`review_level` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_promotion_config` GROUP BY `review_level`
UNION ALL
-- t_promotion_config.status  状态：0-停用, 1-启用
SELECT 't_promotion_config.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_promotion_config` GROUP BY `status`
UNION ALL
-- t_proposal_record.status  0-等待中, 10-待一审, 11-待二审, 20-驳回, 30-待执行, 40-执行中, 50-成功, 60-部分成功, 70-彻底失败, 80-风控拦截
SELECT 't_proposal_record.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_proposal_record` GROUP BY `status`
UNION ALL
-- t_role_data_scope.data_scope_type  数据范围类型
SELECT 't_role_data_scope.data_scope_type' AS col, CAST(`data_scope_type` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_role_data_scope` GROUP BY `data_scope_type`
UNION ALL
-- t_role_data_scope.view_type  数据可见范围类型
SELECT 't_role_data_scope.view_type' AS col, CAST(`view_type` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_role_data_scope` GROUP BY `view_type`
UNION ALL
-- t_script.status  状态：0-停用, 1-启用
SELECT 't_script.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_script` GROUP BY `status`
UNION ALL
-- t_script_ref.status  状态：0-停用, 1-启用
SELECT 't_script_ref.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_script_ref` GROUP BY `status`
UNION ALL
-- t_solvela_job.deleted_flag  删除状态
SELECT 't_solvela_job.deleted_flag' AS col, CAST(`deleted_flag` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_solvela_job` GROUP BY `deleted_flag`
UNION ALL
-- t_solvela_job.enabled_flag  是否开启
SELECT 't_solvela_job.enabled_flag' AS col, CAST(`enabled_flag` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_solvela_job` GROUP BY `enabled_flag`
UNION ALL
-- t_solvela_job.handler_missing_flag  执行器在代码中不存在：该任务不会被执行，列表需标红
SELECT 't_solvela_job.handler_missing_flag' AS col, CAST(`handler_missing_flag` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_solvela_job` GROUP BY `handler_missing_flag`
UNION ALL
-- t_solvela_job.manual_modified_flag  衍生任务是否被人工改过：改过的向导不再覆盖（第三档用）
SELECT 't_solvela_job.manual_modified_flag' AS col, CAST(`manual_modified_flag` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_solvela_job` GROUP BY `manual_modified_flag`
UNION ALL
-- t_solvela_job.terminal_flag  ONE_TIME 任务执行完置 1，列表默认折叠
SELECT 't_solvela_job.terminal_flag' AS col, CAST(`terminal_flag` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_solvela_job` GROUP BY `terminal_flag`
UNION ALL
-- t_solvela_job_log.status  执行状态：0-待执行 1-执行中 2-成功 3-失败 4-超时中断 5-阻塞丢弃 6-错过调度 7-中断
SELECT 't_solvela_job_log.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_solvela_job_log` GROUP BY `status`
UNION ALL
-- t_table_column.user_type  用户类型
SELECT 't_table_column.user_type' AS col, CAST(`user_type` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_table_column` GROUP BY `user_type`
UNION ALL
-- t_task_config.status  任务状态 1-待生效, 2-生效中, 3-已下线
SELECT 't_task_config.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_task_config` GROUP BY `status`
UNION ALL
-- t_task_event.discard_log_flag  是否记录被丢弃事件的流水：1-记录, 0-不记录（高频事件建议关）
SELECT 't_task_event.discard_log_flag' AS col, CAST(`discard_log_flag` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_task_event` GROUP BY `discard_log_flag`
UNION ALL
-- t_task_event.status  状态：0-停用, 1-启用
SELECT 't_task_event.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_task_event` GROUP BY `status`
UNION ALL
-- t_task_prize_mapping.stage_level  任务阶段：单次任务填1，阶梯任务填 1, 2, 3...
SELECT 't_task_prize_mapping.stage_level' AS col, CAST(`stage_level` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_task_prize_mapping` GROUP BY `stage_level`
UNION ALL
-- t_task_record.status  状态：0-进行中, 1-已完成, 2-已发奖, 3-已过期
SELECT 't_task_record.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_task_record` GROUP BY `status`
UNION ALL
-- t_task_record_flow.flow_type  1-进度推进(已生效), 2-事件丢弃(未生效)
SELECT 't_task_record_flow.flow_type' AS col, CAST(`flow_type` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_task_record_flow` GROUP BY `flow_type`
UNION ALL
-- t_task_template.status  状态：0-禁用, 1-启用
SELECT 't_task_template.status' AS col, CAST(`status` AS CHAR) AS value, COUNT(*) AS rows_count FROM `t_task_template` GROUP BY `status`
-- 全局排序，保证结果清晰可读
ORDER BY col, value;