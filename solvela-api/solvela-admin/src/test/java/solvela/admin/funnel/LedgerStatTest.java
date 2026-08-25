package solvela.admin.funnel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.ledger.coupon.dao.MemberCouponDao;
import solvela.ledger.coupon.domain.vo.MemberCouponStatVO;
import solvela.ledger.coupon.service.MemberCouponService;
import solvela.ledger.logistic.dao.PhysicalDeliveryDao;
import solvela.ledger.logistic.domain.vo.PhysicalDeliveryStatVO;
import solvela.ledger.logistic.service.PhysicalDeliveryService;
import solvela.ledger.stat.domain.form.LedgerStatForm;
import solvela.ledger.transaction.dao.MemberAssetTransactionDao;
import solvela.ledger.transaction.domain.vo.MemberAssetTransactionStatVO;
import solvela.ledger.transaction.service.MemberAssetTransactionService;
import solvela.ledger.wallet.dao.MemberWalletDao;
import solvela.ledger.wallet.domain.vo.MemberWalletStatVO;
import solvela.ledger.wallet.service.MemberWalletService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 财务中心四个统计面板的验收（真跑数据库，不是推断）。
 *
 * <h3>这里要拦住的三类错</h3>
 * <ol>
 *   <li><b>key 打错静默变 0</b>：取数链路是 {@code SQL 别名 -> Map<String,Object> -> VO}，
 *       中间靠字符串 key 对接。打错一个字母，{@code toLong} 返回 0，
 *       页面上就是一个安安静静的「0 条异常」—— 编译不报错、SQL 不报错、接口 200。
 *       这一层<b>不依赖库里有没有数据</b>，空表也照样能抓出拼写错误；</li>
 *   <li><b>「默认当天」没生效</b>：不传日期时靠 SQL 里的 {@code COALESCE(..., CURDATE())} 落到当天。
 *       这条路径日常点页面根本走不到（前端总会带上日期），
 *       而它一旦挂了（比如少写 jdbcType=DATE 直接抛 JdbcType OTHER），
 *       只有「用户清空日期」的那一刻才炸；</li>
 *   <li><b>存量指标被时间窗污染</b>：券库存、发货积压、钱包余额都是<b>全量</b>口径。
 *       哪天有人「顺手」把时间窗加进这几条 SQL，压了三天的积压会正好从页面上消失 ——
 *       那恰恰是这几个页面唯一需要有人动手的东西。所以这里明确断言它们不随时间范围变化。</li>
 * </ol>
 *
 * <p>本测试<b>只读</b>：不造数、不改库，可以随时重复跑。
 *
 * @Author alaric
 * @Date 2026-08-18
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class LedgerStatTest {

    @Autowired
    private MemberAssetTransactionService transactionService;
    @Autowired
    private MemberAssetTransactionDao transactionDao;
    @Autowired
    private MemberCouponService couponService;
    @Autowired
    private MemberCouponDao couponDao;
    @Autowired
    private PhysicalDeliveryService deliveryService;
    @Autowired
    private PhysicalDeliveryDao deliveryDao;
    @Autowired
    private MemberWalletService walletService;
    @Autowired
    private MemberWalletDao walletDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==================== key 契约 ====================

    @Test
    @DisplayName("四个面板：SQL 别名与 Service 读取的 key 完全一致（拼错一个字母就会静默变 0）")
    void statKeysMatch() {
        LedgerStatForm form = allTime();

        Map<String, Object> tx = transactionDao.selectStat(form);
        assertNotNull(tx, "聚合查询空表也会有一行，不该返回 null");
        assertEquals(Set.of("txCount", "memberCount", "manualAdjustCount",
                        "negativeBalanceCount", "nonPositiveChangeCount"), tx.keySet(),
                "交易统计：SQL 别名与 Service 读取的 key 对不上");

        List<Map<String, Object>> assetFlow = transactionDao.selectAssetFlowStat(form);
        if (!assetFlow.isEmpty()) {
            assertEquals(Set.of("assetType", "incomeCount", "incomeAmount", "expenseCount", "expenseAmount"),
                    assetFlow.get(0).keySet());
        }
        List<Map<String, Object>> bizType = transactionDao.selectBizTypeStat(form);
        if (!bizType.isEmpty()) {
            assertEquals(Set.of("bizType", "txCount", "memberCount"), bizType.get(0).keySet());
        }

        assertEquals(Set.of("issuedCount", "issuedMemberCount", "issuedUsedCount"),
                couponDao.selectIssuedStat(form).keySet(), "优惠券本期发放：key 对不上");
        assertEquals(Set.of("usedCount", "usedMemberCount"),
                couponDao.selectUsedStat(form).keySet(), "优惠券本期核销：key 对不上");
        assertEquals(Set.of("stockTotalCount", "stockUnusedCount", "stockUsedCount", "stockExpiredCount",
                        "stockVoidCount", "staleUnusedCount", "expiringSoonCount",
                        "usedNoTimeCount", "unusedWithTimeCount", "badValidRangeCount"),
                couponDao.selectStockStat().keySet(), "优惠券库存：key 对不上");
        List<Map<String, Object>> couponStat = couponDao.selectCouponStat(form);
        if (!couponStat.isEmpty()) {
            assertEquals(Set.of("couponCode", "couponName", "issuedCount", "memberCount", "usedCount", "staleCount"),
                    couponStat.get(0).keySet());
        }
        List<Map<String, Object>> couponSource = couponDao.selectSourceStat(form);
        if (!couponSource.isEmpty()) {
            assertEquals(Set.of("sourceType", "issuedCount"), couponSource.get(0).keySet());
        }

        assertEquals(Set.of("newCount", "newMemberCount"),
                deliveryDao.selectNewStat(form).keySet(), "发货本期新增：key 对不上");
        assertEquals(Set.of("totalCount", "pendingCount", "pendingNoAddressCount", "deliveredCount",
                        "signedCount", "returnedCount", "pendingOldestMinutes",
                        "discardedCount", "shippedNoLogisticsNo", "returnedNoLogisticsNo"),
                deliveryDao.selectStatusStat().keySet(), "发货履约状态：key 对不上");
        List<Map<String, Object>> deliverySource = deliveryDao.selectSourceStat();
        if (!deliverySource.isEmpty()) {
            assertEquals(Set.of("sourceType", "deliveryCount", "pendingCount", "shippedCount", "discardedCount"),
                    deliverySource.get(0).keySet());
        }

        assertEquals(Set.of("walletCount", "memberCount", "frozenCount",
                        "negativeBalanceCount", "frozenWithBalanceCount"),
                walletDao.selectStat().keySet(), "钱包统计：key 对不上");
        List<Map<String, Object>> walletAsset = walletDao.selectAssetBalanceStat();
        if (!walletAsset.isEmpty()) {
            assertEquals(Set.of("assetType", "walletCount", "totalBalance", "frozenBalance"),
                    walletAsset.get(0).keySet());
        }
    }

    // ==================== 「默认当天」 ====================

    @Test
    @DisplayName("不传日期 == 传数据库当天：COALESCE(CURDATE()) 这条路径日常点页面走不到，只能在这里验")
    void nullDateFallsBackToDbToday() {
        LocalDate dbToday = jdbcTemplate.queryForObject("SELECT CURDATE()", LocalDate.class);
        assertNotNull(dbToday);

        LedgerStatForm empty = new LedgerStatForm();
        LedgerStatForm today = new LedgerStatForm();
        today.setStatDateBegin(dbToday);
        today.setStatDateEnd(dbToday);

        /*
         * 用数据库的 CURDATE() 而不是 JVM 的 LocalDate.now() 来构造对照组：
         * 两个钟不在同一时区时，「今天」会差出几个小时 —— 那正是这条断言要防的东西，
         * 拿 JVM 的日期当期望值等于把要测的偏差一起带进期望里。
         */
        assertEquals(transactionDao.selectStat(today), transactionDao.selectStat(empty),
                "交易统计：不传日期时没有落到数据库当天");
        assertEquals(couponDao.selectIssuedStat(today), couponDao.selectIssuedStat(empty),
                "优惠券发放：不传日期时没有落到数据库当天");
        assertEquals(couponDao.selectUsedStat(today), couponDao.selectUsedStat(empty),
                "优惠券核销：不传日期时没有落到数据库当天");
        assertEquals(deliveryDao.selectNewStat(today), deliveryDao.selectNewStat(empty),
                "发货新增：不传日期时没有落到数据库当天");
    }

    @Test
    @DisplayName("结束日含当天：时间窗必须是 < 次日零点，写 <= 会把结束日整天筛掉")
    void endDateIsInclusive() {
        Long total = count("SELECT COUNT(*) FROM t_member_asset_transaction");
        assertTrue(total > 0, "t_member_asset_transaction 没有数据，本用例无法证明任何事 —— 先造数再跑");

        LocalDate latest = jdbcTemplate.queryForObject(
                "SELECT DATE(MAX(create_time)) FROM t_member_asset_transaction", LocalDate.class);
        assertNotNull(latest);
        long expected = count("SELECT COUNT(*) FROM t_member_asset_transaction WHERE DATE(create_time) = '"
                + latest + "'");

        LedgerStatForm form = new LedgerStatForm();
        form.setStatDateBegin(latest);
        form.setStatDateEnd(latest);
        assertEquals(expected, toLong(transactionDao.selectStat(form).get("txCount")),
                "把开始日和结束日都设成同一天，应当正好统计到那一天的全部流水；"
                        + "数字偏小说明时间窗又被写回了 <= 当天零点");
    }

    // ==================== 存量指标必须是全量 ====================

    @Test
    @DisplayName("存量指标不随时间范围变化：券库存 / 发货积压 / 钱包余额都是全量口径")
    void stockMetricsIgnoreDateRange() {
        MemberCouponStatVO couponToday = couponService.stat(new LedgerStatForm());
        MemberCouponStatVO couponAll = couponService.stat(allTime());
        assertEquals(couponAll.getStockTotalCount(), couponToday.getStockTotalCount(),
                "券库存跟着时间范围变了：压着多少张没用的券是存量问题，限制在今天只会把它藏起来");
        assertEquals(couponAll.getStaleUnusedCount(), couponToday.getStaleUnusedCount());

        PhysicalDeliveryStatVO deliveryToday = deliveryService.stat(new LedgerStatForm());
        PhysicalDeliveryStatVO deliveryAll = deliveryService.stat(allTime());
        assertEquals(deliveryAll.getPendingCount(), deliveryToday.getPendingCount(),
                "待发货积压跟着时间范围变了：压了三天的单子会正好从页面上消失，"
                        + "而那恰恰是这个页面唯一需要有人动手的东西");

        MemberWalletStatVO walletToday = walletService.stat(new LedgerStatForm());
        MemberWalletStatVO walletAll = walletService.stat(allTime());
        assertEquals(walletAll.getWalletCount(), walletToday.getWalletCount(),
                "钱包账户数跟着时间范围变了：钱包表是存量表，按 create_time 筛出来的是「今天新开的钱包」");
    }

    // ==================== 数值自洽 ====================

    @Test
    @DisplayName("优惠券：四个状态桶之和 = 券总张数，且核销与发放走的是两个不同的时间列")
    void couponNumbersAddUp() {
        long dbTotal = count("SELECT COUNT(*) FROM t_member_coupon");
        assertTrue(dbTotal > 0, "t_member_coupon 没有数据，本用例无法证明任何事 —— 先造数再跑");

        MemberCouponStatVO vo = couponService.stat(allTime());
        assertEquals(dbTotal, vo.getStockTotalCount());
        assertEquals(vo.getStockTotalCount(),
                vo.getStockUnusedCount() + vo.getStockUsedCount()
                        + vo.getStockExpiredCount() + vo.getStockVoidCount(),
                "状态桶之和不等于总数：库里出现了 0/1/2/3 之外的 status");

        /*
         * 核销口径走 used_time，发放口径走 create_time。全量窗口下，
         * 「本期核销」应当等于库里有核销时间的行数 —— 如果有人把核销也改成按 create_time 筛，
         * 这条断言会立刻红。
         */
        assertEquals(count("SELECT COUNT(*) FROM t_member_coupon WHERE used_time IS NOT NULL"),
                vo.getUsedCount(),
                "本期核销的口径不对：它必须落在 used_time 上，落到 create_time 会变成"
                        + "「今天发的券里今天用掉的」——那个数必然接近 0，而且看上去像一条正常业务结论");
    }

    @Test
    @DisplayName("发货：五个状态桶之和 = 总数（含字典外的 -1 已作废），且待发货 = 缺收件信息 + 可直接发")
    void deliveryNumbersAddUp() {
        long dbTotal = count("SELECT COUNT(*) FROM t_physical_delivery");
        assertTrue(dbTotal > 0, "t_physical_delivery 没有数据，本用例无法证明任何事 —— 先造数再跑");

        PhysicalDeliveryStatVO vo = deliveryService.stat(allTime());
        assertEquals(dbTotal, vo.getTotalCount());
        /*
         * ⚠️ -1 是页面「删除」写进去的软作废状态，DDL 列注释和 DeliveryStatusEnum 里都没有它。
         * 这条断言最早就是被它红的：库里有一行 status=-1，四个桶加起来比总数少 1。
         * 少的那一条不是算错，是有一整类单子没人给它一个桶 —— 所以必须单独统计。
         */
        assertEquals(vo.getTotalCount(),
                vo.getPendingCount() + vo.getDeliveredCount() + vo.getSignedCount()
                        + vo.getReturnedCount() + vo.getDiscardedCount(),
                "状态桶之和不等于总数：库里出现了 -1/0/1/2/3 之外的 status");
        assertEquals(vo.getTotalCount() - vo.getDiscardedCount(), vo.getValidCount(),
                "有效单数应当等于总数减去已作废");
        assertEquals(vo.getPendingCount(), vo.getPendingNoAddressCount() + vo.getPendingReadyCount(),
                "待发货没有被完整拆成「缺收件信息」和「可直接发」两类");
        assertEquals(dbTotal,
                vo.getSourceList().stream().mapToLong(PhysicalDeliveryStatVO.SourceStatVO::getDeliveryCount).sum(),
                "来源分布条数之和不等于总数");
    }

    @Test
    @DisplayName("交易：收支按资产类型分开算，净额 = 收入 - 支出，且与库里对得上")
    void transactionAmountIsPerAssetType() {
        long dbTotal = count("SELECT COUNT(*) FROM t_member_asset_transaction");
        assertTrue(dbTotal > 0, "t_member_asset_transaction 没有数据，本用例无法证明任何事 —— 先造数再跑");

        MemberAssetTransactionStatVO vo = transactionService.stat(allTime());
        assertEquals(dbTotal, vo.getTxCount());

        for (MemberAssetTransactionStatVO.AssetFlowVO asset : vo.getAssetList()) {
            Double income = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(change_amount), 0) FROM t_member_asset_transaction"
                            + " WHERE asset_type = ? AND transaction_type = 1", Double.class, asset.getAssetType());
            Double expense = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(change_amount), 0) FROM t_member_asset_transaction"
                            + " WHERE asset_type = ? AND transaction_type = 2", Double.class, asset.getAssetType());
            assertNotNull(income);
            assertNotNull(expense);
            assertEquals(income, asset.getIncomeAmount().doubleValue(), 0.01,
                    "资产 " + asset.getAssetType() + " 的收入金额与库里对不上");
            assertEquals(expense, asset.getExpenseAmount().doubleValue(), 0.01,
                    "资产 " + asset.getAssetType() + " 的支出金额与库里对不上");
            assertEquals(income - expense, asset.getNetAmount().doubleValue(), 0.01,
                    "净额不等于收入减支出。change_amount 存的是绝对值，方向只在 transaction_type 上，"
                            + "直接 SUM(change_amount) 出来的数既不是收入也不是支出");
        }
    }

    @Test
    @DisplayName("钱包：余额按资产类型分开算，账户数之和 = 钱包总数")
    void walletBalanceIsPerAssetType() {
        long dbTotal = count("SELECT COUNT(*) FROM t_member_wallet");
        assertTrue(dbTotal > 0, "t_member_wallet 没有数据，本用例无法证明任何事 —— 先造数再跑");

        MemberWalletStatVO vo = walletService.stat(allTime());
        assertEquals(dbTotal, vo.getWalletCount());
        assertEquals(dbTotal,
                vo.getAssetList().stream().mapToLong(MemberWalletStatVO.AssetBalanceVO::getWalletCount).sum(),
                "资产维度账户数之和不等于钱包总数");

        for (MemberWalletStatVO.AssetBalanceVO asset : vo.getAssetList()) {
            Double expected = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(balance), 0) FROM t_member_wallet WHERE asset_type = ?",
                    Double.class, asset.getAssetType());
            assertNotNull(expected);
            assertEquals(expected, asset.getTotalBalance().doubleValue(), 0.01,
                    "资产 " + asset.getAssetType() + " 的余额合计与库里对不上");
        }
    }

    /**
     * 一个「足够早到能覆盖全表」的时间范围。
     *
     * <p>结束日取明天，避免时区差把今天的数据挡在窗口外 —— 本用例要的是「全量」，
     * 不是「精确到某一天」，边界宽一点不影响结论。
     */
    private LedgerStatForm allTime() {
        LedgerStatForm form = new LedgerStatForm();
        form.setStatDateBegin(LocalDate.of(2000, 1, 1));
        form.setStatDateEnd(LocalDate.now().plusDays(1));
        return form;
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    /**
     * BigDecimal 在断言里只用 doubleValue 比较，避免 scale 不同导致 equals 为 false。
     * 这个方法留着是为了让上面的意图明显：金额比较一律带容差。
     */
    @SuppressWarnings("unused")
    private double amount(BigDecimal value) {
        return value == null ? 0d : value.doubleValue();
    }
}
