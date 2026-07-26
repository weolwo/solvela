-- 奖池坑位映射表新增兜底标记：回显时恢复「兜底自动配平」编辑态；抽奖引擎在命中奖项库存不足时降级到兜底奖项
ALTER TABLE `t_pool_prize_mapping`
    ADD COLUMN `is_fallback` tinyint NOT NULL DEFAULT 0 COMMENT '是否兜底奖项：1-兜底，每池最多一个' AFTER `probability`;
