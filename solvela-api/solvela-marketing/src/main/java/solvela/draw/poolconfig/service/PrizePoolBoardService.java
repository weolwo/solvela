package solvela.draw.poolconfig.service;

import solvela.enums.ActivityStatusEnum;
import solvela.enums.PrizePoolStatusEnum;
import lombok.RequiredArgsConstructor;
import solvela.activity.ActivityConfig;
import solvela.activity.manager.ActivityConfigManager;
import solvela.draw.DrawConfig;
import solvela.draw.PrizePoolConfig;
import solvela.draw.poolconfig.domain.query.PrizePoolConfigQuery;
import solvela.draw.poolconfig.domain.dto.PoolConfigIssueDTO;
import solvela.draw.poolconfig.domain.dto.PrizePoolBoardResultDTO;
import solvela.draw.poolconfig.domain.dto.PrizePoolBoardDTO;
import solvela.draw.poolconfig.manager.PrizePoolConfigManager;
import solvela.draw.PrizePoolItem;
import solvela.draw.poolitem.manager.PrizePoolItemManager;
import solvela.draw.PoolPrizeMapping;
import solvela.draw.prizemapping.manager.PoolPrizeMappingManager;
import solvela.draw.drawconfig.service.DrawConfigService;
import solvela.draw.runtime.DrawPeriodResolver;
import solvela.draw.engine.Ppm;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 奖池配置一览与体检。
 *
 * <h3>这个页面为什么保留编辑</h3>
 * 抽奖工作台保存奖池时<b>只管 poolName</b>（见 {@code workbenchSave} 第 7 步的注释），
 * {@code reset_period} 与 {@code status} 只有这个页面能改 —— 删了它这两个字段就没有入口了。
 *
 * <h3>这个页面独有的体检项</h3>
 * 概率闭环、坑位缺失这类问题概率分析页也会报，但有两条<b>只有这里能发现</b>，
 * 因为它们是「奖池自身配置」与「别处配置」的<b>搭配</b>问题：
 * <ul>
 *   <li><b>重置周期配了却没有任何限领奖项。</b>{@code reset_period} 重置的是
 *       {@code user_max_count} 的计数，池内所有奖项都不限领时，重置周期配成什么都不影响行为 ——
 *       运营以为设了「每天一次」，实际是无限抽；</li>
 *   <li><b>有限领奖项却选了「活动期间」。</b>意味着用户在整个活动里只能中这么多次、永不恢复。
 *       有时是故意的（限量大奖），但更多时候是没意识到默认值的含义。</li>
 * </ul>
 * 这两条都要同时看 {@code t_prize_pool_config} 与 {@code t_prize_pool_item}，
 * 而那两张表分属两个页面，谁都看不全。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@RequiredArgsConstructor
@Service
public class PrizePoolBoardService {

    private final PrizePoolConfigManager prizePoolConfigManager;
    private final DrawConfigService drawConfigService;
    private final PoolPrizeMappingManager poolPrizeMappingManager;
    private final PrizePoolItemManager prizePoolItemManager;
    private final ActivityConfigManager activityConfigManager;

    private static final int UNLIMITED = -1;

    /**
     * 奖池一览。概览与明细一次算完，两者不可能对不上。
     *
     * <p>分页在内存里做：奖池是配置级的量（一个活动几个池），先算全量再切页最简单，
     * 也让「概览统计的是筛选后的全量」天然成立。
     */
    public PrizePoolBoardResultDTO board(PrizePoolConfigQuery queryForm) {
        List<PrizePoolConfig> pools = prizePoolConfigManager.lambdaQuery()
                .eq(StringUtils.isNotBlank(queryForm.getActivityCode()),
                        PrizePoolConfig::getActivityCode, queryForm.getActivityCode())
                .eq(StringUtils.isNotBlank(queryForm.getPoolCode()),
                        PrizePoolConfig::getPoolCode, queryForm.getPoolCode())
                .like(StringUtils.isNotBlank(queryForm.getPoolName()),
                        PrizePoolConfig::getPoolName, queryForm.getPoolName())
                .eq(queryForm.getStatus() != null, PrizePoolConfig::getStatus, queryForm.getStatus())
                .list();

        Map<String, ActivityConfig> activityMap = activityConfigManager.lambdaQuery().list().stream()
                .collect(Collectors.toMap(ActivityConfig::getActivityCode, Function.identity(), (a, b) -> a));
        Map<Long, PrizePoolItem> itemMap = prizePoolItemManager.lambdaQuery().list().stream()
                .collect(Collectors.toMap(PrizePoolItem::getId, Function.identity(), (a, b) -> a));
        Map<String, List<PoolPrizeMapping>> mappingByPool = poolPrizeMappingManager.lambdaQuery().list().stream()
                .collect(Collectors.groupingBy(PoolPrizeMapping::getPoolCode));

        List<PrizePoolBoardDTO> all = new ArrayList<>();
        for (PrizePoolConfig pool : pools) {
            all.add(analyseOne(pool, activityMap, itemMap,
                    mappingByPool.getOrDefault(pool.getPoolCode(), List.of())));
        }

        if (Boolean.TRUE.equals(queryForm.getOnlyIssue())) {
            all = all.stream().filter(v -> v.getDangerCount() > 0 || v.getWarnCount() > 0).collect(Collectors.toList());
        }

        // 排序即优先级：不能抽的排最前（配了却用不了最该先修），其次有告警的，最后按创建时间倒序
        all.sort(Comparator
                .comparing((PrizePoolBoardDTO v) -> Boolean.TRUE.equals(v.getDrawable()) ? 1 : 0)
                .thenComparing(v -> v.getDangerCount() > 0 ? 0 : 1)
                .thenComparing(PrizePoolBoardDTO::getCreateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())));

        PrizePoolBoardResultDTO result = new PrizePoolBoardResultDTO();
        result.setPoolCount(all.size());
        result.setDrawableCount((int) all.stream().filter(v -> Boolean.TRUE.equals(v.getDrawable())).count());
        result.setDangerCount(all.stream().mapToInt(PrizePoolBoardDTO::getDangerCount).sum());
        result.setWarnCount(all.stream().mapToInt(PrizePoolBoardDTO::getWarnCount).sum());
        result.setTotal((long) all.size());
        result.setList(page(all, queryForm.getPageNum(), queryForm.getPageSize()));
        return result;
    }

    private List<PrizePoolBoardDTO> page(List<PrizePoolBoardDTO> all, Long pageNum, Long pageSize) {
        long num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        int from = (int) Math.min((num - 1) * size, all.size());
        int to = (int) Math.min(from + size, all.size());
        return all.subList(from, to);
    }

    private PrizePoolBoardDTO analyseOne(PrizePoolConfig pool, Map<String, ActivityConfig> activityMap,
                                        Map<Long, PrizePoolItem> itemMap, List<PoolPrizeMapping> mappings) {
        PrizePoolBoardDTO vo = new PrizePoolBoardDTO();
        vo.setId(pool.getId());
        vo.setActivityCode(pool.getActivityCode());
        vo.setPoolCode(pool.getPoolCode());
        vo.setPoolName(pool.getPoolName());
        vo.setResetPeriod(java.util.Optional.ofNullable(drawConfigService.getByPool(pool))
                .map(DrawConfig::getResetPeriod).orElse(null));
        vo.setStatus(pool.getStatus());
        vo.setCreateTime(pool.getCreateTime());
        vo.setIssueList(new ArrayList<>());

        ActivityConfig activity = activityMap.get(pool.getActivityCode());
        if (activity == null) {
            vo.getIssueList().add(PoolConfigIssueDTO.danger("ACTIVITY_MISSING",
                    "所属活动 " + pool.getActivityCode() + " 不存在：这个奖池挂在一个已被删除的活动上，永远不会被抽到"));
        } else {
            vo.setActivityName(activity.getActivityName());
            vo.setActivityStatus(activity.getStatus());
        }
        boolean activityOnline = activity != null && activity.getStatus() == ActivityStatusEnum.ONLINE;
        boolean poolOpen = pool.getStatus() == PrizePoolStatusEnum.OPEN;

        // ---- 坑位与概率 ----
        BigDecimal sum = BigDecimal.ZERO;
        int fallbackCount = 0;
        Set<Long> itemIds = new java.util.HashSet<>();
        for (PoolPrizeMapping mapping : mappings) {
            sum = sum.add(mapping.getProbability() == null ? BigDecimal.ZERO : mapping.getProbability());
            if (Boolean.TRUE.equals(mapping.getIsFallback())) {
                fallbackCount++;
            }
            if (mapping.getPrizeItemId() != null) {
                itemIds.add(mapping.getPrizeItemId());
            }
        }
        boolean closed = !mappings.isEmpty() && Ppm.isClosedPercent(sum);
        vo.setSlotCount(mappings.size());
        vo.setProbabilitySum(sum);
        vo.setProbabilityClosed(closed);
        vo.setFallbackCount(fallbackCount);

        // 池内有多少奖项配了单人限领 —— 这是判断 reset_period 有没有意义的唯一依据
        int limitedItemCount = 0;
        for (Long itemId : itemIds) {
            PrizePoolItem item = itemMap.get(itemId);
            if (item != null && item.getUserMaxCount() != null && item.getUserMaxCount() != UNLIMITED) {
                limitedItemCount++;
            }
        }
        vo.setLimitedItemCount(limitedItemCount);

        vo.setDrawable(activityOnline && poolOpen && !mappings.isEmpty() && closed);

        // ---- 体检 ----
        if (mappings.isEmpty()) {
            vo.getIssueList().add(PoolConfigIssueDTO.danger("POOL_EMPTY",
                    "奖池一个坑位都没有：抽奖时快照构造直接抛「奖池快照不能为空」，本池不可用。请到抽奖工作台配置奖项"));
        } else if (!closed) {
            vo.getIssueList().add(PoolConfigIssueDTO.danger("PROBABILITY_NOT_CLOSED",
                    "概率总和为 " + sum.toPlainString() + "%，未闭环到 100%："
                            + "抽奖时快照构造会抛异常且执行链路未捕获，本奖池的每一次抽奖请求都会直接报错"));
        }

        /*
         * 开关与活动状态的搭配。两种「进不来」的形态要分开说 ——
         * 运营看到「奖池已关闭」和「活动没上线」要做的事完全不同。
         */
        if (activityOnline && !poolOpen) {
            vo.getIssueList().add(PoolConfigIssueDTO.warn("POOL_CLOSED_WHILE_ONLINE",
                    "活动已上线但本奖池处于关闭状态：用户抽奖会被拒绝（奖池未开启）。若是有意停用可忽略"));
        }
        if (!activityOnline && poolOpen && activity != null) {
            vo.getIssueList().add(PoolConfigIssueDTO.warn("ONLINE_BUT_ACTIVITY_OFFLINE",
                    "奖池已开启但活动未上线：用户仍然进不来。要真正开放需要先上线活动"));
        }

        /*
         * ⚠️ 本页独有的两条：reset_period 与单人限领的搭配。
         * 这两条要同时看奖池表与奖项表，而它们分属两个页面，谁都看不全。
         */
        // 重置周期住在抽奖配置上（玩法级），但这条体检要同时看奖项的限领设置，所以在奖池页出现
        DrawConfig drawConfig = drawConfigService.getByPool(pool);
        String resetPeriod = drawConfig == null ? null : drawConfig.getResetPeriod();
        boolean periodic = DrawPeriodResolver.needsClock(resetPeriod);
        if (drawConfig == null) {
            vo.getIssueList().add(PoolConfigIssueDTO.warn("NO_DRAW_CONFIG",
                    "这个奖池不属于任何抽奖配置：抽奖走不到它，单人限领也永远不会重置。"
                            + "请先给活动建抽奖配置，再把奖池归到它下面"));
        }
        if (periodic && limitedItemCount == 0 && !mappings.isEmpty()) {
            vo.getIssueList().add(PoolConfigIssueDTO.warn("RESET_PERIOD_USELESS",
                    "抽奖配置了「" + resetPeriodText(resetPeriod) + "」重置，但池内没有任何奖项设置单人限领次数："
                            + "重置周期重置的是限领计数，没有限领就没有计数要重置，这个配置当前不产生任何效果"));
        }
        if (!periodic && limitedItemCount > 0) {
            vo.getIssueList().add(PoolConfigIssueDTO.warn("LIMIT_NEVER_RESETS",
                    "池内有 " + limitedItemCount + " 个奖项设置了单人限领，但重置周期是「活动期间」："
                            + "用户在整个活动内中够次数后将永不恢复。限量大奖可以这么配，日常玩法通常应选按天/周/月"));
        }

        int danger = (int) vo.getIssueList().stream().filter(i -> "DANGER".equals(i.getLevel())).count();
        int warn = (int) vo.getIssueList().stream().filter(i -> "WARN".equals(i.getLevel())).count();
        vo.setDangerCount(danger);
        vo.setWarnCount(warn);
        return vo;
    }

    private String resetPeriodText(String resetPeriod) {
        if (resetPeriod == null) {
            return resetPeriod;
        }
        return switch (resetPeriod.trim().toUpperCase()) {
            case DrawPeriodResolver.PERIOD_DAY -> "每天";
            case DrawPeriodResolver.PERIOD_WEEK -> "每周";
            case DrawPeriodResolver.PERIOD_MONTH -> "每月";
            default -> resetPeriod;
        };
    }
}
