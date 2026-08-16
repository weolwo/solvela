-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- =====================================================================================
-- v3.64.0  抽奖引擎去掉「抽奖成本」：移除 t_prize_pool_config 的 cost_asset_type / cost_value
-- 撰写：2026-08-16
--
-- 背景：抽一次消耗什么、消耗多少，不是抽奖引擎该决定的事。
--   它和「去哪个奖池抽」是同一类决策 —— 属于业务规则，由上游算完再调进来。
--   把单价钉死在奖池上有三个具体的坏处：
--
--   ① 定价是业务规则，不是奖池属性。
--      同一个奖池对新人免费、对老用户收 100 分、活动期间半价，都是完全正常的诉求；
--      单价长在奖池行上，这类规则一条都写不出来，只能靠复制出一堆只有价格不同的奖池。
--
--   ② 三选一的配置项实际只有一个值可用。
--      cost_asset_type 名义上支持 CREDIT / TICKET / NONE，而 DrawExecuteService 里
--      TICKET 直接返回「抽奖券消耗类型暂未开放」，CREDIT 恒定映射钱包 SCORE 资产。
--      运营在下拉里能选三个，选中两个会失败或没区别。
--
--   ③ 与彩票模块不一致。TicketIssueService 早就是纯派发引擎，类注释明写
--      「消耗多少积分、单人限购几张，都由上游业务算完再调进来」。
--      两个同类引擎各写一套扣减逻辑，迟早漂移。
--
--   顺带修掉一个存量隐患：开发库里两个奖池都是 cost_asset_type='CREDIT' 且 cost_value=0.0000，
--   而扣减处判 costValue.signum() <= 0 直接返回不扣 —— 配置上写着「消耗积分」，
--   运行时一分不扣。这种「配了但不生效」的矛盾，随字段一起消失。
--
-- ⚠️ 契约变更：/drawPrizeLog/execute 不再扣费，因此也不再退费。
--    此前无货（返回「手慢了，奖品已被抽完」）时会自动退还已扣积分，现在不会了。
--    上游若在调用前扣了资产，需要按返回值自行退还 —— 判断依据就是返回值，不需要额外接口。
--    当前开发库两个奖池的 cost_value 都是 0，实际从未扣过费，所以本次变更没有存量影响。
--
--   Java 侧 deductDrawCost / refundDrawCost 及 MemberWalletService 依赖已在同一提交里删除。
--
-- 可重复执行（列不存在时跳过）。
-- =====================================================================================

-- ---------- 移除消耗资产类型 ----------
-- MySQL 没有 DROP COLUMN IF EXISTS，用 information_schema 判存在性 + 动态 SQL 保证可重放。
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_prize_pool_config'
       AND column_name = 'cost_asset_type'
);
SET @ddl := IF(@col_exists > 0,
    'ALTER TABLE `t_prize_pool_config` DROP COLUMN `cost_asset_type`',
    'SELECT "cost_asset_type 已不存在，跳过" AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ---------- 移除消耗数值 ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_prize_pool_config'
       AND column_name = 'cost_value'
);
SET @ddl := IF(@col_exists > 0,
    'ALTER TABLE `t_prize_pool_config` DROP COLUMN `cost_value`',
    'SELECT "cost_value 已不存在，跳过" AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ---------- 自查 ----------
-- 以下应返回 0 行：
-- SELECT column_name FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name = 't_prize_pool_config'
--    AND column_name IN ('cost_asset_type','cost_value');
--
-- 剩余列应为：id, tenant_id, activity_code, pool_code, pool_name,
--            reset_period, draw_mode, script_id, status, create_by, create_time, update_by, update_time
-- SELECT column_name FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name = 't_prize_pool_config' ORDER BY ordinal_position;
