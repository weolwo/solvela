package solvela.draw.poolitem.service;

import solvela.enums.EnableStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.activity.ActivityConfig;
import solvela.activity.manager.ActivityConfigManager;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.json.JsonUtils;
import solvela.base.stat.HealthIssue;
import solvela.draw.PrizePoolItem;
import solvela.draw.poolitem.domain.query.PrizePoolItemQuery;
import solvela.draw.poolitem.domain.dto.PrizeItemStockResultDTO;
import solvela.draw.poolitem.domain.dto.PrizeItemStockDTO;
import solvela.draw.poolitem.manager.PrizePoolItemManager;
import solvela.draw.PoolPrizeMapping;
import solvela.draw.prizemapping.manager.PoolPrizeMappingManager;
import solvela.draw.runtime.DrawStockService;
import solvela.prize.PrizeConfig;
import solvela.prize.prizeconfig.manager.PrizeConfigManager;
import solvela.prize.prizeconfig.service.PrizeCatalog;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 奖项库存看板。
 *
 * <h3>为什么原来的列表不够用</h3>
 * 生成器产出的页面把 {@code total_stock} 与 {@code used_stock} 两个裸数字一列列摆出来，
 * 但运营真正要问的是「还剩多少、消耗多快、会不会提前抽空、抽完最坏赔多少」。
 *
 * <h3>最关键的一列：两个库存口径</h3>
 * 运行态的扣减是<b>双层</b>的：Redis Lua 原子预扣扛并发，DB 条件更新做最终一致性兜底。
 * 真正决定「还能不能抽到」的是 <b>Redis 里的剩余量</b>，
 * {@code used_stock} 只是事后对账用的。两者漂移（缓存被误预热、DB 拒绝后回滚失败）
 * 意味着可能超发或少发，是要立刻处理的事故 —— 而原页面只显示 DB 口径，
 * 看到的是个跟运行态无关的安慰性数字。
 *
 * <h3>库存是跨奖池共享的</h3>
 * 一个奖项可以被多个奖池的坑位引用，{@code used_stock} 的注释写的就是「跨奖池累计已出数量」。
 * 所以 A 池把某个奖抽空了，B 池里同一个奖也没了。这一点在原页面上完全看不出来，
 * 这里把引用它的奖池列出来。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class PrizeItemStockService {

    private final PrizePoolItemManager prizePoolItemManager;
    private final PoolPrizeMappingManager poolPrizeMappingManager;
    private final PrizeConfigManager prizeConfigManager;
    private final ActivityConfigManager activityConfigManager;
    private final DrawStockService drawStockService;


    /** 剩余低于总量这个比例时提醒补货，早于抽空发出才有意义 */
    private static final BigDecimal LOW_STOCK_THRESHOLD = new BigDecimal("0.1");

    private static final int UNLIMITED = -1;

    private static final int SCALE = 4;

    /**
     * 库存看板。概览与明细一次算完，卡片数字与列表必然一致。
     *
     * <p>分页在内存里做：奖项数是配置级的量，先算全量再切页，
     * 也让「概览统计的是筛选后的全量」天然成立。
     */
    public PrizeItemStockResultDTO stockBoard(PrizePoolItemQuery queryForm) {
        List<PrizePoolItem> items = prizePoolItemManager.lambdaQuery()
                .eq(StringUtils.isNotBlank(queryForm.getActivityCode()),
                        PrizePoolItem::getActivityCode, queryForm.getActivityCode())
                .list();

        Map<String, PrizeConfig> prizeMap = prizeConfigManager.lambdaQuery().list().stream()
                .collect(Collectors.toMap(PrizeConfig::getPrizeCode, Function.identity(), (a, b) -> a));
        Map<String, ActivityConfig> activityMap = activityConfigManager.lambdaQuery().list().stream()
                .collect(Collectors.toMap(ActivityConfig::getActivityCode, Function.identity(), (a, b) -> a));
        // 一个奖项被哪些奖池引用 —— 库存跨池共享，这是「另一个池的奖怎么也没了」的答案
        Map<Long, List<String>> poolsByItem = poolPrizeMappingManager.lambdaQuery().list().stream()
                .collect(Collectors.groupingBy(PoolPrizeMapping::getPrizeItemId,
                        Collectors.mapping(PoolPrizeMapping::getPoolCode, Collectors.toList())));

        // Redis 剩余量一把取回。逐行取的话一页 200 行就是 200 次串行往返
        Map<String, Map<Long, Integer>> cachedStocks = loadCachedStocks(items);

        List<PrizeItemStockDTO> all = new ArrayList<>();
        for (PrizePoolItem item : items) {
            all.add(analyseOne(item, prizeMap, activityMap, poolsByItem, cachedStocks));
        }

        if (Boolean.TRUE.equals(queryForm.getOnlyIssue())) {
            all = all.stream().filter(v -> !v.getIssueList().isEmpty()).collect(Collectors.toList());
        }

        // 排序即优先级：有危险告警的（含口径漂移）置顶，其次消耗率高的 —— 快抽空的先看见
        all.sort(Comparator
                .comparing((PrizeItemStockDTO v) -> v.getIssueList().stream()
                        .anyMatch(HealthIssue::isDanger) ? 0 : 1)
                .thenComparing(v -> v.getUsedRate() == null ? BigDecimal.ZERO : v.getUsedRate(),
                        Comparator.reverseOrder()));

        PrizeItemStockResultDTO result = new PrizeItemStockResultDTO();
        result.setItemCount(all.size());
        result.setSoldOutCount((int) all.stream()
                .filter(v -> v.getRemainStockDb() != null && v.getRemainStockDb() <= 0).count());
        result.setDriftCount((int) all.stream()
                .filter(v -> v.getStockDrift() != null && v.getStockDrift() != 0).count());
        result.setDangerCount(all.stream().mapToInt(v -> HealthIssue.countDanger(v.getIssueList())).sum());
        result.setWarnCount(all.stream().mapToInt(v -> HealthIssue.countWarn(v.getIssueList())).sum());
        result.setTotalIssuedValue(all.stream().map(v -> v.getIssuedValue() == null ? BigDecimal.ZERO : v.getIssuedValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        result.setTotalRemainValue(all.stream().map(v -> v.getRemainValue() == null ? BigDecimal.ZERO : v.getRemainValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        result.setTotal((long) all.size());
        result.setList(SolvelaPageUtil.subList(all, queryForm.getPageNum(), queryForm.getPageSize()));
        return result;
    }

    /**
     * 单个奖项的库存体检：身份 -> 两个库存口径 -> 奖品与价值 -> 账目自洽。
     */
    private PrizeItemStockDTO analyseOne(PrizePoolItem item, Map<String, PrizeConfig> prizeMap,
                                        Map<String, ActivityConfig> activityMap,
                                        Map<Long, List<String>> poolsByItem,
                                        Map<String, Map<Long, Integer>> cachedStocks) {
        PrizeItemStockDTO vo = fillIdentity(item, activityMap, poolsByItem);
        Integer remainDb = fillStock(item, vo);
        checkStockDrift(item, vo, remainDb, cachedStocks);
        fillPrize(item, prizeMap, vo, remainDb);
        checkLedger(item, vo, remainDb);
        vo.setWhiteListCount(parseWhiteListCount(item.getWhiteList()));
        return vo;
    }

    /**
     * 奖项身份，外加<b>引用它的奖池列表</b>。
     *
     * <p>后者是这个页面独有的一列：库存跨奖池共享（{@code used_stock} 是「跨奖池累计已出数量」），
     * A 池把某个奖抽空了，B 池里同一个奖也没了 —— 不把引用方列出来，
     * 这件事在页面上完全看不出来。
     */
    private PrizeItemStockDTO fillIdentity(PrizePoolItem item, Map<String, ActivityConfig> activityMap,
                                           Map<Long, List<String>> poolsByItem) {
        PrizeItemStockDTO vo = new PrizeItemStockDTO();
        vo.setId(item.getId());
        vo.setActivityCode(item.getActivityCode());
        vo.setPrizeCode(item.getPrizeCode());
        vo.setTotalStock(item.getTotalStock());
        vo.setUsedStock(item.getUsedStock());
        vo.setUserMaxCount(item.getUserMaxCount());
        vo.setIssueList(new ArrayList<>());
        vo.setPoolCodeList(poolsByItem.getOrDefault(item.getId(), List.of()));

        ActivityConfig activity = activityMap.get(item.getActivityCode());
        if (activity != null) {
            vo.setActivityStatus(activity.getStatus());
        }
        return vo;
    }

    /**
     * 一次把这一页所有奖项的 Redis 剩余量取回来。
     *
     * <p>按活动分组是因为库存 key 带活动编码（{@code DrawCacheKey.stock}）——
     * 不分组就凑不出正确的 key。一个活动一次 MGET，通常整页也就一两个活动。
     *
     * @return 活动编码 -> (奖项 id -> 剩余量)。Redis 里没有的奖项不会出现在里面
     */
    private Map<String, Map<Long, Integer>> loadCachedStocks(List<PrizePoolItem> items) {
        Map<String, List<Long>> itemIdsByActivity = items.stream()
                .filter(item -> item.getActivityCode() != null)
                .collect(Collectors.groupingBy(PrizePoolItem::getActivityCode,
                        Collectors.mapping(PrizePoolItem::getId, Collectors.toList())));

        Map<String, Map<Long, Integer>> stocks = new HashMap<>();
        itemIdsByActivity.forEach((activityCode, itemIds) ->
                stocks.put(activityCode, drawStockService.getRemainStocks(activityCode, itemIds)));
        return stocks;
    }

    /**
     * DB 口径的剩余量与消耗率。
     *
     * @return DB 剩余量；不限量的奖项没有这个概念，返回 null
     */
    private Integer fillStock(PrizePoolItem item, PrizeItemStockDTO vo) {
        if (isUnlimited(item)) {
            return null;
        }
        int remainDb = item.getTotalStock() - usedOf(item);
        vo.setRemainStockDb(remainDb);
        if (item.getTotalStock() > 0) {
            vo.setUsedRate(BigDecimal.valueOf(usedOf(item))
                    .divide(BigDecimal.valueOf(item.getTotalStock()), SCALE, RoundingMode.HALF_UP));
        }
        return remainDb;
    }

    /**
     * 与 Redis 口径对账 —— <b>这个页面最该被盯住的一列</b>。
     *
     * <p>运行态的扣减是双层的：Redis Lua 原子预扣扛并发，DB 条件更新做最终一致性兜底。
     * 真正决定「还能不能抽到」的是 Redis 里的剩余量，{@code used_stock} 只是事后对账用的。
     */
    private void checkStockDrift(PrizePoolItem item, PrizeItemStockDTO vo, Integer remainDb,
                                 Map<String, Map<Long, Integer>> cachedStocks) {
        Integer cached = cachedStocks
                .getOrDefault(item.getActivityCode(), Map.of())
                .get(item.getId());
        vo.setRemainStockCache(cached);
        if (cached == null || remainDb == null) {
            return;
        }
        int drift = cached - remainDb;
        vo.setStockDrift(drift);
        if (drift != 0) {
            vo.getIssueList().add(HealthIssue.danger("STOCK_DRIFT",
                    "库存口径漂移：Redis 剩余 " + cached + "，DB 剩余 " + remainDb
                            + "（差 " + drift + "）。运行态按 Redis 预扣，DB 只是兜底对账 —— "
                            + (drift > 0 ? "Redis 偏多可能导致超发" : "Redis 偏少会让奖品提前抽不到")));
        }
    }

    /**
     * 奖品身份，以及<b>已发出/待发出价值</b> —— 抽完最坏赔多少，就看后面这个数。
     */
    private void fillPrize(PrizePoolItem item, Map<String, PrizeConfig> prizeMap,
                           PrizeItemStockDTO vo, Integer remainDb) {
        PrizeConfig prize = prizeMap.get(item.getPrizeCode());
        if (prize == null) {
            vo.getIssueList().add(HealthIssue.danger("PRIZE_MISSING",
                    "奖品「" + item.getPrizeCode() + "」不在资产大库：中奖后派奖会报『奖品配置不存在』，用户拿不到东西"));
            return;
        }
        vo.setPrizeName(prize.getPrizeName());
        vo.setPrizeType(prize.getPrizeType());
        vo.setPrizeValue(prize.getPrizeValue());
        if (prize.getStatus() != EnableStatusEnum.ENABLED) {
            vo.getIssueList().add(HealthIssue.danger("PRIZE_DISABLED",
                    "奖品「" + prize.getPrizeName() + "」已停用，派奖链路会拒绝发放"));
        }
        vo.setIssuedValue(PrizeCatalog.issuedValue(prize, usedOf(item)));
        if (remainDb != null && remainDb > 0) {
            vo.setRemainValue(PrizeCatalog.issuedValue(prize, remainDb));
        }
    }

    /**
     * 库存账目自洽性与补货预警。
     *
     * <p>{@code used_stock} 本不该被人手改 —— 原先的 CRUD 表单却能直接填它，
     * 所以这里的三条「不可能出现」的情况其实都出现过。
     */
    private void checkLedger(PrizePoolItem item, PrizeItemStockDTO vo, Integer remainDb) {
        int used = usedOf(item);
        if (!isUnlimited(item)) {
            if (used > item.getTotalStock()) {
                vo.getIssueList().add(HealthIssue.danger("USED_EXCEEDS_TOTAL",
                        "已出 " + used + " 超过总库存 " + item.getTotalStock() + "：库存账目已经错乱，等于已经超发"));
            } else if (remainDb != null && remainDb <= 0) {
                vo.getIssueList().add(HealthIssue.warn("SOLD_OUT",
                        "已抽空：命中本奖项的请求会降级到兜底，所在奖池没有兜底则直接返回「手慢了，奖品已被抽完」"));
            } else if (isLowStock(vo)) {
                vo.getIssueList().add(HealthIssue.warn("LOW_STOCK",
                        "剩余不足 10%（还剩 " + remainDb + " 个）：按当前消耗速度很快会抽空，需要补货或调低概率"));
            }
        }
        if (used < 0) {
            vo.getIssueList().add(HealthIssue.danger("USED_NEGATIVE",
                    "已出数量为负（" + used + "）：库存账目异常，回滚补偿可能执行了多次"));
        }
        if (vo.getPoolCodeList().isEmpty()) {
            vo.getIssueList().add(HealthIssue.warn("NOT_REFERENCED",
                    "没有任何奖池的坑位引用它：这个奖项配了但永远抽不到"));
        }
    }

    private boolean isLowStock(PrizeItemStockDTO vo) {
        return vo.getUsedRate() != null
                && BigDecimal.ONE.subtract(vo.getUsedRate()).compareTo(LOW_STOCK_THRESHOLD) < 0;
    }

    /** 不限量的奖项没有「剩余」也没有「消耗率」，库存那几条校验对它一条都不适用 */
    private boolean isUnlimited(PrizePoolItem item) {
        return item.getTotalStock() == null || Integer.valueOf(UNLIMITED).equals(item.getTotalStock());
    }

    private int usedOf(PrizePoolItem item) {
        return item.getUsedStock() == null ? 0 : item.getUsedStock();
    }

    /**
     * 白名单人数。解析失败返回 null 而不是 0 —— 0 会被读成「没配白名单」，
     * 而实际情况是「配了但存成了解析不了的格式」，两者的处理完全不同。
     */
    private Integer parseWhiteListCount(String whiteListJson) {
        if (StringUtils.isBlank(whiteListJson)) {
            return 0;
        }
        try {
            List<String> list = JsonUtils.parseType(whiteListJson, new TypeReference<List<String>>() {
            });
            return list == null ? 0 : list.size();
        } catch (RuntimeException e) {
            log.warn("[库存看板] 白名单解析失败，原值={}", whiteListJson, e);
            return null;
        }
    }
}
