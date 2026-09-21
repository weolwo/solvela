-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- ============================================================================
-- 工具 · 清掉指定联调会员的全部数据  2026-09-20
--
-- 【为什么要有这么一份】
--   联调造一个会员很容易（注册接口一调就有），清干净却很难 —— 他身上的行
--   散在 17 张表、5 个域里。每次手工清都要先做一遍「他到底有哪些表的数据」的
--   考古，而漏掉一张就留下一行<b>查不到主人的账</b>。
--
--   🔴 那正是 v3.71.0 花了一整轮才消灭掉的东西，且它当场不报错 ——
--      直到某天有人按 member_id 去 JOIN 会员表，发现对不上。
--
-- 【用法】改下面那一行的会员号，然后整份执行。
--   mysql> SOURCE 数据库SQL脚本/mysql/工具-清理联调会员数据.sql;
--
-- 【它覆盖哪些表】26 张。清单是拿 information_schema 穷举出来的，不是凭印象列的：
--   SELECT TABLE_NAME FROM information_schema.COLUMNS
--    WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'member_id';
--   —— 加新的会员关联表时，<b>重跑这句</b>再补，别靠记忆。
--
-- 🔴 它是【删数据】的脚本，没有 dryRun。执行前务必先确认这几个号是联调造的：
--      SELECT member_id, member_name, create_time FROM t_member WHERE member_id IN (...);
--    生产环境不要用 —— 会员注销走的是 t_member.status，不是物理删除。
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 要清哪些人。⚠️ 改这里，别改下面
--
-- 两种填法，二选一（另一种注释掉）：
--   A. 点名几个号 —— 手工联调造的那几个
--   B. 按账号前缀 —— 验收用例每跑一次造一个会员（p0_<nanoTime>），
--      一个月能攒几千个，只能按前缀清
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_clean_member;
CREATE TEMPORARY TABLE tmp_clean_member (member_id bigint PRIMARY KEY);

-- ---- A. 点名 ----
-- INSERT INTO tmp_clean_member (member_id) VALUES
--     (5731678932),
--     (1304357406);

-- ---- B. 按前缀 ----
-- 🔴 前缀必须够特别。'sv' 会命中<b>所有</b>真实会员（注册时默认账号就是 sv+会员号），
--    执行前先数一遍：SELECT COUNT(*) FROM t_member WHERE member_name LIKE '前缀%';
INSERT INTO tmp_clean_member (member_id)
SELECT member_id FROM t_member WHERE member_name LIKE 'p0\_%';


-- ---------------------------------------------------------------------------
-- 先清子表，最后清会员本身。
--
-- ⚠️ 顺序不是洁癖：先删会员的话，中途任何一条 DELETE 失败，
--    剩下的行就变成永久孤儿 —— 而那时你已经没有 member_name 可以再查出来了。
--
-- 分域列出来，加新表时照着补一行 —— 清单不全就等于没清。
-- ---------------------------------------------------------------------------

-- 会员等级域（2026-09-20 新增的 4 张）
DELETE FROM t_member_period_summary  WHERE member_id IN (SELECT member_id FROM tmp_clean_member);
DELETE FROM t_member_grade_log       WHERE member_id IN (SELECT member_id FROM tmp_clean_member);
DELETE FROM t_member_growth_log      WHERE member_id IN (SELECT member_id FROM tmp_clean_member);
DELETE FROM t_member_growth          WHERE member_id IN (SELECT member_id FROM tmp_clean_member);

-- 营销域：任务
DELETE FROM t_task_record_flow       WHERE member_id IN (SELECT member_id FROM tmp_clean_member);
DELETE FROM t_task_record            WHERE member_id IN (SELECT member_id FROM tmp_clean_member);

-- 营销域：彩票 / 抽奖
DELETE FROM t_lottery_record         WHERE member_id IN (SELECT member_id FROM tmp_clean_member);
DELETE FROM t_draw_prize_log         WHERE member_id IN (SELECT member_id FROM tmp_clean_member);

-- 发奖与提案
DELETE FROM t_prize_log              WHERE member_id IN (SELECT member_id FROM tmp_clean_member);
DELETE FROM t_proposal_record        WHERE member_id IN (SELECT member_id FROM tmp_clean_member);

-- 资产域
DELETE FROM t_member_asset_transaction WHERE member_id IN (SELECT member_id FROM tmp_clean_member);
DELETE FROM t_member_wallet          WHERE member_id IN (SELECT member_id FROM tmp_clean_member);
DELETE FROM t_member_coupon          WHERE member_id IN (SELECT member_id FROM tmp_clean_member);
DELETE FROM t_physical_delivery      WHERE member_id IN (SELECT member_id FROM tmp_clean_member);

-- 商城域
DELETE FROM t_mall_order             WHERE member_id IN (SELECT member_id FROM tmp_clean_member);
DELETE FROM t_mall_address           WHERE member_id IN (SELECT member_id FROM tmp_clean_member);

-- 通知与公告
--
-- 🔴 2026-09-20 补：下面这四张是<b>第一版漏掉的</b>。
--    漏的原因很典型 —— 我是凭印象列的表清单，而不是拿
--      SELECT TABLE_NAME FROM information_schema.COLUMNS WHERE COLUMN_NAME='member_id'
--    去穷举。清完之后再扫，才发现 t_announcement_ack / t_mall_exchange_limit /
--    t_member_announcement_cursor 三张还挂着已删会员的行 —— 正是这份脚本
--    开头警告的那种孤儿，而且是脚本自己造出来的。
--
--    ⚠️ 以后加会员关联表，别靠记忆补这里，跑一次上面那句 information_schema。
DELETE FROM t_member_notification            WHERE member_id IN (SELECT member_id FROM tmp_clean_member);
DELETE FROM t_member_notification_preference WHERE member_id IN (SELECT member_id FROM tmp_clean_member);
DELETE FROM t_announcement_ack               WHERE member_id IN (SELECT member_id FROM tmp_clean_member);
DELETE FROM t_member_announcement_cursor     WHERE member_id IN (SELECT member_id FROM tmp_clean_member);

-- 商城限购额度
DELETE FROM t_mall_exchange_limit    WHERE member_id IN (SELECT member_id FROM tmp_clean_member);

-- 🔴 2026-09-21 补：下面这五张是【第二版又漏掉的】。
--    这次是充值联调之后再扫，发现 t_external_order 还挂着已删会员的充值单。
--    同一个错犯了第二次，原因也一样 —— 上一版是照着「我记得有哪些域」补的，
--    而不是照着 information_schema 的输出逐行对。
--
--    ⚠️ 所以现在把那条差集查询直接写在这里，加表时<b>先跑它再动手</b>：
--      SELECT TABLE_NAME FROM information_schema.COLUMNS
--       WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'member_id'
--         AND TABLE_NAME NOT IN ( ...本脚本已覆盖的表... );
--      -- 返回非空就是漏了（t_member_coupon_dup_* 那类备份快照除外，见下）
DELETE FROM t_coupon_write_off       WHERE member_id IN (SELECT member_id FROM tmp_clean_member);
DELETE FROM t_external_order         WHERE member_id IN (SELECT member_id FROM tmp_clean_member);
DELETE FROM t_mall_favorite          WHERE member_id IN (SELECT member_id FROM tmp_clean_member);
DELETE FROM t_member_operation_limit WHERE member_id IN (SELECT member_id FROM tmp_clean_member);
DELETE FROM t_member_verify          WHERE member_id IN (SELECT member_id FROM tmp_clean_member);

-- ⚠️ t_member_coupon_dup_20260915 带 member_id，但【故意不清】：
--    它是一次性的备份快照，存在的意义就是留住删改之前的样子。
--    清它等于把备份也一起毁掉 —— 那正是需要备份的那一刻最不该发生的事。

-- 会员域自身
DELETE FROM t_member_login_log       WHERE member_id IN (SELECT member_id FROM tmp_clean_member);
DELETE FROM t_member                 WHERE member_id IN (SELECT member_id FROM tmp_clean_member);

DROP TEMPORARY TABLE IF EXISTS tmp_clean_member;


-- ============================================================================
-- 自查：下面每一条都该返回 0
-- ============================================================================
--
--   SET @ids := '5731678932,1304357406,5733479991,7752962438';
--   SELECT 't_member' t, COUNT(*) c FROM t_member WHERE FIND_IN_SET(member_id, @ids)
--   UNION ALL SELECT 't_member_growth', COUNT(*) FROM t_member_growth WHERE FIND_IN_SET(member_id, @ids)
--   UNION ALL SELECT 't_member_wallet', COUNT(*) FROM t_member_wallet WHERE FIND_IN_SET(member_id, @ids)
--   UNION ALL SELECT 't_prize_log',     COUNT(*) FROM t_prize_log     WHERE FIND_IN_SET(member_id, @ids);
--
-- 🔴 这份脚本有一个【结构性够不着】的盲区：选人是 SELECT ... FROM t_member，
--    所以凡是<b>会员号压根没在 t_member 里出现过</b>的行，它一条都选不中。
--    手工联调时随手编一个号（比如 9900000001）去调接口，就会造出这种行 ——
--    删无可删，因为从来就没有「那个会员」可以删。
--
--    🔴 它还有第二个成因，比编号更容易中招：<b>token 比会员行活得久</b>。
--    删掉 t_member 不会让他的登录态失效 —— 只要那个 token 还在浏览器里，
--    之后<b>任何一次点击</b>（看一眼页面、点个公告确认）都会拿着这个
--    已经不存在的会员号往库里写，而且每一行都是这个脚本再也选不中的。
--    2026-09-21 就是这么又造出两行 t_announcement_ack / t_member_announcement_cursor 的。
--
--    ⚠️ 所以清完人之后，顺手把他的会话也清掉，别再用那个页面：
--      redis-cli -n 1 --scan --pattern '*:{memberId}' | xargs redis-cli -n 1 del
--
--    ⚠️ 别编号，用注册接口真造一个。真要扫这种存量，按表点名删：
--      SELECT 't_member_notification' t, COUNT(*) FROM t_member_notification
--       WHERE member_id NOT IN (SELECT member_id FROM t_member);
--
-- ⚠️ Redis 里还有签到标记（member:sign:yyyyMMdd:{memberId}）与登录会话，
--    它们都有 TTL，会自己过期，不用管。真要立刻清：
--      redis-cli -n 1 --scan --pattern '*:{memberId}' | xargs redis-cli -n 1 del
-- ============================================================================
