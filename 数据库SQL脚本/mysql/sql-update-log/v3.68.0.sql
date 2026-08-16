-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- =====================================================================================
-- v3.68.0  t_proposal_record 增加 risk_code（风控拦截原因分类）
-- 撰写：2026-08-16
--
-- 背景：提案漏斗要回答「这些钱为什么没发出去」，可拦截原因此前只有 remark 一个自由文本。
--
--   为什么要和 remark 并存，而不是二选一 —— 两者的读者不同，需求正好相反：
--     ① remark 是**给人读的**：「活动过于火爆，当前奖项预算已耗尽」是直接给用户看的话术，
--        将来必然会改，也早晚会带上具体数值（「剩余预算 3.5，本次申请 10」）。
--        但也正因为如此，它是自由文本，GROUP BY 会炸成几百个不同的值；
--     ② risk_code 是**给机器读的**：取值封闭、稳定，改提示文案不会让统计图悄悄裂开。
--   只留文案会统计不了，只留编码会查不了客诉 —— 所以两个都要。
--   这与 v3.50.0 给 t_task_record_flow 加 discard_code 是同一件事、同一个理由。
--
-- ⚠️ 编码本来就存在：RiskResult.ruleCode 的注释写着「用于打日志和数仓分析」，
--    三个 RiskFilter 也一直在传（SINGLE_MAX_AMOUNT_LIMIT / USER_FREQUENCY_LIMIT /
--    GLOBAL_BUDGET_LIMIT）—— 它只是**从来没进过库**，进了日志就没了。
--    取值现已收敛到枚举 RiskBlockCode，新增拦截规则必须往那里加。
--
-- 可重复执行（加列前查 information_schema；回填只补 risk_code IS NULL 的行）。
-- =====================================================================================


-- -------------------------------------------------------------------------------------
-- 一、加列
-- -------------------------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = 't_proposal_record'
                      AND column_name = 'risk_code');

SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE `t_proposal_record` ADD COLUMN `risk_code` varchar(32) NULL DEFAULT NULL COMMENT ''风控拦截分类(给漏斗聚类)：SINGLE_MAX_AMOUNT_LIMIT/USER_FREQUENCY_LIMIT/GLOBAL_BUDGET_LIMIT，仅 status=80 有值'' AFTER `remark`',
               'SELECT ''t_proposal_record.risk_code 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- -------------------------------------------------------------------------------------
-- 二、统计索引
--     漏斗按「时间范围 + 状态 + 分类」聚合，给它一个能直接命中的复合索引。
--     create_time 放最左：所有统计查询都带时间范围，它的选择性也最好
--     （与 v3.50.0 的 idx_t_tsk_flw_stat 同一思路）。
-- -------------------------------------------------------------------------------------
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 't_proposal_record'
                      AND index_name = 'idx_prop_risk_stat');

SET @ddl2 := IF(@idx_exists = 0,
                'ALTER TABLE `t_proposal_record` ADD INDEX `idx_prop_risk_stat` (`create_time`, `status`, `risk_code`)',
                'SELECT ''idx_prop_risk_stat 已存在，跳过'' AS msg');
PREPARE stmt2 FROM @ddl2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;


-- -------------------------------------------------------------------------------------
-- 三、回填历史数据
--
-- ⚠️ 按 remark 的文本特征匹配。这是**一次性**的补偿动作，
--    不要把它固化成常态逻辑 —— 新数据由 Java 侧直接写 risk_code，不依赖文本匹配。
--    （回填规则依赖的正是「文本会变」这个我们要消灭的东西，所以它只在这一刻可信。）
--
-- 只补 status=80 且 risk_code IS NULL 的行，可重复执行。
-- -------------------------------------------------------------------------------------
UPDATE `t_proposal_record`
SET `risk_code` = CASE
    WHEN `remark` LIKE '%单次发奖金额超限%' THEN 'SINGLE_MAX_AMOUNT_LIMIT'
    WHEN `remark` LIKE '%参与太频繁%'       THEN 'USER_FREQUENCY_LIMIT'
    WHEN `remark` LIKE '%预算已耗尽%'
      OR `remark` LIKE '%活动过于火爆%'     THEN 'GLOBAL_BUDGET_LIMIT'
    ELSE NULL END
WHERE `status` = 80 AND `risk_code` IS NULL;


-- =====================================================================================
-- 自查
-- =====================================================================================

-- 1. 回填结果：每个分类各几条
SELECT IFNULL(risk_code, '(未能归类)') AS 分类, COUNT(*) AS 条数
FROM t_proposal_record WHERE status = 80
GROUP BY risk_code ORDER BY 条数 DESC;

-- 2. 🔴 有没有归不了类的（有输出说明回填规则漏了一种文案，需人工确认后补规则）
--    漏斗对这批行会回显 remark 原文，不会用「其它」把它们盖掉。
SELECT id, remark FROM t_proposal_record
WHERE status = 80 AND risk_code IS NULL;

-- 3. 反向自查：非拦截的提案不该有 risk_code（有则说明写入侧串了）
SELECT COUNT(*) AS 非拦截提案误带分类 FROM t_proposal_record
WHERE status <> 80 AND risk_code IS NOT NULL;
