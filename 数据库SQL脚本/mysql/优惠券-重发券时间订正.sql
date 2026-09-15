-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- ============================================================================
-- 优惠券 · 订正重发券的时间（时区踩坑）  2026-09-15
-- ============================================================================
--
-- 🔴 【这个坑值得记住：脚本里的 NOW() 和应用写进去的时间不是一个钟】
--
--   本项目的 MySQL 服务端跑在 **UTC**（@@system_time_zone = UTC，
--   global.time_zone = SYSTEM）。应用的连接串里有
--       forceConnectionTimeZoneToSession=true
--   所以【应用】的会话时区被强制成 Asia/Shanghai，NOW() 和 ctx.dbNow()
--   都是 +08，和 JVM 的 LocalDateTime.now() 对得上。
--
--   但用 mysql 客户端或别的工具直连时，如果没带这个参数，
--   会话时区就是 SYSTEM = UTC —— 脚本里的 NOW() 比应用写进去的时间**早 8 小时**，
--   而且不报错，只是日期时间悄悄差了一截。
--
--   「优惠券-存量券作废重发.sql」第一次执行时就踩了这个：248 张重发券的
--   有效期起止和创建时间全部落成了 UTC，比应该的时间早 8 小时 ——
--   也就是这批券的有效期**短了 8 小时**。
--
-- 【怎么订正】
--   这批券是刚刚发出来的，所以重新按「现在」起算就是正确答案：
--   valid_start_time = NOW()，valid_end_time = NOW() + 模板有效期。
--   不用去算偏移量 —— 算偏移要假设偏移是多少，而重算不用假设。
--
-- 【执行方式】
--   ⚠️ 必须用一个会话时区是 Asia/Shanghai 的连接执行，否则这个脚本本身
--      也会写错。检查办法：
--        SELECT NOW(), @@session.time_zone;
--      NOW() 应当和你的本地时间一致。
--
--   mysql> SOURCE 数据库SQL脚本/mysql/优惠券-重发券时间订正.sql;
--   可重复执行：每次都重新按当前时间起算，多跑一次只是把有效期再往后推一点。
--   ⚠️ 也正因为如此，**别在重发完很久之后才跑它** —— 那会平白延长一批券的有效期。
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 0. 先确认这个连接的钟是对的
--    NOW() 必须和你本地时间一致；是 UTC 的话就地停手，换连接参数
-- ---------------------------------------------------------------------------
SELECT NOW() AS `当前连接看到的时间`, @@session.time_zone AS `会话时区`;

-- 订正前：看看这批券的有效期长什么样
SELECT COUNT(*)              AS `重发券张数`,
       MIN(valid_start_time) AS `最早生效`,
       MAX(valid_end_time)   AS `最晚失效`
  FROM t_member_coupon
 WHERE source_type = 'REISSUE';


-- ---------------------------------------------------------------------------
-- 1. 按模板重新起算
-- ---------------------------------------------------------------------------
UPDATE t_member_coupon mc
  JOIN t_coupon_template t
    ON t.coupon_code = mc.coupon_code AND t.version = mc.template_version
   SET mc.valid_start_time = NOW(),
       -- 模板保存时校验过「两种有效期二选一」，COALESCE 不会两个都空
       mc.valid_end_time   = COALESCE(DATE_ADD(NOW(), INTERVAL t.valid_days DAY), t.valid_end_time),
       mc.create_time      = NOW()
 WHERE mc.source_type = 'REISSUE'
   AND mc.status = 0;


-- ---------------------------------------------------------------------------
-- 2. 核对：生效时间应当就是「刚刚」
-- ---------------------------------------------------------------------------
SELECT COUNT(*)              AS `重发券张数`,
       MIN(valid_start_time) AS `最早生效`,
       MAX(valid_end_time)   AS `最晚失效`,
       TIMESTAMPDIFF(DAY, MIN(valid_start_time), MIN(valid_end_time)) AS `有效天数`
  FROM t_member_coupon
 WHERE source_type = 'REISSUE';


-- ---------------------------------------------------------------------------
-- 3. 同一个坑：卡单释放任务的 next_trigger_time 也是用 NOW() 写的
--    让它下一分钟就能被扫描线程捞走，之后由 cron 接管
-- ---------------------------------------------------------------------------
UPDATE `t_solvela_job`
   SET `next_trigger_time` = DATE_ADD(NOW(), INTERVAL 1 MINUTE)
 WHERE `handler_name` = 'couponStuckLockRelease'
   AND `deleted_flag` = 0;

SELECT `job_code`, `handler_name`, `next_trigger_time`, `trigger_value`
  FROM `t_solvela_job`
 WHERE `handler_name` = 'couponStuckLockRelease';
