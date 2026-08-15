-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- =====================================================================================
-- v3.50.0  t_task_record_flow 增加 discard_code（丢弃原因分类）
-- 方案：docs/营销中台-数据统计方案.md §5
-- 撰写：2026-08-01
--
-- 为什么要和 discard_reason 并存，而不是二选一 —— 两者的读者不同，需求正好相反：
--   discard_reason 给人读（客诉自证）：必须带具体数值，「单笔金额 99 未达门槛 100」
--     才回答得了客服的问题。但正因为带数值，它是自由文本，GROUP BY 会炸成几百个不同的值。
--   discard_code   给机器读（大屏聚类）：取值封闭稳定，改提示文案不会让统计图悄悄裂开。
-- 只留文本会统计不了，只留码会查不了客诉。
--
-- 🔴 这个前置越晚补越贵：流水表在持续写入，晚补要回填的历史数据只会越来越多。
--
-- 可重复执行（加列前查 information_schema；回填只补 discard_code IS NULL 的行）。
-- =====================================================================================


-- -------------------------------------------------------------------------------------
-- 一、加列
-- -------------------------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = 't_task_record_flow'
                      AND column_name = 'discard_code');

SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE `t_task_record_flow` ADD COLUMN `discard_code` varchar(32) NULL DEFAULT NULL COMMENT ''丢弃原因分类(给大屏聚类)：AMOUNT_MISSING/AMOUNT_BELOW_MIN/STREAK_SAME_DAY/RECORD_NOT_RUNNING/AUDIENCE_MISMATCH/AUDIENCE_UNKNOWN/ROUND_LIMIT_EXCEEDED/CONFIG_INVALID/POOL_REJECTED'' AFTER `after_metric`',
               'SELECT ''t_task_record_flow.discard_code 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- -------------------------------------------------------------------------------------
-- 二、统计索引
--     大屏板块四按「时间范围 + 类型 + 分类」聚合，给它一个能直接命中的复合索引。
--     放 create_time 在最左：所有统计查询都带时间范围，它的选择性也最好。
-- -------------------------------------------------------------------------------------
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 't_task_record_flow'
                      AND index_name = 'idx_t_tsk_flw_stat');

SET @ddl2 := IF(@idx_exists = 0,
                'ALTER TABLE `t_task_record_flow` ADD INDEX `idx_t_tsk_flw_stat` (`create_time`, `flow_type`, `discard_code`)',
                'SELECT ''idx_t_tsk_flw_stat 已存在，跳过'' AS msg');
PREPARE stmt2 FROM @ddl2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;


-- -------------------------------------------------------------------------------------
-- 三、回填历史数据
--
-- ⚠️ 按 discard_reason 的文本特征匹配。这是<b>一次性</b>的补偿动作，
--    不要把它固化成常态逻辑 —— 新数据由 Java 侧直接写 discard_code，不依赖文本匹配。
--    （回填规则依赖的正是「文本会变」这个我们要消灭的东西，所以它只在这一刻可信。）
--
-- 只补 discard_code IS NULL 的行，可重复执行。
-- -------------------------------------------------------------------------------------
UPDATE `t_task_record_flow`
SET `discard_code` = CASE
    WHEN `discard_reason` LIKE '%未达门槛%'            THEN 'AMOUNT_BELOW_MIN'
    WHEN `discard_reason` LIKE '%未携带有效金额%'      THEN 'AMOUNT_MISSING'
    WHEN `discard_reason` LIKE '%当日已计入连续进度%'  THEN 'STREAK_SAME_DAY'
    WHEN `discard_reason` LIKE '%未告知会员属性%'      THEN 'AUDIENCE_UNKNOWN'
    WHEN `discard_reason` LIKE '%仅限%会员参与%'       THEN 'AUDIENCE_MISMATCH'
    WHEN `discard_reason` LIKE '%参与次数已达上限%'    THEN 'ROUND_LIMIT_EXCEEDED'
    WHEN `discard_reason` LIKE '%已不在进行中%'
      OR `discard_reason` LIKE '%已不可推进%'          THEN 'RECORD_NOT_RUNNING'
    WHEN `discard_reason` LIKE '%任务类型未配置或非法%'
      OR `discard_reason` LIKE '%的进度策略实现%'      THEN 'CONFIG_INVALID'
    WHEN `discard_reason` LIKE '%线程池队列已满%'      THEN 'POOL_REJECTED'
    ELSE NULL END
WHERE `flow_type` = 2 AND `discard_code` IS NULL;


-- =====================================================================================
-- 自查
-- =====================================================================================

-- 1. 回填结果：每个分类各几条
SELECT IFNULL(discard_code, '(未能归类)') AS 分类, COUNT(*) AS 条数
FROM t_task_record_flow WHERE flow_type = 2
GROUP BY discard_code ORDER BY 条数 DESC;

-- 2. 🔴 有没有归不了类的（有输出说明回填规则漏了一种文案，需人工确认后补规则）
SELECT id, discard_reason FROM t_task_record_flow
WHERE flow_type = 2 AND discard_code IS NULL;

-- 3. 反向自查：推进流水不该有 discard_code（有则说明写入侧串了）
SELECT COUNT(*) AS 推进流水误带分类 FROM t_task_record_flow
WHERE flow_type = 1 AND discard_code IS NOT NULL;

-- 4. 索引是否就位
SELECT index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 't_task_record_flow'
GROUP BY index_name;
