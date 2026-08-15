-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- 修复奖励派发链路的两个阻断性缺陷（2026-07-26 抽奖压测暴露）
--
-- 现象：500 次抽奖 263 次中奖，t_prize_log 却是 0 行 —— 派奖链路整条没落地。
-- 派发走 @TransactionalEventListener(AFTER_COMMIT)，抽奖事务已提交，
-- 所以插入失败只在日志里报错，对抽奖主链路完全无感，非常容易被漏掉。

-- ---------- 1.【阻断】fail_reason 是 NOT NULL 且无默认值 ----------
-- PrizeDispatchHandler.buildPrizeLog() 不会设置 failReason（正常发奖本就没有异常原因），
-- MyBatis-Plus 默认策略会把 null 字段从 INSERT 里省略，
-- 而 MySQL 开着 STRICT_TRANS_TABLES，于是每次插入都抛
--   Field 'fail_reason' doesn't have a default value
-- 一个叫「异常原因」的列本来就不该 NOT NULL —— 没异常时它就该是 NULL。
ALTER TABLE `t_prize_log`
    MODIFY COLUMN `fail_reason` varchar(128) NULL DEFAULT NULL COMMENT '异常原因：发奖失败时才有值';

-- ---------- 2.【防重失效】uk_external_biz 唯一索引缺失 ----------
-- PrizeDispatchHandler 用 catch (DuplicateKeyException) 做跨系统防重，
-- 抽奖侧把 traceId 作为 sourceBizId 传进来（DrawExecuteService.publishPrizeEvent），
-- 但 t_prize_log 上压根没有这个唯一索引，防重实际是空转 —— 事件重投就会重复发奖。
-- external_biz_no 可为 NULL，MySQL 唯一索引允许多个 NULL，不影响没有外部单号的记录。
-- ⚠️ 若库中已有重复 external_biz_no，本语句会失败，先查：
--   SELECT external_biz_no, COUNT(*) c FROM t_prize_log
--   WHERE external_biz_no IS NOT NULL GROUP BY external_biz_no HAVING c > 1;
ALTER TABLE `t_prize_log`
    ADD UNIQUE KEY `uk_external_biz` (`external_biz_no`);
