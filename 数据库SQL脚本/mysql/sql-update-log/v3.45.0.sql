-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- =====================================================================================
-- v3.45.0  公共派发链路两处修正：提案来源类型 + 券名称
-- 撰写：2026-08-01
--
-- 这两处都是<b>既有缺陷</b>，抽奖 / 彩票 / 任务三条链路同样受影响，不是任务模块引入的。
-- 详见 docs/任务中台-改进技术方案.md §9.4。
--
-- 本脚本可重复执行（加列前先查 information_schema）。
-- =====================================================================================


-- -------------------------------------------------------------------------------------
-- 一、t_proposal_record 增加 asset_name（资产展示名）
--
-- 🔴 为什么必须加这一列，而不是让账务侧回查营销域：
--    CouponAssetHandler 早先就是反查 t_prize_log 拿名称的，后来被刻意改掉了 ——
--    那是「账务域依赖营销域」的错误依赖方向，拆微服务时会直接卡住
--    （见该类第 42~44 行注释，依赖方向已翻转为「营销 -> 账务」）。
--    但翻转之后没有补上「名称」这条信息的搬运通道，于是 handler 退而用了 proposal.remark，
--    而 remark 在 ProposalRecordService.saveProposal 里被固定写成「提案生成成功」——
--    实测发出去的券 coupon_name 全都是「提案生成成功」，这是<b>用户可见</b>的错误文案。
--
--    正解是让提案自己携带展示名：营销侧知道奖品叫什么，主动传下来，账务侧只认自己收到的字段。
--
-- 可空：存量提案没有这个值；账务侧取不到时回退用 asset_ref（券模编码），不会因此发不出去。
-- -------------------------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = 't_proposal_record'
                      AND column_name = 'asset_name');

SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE `t_proposal_record` ADD COLUMN `asset_name` varchar(128) NULL DEFAULT NULL COMMENT ''资产展示名（券名/商品名）：由营销侧传入，避免账务域反查营销域'' AFTER `asset_ref`',
               'SELECT ''t_proposal_record.asset_name 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- -------------------------------------------------------------------------------------
-- 二、source_type 的注释补上 LOTTERY
--
-- 四个 @PrizeStrategy handler 此前都硬编码 EventTypeEnum.LOTTERY_DRAW，
-- 于是任务发的奖在提案表里被记成「彩票抽奖」，抽奖发的也一样。
-- 而 DDL 注释与 ProposalRecordAddForm 的 @Schema 写的一直是 TASK/DRAW/MANUAL
-- —— 代码与契约从第一天起就对不上，只是没人拿这个字段做判据，所以一直没暴露。
-- 现在改为由 ProposalSourceResolver 从 t_activity_config.activity_type 推导，
-- 取值集合相应补上 LOTTERY。
--
-- ⚠️ MODIFY COLUMN 时原定义 varchar(32) NOT NULL 已原样抄全 ——
--    漏一个 NOT NULL 就等于顺手把约束放开了（v3.43.0 踩过同一件事）。
-- -------------------------------------------------------------------------------------
ALTER TABLE `t_proposal_record`
    MODIFY COLUMN `source_type` varchar(32) NOT NULL
        COMMENT '来源：TASK(任务), DRAW(抽奖), LOTTERY(彩票), MANUAL(人工)';


-- -------------------------------------------------------------------------------------
-- 三、存量数据说明（刻意不迁移，读前先看）
--
-- 存量 source_type 全是 'LOTTERY_DRAW'，且其中混着抽奖与彩票两种来源，
-- 仅凭这一列<b>无法区分</b>，要区分得回连 t_prize_log.activity_code -> t_activity_config。
-- 不做自动迁移的理由：
--   ① uk_prop_source (source_type, asset_type, source_biz_id) 是幂等键，
--      批量改 source_type 等于改幂等命名空间，收益为零、风险不小；
--   ② 这些是压测与联调产生的数据，本就要清理，没有迁移价值。
--
-- 若确实想把存量归位（生产环境慎用，先备份），可用下面这条按活动类型回填：
--
-- UPDATE t_proposal_record p
--   JOIN t_prize_log l ON l.external_biz_no = p.source_biz_id
--   JOIN t_activity_config a ON a.activity_code = l.activity_code
--    SET p.source_type = CASE a.activity_type
--                            WHEN 'DRAW' THEN 'DRAW'
--                            WHEN 'TASK' THEN 'TASK'
--                            WHEN 'LOTTERY' THEN 'LOTTERY'
--                            ELSE 'MANUAL' END
--  WHERE p.source_type = 'LOTTERY_DRAW';
-- -------------------------------------------------------------------------------------


-- 自查：列已就位
SELECT column_name, column_type, is_nullable, column_comment
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 't_proposal_record'
  AND column_name IN ('asset_ref', 'asset_name', 'source_type');
