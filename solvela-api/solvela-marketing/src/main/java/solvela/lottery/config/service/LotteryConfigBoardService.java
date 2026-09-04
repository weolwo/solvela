package solvela.lottery.config.service;

import solvela.enums.LotteryConfigStatusEnum;
import solvela.enums.IssueStatusEnum;
import lombok.RequiredArgsConstructor;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.stat.HealthIssue;
import solvela.base.stat.Rate;
import solvela.base.stat.StatRow;
import solvela.activity.ActivityConfig;
import solvela.activity.manager.ActivityConfigManager;
import solvela.lottery.LotteryConfig;
import solvela.lottery.config.domain.query.LotteryConfigQuery;
import solvela.lottery.config.domain.dto.LotteryConfigBoardResultDTO;
import solvela.lottery.config.domain.dto.LotteryConfigBoardDTO;
import solvela.lottery.config.manager.LotteryConfigManager;
import solvela.lottery.issue.dao.LotteryIssueDao;
import solvela.lottery.LotteryIssue;
import solvela.lottery.issue.manager.LotteryIssueManager;
import solvela.lottery.LotteryPrizeRule;
import solvela.lottery.prizerule.manager.LotteryPrizeRuleManager;
import solvela.lottery.record.dao.LotteryRecordDao;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 彩票玩法一览与体检。
 *
 * <h3>本页独有的那条：号码空间占用率</h3>
 * {@code number_length} 决定号码空间是 10^n，{@code total_count} 是单期发行上限。
 * 两者的比值才是「这个玩法还有多少扩容余地」，而它<b>没有任何地方显示过</b> ——
 * 列表原先只把两个裸数字并排一放，谁也不会去心算 10 的 n 次方。
 *
 * <p>为什么它要紧：一旦发出过号码，号码长度与发行上限就<b>永久冻结</b>
 * （{@code LotteryConfigService.resolveLockReason}：号码由游标加密得来，
 * 改参数会让已发号码无法验证、且新号码可能与历史重复）。
 * 所以占用率顶到 100% 且已发过号，等于这个玩法的发行量再也加不上去 ——
 * 想扩容只能新建玩法。这件事必须在发第一个号之前就让运营看见。
 *
 * <h3>「能不能领号」的判据与运行态对齐</h3>
 * {@code TicketIssueService} 放行需要三条同时成立：玩法已上线、期号是待开奖、
 * 当前时间落在售卖窗口内。这里逐条照搬 —— 两边一旦漂移，页面显示「可领号」
 * 而用户实际领不到，排查会非常费劲。时间同样取<b>数据库时钟</b>（铁律 9/10）。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@RequiredArgsConstructor
@Service
public class LotteryConfigBoardService {

    private final LotteryConfigManager lotteryConfigManager;
    private final LotteryIssueManager lotteryIssueManager;
    private final LotteryIssueDao lotteryIssueDao;
    private final LotteryPrizeRuleManager lotteryPrizeRuleManager;
    private final LotteryRecordDao lotteryRecordDao;
    private final ActivityConfigManager activityConfigManager;
    /** 占用率到这个比例就提示扩容余地不足，早于发号才有意义 */
    private static final BigDecimal SPACE_WARN_THRESHOLD = new BigDecimal("0.9");

    public LotteryConfigBoardResultDTO board(LotteryConfigQuery queryForm) {
        List<LotteryConfig> configs = lotteryConfigManager.lambdaQuery()
                .eq(StringUtils.isNotBlank(queryForm.getActivityCode()),
                        LotteryConfig::getActivityCode, queryForm.getActivityCode())
                .eq(StringUtils.isNotBlank(queryForm.getLotteryCode()),
                        LotteryConfig::getLotteryCode, queryForm.getLotteryCode())
                .like(StringUtils.isNotBlank(queryForm.getLotteryName()),
                        LotteryConfig::getLotteryName, queryForm.getLotteryName())
                .eq(queryForm.getStatus() != null, LotteryConfig::getStatus, queryForm.getStatus())
                .list();

        Map<String, ActivityConfig> activityMap = activityConfigManager.lambdaQuery().list().stream()
                .collect(Collectors.toMap(ActivityConfig::getActivityCode, a -> a, (a, b) -> a));
        Map<String, List<LotteryIssue>> issueMap = lotteryIssueManager.lambdaQuery().list().stream()
                .collect(Collectors.groupingBy(LotteryIssue::getLotteryCode));
        Map<String, Long> ruleCountMap = lotteryPrizeRuleManager.lambdaQuery().list().stream()
                .collect(Collectors.groupingBy(LotteryPrizeRule::getLotteryCode, Collectors.counting()));
        Map<String, Map<String, Object>> recordStatMap = lotteryRecordDao.selectStatByLottery().stream()
                .collect(Collectors.toMap(m -> String.valueOf(m.get("lotteryCode")), m -> m, (a, b) -> a));

        // 售卖窗口的判定取数据库时钟，与运行态领号用的是同一个（铁律 9/10）
        LocalDateTime dbNow = lotteryIssueDao.selectDbNow();

        List<LotteryConfigBoardDTO> all = new ArrayList<>();
        for (LotteryConfig config : configs) {
            all.add(analyseOne(config, activityMap,
                    issueMap.getOrDefault(config.getLotteryCode(), List.of()),
                    ruleCountMap.getOrDefault(config.getLotteryCode(), 0L),
                    StatRow.of(recordStatMap.get(config.getLotteryCode())), dbNow));
        }

        if (Boolean.TRUE.equals(queryForm.getOnlyIssue())) {
            all = all.stream().filter(v -> v.getDangerCount() > 0 || v.getWarnCount() > 0).collect(Collectors.toList());
        }

        // 排序即优先级：已上线且有危险告警的排最前 —— 那是正在流血的
        all.sort(Comparator
                .comparing((LotteryConfigBoardDTO v) -> v.getDangerCount() > 0 ? 0 : 1)
                .thenComparing(v -> v.getStatus() == LotteryConfigStatusEnum.ONLINE ? 0 : 1)
                .thenComparing(LotteryConfigBoardDTO::getSoldTotal, Comparator.reverseOrder()));

        LotteryConfigBoardResultDTO result = new LotteryConfigBoardResultDTO();
        result.setLotteryCount(all.size());
        result.setSellableCount((int) all.stream()
                .filter(v -> v.getStatus() == LotteryConfigStatusEnum.ONLINE && v.getDangerCount() == 0
                        && v.getWaitIssueCount() > 0).count());
        result.setDangerCount(all.stream().mapToInt(LotteryConfigBoardDTO::getDangerCount).sum());
        result.setWarnCount(all.stream().mapToInt(LotteryConfigBoardDTO::getWarnCount).sum());
        result.setTotalSold(all.stream().mapToLong(LotteryConfigBoardDTO::getSoldTotal).sum());
        result.setTotal((long) all.size());
        result.setList(SolvelaPageUtil.subList(all, queryForm.getPageNum(), queryForm.getPageSize()));
        return result;
    }

    /**
     * 单个彩票玩法的体检：身份 -> 号码空间 -> 期号与发号 -> 三类校验。
     */
    private LotteryConfigBoardDTO analyseOne(LotteryConfig config, Map<String, ActivityConfig> activityMap,
                                            List<LotteryIssue> issues, long ruleCount,
                                            StatRow recordStat, LocalDateTime dbNow) {
        LotteryConfigBoardDTO vo = new LotteryConfigBoardDTO();
        vo.setId(config.getId());
        vo.setActivityCode(config.getActivityCode());
        vo.setLotteryCode(config.getLotteryCode());
        vo.setLotteryName(config.getLotteryName());
        vo.setNumberLength(config.getNumberLength());
        vo.setTotalCount(config.getTotalCount());
        vo.setStatus(config.getStatus());
        vo.setIssueList(new ArrayList<>());

        ActivityConfig activity = activityMap.get(config.getActivityCode());
        if (activity != null) {
            vo.setActivityName(activity.getActivityName());
        }

        long space = fillNumberSpace(config, vo);
        IssueTally tally = tally(issues, dbNow);
        tally.fill(vo);
        vo.setRuleCount((int) ruleCount);
        vo.setMemberCount(recordStat.count("memberCount"));
        vo.setWinCount(recordStat.count("winCount"));

        boolean online = config.getStatus() == LotteryConfigStatusEnum.ONLINE;
        checkPrizeRule(vo, ruleCount, online);
        checkSellable(vo, issues, tally, online);
        checkNumberSpace(vo, config, space);

        vo.setDangerCount(HealthIssue.countDanger(vo.getIssueList()));
        vo.setWarnCount(HealthIssue.countWarn(vo.getIssueList()));
        return vo;
    }

    /**
     * 号码空间 = 10^号码长度，以及发行上限对它的占用率。
     *
     * @return 号码空间；号码长度没配时为 0
     */
    private long fillNumberSpace(LotteryConfig config, LotteryConfigBoardDTO vo) {
        if (config.getNumberLength() == null || config.getNumberLength() <= 0) {
            return 0L;
        }
        long space = (long) Math.pow(10, config.getNumberLength());
        vo.setNumberSpace(space);
        if (config.getTotalCount() != null) {
            vo.setSpaceUsage(Rate.share(config.getTotalCount(), space));
        }
        return space;
    }

    /**
     * 期号盘点。
     *
     * @param sellableNow 现在真能领到号的期数：待开奖 <b>且</b> 当前时间落在售卖窗口内。
     *                    与运行态领号的判定口径一致，否则这个页面会说「能领」而用户领不到
     */
    private record IssueTally(int issueCount, int waitCount, int openedCount, int sellableNow, long sold) {

        void fill(LotteryConfigBoardDTO vo) {
            vo.setIssueCount(issueCount);
            vo.setWaitIssueCount(waitCount);
            vo.setOpenedIssueCount(openedCount);
            vo.setSoldTotal(sold);
            // 发出过号码之后，号码长度与发行上限永久冻结，见 checkNumberSpace
            vo.setParamsFrozen(sold > 0);
        }
    }

    private IssueTally tally(List<LotteryIssue> issues, LocalDateTime dbNow) {
        int waitCount = 0;
        int openedCount = 0;
        int sellableNow = 0;
        long sold = 0L;
        for (LotteryIssue issue : issues) {
            sold += issue.getSoldCount() == null ? 0 : issue.getSoldCount();
            if (issue.getStatus() == IssueStatusEnum.WAIT) {
                waitCount++;
                if (onSale(issue, dbNow)) {
                    sellableNow++;
                }
            } else if (issue.getStatus() == IssueStatusEnum.OPENED) {
                openedCount++;
            }
        }
        return new IssueTally(issues.size(), waitCount, openedCount, sellableNow, sold);
    }

    /** 售卖窗口两端都可以不配，不配就是那一端不设限 */
    private boolean onSale(LotteryIssue issue, LocalDateTime dbNow) {
        boolean started = issue.getSaleStartTime() == null || !dbNow.isBefore(issue.getSaleStartTime());
        boolean notEnded = issue.getSaleEndTime() == null || !dbNow.isAfter(issue.getSaleEndTime());
        return started && notEnded;
    }

    /**
     * 上线的唯一前置条件就是「配了奖级规则」（见 {@code LotteryConfigService.checkOnlineReady}）——
     * 否则号码发出去了、开奖时却无奖可发，而那时号码已经在用户手里。
     */
    private void checkPrizeRule(LotteryConfigBoardDTO vo, long ruleCount, boolean online) {
        if (ruleCount > 0) {
            return;
        }
        vo.getIssueList().add(HealthIssue.danger("NO_PRIZE_RULE",
                online ? "已上线却没有配置任何奖级规则：号码发出去了，开奖时无奖可发"
                        : "尚未配置奖级规则，无法上线：号码发出去了却无奖可发，补救代价远高于此刻配好"));
    }

    /**
     * 上线了但用户领不到号的两种形态。分开报是因为处理动作不同：
     * 一个要去建期号，一个要去调售卖时间或开新期。
     */
    private void checkSellable(LotteryConfigBoardDTO vo, List<LotteryIssue> issues, IssueTally tally, boolean online) {
        if (!online) {
            return;
        }
        if (issues.isEmpty()) {
            vo.getIssueList().add(HealthIssue.danger("NO_ISSUE",
                    "已上线但一个期号都没有：用户点领号会被拒（期号不存在），玩法等于开着门却没有货"));
        } else if (tally.sellableNow() == 0) {
            vo.getIssueList().add(HealthIssue.warn("NO_SELLABLE_ISSUE",
                    "已上线但当前没有任何期号处于可领号状态：所有期号要么还没开售、要么已停售、要么已开奖。"
                            + "用户现在领不到号"));
        }
    }

    /**
     * 号码空间占用率 —— 本页独有、也是最该提前看见的一条。
     *
     * <p>发过号之后号码长度与发行上限<b>永久冻结</b>（号码由游标加密得来，改了会让已发号码
     * 无法验证、新号码还可能与历史重复）。所以占用率顶满 + 已冻结 = 这个玩法的发行量
     * 再也加不上去，想扩容只能新建玩法 —— 事后才发现就来不及了。
     */
    private void checkNumberSpace(LotteryConfigBoardDTO vo, LotteryConfig config, long space) {
        if (vo.getSpaceUsage() == null || space <= 0) {
            return;
        }
        if (config.getTotalCount() != null && config.getTotalCount() > space) {
            vo.getIssueList().add(HealthIssue.danger("SPACE_OVERFLOW",
                    "单期发行上限 " + config.getTotalCount() + " 超过 " + config.getNumberLength()
                            + " 位号码的空间 " + space + "：超出部分永远发不出来"));
            return;
        }
        if (vo.getSpaceUsage().compareTo(SPACE_WARN_THRESHOLD) < 0) {
            return;
        }
        String pct = vo.getSpaceUsage().multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        vo.getIssueList().add(HealthIssue.warn("SPACE_ALMOST_FULL",
                "号码空间已占用 " + pct + "%（发行上限 " + config.getTotalCount() + " / 空间 " + space + "）。"
                        + (Boolean.TRUE.equals(vo.getParamsFrozen())
                        ? "本玩法已发出号码，号码长度与发行上限已永久冻结 —— 发行量无法再提高，扩容只能新建玩法"
                        : "若还要提高发行量，请在发出第一个号码之前增加号码长度；一旦发号，这两个参数永久冻结")));
    }
}
