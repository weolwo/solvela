-- ============================================================================
-- v3.58.0  定时任务模块重构 · 第二档（换内核：数据库抢占式调度）
-- ============================================================================
--
-- 配套方案：docs/定时任务模块-重构技术方案.md（v4 终稿）
-- 前置：v3.57.0（第一档）必须先执行。
--
-- 这一档把调度的真源从「各节点内存里的 ScheduledFuture」搬到数据库：
--   next_trigger_time 落库，节点靠一条 UPDATE ... WHERE trigger_version = ? 抢执行权。
--
-- 一个改动同时解决三件事：
--   ① 时钟只剩数据库一个（铁律 9/10），JVM 时钟不再参与任何调度判断；
--   ② 停机期间漏掉的调度在库里看得见（next_trigger_time 已过期），可按策略补跑 ——
--      原来是无痕消失，运营永远不知道昨晚那次统计没跑；
--   ③ pub/sub 降级成纯加速通道，丢消息最多晚一秒生效，不再影响正确性。
--
-- ⚠️ 执行前必须停掉旧版本进程：本脚本会改列名与列类型。
-- ⚠️ 时钟源沿用 v3.38.0 口径（铁律 9）：create_time / update_time 由数据库产生，
--    Java 侧不填充，本脚本不碰这两列的默认值。
-- ============================================================================


-- ############################################################################
-- 一、t_smart_job
-- ############################################################################

-- ----------------------------------------------------------------------------
-- 1.1 业务编码（铁律 8：10 位大写字母+数字，全局唯一）
--
-- 为什么要有它：handler_name 不再唯一（同一个执行器可以挂 N 个任务，靠 param 区分），
-- 于是需要另一个稳定的对外标识 —— 日志、告警、跳转链接都指向它，
-- 而 job_id 是自增主键，跨环境不一致，不适合对外。
-- ----------------------------------------------------------------------------
ALTER TABLE `t_smart_job`
    ADD COLUMN `job_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL
        COMMENT '任务编码：10位大写字母+数字，全局唯一' AFTER `job_id`,
    ADD COLUMN `job_group` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'BUSINESS'
        COMMENT '分组：SYSTEM/DATA/ACTIVITY/OPS/BUSINESS' AFTER `handler_name`;

-- 存量补编码。UUID 取前 10 位并转大写，恰好落在 [A-Z0-9]{10}：
-- REPLACE 掉连字符后是 32 位 hex，只含 0-9a-f，转大写后完全符合 BIZ_CODE_REGEX。
UPDATE `t_smart_job`
   SET `job_code` = UPPER(SUBSTRING(REPLACE(UUID(), '-', ''), 1, 10))
 WHERE `job_code` IS NULL;

ALTER TABLE `t_smart_job`
    MODIFY COLUMN `job_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL
        COMMENT '任务编码：10位大写字母+数字，全局唯一';
CREATE UNIQUE INDEX `uk_job_code` ON `t_smart_job` (`job_code`);


-- ----------------------------------------------------------------------------
-- 1.2 🔴 调度真源：next_trigger_time + trigger_version
--
-- 抢占语义：
--   UPDATE t_smart_job SET next_trigger_time = ?, trigger_version = trigger_version + 1
--    WHERE job_id = ? AND trigger_version = ? AND enabled_flag = 1 AND deleted_flag = 0
-- 影响行数 = 1 才算抢到。后两个条件防「幽灵执行」：
-- 扫描到数据与执行 UPDATE 之间的几毫秒里任务被停用/删除，没有它们照样会被抢占并投递，
-- 而运营的认知是「我明明关了」—— 属于最难自证的一类故障。
-- ----------------------------------------------------------------------------
ALTER TABLE `t_smart_job`
    ADD COLUMN `next_trigger_time` datetime NULL DEFAULT NULL
        COMMENT '下次触发时间：调度的唯一真源，由数据库时钟产生' AFTER `trigger_value`,
    ADD COLUMN `prev_trigger_time` datetime NULL DEFAULT NULL
        COMMENT '上次触发时间' AFTER `next_trigger_time`,
    ADD COLUMN `trigger_version` bigint NOT NULL DEFAULT 0
        COMMENT '抢占乐观锁版本号' AFTER `prev_trigger_time`,
    ADD COLUMN `jitter_seconds` int NOT NULL DEFAULT 0
        COMMENT '打散秒数：按 job_id 确定性偏移，防整点惊群；ONE_TIME 强制 0' AFTER `trigger_version`;


-- ----------------------------------------------------------------------------
-- 1.3 🔴 触发类型：砍掉 FIXED_DELAY
--
-- 它与抢占式调度概念上不相容：FIXED_DELAY 的语义是「上一次**执行结束**后再等 N 秒」，
-- 而抢占发生在任务**开始之前** —— 那一刻根本不知道它什么时候结束。
--
-- 若强行保留只有两条路，都不可接受：
--   ① 按「触发时刻 + N 秒」算 → 那产出的是 FIXED_RATE。名字没变、配置没变、日志没变，
--      语义悄悄换了，正是本项目铁律反复在防的形状；
--   ② 抢占时把 next_trigger_time 推到 2099、执行完回填 → 会让 §7.1 的调度器自监控瞎掉
--      （判据是 next_trigger_time < now - 5min，而 2099 永远不满足）。节点在执行中崩溃、
--      finally 没跑到，任务就**永久死亡且不触发任何告警** —— 最坏的一类故障。
--
-- 代价接近零：CRON 支持秒字段（*/10 * * * * * = 每 10 秒），短周期场景完全覆盖；
-- 「不许与上一次重叠」本就是 block_strategy 的职责，不该由触发类型表达。
--
-- ⚠️ 存量转换的语义变化：从「上次跑完 + N 秒」变成「每 N 秒」。
--    N 远大于任务耗时时两者几乎等价；若某任务耗时接近 N，转换后会更频繁地触发，
--    但 block_strategy=DISCARD（默认）会挡住重叠的那次，不会并发。
-- ----------------------------------------------------------------------------
-- ⚠️ SET 子句里 remark 必须写在 trigger_value **前面**：
--    MySQL 的 UPDATE 从左到右求值，且后面的表达式看到的是**已更新**的值（这点与标准 SQL 不同）。
--    写在后面的话，remark 里记下的就是刚生成的 cron 串，而不是原来的秒数 —— 迁移痕迹当场失真。
UPDATE `t_smart_job`
   SET `remark`        = CONCAT(IFNULL(`remark`, ''), ' [v3.58.0 由 fixed_delay ', `trigger_value`,
                                ' 秒自动转为 cron，语义由「上次跑完+N秒」变为「每N秒」]'),
       `trigger_value` = CONCAT('*/', `trigger_value`, ' * * * * *'),
       `trigger_type`  = 'cron'
 WHERE `trigger_type` = 'fixed_delay'
   AND CAST(`trigger_value` AS UNSIGNED) BETWEEN 1 AND 59;

-- 间隔 >= 60 秒的转成「每 N 分钟」；不能整除 60 的会有取整误差，所以单独标注出来让人复核
UPDATE `t_smart_job`
   SET `remark`        = CONCAT(IFNULL(`remark`, ''), ' [v3.58.0 由 fixed_delay ', `trigger_value`,
                                ' 秒转为按分钟 cron，⚠️ 存在取整误差，请人工复核]'),
       `trigger_value` = CONCAT('0 */', GREATEST(1, FLOOR(CAST(`trigger_value` AS UNSIGNED) / 60)), ' * * * *'),
       `trigger_type`  = 'cron'
 WHERE `trigger_type` = 'fixed_delay';


-- ----------------------------------------------------------------------------
-- 1.4 执行策略（由三档预设展开，见方案 §9.1）
--
-- 🔴 misfire_threshold_sec 必须随档位联动，不能全局写死 60 秒。
--    原因是它与背压跳过直接冲突：池满时任务会被「跳过」留在库里排队，
--    若阈值固定 60 秒，排队超过一分钟就会被误判成 misfire；
--    策略是 SKIP 的话就被静默丢弃了，完全违背背压排队的初衷。
--    危害最大的是 NORMAL 档（SLOW 池 + SKIP），所以它的阈值放宽到 5 分钟。
-- ----------------------------------------------------------------------------
ALTER TABLE `t_smart_job`
    ADD COLUMN `preset_code` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'NORMAL'
        COMMENT '预设档位：LIGHT/NORMAL/HEAVY/CUSTOM，仅记录来源，落库的是展开后的值' AFTER `param`,
    ADD COLUMN `timeout_seconds` int NOT NULL DEFAULT 0
        COMMENT '超时秒数，0=取执行器声明值' AFTER `preset_code`,
    ADD COLUMN `retry_times` int NOT NULL DEFAULT 0
        COMMENT '失败重试次数，仅幂等执行器允许 > 0' AFTER `timeout_seconds`,
    ADD COLUMN `retry_interval` int NOT NULL DEFAULT 30
        COMMENT '重试间隔秒数' AFTER `retry_times`,
    ADD COLUMN `misfire_strategy` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'SKIP'
        COMMENT '错过调度策略：SKIP/FIRE_ONCE' AFTER `retry_interval`,
    ADD COLUMN `misfire_threshold_sec` int NOT NULL DEFAULT 300
        COMMENT '判定错过调度的阈值秒数，随档位联动' AFTER `misfire_strategy`,
    ADD COLUMN `block_strategy` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DISCARD'
        COMMENT '阻塞策略：DISCARD/SERIAL/OVERRIDE' AFTER `misfire_threshold_sec`;


-- ----------------------------------------------------------------------------
-- 1.5 运维与归属
--
-- app_env：🔴 不隔离会怎样 —— 四套环境共用一份 DDL，一旦 dev 连到与 prod 同源的库，
--          dev 机器会去**抢生产任务并执行**。这个坑爆起来极难查，成本远高于加一列。
-- owner_* / source / manual_modified_flag：第三档「活动向导生成任务」才会用，
--          现在一起加是为了避免二次改库（改表要停进程，能少一次是一次）。
-- ----------------------------------------------------------------------------
ALTER TABLE `t_smart_job`
    ADD COLUMN `app_env` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'dev'
        COMMENT '环境标识：只有 env 匹配的节点才会抢这个任务',
    ADD COLUMN `alarm_receiver` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL
        COMMENT '告警接收人，多个逗号分隔',
    ADD COLUMN `continuous_fail_count` int NOT NULL DEFAULT 0
        COMMENT '连续失败次数，成功时清零；用于告警阈值',
    ADD COLUMN `handler_missing_flag` tinyint NOT NULL DEFAULT 0
        COMMENT '执行器在代码中不存在：该任务不会被执行，列表需标红',
    ADD COLUMN `terminal_flag` tinyint NOT NULL DEFAULT 0
        COMMENT 'ONE_TIME 任务执行完置 1，列表默认折叠',
    ADD COLUMN `owner_biz_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL
        COMMENT '归属业务类型：SYSTEM/ACTIVITY（第三档用）',
    ADD COLUMN `owner_biz_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL
        COMMENT '归属业务编码，如活动编码（第三档用）',
    ADD COLUMN `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'MANUAL'
        COMMENT '来源：MANUAL 人工创建 / SYSTEM 向导生成（第三档用）',
    ADD COLUMN `manual_modified_flag` tinyint NOT NULL DEFAULT 0
        COMMENT '衍生任务是否被人工改过：改过的向导不再覆盖（第三档用）';

-- 🔴 扫描主索引。列顺序按等值条件在前、范围条件在后排：
--    app_env / enabled_flag / deleted_flag 都是等值，next_trigger_time 是范围。
CREATE INDEX `idx_next_trigger`
    ON `t_smart_job` (`app_env`, `enabled_flag`, `deleted_flag`, `next_trigger_time`);


-- ############################################################################
-- 二、t_smart_job_log
-- ############################################################################

-- ----------------------------------------------------------------------------
-- 2.1 主键换 bigint
--
-- 执行记录是会真正涨起来的那张表：秒级任务 × 多个任务 × 几年，int 的 21 亿并非遥不可及。
-- （t_smart_job.job_id 保持 int：那是任务**配置**，几百行到头了，
--   换成 bigint 要连带改动全工程的 Integer jobId，代价远大于收益。）
-- ----------------------------------------------------------------------------
ALTER TABLE `t_smart_job_log`
    MODIFY COLUMN `log_id` bigint NOT NULL AUTO_INCREMENT;


-- ----------------------------------------------------------------------------
-- 2.2 🔴 param → param_snapshot，execute_result 拆成摘要与堆栈
--
-- 改名不是洁癖：重试与「一键重跑」必须复现**当时那一次**的参数，
-- 而不是重读可能已被人改过的当前配置。列名叫 param 会诱导人写成后者。
--
-- 拆列同理：原来一列既装「本次处理数据 N 条」这种给运营看的人话，
-- 又装异常堆栈，于是不得不把堆栈截到 1800 —— 摘要和堆栈的长度诉求本就不同。
-- ----------------------------------------------------------------------------
-- 🔴 三步的顺序不能调换：先加新列 → 再搬运并截断存量 → 最后才收窄列宽。
--    直接 CHANGE 成 varchar(512) 的话，严格模式下超长的存量会让整条 ALTER 报错，
--    非严格模式下则被静默截字 —— 后者更糟，堆栈没了还查不出来。
ALTER TABLE `t_smart_job_log`
    ADD COLUMN `error_detail` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL
        COMMENT '失败时的异常堆栈（已截断）' AFTER `execute_result`;

-- 存量：老的 execute_result 在失败记录里装的是堆栈，挪到 error_detail 去
UPDATE `t_smart_job_log`
   SET `error_detail`    = LEFT(`execute_result`, 2000),
       `execute_result`  = NULL
 WHERE `status` = 3 AND `execute_result` IS NOT NULL;

-- 剩下的（成功记录里的人话摘要）截到 512，为下一步收窄列宽做准备
UPDATE `t_smart_job_log` SET `execute_result` = LEFT(`execute_result`, 512)
 WHERE CHAR_LENGTH(`execute_result`) > 512;

ALTER TABLE `t_smart_job_log`
    CHANGE COLUMN `param` `param_snapshot` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL
        COMMENT '执行时的参数快照（不是当前配置）：重试与重跑都复现这一份',
    CHANGE COLUMN `execute_result` `result_summary` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL
        COMMENT '执行结果摘要：执行器返回的人话，给运营看';


-- ----------------------------------------------------------------------------
-- 2.3 🔴 调度语义列
--
-- trigger_time：本次调度的「原定」触发时刻。它是 uk_job_trigger 的组成部分，
--               也是 bizDate 的计算基准 —— 用 now() 算的话，misfire 补跑会处理错日期，
--               而补跑恰恰是 bizDate 存在的意义。
-- retry_seq   ：同一触发点的第几次尝试。重试是**新记录**而不是改旧记录，
--               靠这一列区分；旧记录保留完整的失败现场。
-- fire_time   ：何时该被扫描线程捞起执行。手动触发 = now，重试 = now + interval。
--               🔴 只有 PENDING 记录才有值，其余状态恒 NULL ——
--               FAIL 记录若还带着 fire_time，下一秒就会被再次扫到，变成无限重试。
-- ----------------------------------------------------------------------------
ALTER TABLE `t_smart_job_log`
    ADD COLUMN `app_env` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'dev'
        COMMENT '环境标识：冗余列，避免日志表扫描每秒 join t_smart_job' AFTER `job_name`,
    ADD COLUMN `trigger_source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'SCHEDULE'
        COMMENT '触发来源：SCHEDULE 定时 / MANUAL 手动。无 RETRY —— 重试继承原值，靠 retry_seq 区分' AFTER `trace_id`,
    ADD COLUMN `trigger_time` datetime NULL DEFAULT NULL
        COMMENT '本次调度的原定触发时刻（不是执行时刻）' AFTER `trigger_source`,
    ADD COLUMN `retry_seq` int NOT NULL DEFAULT 0
        COMMENT '同一触发点的第几次尝试，0 为首次' AFTER `trigger_time`,
    ADD COLUMN `biz_date` date NULL DEFAULT NULL
        COMMENT '业务日期：正常调度=触发日+bizDateOffset，重跑时可指定历史日期' AFTER `retry_seq`,
    ADD COLUMN `fire_time` datetime NULL DEFAULT NULL
        COMMENT '何时该被捞起执行。仅 PENDING 有值，其余状态恒 NULL',
    ADD COLUMN `retry_of_log_id` bigint NULL DEFAULT NULL
        COMMENT '本次是哪条记录的重试',
    ADD COLUMN `schedule_delay_ms` bigint NULL DEFAULT NULL
        COMMENT '调度延迟 = 实际开始 - 原定触发。持续增长即为扩容信号';

-- 存量记录补 trigger_time：老数据没有这个概念，用执行开始时间兜底。
-- 必须补齐，否则下面的唯一索引会因为多行 NULL 而失去防重作用
-- （MySQL 的唯一索引允许多个 NULL，那等于没约束）。
UPDATE `t_smart_job_log` SET `trigger_time` = `execute_start_time` WHERE `trigger_time` IS NULL;

ALTER TABLE `t_smart_job_log`
    MODIFY COLUMN `trigger_time` datetime NOT NULL COMMENT '本次调度的原定触发时刻（不是执行时刻）';


-- ----------------------------------------------------------------------------
-- 2.4 🔴 uk_job_trigger：把「不重复执行」从靠逻辑变成靠约束
--
-- 抢占式调度理论上保证单次执行，但网络分区、长 GC 停顿、时钟跳变下仍有窗口。
-- 抢到调度权的节点**先插日志**，插失败（重复键）即说明别人已经跑过这个触发点，直接放弃。
-- 这和本项目已有的 biz_id_required、uk_pool_code 是同一个套路：
-- **把口头约定变成表里的强制契约**。成本只是一个索引。
--
-- 必须含 trigger_source：MANUAL 记录的 trigger_time 取的是点击时刻，
-- 若恰好与该任务的 CRON 触发时刻撞在同一秒，唯一键冲突会让手动执行直接失败。
-- 带上它之后两类记录物理隔离 —— 语义上本就该分开：
-- SCHEDULE 的 trigger_time 是「原定调度时刻」，MANUAL 的是「点击时刻」，不是一个概念。
--
-- ⚠️ 同一秒内连点两次「手动执行」仍会撞键。**这个保留，它是防重特性**，
--    但服务端必须捕获重复键并转译成人话（「1 秒内已触发，请勿重复点击」）——
--    理由同铁律 8：唯一索引直接抛 SQL 异常对运营不友好。
--
-- ⚠️ 建索引前先去重：存量记录补 trigger_time 时用的是 execute_start_time，
--    同一任务同一秒内的多条记录会撞。保留 log_id 最大的那条。
-- ----------------------------------------------------------------------------
DELETE l1 FROM `t_smart_job_log` l1
  JOIN `t_smart_job_log` l2
    ON l1.`job_id`         = l2.`job_id`
   AND l1.`trigger_time`   = l2.`trigger_time`
   AND l1.`retry_seq`      = l2.`retry_seq`
   AND l1.`trigger_source` = l2.`trigger_source`
   AND l1.`log_id`         < l2.`log_id`;

CREATE UNIQUE INDEX `uk_job_trigger`
    ON `t_smart_job_log` (`job_id`, `trigger_time`, `retry_seq`, `trigger_source`);

-- 日志表扫描（手动 + 重试）用
CREATE INDEX `idx_pending` ON `t_smart_job_log` (`app_env`, `status`, `fire_time`);
-- 僵尸记录扫描用
CREATE INDEX `idx_running` ON `t_smart_job_log` (`status`, `execute_start_time`);


-- ############################################################################
-- 三、初始化 next_trigger_time
--
-- 存量任务的下次触发时间此前只活在各节点内存里，现在必须落库，否则新调度器扫不到它们。
-- 统一置为 now()：启动后第一轮扫描就会全部触发一次，然后各自按 cron 排下一次。
-- 这比精确回填「本该在什么时候触发」简单得多，代价是升级后会多跑一轮 ——
-- 而所有任务都应当是可重复执行的（不幂等的任务本就不该配重试），这个代价可以接受。
--
-- ⚠️ 若某个任务重跑一次代价很大（例如发奖），升级前先把它停用，升级后再手工开。
-- ############################################################################
UPDATE `t_smart_job`
   SET `next_trigger_time` = NOW()
 WHERE `next_trigger_time` IS NULL
   AND `deleted_flag` = 0;


-- ############################################################################
-- 四、自查
-- ############################################################################
-- 1) 还有没有残留的 fixed_delay（应为 0）
-- SELECT COUNT(*) FROM t_smart_job WHERE trigger_type = 'fixed_delay';
--
-- 2) 被自动转换的任务，人工复核一遍 cron 是否符合预期
-- SELECT job_id, job_name, trigger_value, remark FROM t_smart_job WHERE remark LIKE '%v3.58.0%';
--
-- 3) 唯一索引是否真的建上了（存量重复没清干净会建失败）
-- SELECT index_name, COUNT(*) FROM information_schema.statistics
--  WHERE table_schema = 'smart_admin_v3' AND table_name = 't_smart_job_log'
--    AND index_name IN ('uk_job_trigger','idx_pending','idx_running') GROUP BY index_name;
--
-- 4) 所有未删除任务都应有 next_trigger_time
-- SELECT COUNT(*) FROM t_smart_job WHERE deleted_flag = 0 AND next_trigger_time IS NULL;
--
-- 5) app_env 是否与各环境的 smart.job.env 配置一致（不一致的任务永远不会被抢到）
-- SELECT app_env, COUNT(*) FROM t_smart_job GROUP BY app_env;
