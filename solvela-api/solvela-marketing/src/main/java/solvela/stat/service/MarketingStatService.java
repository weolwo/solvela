package solvela.stat.service;

import lombok.RequiredArgsConstructor;
import solvela.activity.service.ActivityConfigService;
import solvela.activity.spi.ActivityRefProvider;
import solvela.enums.ActivityTypeEnum;
import solvela.stat.dao.MarketingStatDao;
import solvela.stat.domain.vo.EventHealthVO;
import solvela.stat.domain.vo.GameplayVO;
import solvela.stat.domain.vo.OverviewVO;
import solvela.stat.domain.vo.ParticipationByTypeVO;
import solvela.stat.domain.vo.ParticipationTrendVO;
import solvela.stat.domain.vo.ParticipationVO;
import solvela.stat.domain.vo.PrizeHealthVO;
import solvela.stat.domain.vo.TaskFunnelVO;
import solvela.stat.domain.vo.TopMemberVO;
import solvela.task.constant.TaskDiscardCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 营销统计 Service
 *
 * @Author weolwo
 * @Date 2026-08-02
 */
@Service
@RequiredArgsConstructor
public class MarketingStatService {

    /** 有参与行为的玩法类型。BASIC 没有参与行为，不出现在图上 */
    private static final List<String> GAMEPLAY_TYPES = List.of("DRAW", "TASK", "LOTTERY");

    /** 活动禁用态。其余取值（含历史值 0）一律按启用处理 —— 只有开关两态，不看起止时间 */
    private static final int ACTIVITY_STATUS_DISABLED = 2;

    private static final int FLOW_TYPE_ADVANCE = 1;
    private static final int FLOW_TYPE_DISCARD = 2;

    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 90;
    private static final int TOP_MEMBER_LIMIT = 10;

    private final MarketingStatDao marketingStatDao;

    /**
     * 各玩法的引用查询实现，由 solvela-marketing 注册（依赖倒置，见 ActivityRefProvider 类注释）。
     * 用 ObjectProvider 而非直接注入 List：与 ActivityConfigService 保持同一种写法。
     */
    private final ObjectProvider<ActivityRefProvider> refProviders;

    /** 完备度判据复用活动域已有的那份，不在统计里另写一套（另写必然漂移） */
    private final ActivityConfigService activityConfigService;

    // ───────────────────────── 首页参与统计 ─────────────────────────

    public ParticipationVO participation(Integer days) {
        int span = normalizeDays(days);
        // 从 span 天前的 0 点开始，含今天
        LocalDate fromDate = LocalDate.now().minusDays(span - 1L);
        LocalDateTime fromTime = fromDate.atStartOfDay();

        ParticipationVO vo = new ParticipationVO();
        vo.setDays(span);
        vo.setDateList(buildDateList(fromDate, span));
        vo.setTrendList(fillTrend(marketingStatDao.participationTrend(fromTime), vo.getDateList()));
        vo.setByTypeList(fillByType(marketingStatDao.participationByType(fromTime)));
        return vo;
    }

    // ───────────────────────── 板块一：活动总览 ─────────────────────────

    /**
     * 活动状态分布 + 活动卡片 + 逾期未开奖告警。
     *
     * <p>🔴 活动只有<b>启用 / 禁用</b>两态：{@code status = 2} 为禁用，其余（含历史值 0）为启用。
     * 用 {@code status = 1} 过滤是本项目最容易踩的一脚 —— 开发库 6 条活动里 5 条是历史值 0，
     * 那样只会查出 1 条。
     */
    public OverviewVO overview() {
        List<OverviewVO.ActivityCard> cards = marketingStatDao.activityCardList();

        // 完备度一次批量查，避免逐个活动打一次 SPI
        Map<String, Boolean> configuredMap = cards.isEmpty()
                ? Collections.emptyMap()
                : activityConfigService.queryConfiguredStatus(
                        cards.stream().map(OverviewVO.ActivityCard::getActivityCode).toList());

        int enabled = 0;
        for (OverviewVO.ActivityCard card : cards) {
            boolean isEnabled = card.getStatus() == null || card.getStatus() != ACTIVITY_STATUS_DISABLED;
            card.setEnabled(isEnabled);
            if (isEnabled) {
                enabled++;
            }
            card.setGameplayCount(countGameplay(card.getActivityType(), card.getActivityCode()));
            // 查不到时按「完备」处理：宁可不显示告警，也不误报一个不存在的问题
            card.setConfigured(configuredMap.getOrDefault(card.getActivityCode(), Boolean.TRUE));
        }

        OverviewVO vo = new OverviewVO();
        vo.setActivityList(cards);
        vo.setEnabledCount(enabled);
        vo.setDisabledCount(cards.size() - enabled);
        vo.setOverdueIssueList(marketingStatDao.overdueIssueList());
        return vo;
    }

    /** BASIC 与未装载玩法模块时都返回 0，不抛异常 —— 大屏少一个数好过整块打不开 */
    private long countGameplay(String activityType, String activityCode) {
        ActivityTypeEnum type = ActivityTypeEnum.resolve(activityType);
        if (type == null || !ActivityTypeEnum.hasGameplay(activityType)) {
            return 0L;
        }
        return refProviders.stream()
                .filter(p -> type == p.supportType())
                .findFirst()
                .map(p -> p.gameplayCount(activityCode))
                .orElse(0L);
    }

    // ───────────────────────── 板块二：发奖健康度 ─────────────────────────

    /**
     * 发奖健康度。activityCode 为空即全局。
     *
     * <p>条数与价值是全量口径（不受 days 影响），只有趋势按 days 取窗口 ——
     * 运营问「一共发了多少」时不该被时间窗悄悄截断。
     */
    public PrizeHealthVO prizeHealth(String activityCode, Integer days) {
        int span = normalizeDays(days);
        LocalDate fromDate = LocalDate.now().minusDays(span - 1L);

        PrizeHealthVO vo = marketingStatDao.prizeSummary(activityCode);
        if (vo == null) {
            vo = new PrizeHealthVO();
        }
        vo.setActivityCode(activityCode);
        vo.setDays(span);
        vo.setByAssetList(marketingStatDao.prizeByAsset(activityCode));
        vo.setFailReasonList(marketingStatDao.prizeFailReasonTop(activityCode));

        List<String> dateList = buildDateList(fromDate, span);
        vo.setDateList(dateList);
        vo.setTrendList(fillPrizeTrend(
                marketingStatDao.prizeTrend(activityCode, fromDate.atStartOfDay()), dateList, vo.getByAssetList()));
        return vo;
    }

    /**
     * 补齐「日期 × 资产类型」缺失的组合。
     * <p>只补该口径下真实出现过的资产类型：全量补四种会让图上多出三条恒为 0 的线。
     */
    private List<PrizeHealthVO.TrendItem> fillPrizeTrend(List<PrizeHealthVO.TrendItem> rows,
                                                         List<String> dateList,
                                                         List<PrizeHealthVO.AssetItem> assets) {
        Map<String, BigDecimal> indexed = rows.stream()
                .collect(Collectors.toMap(r -> r.getStatDate() + "|" + r.getPrizeType(),
                        PrizeHealthVO.TrendItem::getIssuedValue, (a, b) -> a));
        List<String> types = assets.stream().map(PrizeHealthVO.AssetItem::getPrizeType).toList();
        List<PrizeHealthVO.TrendItem> result = new ArrayList<>(dateList.size() * types.size());
        for (String date : dateList) {
            for (String type : types) {
                PrizeHealthVO.TrendItem item = new PrizeHealthVO.TrendItem();
                item.setStatDate(date);
                item.setPrizeType(type);
                item.setIssuedValue(indexed.getOrDefault(date + "|" + type, BigDecimal.ZERO));
                result.add(item);
            }
        }
        return result;
    }

    // ───────────────────────── 板块三：玩法运行态 ─────────────────────────

    public GameplayVO gameplay(String activityCode) {
        GameplayVO vo = new GameplayVO();
        var activity = activityConfigService.getByActivityCode(activityCode);
        if (activity == null) {
            return vo;
        }
        vo.setActivityType(activity.getActivityType());
        ActivityTypeEnum type = ActivityTypeEnum.resolve(activity.getActivityType());
        if (type == null) {
            return vo;
        }
        switch (type) {
            case DRAW -> vo.setDrawStatList(marketingStatDao.drawStat(activityCode));
            case LOTTERY -> vo.setLotteryIssueList(marketingStatDao.lotteryIssueList(activityCode));
            case TASK -> vo.setTaskOptionList(marketingStatDao.taskOptionList(activityCode));
            // BASIC 按定义没有玩法运行态，三个列表都留空
            case BASIC -> { }
        }
        return vo;
    }

    public TaskFunnelVO taskFunnel(Long taskConfigId) {
        TaskFunnelVO vo = new TaskFunnelVO();
        vo.setTaskConfigId(taskConfigId);
        vo.setTaskName(marketingStatDao.taskName(taskConfigId));
        // 接取人数与各档到达人数<b>必须同为「人数」</b>：周期型任务每人每周期一条记录，
        // 一旦有一边用记录数，漏斗下层就会比上层宽（实测撞到过，见 Mapper 里的注释）
        vo.setJoinedCount(defaultZero(marketingStatDao.taskJoinedCount(taskConfigId)));
        vo.setStageList(marketingStatDao.taskFunnelStages(taskConfigId));
        return vo;
    }

    // ───────────────────────── 板块四：事件健康度 ─────────────────────────

    public EventHealthVO eventHealth(Integer days) {
        int span = normalizeDays(days);
        LocalDateTime fromTime = LocalDate.now().minusDays(span - 1L).atStartOfDay();

        EventHealthVO vo = new EventHealthVO();
        vo.setDays(span);
        vo.setAdvanceCount(0);
        vo.setDiscardCount(0);
        for (Map<String, Object> row : marketingStatDao.eventFlowTypeCount(fromTime)) {
            int flowType = ((Number) row.get("flowType")).intValue();
            int cnt = ((Number) row.get("cnt")).intValue();
            if (flowType == FLOW_TYPE_ADVANCE) {
                vo.setAdvanceCount(cnt);
            } else if (flowType == FLOW_TYPE_DISCARD) {
                vo.setDiscardCount(cnt);
            }
        }

        List<EventHealthVO.DiscardItem> items = marketingStatDao.discardStat(fromTime);
        int attention = 0;
        for (EventHealthVO.DiscardItem item : items) {
            TaskDiscardCode code = TaskDiscardCode.resolve(item.getDiscardCode());
            if (code == null) {
                // 库里出现了枚举里没有的码：说明有人绕过枚举写了值，或枚举被删过。
                // 当成「需要人介入」而不是静默忽略 —— 归不了类本身就是个问题信号。
                item.setDiscardDesc(item.getDiscardCode() + "（未识别的分类码）");
                item.setNeedsAttention(true);
            } else {
                item.setDiscardDesc(code.getDesc());
                item.setNeedsAttention(code.needsAttention());
            }
            if (Boolean.TRUE.equals(item.getNeedsAttention())) {
                attention += item.getCount() == null ? 0 : item.getCount();
            }
        }
        vo.setDiscardList(items);
        vo.setAttentionCount(attention);
        return vo;
    }

    // ───────────────────────── 板块五：用户维度 ─────────────────────────

    public List<TopMemberVO> topMembers(Integer days) {
        int span = normalizeDays(days);
        LocalDateTime fromTime = LocalDate.now().minusDays(span - 1L).atStartOfDay();
        return marketingStatDao.topMembers(fromTime, TOP_MEMBER_LIMIT);
    }

    // ───────────────────────── 公共 ─────────────────────────

    private int normalizeDays(Integer days) {
        if (days == null || days <= 0) {
            return DEFAULT_DAYS;
        }
        return Math.min(days, MAX_DAYS);
    }

    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    private List<String> buildDateList(LocalDate from, int span) {
        List<String> list = new ArrayList<>(span);
        for (int i = 0; i < span; i++) {
            list.add(from.plusDays(i).toString());
        }
        return list;
    }

    /**
     * 补齐缺失的「日期 × 类型」组合。
     * <p>没有参与的那天 SQL 不会返回行，前端拿到稀疏数据画出来的折线会断，必须补 0。
     */
    private List<ParticipationTrendVO> fillTrend(List<ParticipationTrendVO> rows, List<String> dateList) {
        Map<String, Integer> indexed = rows.stream()
                .collect(Collectors.toMap(r -> r.getStatDate() + "|" + r.getActivityType(),
                        ParticipationTrendVO::getMemberCount, (a, b) -> a));
        List<ParticipationTrendVO> result = new ArrayList<>(dateList.size() * GAMEPLAY_TYPES.size());
        for (String date : dateList) {
            for (String type : GAMEPLAY_TYPES) {
                ParticipationTrendVO item = new ParticipationTrendVO();
                item.setStatDate(date);
                item.setActivityType(type);
                item.setMemberCount(indexed.getOrDefault(date + "|" + type, 0));
                result.add(item);
            }
        }
        return result;
    }

    /** 三种玩法都要出现，没有数据的补 0，否则柱子会时有时无 */
    private List<ParticipationByTypeVO> fillByType(List<ParticipationByTypeVO> rows) {
        Map<String, Integer> indexed = rows.stream()
                .collect(Collectors.toMap(ParticipationByTypeVO::getActivityType,
                        ParticipationByTypeVO::getMemberCount, (a, b) -> a));
        return GAMEPLAY_TYPES.stream().map(type -> {
            ParticipationByTypeVO item = new ParticipationByTypeVO();
            item.setActivityType(type);
            item.setMemberCount(indexed.getOrDefault(type, 0));
            return item;
        }).collect(Collectors.toList());
    }
}
