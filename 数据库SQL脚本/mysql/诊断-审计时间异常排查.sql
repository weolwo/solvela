-- ============================================================
-- 诊断：排查 create_time / update_time 是否被人为写入过异常值
--
-- 背景：AddForm / UpdateForm 曾经带着 createTime / updateTime / createBy / updateBy 四个字段，
--       经 SmartBeanUtil.copy 自动拷进实体后随 INSERT/UPDATE 落库，
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
