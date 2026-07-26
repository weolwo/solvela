-- 统一时间戳时钟源：create_time / update_time 一律由数据库产生（2026-07-26）
--
-- 背景：此前 create_time / update_time 由 MybatisPlusFillHandler 用 JVM 的 LocalDateTime.now() 填充，
-- 而绕过实体的 raw SQL（自定义 <update>、以及 ON UPDATE CURRENT_TIMESTAMP）走的是 MySQL 时钟 ——
-- 两套时钟源并存，产生过两类问题：
--   ① 时区不一致：MySQL 服务器在 UTC、JVM 在东八区，update_time 比 create_time 早整 8 小时
--      （已由连接串的 connectionTimeZone + forceConnectionTimeZoneToSession 修复）
--   ② 亚秒舍入不一致：datetime(0) 丢掉小数时，JDBC 送入的值与 CURRENT_TIMESTAMP 的舍入方向不同，
--      实测「被 update 过的记录」有 18/48 出现 update_time 比 create_time 早 1 秒，
--      而「只 insert 从未 update」的记录 0/100 异常 —— 精确指向双时钟源。
--
-- 治本做法是只留一个时钟：全部交给数据库。绝大多数表的 DDL 本就具备
-- DEFAULT CURRENT_TIMESTAMP / ON UPDATE CURRENT_TIMESTAMP，去掉 Java 填充即可自动生效。
-- 只有下面两列缺 ON UPDATE，补齐后才不会出现「更新了但 update_time 不动」的功能退化。
--
-- 附带收益：多实例部署时各应用节点的 JVM 时钟未必一致，而数据库只有一个，
-- 单一时钟源顺带消除了节点间时钟漂移带来的数据错乱。

ALTER TABLE `t_department`
    MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `t_file`
    MODIFY COLUMN `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- 自查：以下应返回 0 行（有 update_time 列却缺 ON UPDATE 兜底的表）
-- SELECT table_name FROM information_schema.columns
-- WHERE table_schema='smart_admin_v3' AND column_name='update_time'
--   AND extra NOT LIKE '%on update%';
