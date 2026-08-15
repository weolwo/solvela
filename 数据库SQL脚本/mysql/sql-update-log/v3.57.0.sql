-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- ============================================================================
-- v3.57.0  定时任务模块重构 · 第一档（止血与接线）
-- ============================================================================
--
-- 配套方案：docs/定时任务模块-重构技术方案.md（v4 终稿）
-- 本档只做「不改调度模型」的止血改动；抢占式调度所需的
-- next_trigger_time / trigger_version / uk_job_trigger 等列在第二档（v3.58.0）加。
--
-- 本脚本改三件事：
--   ① t_smart_job.job_class        → handler_name  （按执行器名注册，与类名解耦）
--   ② t_smart_job_log.success_flag → status        （布尔换多态，并新增 trace_id）
--   ③ 清理两条示例任务数据（对应的 sample 类已随重构删除）
--
-- ⚠️ 时钟源沿用 v3.38.0 口径（铁律 9）：create_time / update_time 由数据库产生，
--    Java 侧不填充，本脚本不碰这两列的默认值。
--
-- ⚠️ 执行前请确认已停掉旧版本进程：改列名期间旧代码会持续报 Unknown column。
-- ============================================================================


-- ----------------------------------------------------------------------------
-- ① t_smart_job：job_class → handler_name
--
-- 为什么必须改：原实现拿 bean.getClass().getName() 去匹配这一列，而执行类只要挂了
-- @Transactional / @Async 就会被 CGLIB 代理，类名变成 Xxx$$SpringCGLIB$$0，
-- 与库里的全限定类名永远对不上 —— 匹配不到时代码是一句 continue：
-- 不报错、不打日志、任务从此不跑，而新增任务时的 Class.forName 校验用的是原类名，
-- 校验还必过。「保存成功 + 永不执行」，全程零信号。
--
-- 现在这一列存 @SmartJobHandler(name = "...") 声明的名字，与类名彻底解耦，
-- 且启动时会与代码做双向对账，对不上直接打 ERROR 点名。
--
-- 长度从 200 收到 64：存的不再是全限定类名，而是一个短名字。
-- ----------------------------------------------------------------------------
ALTER TABLE `t_smart_job`
    CHANGE COLUMN `job_class` `handler_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL
        COMMENT '执行器名称，对应 @SmartJobHandler#name()';

-- 同一个执行器允许挂多个任务（靠 param 区分），所以这一列**不加**唯一索引。
-- 原代码在 Service 层禁止「同一执行类配多个任务」，该限制已取消：
-- 它让「A 活动每天 2 点跑、B 活动每小时跑」只能靠复制一个类出来，对运营是硬伤。
CREATE INDEX `idx_handler_name` ON `t_smart_job` (`handler_name`);


-- ----------------------------------------------------------------------------
-- ② t_smart_job_log：success_flag → status
--
-- 为什么必须改：布尔表达不了「执行中」。原实现只好在执行**开始前**先写
-- success_flag = 1，于是进程一崩，库里就永久留着一条**假的「成功」**记录 ——
-- 运营看到的是绿的，实际那次根本没跑完；这条假记录还会让
-- 「上一次是否仍在执行」的判断永久误判。
--
-- 取值（对齐 SmartJobExecuteStatusEnum）：
--   0-待执行  1-执行中  2-成功  3-失败  4-超时中断
--   5-阻塞丢弃  6-错过调度  7-中断        ← 5~7 是第二档才会写入，值先占好，免得二次改库
-- ----------------------------------------------------------------------------
ALTER TABLE `t_smart_job_log`
    CHANGE COLUMN `success_flag` `status` tinyint NOT NULL DEFAULT 1
        COMMENT '执行状态：0-待执行 1-执行中 2-成功 3-失败 4-超时中断 5-阻塞丢弃 6-错过调度 7-中断';

-- 存量映射：旧的 1(成功) → 2(SUCCESS)，0(失败) → 3(FAIL)。
-- 这两条 UPDATE 的先后无所谓，也不需要中转值 —— 目标值域 {2,3} 与源值域 {0,1} 不相交，
-- 谁先跑都不会把已转换的行再转一次。（这正是新枚举刻意把 SUCCESS 排到 2 而不是复用 1 的原因之一。）
UPDATE `t_smart_job_log` SET `status` = 3 WHERE `status` = 0;
UPDATE `t_smart_job_log` SET `status` = 2 WHERE `status` = 1;

-- 链路追踪 id：与 web 请求共用 logback 里已有的 %X{traceId}，
-- 定时任务打的业务日志从此能和接口请求走同一套检索
ALTER TABLE `t_smart_job_log`
    ADD COLUMN `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL
        COMMENT '链路追踪id' AFTER `job_name`;

-- 按任务查执行记录是最高频的查询（后台点开任意任务就是它）
CREATE INDEX `idx_job_time` ON `t_smart_job_log` (`job_id`, `execute_start_time`);


-- ----------------------------------------------------------------------------
-- ③ 清理示例任务
--
-- net.lab1024.sa.base.module.support.job.sample.SmartJobSample1 / 2 已随重构删除。
-- 这两条配置留着的话，启动对账会把它们报成「handler 在代码中不存在」——
-- 那是对的行为，但用两条示例数据去触发它没有意义。
--
-- ⚠️ 若你们的生产库里有真实任务，它们的 handler_name 现在仍是旧的全限定类名，
--    启动日志会逐条点名。处理方式：给对应执行类补上 @SmartJobHandler 注解，
--    再把这一列的值改成注解里的 name。示例：
--      UPDATE t_smart_job SET handler_name = 'fileOrphanClean'
--       WHERE handler_name = 'net.lab1024.sa.base.xxx.FileOrphanCleanJob';
-- ----------------------------------------------------------------------------
-- 先删日志再删配置：反过来的话第一条 DELETE 之后就查不到 job_id 了，日志会变成孤儿
DELETE FROM `t_smart_job_log`
 WHERE `job_id` IN (SELECT `job_id` FROM `t_smart_job`
                     WHERE `handler_name` LIKE 'net.lab1024.sa.base.module.support.job.sample.%');

DELETE FROM `t_smart_job`
 WHERE `handler_name` LIKE 'net.lab1024.sa.base.module.support.job.sample.%';


-- ----------------------------------------------------------------------------
-- 自查
-- ----------------------------------------------------------------------------
-- 1) 列是否都在
-- SELECT column_name, column_type, column_comment FROM information_schema.columns
--  WHERE table_schema = 'smart_admin_v3' AND table_name IN ('t_smart_job','t_smart_job_log')
--    AND column_name IN ('handler_name','status','trace_id');
--
-- 2) 还有哪些任务的 handler_name 是旧的全限定类名（这些启动后会被点名）
-- SELECT job_id, job_name, handler_name FROM t_smart_job
--  WHERE deleted_flag = 0 AND handler_name LIKE '%.%';
--
-- 3) 存量状态映射是否正确（不应再有 0 或 1 之外的意外值）
-- SELECT status, COUNT(*) FROM t_smart_job_log GROUP BY status;
