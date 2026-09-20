-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- ⚠️ 本脚本不带 NOW()/CURDATE()，不受 README 里那个时区坑影响。

-- ============================================================================
-- 实物履约 · 补上奖品名（C 端补填收货信息要用）  2026-09-18
-- ============================================================================
--
-- 【要解决什么】
--   PhysicalAssetHandler 的类注释把实物履约写成三段：
--
--       ① 中奖 → 生成履约单（此刻只知道发什么、发给谁，**不知道寄到哪**）
--       ② 用户在 C 端补填收货信息
--       ③ 运营发货、回填物流单号
--
--   🔴 第 ② 步【一直不存在】。receiver_name 那一列的注释写着
--      「中奖时未知，由用户后续补填 —— 所以可空，不是忘了加约束」，
--      而补填的入口从来没做，只能靠客服手工改库。
--
--   本次把 ② 补上。做的时候发现一个拦路的问题：
--
--   🔴 t_physical_delivery 【没有存奖品名】。
--      两个创建点手里明明都有：
--        · 中奖链路 PhysicalAssetHandler —— proposal.getAssetName()
--        · 商城链路 AssetGrantApiService.grantPhysical —— cmd.assetName()
--      但表里没有列，所以两边都把它丢了。
--
--      后果是 C 端只能显示「来源单号 M20260905120000123ABCDEF，待发货」——
--      用户完全认不出这是什么东西。而这一页存在的意义就是回答「我的东西呢」。
--
-- 【为什么是加列，不是 join 回上游】
--   join 不回去：中奖那一半在 t_proposal_record（风控域），商城那一半在
--   t_mall_order（商城域），而 t_physical_delivery 在资产域 ——
--   marketing ↮ ledger、mall ↮ ledger 两条缝都有架构测试守着，
--   资产域拿着 source_biz_id 也查不出人来。
--
--   而且这里本来就该存快照：履约单是<b>单据</b>，记的是「当时发的是什么」。
--   商品改名之后历史单据不该跟着变 —— 和 member_name、t_mall_order 里
--   那些商品快照列是同一个模式。
--
-- 【可重复执行】
--   加列那段判断了 information_schema，重复跑不会报 Duplicate column。
--
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1. 加列
-- ----------------------------------------------------------------------------
--
-- 可空：存量 845 行没有这个信息，也补不出来（见下面第 2 节）。
-- 长度 128 对齐 t_proposal_record.asset_name。
--
SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 't_physical_delivery'
       AND COLUMN_NAME = 'prize_name'
);

SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `t_physical_delivery`
       ADD COLUMN `prize_name` varchar(128) DEFAULT NULL
       COMMENT ''奖品/商品名【快照】：创建时从上游抄下来，上游改名不跟着变''
       AFTER `source_type`',
    'SELECT ''prize_name 已存在，跳过'' AS msg');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ----------------------------------------------------------------------------
-- 2. 存量数据：刻意【不回填】
-- ----------------------------------------------------------------------------
--
-- 🔴 回填要 join 回 t_proposal_record / t_mall_order 去猜，而那正是上面说
--    「join 不回去」的那两张表 —— 在 SQL 里绕过架构边界，比在 Java 里绕过更隐蔽：
--    没有任何测试会红，而下次拆服务时这段脚本会是第一个炸的地方。
--
-- 存量单的 prize_name 保持 NULL，C 端按「实物奖品」这个兜底文案显示。
-- 那是老单子，数量有限，而且多数早就发完了。
--
-- 要对存量做一次性回填的话，那是<b>一次运营动作</b>：
-- 导出 → 人工核对 → 按 id 更新，别写进这个会被反复执行的脚本里。


-- ============================================================================
-- 验证
-- ============================================================================
--
--   SHOW COLUMNS FROM t_physical_delivery LIKE 'prize_name';
--
--   -- 新单应当有名字；老单是 NULL，这是预期的
--   SELECT id, source_type, source_biz_id, prize_name, status
--     FROM t_physical_delivery ORDER BY id DESC LIMIT 10;
--
-- ⚠️ 改了表结构，记得重新导出基线：
--      数据库SQL脚本/tools/DumpSchema.java
--    🔴 别手改 schema-baseline.sql（README 红线 #6）。
-- ============================================================================
