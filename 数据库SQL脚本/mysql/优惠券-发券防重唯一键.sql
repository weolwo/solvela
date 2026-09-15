-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- ⚠️ 本脚本不带 NOW()/CURDATE()，不受 README 里那个时区坑影响。

-- ============================================================================
-- 优惠券 · 发券防重：补上那个从来没生效过的唯一键  2026-09-15
-- ============================================================================
--
-- 【要解决什么】
--   CouponAssetHandler.dispatch 里有一段：
--
--       } catch (DuplicateKeyException e) {
--           log.warn("【防重拦截】该提案已发过券: {}", proposal.getId());
--           return DispatchOutcome.success();
--       }
--
--   🔴 这段【从来没有执行过】—— t_member_coupon 上压根没有唯一键可违反，
--      (source_type, source_biz_id) 只有普通索引 idx_source。
--
--   也就是说：同一个提案被重发两次，就会发出两张券，不报错、不告警。
--   发奖那条路今天的幂等实际靠的是派发引擎的状态机，而这一层的防线是空的。
--   纵深防御少了一层，而少的那一层恰好是最靠近钱的那一层。
--
-- 【为什么阶段 5 没一起做】
--   当时库里有 53 组重复，唯一键建不出来，所以只给 MANUAL 做了一个函数索引
--   （uk_manual_src），并把这条记成待办（见「优惠券-人工发券.sql」§1 的注释）。
--   本脚本就是来还这笔账的。
--
-- ============================================================================
-- 🔴 那 53 组重复到底是什么 —— 先查清楚再删
-- ============================================================================
--
--   查下来【不是】「同一个提案发了两张券」，而是**提案 id 被两代造数复用了**：
--
--     id  | source_biz_id | member_id  | 提案上的 member | create_time
--     ----+---------------+------------+-----------------+------------------
--      95 | 774           | 8104992981 | 1738812556      | 07-26 16:50  ← 对不上
--     495 | 774           | 1738812556 | 1738812556      | 07-26 22:42  ← 对得上
--
--   两行属于【两个不同的会员】。16:xx 那一代的提案后来被重新造过，
--   id 让给了别人，而券留了下来 —— 于是一个 source_biz_id 指向了两个人。
--
--   ⚠️ 所以这 53 组【不是】防重失效的证据。防重失效是【另一件事】，
--      靠读代码确认（没有唯一键），不靠这份脏数据。两件事别混起来说。
--
-- 【删哪一行：留 id 大的】
--   19 组的提案今天还在，可以拿它当裁判 —— 结果是
--   **19 组里对得上提案 member 的全都是 id 大的那一行，零例外**。
--   另外 34 组的提案已经不在了（439 行 PROPOSAL 券是这样的孤儿），
--   没有裁判可用，只能沿用同一条规律。
--
--   兜底：这 106 行【全部是 status=2 已过期】，没有任何用户正拿着。
--   删错也不会从谁手里拿走一张能用的券 —— 这是敢动它们的真正原因。
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 0. 盘点：动手之前先把数字记下来
-- ---------------------------------------------------------------------------
SELECT '删除前' AS 阶段,
       (SELECT COUNT(*) FROM t_member_coupon) AS 券总数,
       (SELECT COUNT(*) FROM (
          SELECT source_biz_id FROM t_member_coupon
           WHERE source_type = 'PROPOSAL'
           GROUP BY source_biz_id HAVING COUNT(*) > 1) t) AS 重复组数;


-- ---------------------------------------------------------------------------
-- 1. 先归档，再删除
--
-- 🔴 备份表不是形式主义：删掉的是券，而券是用户的资产。
--    哪怕它已经过期、哪怕判据看着很稳，也要留一份能查回去的原件。
--    表名带日期，下次再删是另一张表，不会覆盖这一次。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_member_coupon_dup_20260915`
    LIKE `t_member_coupon`;

INSERT INTO `t_member_coupon_dup_20260915`
SELECT * FROM `t_member_coupon`
 WHERE id IN (
   SELECT keep.min_id FROM (
     SELECT MIN(id) AS min_id
       FROM t_member_coupon
      WHERE source_type = 'PROPOSAL'
      GROUP BY source_biz_id
     HAVING COUNT(*) > 1) keep)
   AND NOT EXISTS (SELECT 1 FROM `t_member_coupon_dup_20260915` b WHERE b.id = t_member_coupon.id);


-- ---------------------------------------------------------------------------
-- 2. 删掉每组里 id 小的那一行
--
-- ⚠️ MySQL 不允许在 DELETE 的子查询里直接读被删的表，所以套一层派生表。
--    幂等：第二遍跑时已经没有重复组了，影响 0 行。
-- ---------------------------------------------------------------------------
DELETE FROM `t_member_coupon`
 WHERE id IN (
   SELECT min_id FROM (
     SELECT MIN(id) AS min_id
       FROM t_member_coupon
      WHERE source_type = 'PROPOSAL'
      GROUP BY source_biz_id
     HAVING COUNT(*) > 1) keep);


-- ---------------------------------------------------------------------------
-- 3. 🔴 闸门：还有一组重复就不许往下走
--
--    下一步是加唯一键。带着重复去 ALTER，MySQL 会报一个只说「Duplicate entry」
--    的错，不告诉你还剩几组 —— 在这里先炸，错误信息是人话。
-- ---------------------------------------------------------------------------
SELECT IF(COUNT(*) = 0, '闸门通过：没有重复了',
          CONCAT('🔴 还有 ', COUNT(*), ' 组重复，停在这里，不要继续'))
  FROM (SELECT source_type, source_biz_id
          FROM t_member_coupon
         WHERE source_biz_id IS NOT NULL
         GROUP BY source_type, source_biz_id
        HAVING COUNT(*) > 1) t;


-- ---------------------------------------------------------------------------
-- 4. 换索引：函数索引 → 普通唯一键
--
-- 【为什么可以换成普通唯一键了】
--   阶段 5 用函数索引，是因为当时只能给 MANUAL 一家做防重。
--   重复清掉之后，(source_type, source_biz_id) 本身就是唯一的，
--   一个普通唯一键就能把【所有来源】一起管住 —— 语义也更正：
--   **一张来源单据只产出一张券**。
--
-- 【NULL 怎么办】
--   唯一键允许多个 NULL，所以将来若有某个来源不填 source_biz_id，
--   它自动不受约束 —— 不需要再写函数索引来绕。
--   （今天库里 source_biz_id 为空的行是 0 行。）
--
-- 【各来源的幂等键长什么样】
--   PROPOSAL : <提案id>                     ← 一个提案一张券
--   MANUAL   : <工单号>:<会员号>:<序号>
--   MALL     : <订单号>:<序号>
--   REISSUE  : <旧券id>
--   ⚠️ PROPOSAL 是唯一没有序号的那个。今天成立，因为一个提案确实只发一张券；
--      哪天要「一个提案发 3 张」，必须先给它加序号，否则第二张会撞键。
--      这句话在 CouponAssetHandler 里也留了一份。
--
-- 【顺手删掉 idx_source】
--   新唯一键的列顺序和它一模一样，普通索引变成纯冗余 ——
--   留着只是让每次写入多维护一棵 B+ 树。
-- ---------------------------------------------------------------------------
ALTER TABLE `t_member_coupon`
  DROP INDEX `uk_manual_src`,
  DROP INDEX `idx_source`,
  ADD UNIQUE KEY `uk_source` (`source_type`, `source_biz_id`);


-- ---------------------------------------------------------------------------
-- 5. 验收
-- ---------------------------------------------------------------------------
SELECT '删除后' AS 阶段,
       (SELECT COUNT(*) FROM t_member_coupon) AS 券总数,
       (SELECT COUNT(*) FROM t_member_coupon_dup_20260915) AS 已归档,
       (SELECT COUNT(*) FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_member_coupon'
           AND INDEX_NAME = 'uk_source') AS 唯一键列数;
