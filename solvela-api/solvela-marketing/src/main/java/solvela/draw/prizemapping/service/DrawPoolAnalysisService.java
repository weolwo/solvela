package solvela.draw.prizemapping.service;

import solvela.enums.ActivityStatusEnum;
import lombok.RequiredArgsConstructor;
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
import solvela.draw.prizemapping.domain.dto.DrawPoolIssueDTO;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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


    private static final int AMOUNT_SCALE = 4;

    /**
     * 奖池概率分析。概览与明细一次算完，两者不可能对不上。
     *
     * <p>分页在内存里做：奖池数是配置级的量（一个活动几个池），
     * 先算全量再切页最简单，也让「概览统计的是筛选后的全量」天然成立。
     */
    public DrawPoolAnalysisResultDTO analysis(PoolPrizeMappingQuery queryForm) {
        // ---- 1. 备料，之后不再碰数据库 ----
        List<PrizePoolConfig> pools = prizePoolConfigManager.lambdaQuery()
                .eq(StringUtils.isNotBlank(queryForm.getActivityCode()),
                        PrizePoolConfig::getActivityCode, queryForm.getActivityCode())
                .eq(StringUtils.isNotBlank(queryForm.getPoolCode()),
                        PrizePoolConfig::getPoolCode, queryForm.getPoolCode())
                .list();
        Map<String, PrizePoolConfig> poolMap = pools.stream()
                .collect(Collectors.toMap(PrizePoolConfig::getPoolCode, Function.identity(), (a, b) -> a));

        /*
         * 坑位映射表里没有 activity_code，活动维度只能通过奖池换算过来。
         * 按活动筛选时用「本活动的奖池编码集合」去捞映射，而不是不带条件全捞回来再过滤 ——
         * 后者在映射行多起来之后是笔无谓的开销。
         * 集合为空说明这个活动一个奖池都没有，直接给空列表，不能漏掉 in 条件把全表捞回来。
         */
        boolean scopeByActivity = StringUtils.isNotBlank(queryForm.getActivityCode());
        List<PoolPrizeMapping> mappings;
        if (scopeByActivity && poolMap.isEmpty()) {
            mappings = List.of();
        } else {
            mappings = poolPrizeMappingManager.lambdaQuery()
                    .eq(StringUtils.isNotBlank(queryForm.getPoolCode()),
                            PoolPrizeMapping::getPoolCode, queryForm.getPoolCode())
                    .in(scopeByActivity, PoolPrizeMapping::getPoolCode, poolMap.keySet())
                    .list();
        }

        Map<Long, PrizePoolItem> itemMap = prizePoolItemManager.lambdaQuery().list().stream()
                .collect(Collectors.toMap(PrizePoolItem::getId, Function.identity(), (a, b) -> a));
        Map<String, PrizeConfig> prizeMap = prizeConfigManager.lambdaQuery().list().stream()
                .collect(Collectors.toMap(PrizeConfig::getPrizeCode, Function.identity(), (a, b) -> a));
        Map<String, ActivityConfig> activityMap = activityConfigManager.lambdaQuery().list().stream()
                .collect(Collectors.toMap(ActivityConfig::getActivityCode, Function.identity(), (a, b) -> a));

        /*
         * 坑位映射可能指向一个已经不存在的奖池（孤儿映射）——
         * 那些行同样要露头，否则运营只会以为「映射没了」，而不是「奖池没了」。
         *
         * ⚠️ 孤儿映射只在「不按活动筛选」时才会出现：它挂靠的奖池都没了，
         * 自然也就无从判断属于哪个活动。所以清空活动筛选是发现孤儿的唯一入口。
         */
        Map<String, List<PoolPrizeMapping>> grouped = mappings.stream()
                .collect(Collectors.groupingBy(PoolPrizeMapping::getPoolCode, LinkedHashMap::new, Collectors.toList()));

        List<DrawPoolAnalysisDTO> all = new ArrayList<>();
        for (PrizePoolConfig pool : pools) {
            all.add(analyseOne(pool.getPoolCode(), pool,
                    grouped.getOrDefault(pool.getPoolCode(), List.of()),
                    itemMap, prizeMap, activityMap));
        }
        for (Map.Entry<String, List<PoolPrizeMapping>> entry : grouped.entrySet()) {
            if (!poolMap.containsKey(entry.getKey())) {
                all.add(analyseOne(entry.getKey(), null, entry.getValue(), itemMap, prizeMap, activityMap));
            }
        }

        // ---- 2. 排序即优先级：未闭环（现在就在报错）> 有危险告警 > 已上线 ----
        all.sort(Comparator
                .comparing((DrawPoolAnalysisDTO v) -> Boolean.TRUE.equals(v.getProbabilityClosed()) ? 1 : 0)
                .thenComparing(v -> v.getDangerCount() > 0 ? 0 : 1)
                .thenComparing(v -> v.getActivityStatus() == ActivityStatusEnum.ONLINE ? 0 : 1)
                .thenComparing(DrawPoolAnalysisDTO::getPoolCode));

        if (Boolean.TRUE.equals(queryForm.getOnlyIssue())) {
            all = all.stream().filter(v -> v.getDangerCount() > 0 || v.getWarnCount() > 0).collect(Collectors.toList());
        }

        DrawPoolAnalysisResultDTO result = new DrawPoolAnalysisResultDTO();
        result.setPoolCount(all.size());
        result.setSlotCount(all.stream().mapToInt(v -> v.getSlotList().size()).sum());
        result.setDangerCount(all.stream().mapToInt(DrawPoolAnalysisDTO::getDangerCount).sum());
        result.setWarnCount(all.stream().mapToInt(DrawPoolAnalysisDTO::getWarnCount).sum());
        result.setBrokenPoolCount((int) all.stream()
                .filter(v -> !Boolean.TRUE.equals(v.getProbabilityClosed())).count());
        result.setTotalExpectedCostPerDraw(all.stream().map(DrawPoolAnalysisDTO::getExpectedCostPerDraw)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        result.setTotal((long) all.size());
        result.setList(page(all, queryForm.getPageNum(), queryForm.getPageSize()));
        return result;
    }

    private List<DrawPoolAnalysisDTO> page(List<DrawPoolAnalysisDTO> all, Long pageNum, Long pageSize) {
        long num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        int from = (int) Math.min((num - 1) * size, all.size());
        int to = (int) Math.min(from + size, all.size());
        return all.subList(from, to);
    }

    private DrawPoolAnalysisDTO analyseOne(String poolCode, PrizePoolConfig pool, List<PoolPrizeMapping> mappings,
                                          Map<Long, PrizePoolItem> itemMap, Map<String, PrizeConfig> prizeMap,
                                          Map<String, ActivityConfig> activityMap) {
        DrawPoolAnalysisDTO vo = new DrawPoolAnalysisDTO();
        vo.setPoolCode(poolCode);
        vo.setIssueList(new ArrayList<>());

        String activityCode = null;
        if (pool == null) {
            vo.getIssueList().add(DrawPoolIssueDTO.danger("POOL_MISSING",
                    "奖池配置不存在（编码 " + poolCode + "），这些坑位映射是孤儿数据：抽奖找不到奖池，配置也无处修改"));
        } else {
            vo.setPoolName(pool.getPoolName());
            vo.setPoolStatus(pool.getStatus());
            activityCode = pool.getActivityCode();
            vo.setActivityCode(activityCode);
            ActivityConfig activity = activityMap.get(activityCode);
            if (activity != null) {
                vo.setActivityStatus(activity.getStatus());
            }
        }

        // 坑位顺序 = 概率区间的累加顺序，与引擎 DrawExecuteService 取快照时的排序一致
        List<PoolPrizeMapping> sorted = mappings.stream()
                .sorted(Comparator.comparing(m -> m.getSortWeight() == null ? Integer.MAX_VALUE : m.getSortWeight()))
                .collect(Collectors.toList());

        List<DrawPoolSlotDTO> slots = new ArrayList<>();
        BigDecimal acc = BigDecimal.ZERO;
        BigDecimal expectedCost = BigDecimal.ZERO;
        // 仍有库存的坑位概率之和 —— 「现在抽一次真能拿到东西的概率」
        BigDecimal availableProb = BigDecimal.ZERO;
        Boolean fallbackHasStock = null;
        int fallbackCount = 0;

        for (PoolPrizeMapping mapping : sorted) {
            DrawPoolSlotDTO slot = new DrawPoolSlotDTO();
            slot.setId(mapping.getId());
            slot.setSortWeight(mapping.getSortWeight());
            slot.setPrizeItemId(mapping.getPrizeItemId());
            slot.setProbability(mapping.getProbability());
            slot.setFallback(Boolean.TRUE.equals(mapping.getIsFallback()));
            slot.setIssueList(new ArrayList<>());
            if (Boolean.TRUE.equals(slot.getFallback())) {
                fallbackCount++;
            }

            BigDecimal prob = mapping.getProbability() == null ? BigDecimal.ZERO : mapping.getProbability();
            slot.setRangeMin(acc);
            acc = acc.add(prob);
            slot.setRangeMax(acc);

            if (prob.signum() < 0) {
                slot.getIssueList().add(DrawPoolIssueDTO.danger("PROBABILITY_NEGATIVE",
                        "概率为负数 " + prob.toPlainString() + "，会把后续坑位的命中区间整体推乱"));
            } else if (prob.signum() == 0 && !Boolean.TRUE.equals(slot.getFallback())) {
                slot.getIssueList().add(DrawPoolIssueDTO.warn("PROBABILITY_ZERO",
                        "概率为 0 且不是兜底奖项：这个坑位永远不会被抽中"));
            }

            PrizePoolItem item = itemMap.get(mapping.getPrizeItemId());
            boolean slotHasStock = false;
            if (item == null) {
                slot.getIssueList().add(DrawPoolIssueDTO.danger("ITEM_MISSING",
                        "奖项 id " + mapping.getPrizeItemId() + " 不存在：抽奖时会直接返回「奖池配置异常：奖项已被删除」"));
            } else {
                slot.setPrizeCode(item.getPrizeCode());
                slot.setTotalStock(item.getTotalStock());
                slot.setUsedStock(item.getUsedStock());

                Integer remainDb = null;
                // 不限量的坑位永远算「有货」
                slotHasStock = item.getTotalStock() == null || Integer.valueOf(UNLIMITED).equals(item.getTotalStock());
                if (item.getTotalStock() != null && !Integer.valueOf(UNLIMITED).equals(item.getTotalStock())) {
                    int used = item.getUsedStock() == null ? 0 : item.getUsedStock();
                    remainDb = item.getTotalStock() - used;
                    slot.setRemainStockDb(remainDb);
                    slotHasStock = remainDb > 0;
                    if (remainDb <= 0) {
                        /*
                         * 兜底奖项抽空要单独按 DANGER 报，不能和普通坑位一样只是个警告。
                         *
                         * 兜底是库存降级的最后一道防线：概率命中的奖项没库存时，引擎会退到兜底
                         * （DrawEngine 第 3 步）。兜底自己也没库存，这一退就退空了 ——
                         * 结果是整池只要命中任何一个缺货奖项，用户直接收到「手慢了，奖品已被抽完」。
                         * 而兜底通常占着最大那块概率，它一空，受影响的是绝大多数请求。
                         */
                        if (Boolean.TRUE.equals(slot.getFallback())) {
                            slot.getIssueList().add(DrawPoolIssueDTO.danger("FALLBACK_SOLD_OUT",
                                    "兜底奖项自己已经抽空：兜底是库存不足时的最后一道降级，它没库存意味着降级也失败。"
                                            + "本坑位概率 " + prob.toPlainString() + "%，加上其他缺货奖项降级过来的请求，"
                                            + "这部分用户全部会收到「手慢了，奖品已被抽完」"));
                        } else {
                            slot.getIssueList().add(DrawPoolIssueDTO.warn("SOLD_OUT",
                                    "库存已耗尽：命中本坑位的请求会降级到兜底奖项，没有兜底则返回「手慢了，奖品已被抽完」"));
                        }
                    }
                }

                // 运行态真正用来预扣的是 Redis 里的数，与 DB 口径漂移就是事故
                if (activityCode != null) {
                    Integer cached = drawStockService.getRemainStock(activityCode, item.getId());
                    slot.setRemainStockCache(cached);
                    if (cached != null && remainDb != null && cached.intValue() != remainDb.intValue()) {
                        slot.getIssueList().add(DrawPoolIssueDTO.danger("STOCK_DRIFT",
                                "库存口径漂移：Redis 剩余 " + cached + "，DB 剩余 " + remainDb
                                        + "。运行态按 Redis 预扣，两者不一致意味着缓存被误预热或回滚失败，可能超发或少发"));
                    }
                }

                PrizeConfig prize = prizeMap.get(item.getPrizeCode());
                if (prize == null) {
                    slot.getIssueList().add(DrawPoolIssueDTO.danger("PRIZE_MISSING",
                            "奖品「" + item.getPrizeCode() + "」不在资产大库：派奖时会报『奖品配置不存在』，用户中了奖拿不到东西"));
                } else {
                    slot.setPrizeName(prize.getPrizeName());
                    slot.setPrizeType(prize.getPrizeType());
                    slot.setPrizeValue(prize.getPrizeValue());
                    if (prize.getPrizeValue() != null) {
                        BigDecimal cost = prob.divide(HUNDRED, 8, RoundingMode.HALF_UP)
                                .multiply(prize.getPrizeValue());
                        slot.setExpectedCostPerDraw(cost.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
                        expectedCost = expectedCost.add(cost);
                    }
                }
            }

            if (slotHasStock && prob.signum() > 0) {
                availableProb = availableProb.add(prob);
            }
            if (Boolean.TRUE.equals(slot.getFallback()) && fallbackHasStock == null) {
                // 多兜底时以引擎的口径为准：只认坑位顺序里的第一个
                fallbackHasStock = slotHasStock;
            }
            slots.add(slot);
        }

        vo.setAvailableProbability(availableProb);
        vo.setFallbackHasStock(fallbackHasStock);
        vo.setSlotList(slots);
        vo.setSlotCount(slots.size());
        vo.setProbabilitySum(acc);
        vo.setFallbackCount(fallbackCount);
        vo.setExpectedCostPerDraw(expectedCost.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));

        // ---- 整池校验 ----
        boolean closed = !slots.isEmpty() && Ppm.isClosedPercent(acc);
        vo.setProbabilityClosed(closed);
        if (slots.isEmpty()) {
            vo.getIssueList().add(DrawPoolIssueDTO.danger("POOL_EMPTY",
                    "奖池一个坑位都没有：抽奖时快照构造直接抛「奖池快照不能为空」，本池不可用"));
        } else if (!closed) {
            vo.getIssueList().add(DrawPoolIssueDTO.danger("PROBABILITY_NOT_CLOSED",
                    "概率总和为 " + acc.toPlainString() + "%，未闭环到 100%："
                            + "抽奖时快照构造会抛异常且执行链路未捕获，本奖池的每一次抽奖请求都会直接报错"));
        }
        if (fallbackCount > 1) {
            vo.getIssueList().add(DrawPoolIssueDTO.danger("MULTI_FALLBACK",
                    "配置了 " + fallbackCount + " 个兜底奖项：引擎只认坑位顺序里的第一个，其余的静默失效"));
        } else if (fallbackCount == 0 && !slots.isEmpty()) {
            vo.getIssueList().add(DrawPoolIssueDTO.warn("NO_FALLBACK",
                    "没有兜底奖项：命中的奖项一旦无库存就直接返回「手慢了，奖品已被抽完」，没有降级余地"));
        }

        /*
         * 有货概率覆盖率：奖池真实健康度。
         *
         * 配置里的概率是静态的，但库存会被抽空 —— 抽空的坑位仍占着概率区间，
         * 命中后只会走降级或直接失败。所以「还有多少概率真能拿到东西」才是用户的实际体验，
         * 而这个数在原页面上完全不存在。
         *
         * 分级说明：兜底还有货时，缺口部分由兜底接住，用户至少拿到安慰奖；
         * 兜底也空了，缺口就是纯失败率 —— 严重程度差一个量级，所以分开报。
         */
        if (!slots.isEmpty() && closed) {
            BigDecimal gap = HUNDRED.subtract(availableProb);
            if (gap.signum() > 0) {
                boolean fallbackAlive = Boolean.TRUE.equals(fallbackHasStock);
                String gapText = gap.stripTrailingZeros().toPlainString();
                if (!fallbackAlive) {
                    vo.getIssueList().add(DrawPoolIssueDTO.danger("NO_STOCK_EXPOSURE",
                            "约 " + gapText + "% 的抽奖请求会直接失败："
                                    + "这部分概率对应的奖项已抽空，而"
                                    + (fallbackCount == 0 ? "本池没有兜底奖项" : "兜底奖项自己也没有库存了")
                                    + "，用户会收到「手慢了，奖品已被抽完」"));
                } else {
                    vo.getIssueList().add(DrawPoolIssueDTO.warn("STOCK_DEGRADED",
                            "约 " + gapText + "% 的抽奖请求要靠兜底接住：这部分概率对应的奖项已抽空，"
                                    + "命中后会降级发兜底奖品。兜底一旦也抽空，这部分就变成纯失败"));
                }
            }
        }

        int danger = (int) vo.getIssueList().stream().filter(i -> "DANGER".equals(i.getLevel())).count()
                + slots.stream().mapToInt(s -> (int) s.getIssueList().stream()
                        .filter(i -> "DANGER".equals(i.getLevel())).count()).sum();
        int warn = (int) vo.getIssueList().stream().filter(i -> "WARN".equals(i.getLevel())).count()
                + slots.stream().mapToInt(s -> (int) s.getIssueList().stream()
                        .filter(i -> "WARN".equals(i.getLevel())).count()).sum();
        vo.setDangerCount(danger);
        vo.setWarnCount(warn);
        return vo;
    }
}
