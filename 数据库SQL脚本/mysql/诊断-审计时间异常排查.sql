-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- ============================================================
-- 诊断：排查 create_time / update_time 是否被人为写入过异常值
--
-- 背景：AddForm / UpdateForm 曾经带着 createTime / updateTime / createBy / updateBy 四个字段，
--       经 SolvelaBeanUtil.copy 自动拷进实体后随 INSERT/UPDATE 落库，
--       会覆盖掉 DDL 的 DEFAULT CURRENT_TIMESTAMP / ON UPDATE CURRENT_TIMESTAMP。
--       该入口已在 36 个 Form DTO 上摘除，本脚本用于核对历史数据。
--
-- ⚠️ 本脚本只做 SELECT，不修改任何数据。
-- ⚠️ 执行前先确认会话时区与应用一致（交接文档铁律 10）：
--       SELECT @@session.time_zone, now();
--    否则统计口径会整体偏 8 小时，把正常数据误判成异常。
-- ============================================================

-- ------------------------------------------------------------
-- 1）总览：哪些表存在「更新时间早于创建时间」——数据库自己生成绝不会出现这种情况
-- ------------------------------------------------------------
SELECT 'A. update_time < create_time（逻辑上不可能，几乎可以确定是人为写入）' AS check_item;

SELECT table_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND column_name = 'create_time'
GROUP BY table_name;
-- 把上面列出的表逐个代入下面这句（示例用 t_task_config）：

SELECT * FROM t_task_config      WHERE update_time < create_time LIMIT 50;
SELECT * FROM t_task_record      WHERE update_time < create_time LIMIT 50;
SELECT * FROM t_task_template    WHERE update_time < create_time LIMIT 50;
SELECT * FROM t_task_prize_mapping WHERE update_time < create_time LIMIT 50;

-- ------------------------------------------------------------
-- 2）create_time 为 NULL：说明 INSERT 时显式写入了 null，把 DDL 默认值挡掉了
-- ------------------------------------------------------------
SELECT 'B. create_time IS NULL' AS check_item;

SELECT 't_task_config' AS t, COUNT(*) AS cnt FROM t_task_config      WHERE create_time IS NULL
UNION ALL SELECT 't_task_record',       COUNT(*) FROM t_task_record       WHERE create_time IS NULL
UNION ALL SELECT 't_task_template',     COUNT(*) FROM t_task_template     WHERE create_time IS NULL
UNION ALL SELECT 't_task_prize_mapping',COUNT(*) FROM t_task_prize_mapping WHERE create_time IS NULL;

-- ------------------------------------------------------------
-- 3）create_time 是整点（00:00:00）：前端选择器只选日期时会补 0 点，
--    数据库自动生成的时间几乎不可能正好落在整秒的 00:00:00
-- ------------------------------------------------------------
SELECT 'C. create_time 恰好为当天 00:00:00（疑似前端选择器写入）' AS check_item;

SELECT 't_task_config' AS t, COUNT(*) AS cnt FROM t_task_config      WHERE TIME(create_time) = '00:00:00'
UNION ALL SELECT 't_task_record',       COUNT(*) FROM t_task_record       WHERE TIME(create_time) = '00:00:00'
UNION ALL SELECT 't_task_template',     COUNT(*) FROM t_task_template     WHERE TIME(create_time) = '00:00:00'
UNION ALL SELECT 't_task_prize_mapping',COUNT(*) FROM t_task_prize_mapping WHERE TIME(create_time) = '00:00:00';

-- ------------------------------------------------------------
-- 4）建表检查：确认这两列确实带了 DDL 默认值（新建表容易漏，交接文档铁律 9）
-- ------------------------------------------------------------
SELECT 'D. 缺少 DEFAULT CURRENT_TIMESTAMP / ON UPDATE 的表' AS check_item;

SELECT table_name, column_name, column_default, extra
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND column_name IN ('create_time', 'update_time')
  AND (
        (column_name = 'create_time' AND (column_default IS NULL OR column_default NOT LIKE '%CURRENT_TIMESTAMP%'))
     OR (column_name = 'update_time' AND extra NOT LIKE '%on update%')
      );

-- ------------------------------------------------------------
-- 订正建议（确认异常后再执行，此处只给模板、默认注释掉）
--   把被人为写坏的 create_time 回填为该行最早的可信时间。
--   ⚠️ 没有通用的正确答案，务必先人工确认样本再决定口径。
-- ------------------------------------------------------------
-- UPDATE t_task_config SET create_time = update_time WHERE update_time < create_time;

-- ============================================================
-- 实跑结果与订正（2026-08-02）
--
-- 排查结论：
--   A. update_time < create_time            -> 无
--   B. create_time IS NULL                  -> t_task_prize_mapping 2 行 ⚠️
--   C. create_time 恰为 00:00:00            -> 无
--   D. 缺少 DDL 默认值的表                   -> 无
--
-- 成因已定位：实体模板 Entity.java.vm 里对 create_time / update_time
-- 硬编码了 @TableField(fill = ...)。MyBatis-Plus 3.5.17 的
-- TableFieldInfo#getInsertSqlPropertyMaybeIf 中 withInsertFill 为 true 时直接 return，
-- 不生成 <if> 判空，于是 null 被显式写进 INSERT，把 DDL 默认值挡掉。
-- 模板、生成器 import、以及 MybatisPlusFillHandler 里那段说反了的注释均已修正。
--
-- ⚠️ 实际数据：这 2 行的 create_time 与 update_time **都是 NULL**，
--    （update_time 的 DDL 是 DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP，
--      能变成 NULL 只可能是被显式写入 —— 对应 @TableField(fill = FieldFill.INSERT_UPDATE)，
--      INSERT_UPDATE 同样让 withInsertFill 为 true。诊断闭环。）
--    所以行内已无可用时间，用 COALESCE(update_time, ...) 是不成立的。
--
--    改用父表回填：奖励映射不可能早于它所属的任务配置，且两者是同一次提交里
--    一起落库的（任务向导拆解组装 taskConfig + prizeMappingList 主子表 DTO），
--    取 t_task_config.create_time 是可解释、可复查的口径。
-- ============================================================

-- 步骤 1：先确认父行存在且时间可信（预期返回 2 行，且 cfg_create_time 非空）
SELECT m.id            AS mapping_id,
       m.task_config_id,
       c.task_name,
       c.create_time   AS cfg_create_time,
       c.update_time   AS cfg_update_time
  FROM t_task_prize_mapping m
  LEFT JOIN t_task_config c ON c.id = m.task_config_id
 WHERE m.create_time IS NULL;

-- 步骤 1 实跑结果（2026-08-02）：父行时间完好，回填可行
--   mapping_id=4   task_config_id=6   任务名「P3验收-内嵌提交测试」  cfg_create_time=2026-07-30 08:41:29
--   mapping_id=55  task_config_id=50  任务名「ggg」                  cfg_create_time=2026-08-01 18:24:04
--
-- ⚠️ 这两条任务名一看就是测试数据（P3 验收那轮 + 随手敲的 ggg）。
--    若确认是开发库垃圾数据，选方案 B 直接删更干净；生产库或不确定时选方案 A。

-- ------------------------------------------------------------
-- 方案 A：从父表回填（保守，保留数据）
-- ------------------------------------------------------------
-- UPDATE t_task_prize_mapping m
--   JOIN t_task_config c ON c.id = m.task_config_id
--    SET m.create_time = c.create_time,
--        m.update_time = COALESCE(m.update_time, c.update_time, c.create_time)
--  WHERE m.create_time IS NULL
--    AND c.create_time IS NOT NULL;

-- ------------------------------------------------------------
-- 方案 B：确认是测试数据后直接删除
--   ⚠️ 删之前再确认一次这两条任务配置本身要不要一并清理
--      （t_task_config id = 6, 50），否则会留下没有奖励映射的孤儿配置。
-- ------------------------------------------------------------
-- DELETE FROM t_task_prize_mapping WHERE id IN (4, 55);

-- ------------------------------------------------------------
-- 步骤 3：处理后复查，预期 0 行
-- ------------------------------------------------------------
-- SELECT * FROM t_task_prize_mapping WHERE create_time IS NULL OR update_time IS NULL;
