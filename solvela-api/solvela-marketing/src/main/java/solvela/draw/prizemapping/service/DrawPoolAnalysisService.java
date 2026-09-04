package solvela.draw.prizemapping.service;

import solvela.enums.ActivityStatusEnum;
import lombok.RequiredArgsConstructor;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.stat.HealthIssue;
import solvela.activity.ActivityConfig;
import solvela.activity.manager.ActivityConfigManager;
import solvela.draw.PrizePoolConfig;
import solvela.draw.poolconfig.manager.PrizePoolConfigManager;
import solvela.draw.PrizePoolItem;
import solvela.draw.poolitem.manager.PrizePoolItemManager;
import solvela.draw.PoolPrizeMapping;
import solvela.draw.prizemapping.domain.query.PoolPrizeMappingQuery;
import solvela.draw.prizemapping.domain.dto.DrawPoolAnalysisResultDTO;
import solvela.draw.prizemapping.domain.dto.DrawPoolAnalysisDTO;
import solvela.draw.prizemapping.domain.dto.DrawPoolSlotDTO;
import solvela.draw.prizemapping.manager.PoolPrizeMappingManager;
import solvela.draw.runtime.DrawStockService;
import solvela.prize.PrizeConfig;
import solvela.prize.prizeconfig.manager.PrizeConfigManager;
import solvela.draw.engine.Ppm;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 奖池概率结构分析与体检。
 *
 * <h3>这个 Service 存在的理由</h3>
 * {@code t_pool_prize_mapping} 一行只有「奖池编码 + 奖项id + 概率」，
 * 平铺成列表看不出任何东西 —— 因为坑位的意义完全来自它在整池里的位置：
 * 概率要按 sortWeight 累加成区间才是引擎的判定依据，闭环要整池判定，
 * 期望赔付要逐坑相加。所以这里按奖池分组重算一遍。
 *
 * <h3>最重要的一条校验</h3>
 * 概率总和必须等于 100。{@code DrawPoolSnapshot} 的构造函数发现未闭环会抛
 * {@code IllegalArgumentException}，而 {@code DrawExecuteService} 的执行路径
 * <b>没有捕获它</b> —— 后果不是「某个奖抽不到」，而是<b>这个奖池每一次抽奖请求都直接报错</b>。
 * 所以未闭环的奖池数在概览里单独占一个数字，不和其他告警混在一起。
 *
 * <h3>为什么要同时读 Redis 库存</h3>
 * 运行态预扣用的是 Redis 里的剩余量，DB 的 used_stock 只是最终一致性兜底。
 * 两者漂移（缓存被误预热、回滚失败）是真实事故，而现有页面只显示 DB 口径，
 * 看到的是个安慰性的数字。这里把两个口径并排放出来，不一致就报出来。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@RequiredArgsConstructor
@Service
public class DrawPoolAnalysisService {

    private final PoolPrizeMappingManager poolPrizeMappingManager;
    private final PrizePoolConfigManager prizePoolConfigManager;
    private final PrizePoolItemManager prizePoolItemManager;
    private final PrizeConfigManager prizeConfigManager;
    private final ActivityConfigManager activityConfigManager;
    private final DrawStockService drawStockService;

    private static final BigDecimal HUNDRED = new BigDecimal("100");


    private static final int UNLIMITED = -1;


    /** 期望赔付展示到 4 位：单次抽奖成本常在几分钱量级，位数少了整列都是 0.00 */
    private static final int AMOUNT_SCALE = 4;

    /**
     * 逐坑算期望赔付时的中间精度。取得比展示精度高，是因为「先各自取整再相加」
     * 与「相加之后再取整」在十几个坑位上会差出肉眼可见的一截，
     * 而整池期望赔付是拿来估活动预算的。
     */
    private static final int COST_INTERNAL_SCALE = 8;

    /**
     * 奖池概率分析。概览与明细一次算完，两者不可能对不上。
     *
     * <p>分页在内存里做：奖池数是配置级的量（一个活动几个池），
     * 先算全量再切页最简单，也让「概览统计的是筛选后的全量」天然成立。
     */
    public DrawPoolAnalysisResultDTO analysis(PoolPrizeMappingQuery queryForm) {
        Materials materials = loadMaterials(queryForm);
        List<DrawPoolAnalysisDTO> all = analyseAll(materials);

        // 排序即优先级：未闭环（现在就在报错）> 有危险告警 > 已上线
        all.sort(Comparator
                .comparing((DrawPoolAnalysisDTO v) -> Boolean.TRUE.equals(v.getProbabilityClosed()) ? 1 : 0)
                .thenComparing(v -> v.getDangerCount() > 0 ? 0 : 1)
                .thenComparing(v -> v.getActivityStatus() == ActivityStatusEnum.ONLINE ? 0 : 1)
                .thenComparing(DrawPoolAnalysisDTO::getPoolCode));
        if (Boolean.TRUE.equals(queryForm.getOnlyIssue())) {
            all = all.stream().filter(v -> v.getDangerCount() > 0 || v.getWarnCount() > 0).toList();
        }
        return summarize(all, queryForm);
    }

    /**
     * 一次把料备齐，之后<b>不再碰数据库</b>。
     *
     * @param poolMap 本次筛选范围内的奖池，编码 -> 配置
     * @param grouped 坑位映射按奖池分组。key 未必在 {@code poolMap} 里 ——
     *                那种就是孤儿映射，见 {@link #analyseAll}
     */
    private record Materials(List<PrizePoolConfig> pools,
                             Map<String, PrizePoolConfig> poolMap,
                             Map<String, List<PoolPrizeMapping>> grouped,
                             Map<Long, PrizePoolItem> itemMap,
                             Map<String, PrizeConfig> prizeMap,
                             Map<String, ActivityConfig> activityMap,
                             Map<String, Map<Long, Integer>> cachedStocks) {
    }

    private Materials loadMaterials(PoolPrizeMappingQuery queryForm) {
        List<PrizePoolConfig> pools = prizePoolConfigManager.lambdaQuery()
                .eq(StringUtils.isNotBlank(queryForm.getActivityCode()),
                        PrizePoolConfig::getActivityCode, queryForm.getActivityCode())
                .eq(StringUtils.isNotBlank(queryForm.getPoolCode()),
                        PrizePoolConfig::getPoolCode, queryForm.getPoolCode())
                .list();
        Map<String, PrizePoolConfig> poolMap = pools.stream()
                .collect(Collectors.toMap(PrizePoolConfig::getPoolCode, Function.identity(), (a, b) -> a));

        Map<String, List<PoolPrizeMapping>> grouped = loadMappings(queryForm, poolMap).stream()
                .collect(Collectors.groupingBy(PoolPrizeMapping::getPoolCode, LinkedHashMap::new, Collectors.toList()));

        return new Materials(pools, poolMap, grouped,
                prizePoolItemManager.lambdaQuery().list().stream()
                        .collect(Collectors.toMap(PrizePoolItem::getId, Function.identity(), (a, b) -> a)),
                prizeConfigManager.lambdaQuery().list().stream()
                        .collect(Collectors.toMap(PrizeConfig::getPrizeCode, Function.identity(), (a, b) -> a)),
                activityConfigManager.lambdaQuery().list().stream()
                        .collect(Collectors.toMap(ActivityConfig::getActivityCode, Function.identity(), (a, b) -> a)),
                loadCachedStocks(pools, grouped));
    }

    /**
     * 一次把这一页会用到的 Redis 剩余量全取回来。
     *
     * <p>逐个坑位调 {@code getRemainStock} 的话，一页 10 个池 x 8 个坑位就是 80 次串行往返 ——
     * 而这一列只是给运营看「Redis 与 DB 对不对得上」。
     *
     * <p>库存 key 带活动编码，所以按活动分组各发一次 MGET。孤儿映射（奖池已不存在）
     * 无从判断活动，它们本来也查不了缓存，直接跳过。
     *
     * @return 活动编码 -> (奖项 id -> 剩余量)
     */
    private Map<String, Map<Long, Integer>> loadCachedStocks(List<PrizePoolConfig> pools,
                                                             Map<String, List<PoolPrizeMapping>> grouped) {
        Map<String, Set<Long>> itemIdsByActivity = new HashMap<>();
        for (PrizePoolConfig pool : pools) {
            if (pool.getActivityCode() == null) {
                continue;
            }
            for (PoolPrizeMapping mapping : grouped.getOrDefault(pool.getPoolCode(), List.of())) {
                if (mapping.getPrizeItemId() != null) {
                    itemIdsByActivity.computeIfAbsent(pool.getActivityCode(), key -> new LinkedHashSet<>())
                            .add(mapping.getPrizeItemId());
                }
            }
        }

        Map<String, Map<Long, Integer>> stocks = new HashMap<>();
        itemIdsByActivity.forEach((activityCode, itemIds) ->
                stocks.put(activityCode, drawStockService.getRemainStocks(activityCode, itemIds)));
        return stocks;
    }

    /**
     * 坑位映射表里<b>没有 activity_code</b>，活动维度只能通过奖池换算过来。
     *
     * <p>按活动筛选时用「本活动的奖池编码集合」去捞映射，而不是不带条件全捞回来再过滤 ——
     * 后者在映射行多起来之后是笔无谓的开销。集合为空说明这个活动一个奖池都没有，
     * 直接给空列表：<b>不能漏掉 in 条件把全表捞回来</b>。
     */
    private List<PoolPrizeMapping> loadMappings(PoolPrizeMappingQuery queryForm,
                                                Map<String, PrizePoolConfig> poolMap) {
        boolean scopeByActivity = StringUtils.isNotBlank(queryForm.getActivityCode());
        if (scopeByActivity && poolMap.isEmpty()) {
            return List.of();
        }
        return poolPrizeMappingManager.lambdaQuery()
                .eq(StringUtils.isNotBlank(queryForm.getPoolCode()),
                        PoolPrizeMapping::getPoolCode, queryForm.getPoolCode())
                .in(scopeByActivity, PoolPrizeMapping::getPoolCode, poolMap.keySet())
                .list();
    }

    /**
     * 逐池分析，外加<b>孤儿映射</b>：坑位还在、它挂靠的奖池已经没了。
     *
     * <p>那些行同样要露头，否则运营只会以为「映射没了」，而不是「奖池没了」。
     *
     * <p>⚠️ 孤儿映射只在「不按活动筛选」时才会出现：它挂靠的奖池都没了，
     * 自然也就无从判断属于哪个活动。所以清空活动筛选是发现孤儿的唯一入口。
     */
    private List<DrawPoolAnalysisDTO> analyseAll(Materials materials) {
        List<DrawPoolAnalysisDTO> all = new ArrayList<>();
        for (PrizePoolConfig pool : materials.pools()) {
            all.add(analyseOne(pool.getPoolCode(), pool,
                    materials.grouped().getOrDefault(pool.getPoolCode(), List.of()),
                    materials.itemMap(), materials.prizeMap(), materials.activityMap(),
                    materials.cachedStocks()));
        }
        for (Map.Entry<String, List<PoolPrizeMapping>> entry : materials.grouped().entrySet()) {
            if (!materials.poolMap().containsKey(entry.getKey())) {
                all.add(analyseOne(entry.getKey(), null, entry.getValue(),
                        materials.itemMap(), materials.prizeMap(), materials.activityMap(),
                    materials.cachedStocks()));
            }
        }
        return all;
    }

    /**
     * 概览按<b>筛选后的全量</b>算，再切页 —— 卡片上的数字与列表翻到第几页无关。
     */
    private DrawPoolAnalysisResultDTO summarize(List<DrawPoolAnalysisDTO> all, PoolPrizeMappingQuery queryForm) {
        DrawPoolAnalysisResultDTO result = new DrawPoolAnalysisResultDTO();
        result.setPoolCount(all.size());
        result.setSlotCount(all.stream().mapToInt(v -> v.getSlotList().size()).sum());
        result.setDangerCount(all.stream().mapToInt(DrawPoolAnalysisDTO::getDangerCount).sum());
        result.setWarnCount(all.stream().mapToInt(DrawPoolAnalysisDTO::getWarnCount).sum());
        // 未闭环的池现在就在报错，单独占一个数字，不和其他告警混在一起
        result.setBrokenPoolCount((int) all.stream()
                .filter(v -> !Boolean.TRUE.equals(v.getProbabilityClosed())).count());
        result.setTotalExpectedCostPerDraw(all.stream().map(DrawPoolAnalysisDTO::getExpectedCostPerDraw)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        result.setTotal((long) all.size());
        result.setList(SolvelaPageUtil.subList(all, queryForm.getPageNum(), queryForm.getPageSize()));
        return result;
    }

    /**
     * 单个奖池的体检：先认身份，再逐坑扫一遍，最后做只有站在整池角度才看得出来的校验。
     *
     * <p>三段分开不是为了短，是因为它们各自回答不同的问题：坑位级的问题（概率为负、
     * 奖项被删）看一行就能判定；整池级的问题（概率没闭环、配了两个兜底、
     * 有多少概率其实已经抽空）单看任何一行都看不出来。
     *
     * @param pool 为 null 表示<b>孤儿映射</b>：坑位还在，它挂靠的奖池已经没了
     */
    private DrawPoolAnalysisDTO analyseOne(String poolCode, PrizePoolConfig pool, List<PoolPrizeMapping> mappings,
                                          Map<Long, PrizePoolItem> itemMap, Map<String, PrizeConfig> prizeMap,
                                          Map<String, ActivityConfig> activityMap,
                                          Map<String, Map<Long, Integer>> cachedStocks) {
        DrawPoolAnalysisDTO vo = new DrawPoolAnalysisDTO();
        vo.setPoolCode(poolCode);
        vo.setIssueList(new ArrayList<>());

        String activityCode = fillIdentity(poolCode, pool, activityMap, vo);
        PoolScan scan = scanSlots(mappings, activityCode, itemMap, prizeMap, cachedStocks);
        scan.fill(vo);
        checkPool(vo, scan);

        vo.setDangerCount(countIssues(vo, HealthIssue::isDanger));
        vo.setWarnCount(countIssues(vo, HealthIssue::isWarn));
        return vo;
    }

    /**
     * 奖池身份：名字、状态、所属活动。
     *
     * @return 活动编码，孤儿奖池为 null（查 Redis 库存要用它，没有就查不了）
     */
    private String fillIdentity(String poolCode, PrizePoolConfig pool,
                                Map<String, ActivityConfig> activityMap, DrawPoolAnalysisDTO vo) {
        if (pool == null) {
            vo.getIssueList().add(HealthIssue.danger("POOL_MISSING",
                    "奖池配置不存在（编码 " + poolCode + "），这些坑位映射是孤儿数据：抽奖找不到奖池，配置也无处修改"));
            return null;
        }
        vo.setPoolName(pool.getPoolName());
        vo.setPoolStatus(pool.getStatus());
        vo.setActivityCode(pool.getActivityCode());
        ActivityConfig activity = activityMap.get(pool.getActivityCode());
        if (activity != null) {
            vo.setActivityStatus(activity.getStatus());
        }
        return pool.getActivityCode();
    }

    /**
     * 逐坑扫描的结果：坑位列表，以及四个只能边扫边累加出来的整池指标。
     *
     * @param probabilitySum       概率总和，用来判闭环
     * @param expectedCost         期望赔付，<b>未取整</b>的累加值 ——
     *                             逐坑取整再相加与相加后再取整不是同一个数
     * @param availableProbability 仍有库存的坑位概率之和，即「现在抽一次真能拿到东西的概率」
     * @param fallbackHasStock     第一个兜底坑位还有没有货；没有兜底时为 null
     */
    private record PoolScan(List<DrawPoolSlotDTO> slots,
                            BigDecimal probabilitySum,
                            BigDecimal expectedCost,
                            BigDecimal availableProbability,
                            Boolean fallbackHasStock,
                            int fallbackCount) {

        void fill(DrawPoolAnalysisDTO vo) {
            vo.setSlotList(slots);
            vo.setSlotCount(slots.size());
            vo.setProbabilitySum(probabilitySum);
            vo.setAvailableProbability(availableProbability);
            vo.setFallbackHasStock(fallbackHasStock);
            vo.setFallbackCount(fallbackCount);
            vo.setExpectedCostPerDraw(expectedCost.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
        }
    }

    private PoolScan scanSlots(List<PoolPrizeMapping> mappings, String activityCode,
                               Map<Long, PrizePoolItem> itemMap, Map<String, PrizeConfig> prizeMap,
                               Map<String, Map<Long, Integer>> cachedStocks) {
        // 坑位顺序 = 概率区间的累加顺序，与引擎 DrawExecuteService 取快照时的排序一致
        List<PoolPrizeMapping> sorted = mappings.stream()
                .sorted(Comparator.comparing(m -> m.getSortWeight() == null ? Integer.MAX_VALUE : m.getSortWeight()))
                .toList();

        List<DrawPoolSlotDTO> slots = new ArrayList<>();
        BigDecimal acc = BigDecimal.ZERO;
        BigDecimal expectedCost = BigDecimal.ZERO;
        BigDecimal availableProb = BigDecimal.ZERO;
        Boolean fallbackHasStock = null;
        int fallbackCount = 0;

        for (PoolPrizeMapping mapping : sorted) {
            SlotScan scanned = scanSlot(mapping, acc, activityCode, itemMap, prizeMap, cachedStocks);
            DrawPoolSlotDTO slot = scanned.slot();
            BigDecimal prob = probabilityOf(mapping);

            acc = slot.getRangeMax();
            expectedCost = expectedCost.add(scanned.expectedCost());
            if (scanned.hasStock() && prob.signum() > 0) {
                availableProb = availableProb.add(prob);
            }
            if (Boolean.TRUE.equals(slot.getFallback())) {
                fallbackCount++;
                if (fallbackHasStock == null) {
                    // 多兜底时以引擎的口径为准：只认坑位顺序里的第一个
                    fallbackHasStock = scanned.hasStock();
                }
            }
            slots.add(slot);
        }
        return new PoolScan(slots, acc, expectedCost, availableProb, fallbackHasStock, fallbackCount);
    }

    /**
     * 一个坑位扫完之后，除了它自己的 DTO，还有两件事只有这里知道、而整池要用。
     *
     * @param hasStock     还有没有货。不能从 DTO 反推：奖项被删时它既不是「不限量」
     *                     也不是「剩余为 0」，但那个坑位确实发不出东西
     * @param expectedCost 本坑位的期望赔付，未取整，理由见 {@link PoolScan#expectedCost}
     */
    private record SlotScan(DrawPoolSlotDTO slot, boolean hasStock, BigDecimal expectedCost) {
    }

    private SlotScan scanSlot(PoolPrizeMapping mapping, BigDecimal rangeMin, String activityCode,
                              Map<Long, PrizePoolItem> itemMap, Map<String, PrizeConfig> prizeMap,
                              Map<String, Map<Long, Integer>> cachedStocks) {
        BigDecimal prob = probabilityOf(mapping);

        DrawPoolSlotDTO slot = new DrawPoolSlotDTO();
        slot.setId(mapping.getId());
        slot.setSortWeight(mapping.getSortWeight());
        slot.setPrizeItemId(mapping.getPrizeItemId());
        slot.setProbability(mapping.getProbability());
        slot.setFallback(Boolean.TRUE.equals(mapping.getIsFallback()));
        slot.setIssueList(new ArrayList<>());
        // 本坑位在引擎里对应的命中区间 [rangeMin, rangeMax)
        slot.setRangeMin(rangeMin);
        slot.setRangeMax(rangeMin.add(prob));

        checkProbability(slot, prob);

        PrizePoolItem item = itemMap.get(mapping.getPrizeItemId());
        if (item == null) {
            slot.getIssueList().add(HealthIssue.danger("ITEM_MISSING",
                    "奖项 id " + mapping.getPrizeItemId() + " 不存在：抽奖时会直接返回「奖池配置异常：奖项已被删除」"));
            return new SlotScan(slot, false, BigDecimal.ZERO);
        }

        boolean hasStock = fillStock(slot, item, prob, activityCode, cachedStocks);
        BigDecimal expectedCost = fillPrize(slot, item, prizeMap, prob);
        return new SlotScan(slot, hasStock, expectedCost);
    }

    private void checkProbability(DrawPoolSlotDTO slot, BigDecimal prob) {
        if (prob.signum() < 0) {
            slot.getIssueList().add(HealthIssue.danger("PROBABILITY_NEGATIVE",
                    "概率为负数 " + prob.toPlainString() + "，会把后续坑位的命中区间整体推乱"));
        } else if (prob.signum() == 0 && !Boolean.TRUE.equals(slot.getFallback())) {
            slot.getIssueList().add(HealthIssue.warn("PROBABILITY_ZERO",
                    "概率为 0 且不是兜底奖项：这个坑位永远不会被抽中"));
        }
    }

    /**
     * 库存两个口径：DB 的 total - used，以及运行态真正用来预扣的 Redis 剩余量。
     *
     * <p>两个口径并排放出来是这个页面的核心价值：现有页面只显示 DB 口径，
     * 而漂移（缓存被误预热、回滚失败）是真实事故，光看 DB 是个安慰性的数字。
     *
     * @return 这个坑位现在还能不能发出东西
     */
    private boolean fillStock(DrawPoolSlotDTO slot, PrizePoolItem item, BigDecimal prob, String activityCode,
                              Map<String, Map<Long, Integer>> cachedStocks) {
        slot.setPrizeCode(item.getPrizeCode());
        slot.setTotalStock(item.getTotalStock());
        slot.setUsedStock(item.getUsedStock());

        // 不限量的坑位永远算「有货」
        boolean unlimited = item.getTotalStock() == null || Integer.valueOf(UNLIMITED).equals(item.getTotalStock());
        Integer remainDb = null;
        boolean hasStock = true;
        if (!unlimited) {
            int used = item.getUsedStock() == null ? 0 : item.getUsedStock();
            remainDb = item.getTotalStock() - used;
            slot.setRemainStockDb(remainDb);
            hasStock = remainDb > 0;
            if (!hasStock) {
                slot.getIssueList().add(soldOutIssue(slot, prob));
            }
        }

        if (activityCode != null) {
            Integer cached = cachedStocks.getOrDefault(activityCode, Map.of()).get(item.getId());
            slot.setRemainStockCache(cached);
            if (cached != null && remainDb != null && cached.intValue() != remainDb.intValue()) {
                slot.getIssueList().add(HealthIssue.danger("STOCK_DRIFT",
                        "库存口径漂移：Redis 剩余 " + cached + "，DB 剩余 " + remainDb
                                + "。运行态按 Redis 预扣，两者不一致意味着缓存被误预热或回滚失败，可能超发或少发"));
            }
        }
        return hasStock;
    }

    /**
     * 抽空的坑位分两种，严重程度差一个量级。
     *
     * <p>兜底是库存降级的最后一道防线：概率命中的奖项没库存时，引擎会退到兜底
     * （{@code DrawEngine} 第 3 步）。兜底自己也没库存，这一退就退空了 ——
     * 结果是整池只要命中任何一个缺货奖项，用户直接收到「手慢了，奖品已被抽完」。
     * 而兜底通常占着最大那块概率，它一空，受影响的是绝大多数请求。
     */
    private HealthIssue soldOutIssue(DrawPoolSlotDTO slot, BigDecimal prob) {
        if (Boolean.TRUE.equals(slot.getFallback())) {
            return HealthIssue.danger("FALLBACK_SOLD_OUT",
                    "兜底奖项自己已经抽空：兜底是库存不足时的最后一道降级，它没库存意味着降级也失败。"
                            + "本坑位概率 " + prob.toPlainString() + "%，加上其他缺货奖项降级过来的请求，"
                            + "这部分用户全部会收到「手慢了，奖品已被抽完」");
        }
        return HealthIssue.warn("SOLD_OUT",
                "库存已耗尽：命中本坑位的请求会降级到兜底奖项，没有兜底则返回「手慢了，奖品已被抽完」");
    }

    /**
     * 奖品身份与期望赔付 = 命中概率 × 奖品单价。
     *
     * @return 本坑位的期望赔付，未取整；奖品没配单价时为 0
     */
    private BigDecimal fillPrize(DrawPoolSlotDTO slot, PrizePoolItem item,
                                 Map<String, PrizeConfig> prizeMap, BigDecimal prob) {
        PrizeConfig prize = prizeMap.get(item.getPrizeCode());
        if (prize == null) {
            slot.getIssueList().add(HealthIssue.danger("PRIZE_MISSING",
                    "奖品「" + item.getPrizeCode() + "」不在资产大库：派奖时会报『奖品配置不存在』，用户中了奖拿不到东西"));
            return BigDecimal.ZERO;
        }
        slot.setPrizeName(prize.getPrizeName());
        slot.setPrizeType(prize.getPrizeType());
        slot.setPrizeValue(prize.getPrizeValue());
        if (prize.getPrizeValue() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal cost = prob.divide(HUNDRED, COST_INTERNAL_SCALE, RoundingMode.HALF_UP)
                .multiply(prize.getPrizeValue());
        slot.setExpectedCostPerDraw(cost.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
        return cost;
    }

    /**
     * 整池校验：这些问题单看任何一个坑位都发现不了。
     */
    private void checkPool(DrawPoolAnalysisDTO vo, PoolScan scan) {
        boolean empty = scan.slots().isEmpty();
        boolean closed = !empty && Ppm.isClosedPercent(scan.probabilitySum());
        vo.setProbabilityClosed(closed);

        if (empty) {
            vo.getIssueList().add(HealthIssue.danger("POOL_EMPTY",
                    "奖池一个坑位都没有：抽奖时快照构造直接抛「奖池快照不能为空」，本池不可用"));
        } else if (!closed) {
            vo.getIssueList().add(HealthIssue.danger("PROBABILITY_NOT_CLOSED",
                    "概率总和为 " + scan.probabilitySum().toPlainString() + "%，未闭环到 100%："
                            + "抽奖时快照构造会抛异常且执行链路未捕获，本奖池的每一次抽奖请求都会直接报错"));
        }

        if (scan.fallbackCount() > 1) {
            vo.getIssueList().add(HealthIssue.danger("MULTI_FALLBACK",
                    "配置了 " + scan.fallbackCount() + " 个兜底奖项：引擎只认坑位顺序里的第一个，其余的静默失效"));
        } else if (scan.fallbackCount() == 0 && !empty) {
            vo.getIssueList().add(HealthIssue.warn("NO_FALLBACK",
                    "没有兜底奖项：命中的奖项一旦无库存就直接返回「手慢了，奖品已被抽完」，没有降级余地"));
        }

        if (!empty && closed) {
            checkStockExposure(vo, scan);
        }
    }

    /**
     * 有货概率覆盖率：奖池的真实健康度。
     *
     * <p>配置里的概率是静态的，但库存会被抽空 —— 抽空的坑位仍占着概率区间，
     * 命中后只会走降级或直接失败。所以「还有多少概率真能拿到东西」才是用户的实际体验，
     * 而这个数在原页面上完全不存在。
     *
     * <p>分级说明：兜底还有货时，缺口部分由兜底接住，用户至少拿到安慰奖；
     * 兜底也空了，缺口就是纯失败率 —— 严重程度差一个量级，所以分开报。
     */
    private void checkStockExposure(DrawPoolAnalysisDTO vo, PoolScan scan) {
        BigDecimal gap = HUNDRED.subtract(scan.availableProbability());
        if (gap.signum() <= 0) {
            return;
        }
        String gapText = gap.stripTrailingZeros().toPlainString();
        if (Boolean.TRUE.equals(scan.fallbackHasStock())) {
            vo.getIssueList().add(HealthIssue.warn("STOCK_DEGRADED",
                    "约 " + gapText + "% 的抽奖请求要靠兜底接住：这部分概率对应的奖项已抽空，"
                            + "命中后会降级发兜底奖品。兜底一旦也抽空，这部分就变成纯失败"));
            return;
        }
        vo.getIssueList().add(HealthIssue.danger("NO_STOCK_EXPOSURE",
                "约 " + gapText + "% 的抽奖请求会直接失败：这部分概率对应的奖项已抽空，而"
                        + (scan.fallbackCount() == 0 ? "本池没有兜底奖项" : "兜底奖项自己也没有库存了")
                        + "，用户会收到「手慢了，奖品已被抽完」"));
    }

    /**
     * 整池告警数 = 池级 + 所有坑位级。两级合起来数，是因为页面按这个数排序与置顶，
     * 而「某个坑位的奖品被删了」对运营的紧急程度与池级问题没有区别。
     */
    private int countIssues(DrawPoolAnalysisDTO vo, Predicate<HealthIssue> level) {
        int count = (int) vo.getIssueList().stream().filter(level).count();
        for (DrawPoolSlotDTO slot : vo.getSlotList()) {
            count += (int) slot.getIssueList().stream().filter(level).count();
        }
        return count;
    }

    private BigDecimal probabilityOf(PoolPrizeMapping mapping) {
        return mapping.getProbability() == null ? BigDecimal.ZERO : mapping.getProbability();
    }
}
