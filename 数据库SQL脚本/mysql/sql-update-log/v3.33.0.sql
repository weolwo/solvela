-- 任务模板表新增默认触发事件列：模板级建议值，任务配置向导中可覆盖
ALTER TABLE `t_task_template`
    ADD COLUMN `trigger_event` varchar(32) NULL DEFAULT NULL COMMENT '默认触发事件：模板建议值，向导中可覆盖' AFTER `task_type`;
