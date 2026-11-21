-- 生产升级：第二批 String 类型列在 Java 里改成枚举，【发版前】在每个环境跑一次。
--
-- 只读，不改任何数据。每一段的结果都必须是空集：
-- 新版代码按枚举读这些列，库里只要有一行取值不在枚举里（大小写不同、旧写法、拼错），读到那一行就直接报错。
-- 有结果就先订正数据，再发版。第一批（prize_type / asset_type）见同目录「资产类型枚举化-发版前核对」。
--
-- ⚠️ 必须 BINARY 比较：库的排序规则大小写不敏感，"draw" 会被当成 "DRAW" 放过，而枚举解析是大小写敏感的。
-- 以后给对应枚举加值，这里的 IN 列表跟着加。

-- ActivityTypeEnum：solvela-contract/src/main/java/solvela/enums/ActivityTypeEnum.java
SELECT 't_activity_config.activity_type' AS col, activity_type AS val, COUNT(*) AS cnt FROM t_activity_config
WHERE BINARY activity_type NOT IN ('BASIC', 'DRAW', 'TASK', 'LOTTERY') GROUP BY activity_type
UNION ALL
-- t_prize_log.activity_type 允许为空（存量行没有这个值），空值不算非法
SELECT 't_prize_log.activity_type', activity_type, COUNT(*) FROM t_prize_log
WHERE activity_type IS NOT NULL AND BINARY activity_type NOT IN ('BASIC', 'DRAW', 'TASK', 'LOTTERY') GROUP BY activity_type
UNION ALL
-- ProposalSourceTypeEnum：solvela-contract/src/main/java/solvela/enums/ProposalSourceTypeEnum.java
SELECT 't_proposal_record.source_type', source_type, COUNT(*) FROM t_proposal_record
WHERE BINARY source_type NOT IN ('TASK', 'DRAW', 'LOTTERY', 'MANUAL') GROUP BY source_type;
