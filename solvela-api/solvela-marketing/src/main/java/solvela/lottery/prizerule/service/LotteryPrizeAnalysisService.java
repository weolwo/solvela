package solvela.lottery.prizerule.service;

import solvela.enums.LotteryConfigStatusEnum;
import solvela.enums.EnableStatusEnum;
import lombok.RequiredArgsConstructor;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.stat.HealthIssue;
import solvela.lottery.LotteryConfig;
import solvela.lottery.config.manager.LotteryConfigManager;
import solvela.lottery.constant.LotteryConst;
import solvela.lottery.engine.MatchRuleEnum;
import solvela.lottery.engine.PrizeRuleProbability;
import solvela.lottery.engine.PrizeRuleProbability.RuleMask;
import solvela.lottery.LotteryPrizeRule;
import solvela.lottery.prizerule.domain.query.LotteryPrizeRuleQuery;
import solvela.lottery.prizerule.domain.dto.LotteryPrizeAnalysisResultDTO;
import solvela.lottery.prizerule.domain.dto.LotteryPrizeAnalysisDTO;
import solvela.lottery.prizerule.domain.dto.LotteryPrizeRuleAnalysisDTO;
import solvela.lottery.prizerule.manager.LotteryPrizeRuleManager;
import solvela.prize.PrizeConfig;
import solvela.prize.prizeconfig.manager.PrizeConfigManager;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 奖励结构分析与配置体检。
 *
 * <h3>这个 Service 存在的理由</h3>
 * {@code t_lottery_prize_rule} 里只有「匹配规则 + 匹配长度 + 奖品编码」，
 * 光看这三个字段无法回答运营真正要问的问题：这套奖级<b>一期要赔多少钱</b>、
 * 有没有哪一级<b>永远中不了</b>、绑的奖品<b>还能不能发得出去</b>。
 * 这里把死数据翻译成这三个答案。
 *
 * <h3>为什么在 Java 里算而不是写进 SQL</h3>
 * 净中奖率要对「更高奖级」做容斥（见 {@link PrizeRuleProbability}），
 * 是个跨行、跨子集的组合计算，SQL 写出来既不可读也不可测。
 * 而数据量是配置级的（玩法数十、每个玩法奖级个位数），一次全量捞进内存毫无压力。
 *
 * <h3>体检口径与工作台校验的关系</h3>
 * 工作台 {@code LotteryConfigService.validateRules} 是<b>写入时</b>的守门员，
 * 这里是<b>存量数据</b>的体检 —— 两者覆盖的问题有重叠但不相同：
 * <ul>
 *   <li>历史脏数据是在校验加上之前写进去的，守门员管不着；</li>
 *   <li>奖品可能<b>事后</b>被停用或删除，写入时合法、现在不合法；</li>
 *   <li>「奖级被更高奖级完全覆盖」这一条<b>工作台目前查不出来</b>，
 *       而它恰恰是最隐蔽的一种 —— 配置看起来完全正常，开奖时那一级一张票都认领不到。</li>
 * </ul>
 *
 * @Author alaric
 * @Date 2026-08-15
 */
@RequiredArgsConstructor
@Service
public class LotteryPrizeAnalysisService {

    private final LotteryPrizeRuleManager lotteryPrizeRuleManager;
    private final LotteryConfigManager lotteryConfigManager;
    private final PrizeConfigManager prizeConfigManager;

    /**
     * 中奖率保留位数。9 位号码的 EXACT 命中率是 1e-9，再留几位余量，
     * 保证最小的那个概率不会被四舍五入成 0（显示成 0 会让人以为规则是死的）
     */
    private static final int RATE_SCALE = 12;

    /** 金额与注数保留 2 位，够用且不会给人「精确到小数点后八位」的错觉 */
    private static final int AMOUNT_SCALE = 2;

    /**
     * 奖励结构分析。
     *
     * <p>概览与明细在同一次计算里产出，两者不可能对不上。
     * 分页在内存里做：筛选后的玩法数是配置级的量，先算全量再切页最简单，
     * 也让「概览统计的是筛选后的全量」这件事天然成立。
     */
    public LotteryPrizeAnalysisResultDTO analysis(LotteryPrizeRuleQuery queryForm) {
        // ---- 1. 三次查询把料备齐，之后不再碰数据库 ----
        List<LotteryPrizeRule> ruleList = lotteryPrizeRuleManager.lambdaQuery()
                .eq(StringUtils.isNotBlank(queryForm.getLotteryCode()),
                        LotteryPrizeRule::getLotteryCode, queryForm.getLotteryCode())
                .list();

        Map<String, LotteryConfig> configMap = lotteryConfigManager.lambdaQuery().list().stream()
                .collect(Collectors.toMap(LotteryConfig::getLotteryCode, Function.identity(), (a, b) -> a));

        Set<String> prizeCodes = ruleList.stream()
                .map(LotteryPrizeRule::getPrizeCode).filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        Map<String, PrizeConfig> prizeMap = prizeCodes.isEmpty() ? Map.of()
                : prizeConfigManager.lambdaQuery().in(PrizeConfig::getPrizeCode, prizeCodes).list().stream()
                        .collect(Collectors.toMap(PrizeConfig::getPrizeCode, Function.identity(), (a, b) -> a));

        // ---- 2. 按玩法分组，逐玩法分析 ----
        Map<String, List<LotteryPrizeRule>> grouped = ruleList.stream()
                .collect(Collectors.groupingBy(LotteryPrizeRule::getLotteryCode, LinkedHashMap::new, Collectors.toList()));

        List<LotteryPrizeAnalysisDTO> all = new ArrayList<>();
        for (Map.Entry<String, List<LotteryPrizeRule>> entry : grouped.entrySet()) {
            all.add(analyseOne(entry.getKey(), entry.getValue(), configMap.get(entry.getKey()), prizeMap));
        }

        // ---- 3. 排序：有危险告警的置顶，其次已上线的，最后按预计赔付从高到低 ----
        // 排序即优先级：已上线 + 有 DANGER 的玩法是真在流血，必须第一眼看见
        all.sort(Comparator
                .comparing((LotteryPrizeAnalysisDTO v) -> v.getDangerCount() > 0 ? 0 : 1)
                .thenComparing(v -> v.getLotteryStatus() == LotteryConfigStatusEnum.ONLINE ? 0 : 1)
                .thenComparing(LotteryPrizeAnalysisDTO::getTotalExpectedCost, Comparator.reverseOrder()));

        if (Boolean.TRUE.equals(queryForm.getOnlyIssue())) {
            all = all.stream().filter(v -> v.getDangerCount() > 0 || v.getWarnCount() > 0).collect(Collectors.toList());
        }

        // ---- 4. 概览按筛选后的全量算，再切页 ----
        LotteryPrizeAnalysisResultDTO result = new LotteryPrizeAnalysisResultDTO();
        result.setLotteryCount(all.size());
        result.setRuleCount(all.stream().mapToInt(v -> v.getRuleList().size()).sum());
        result.setDangerCount(all.stream().mapToInt(LotteryPrizeAnalysisDTO::getDangerCount).sum());
        result.setWarnCount(all.stream().mapToInt(LotteryPrizeAnalysisDTO::getWarnCount).sum());
        result.setTotalExpectedCost(all.stream().map(LotteryPrizeAnalysisDTO::getTotalExpectedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        result.setTotal((long) all.size());
        result.setList(SolvelaPageUtil.subList(all, queryForm.getPageNum(), queryForm.getPageSize()));
        return result;
    }

    /**
     * 单个玩法的分析：身份 -> 逐奖级扫描 -> 汇总。
     */
    private LotteryPrizeAnalysisDTO analyseOne(String lotteryCode, List<LotteryPrizeRule> rules,
                                              LotteryConfig config, Map<String, PrizeConfig> prizeMap) {
        LotteryPrizeAnalysisDTO vo = new LotteryPrizeAnalysisDTO();
        vo.setLotteryCode(lotteryCode);
        vo.setIssueList(new ArrayList<>());

        if (config == null) {
            // 玩法配置不存在：号码长度未知，整套概率模型无从谈起，只能如实报「算不了」
            vo.getIssueList().add(HealthIssue.danger("LOTTERY_MISSING",
                    "玩法配置不存在（彩票编码 " + lotteryCode + "）。这批奖级规则挂在一个不存在的玩法上，"
                            + "既不会被开奖用到，也无法计算中奖率"));
        } else {
            vo.setLotteryName(config.getLotteryName());
            vo.setActivityCode(config.getActivityCode());
            vo.setNumberLength(config.getNumberLength());
            vo.setTotalCount(config.getTotalCount());
            vo.setLotteryStatus(config.getStatus());
        }

        RuleScan scan = scanRules(sortedByLevel(rules), config, prizeMap);
        scan.fill(vo);

        vo.setDangerCount(countIssues(vo, HealthIssue::isDanger));
        vo.setWarnCount(countIssues(vo, HealthIssue::isWarn));
        return vo;
    }

    /**
     * 奖级升序 —— 这个顺序<b>就是开奖的认领顺序</b>，净中奖率完全依赖它：
     * 一张票只中最高一级，轮到下一级时符合条件的票已经被取走了。
     */
    private List<LotteryPrizeRule> sortedByLevel(List<LotteryPrizeRule> rules) {
        return rules.stream()
                .sorted(Comparator.comparingInt(r -> r.getPrizeLevel() == null ? Integer.MAX_VALUE : r.getPrizeLevel()))
                .toList();
    }

    /**
     * 逐奖级扫完的结果：每一级的明细，以及三个整玩法级的合计。
     *
     * <p>三个合计都是<b>未取整</b>的累加值：逐级取整再相加与相加后再取整不是同一个数，
     * 而预计赔付是拿来估活动预算的。
     */
    private record RuleScan(List<LotteryPrizeRuleAnalysisDTO> rules,
                            BigDecimal totalNetRate,
                            BigDecimal totalWinCount,
                            BigDecimal totalCost) {

        void fill(LotteryPrizeAnalysisDTO vo) {
            vo.setRuleList(rules);
            vo.setTotalNetRate(scaleRate(totalNetRate));
            vo.setTotalExpectedWinCount(scaleAmount(totalWinCount));
            vo.setTotalExpectedCost(scaleAmount(totalCost));
        }
    }

    private RuleScan scanRules(List<LotteryPrizeRule> sorted, LotteryConfig config,
                               Map<String, PrizeConfig> prizeMap) {
        int numberLength = config == null || config.getNumberLength() == null ? 0 : config.getNumberLength();
        List<RuleMask> masks = sorted.stream()
                .map(r -> PrizeRuleProbability.toMask(MatchRuleEnum.resolve(r.getMatchRule()),
                        r.getMatchLength(), numberLength == 0 ? null : numberLength))
                .toList();
        // 同一奖品被多个奖级引用：中奖后无法判断该按哪一级的口径统计
        Map<String, Long> prizeCodeCount = sorted.stream()
                .filter(r -> StringUtils.isNotBlank(r.getPrizeCode()))
                .collect(Collectors.groupingBy(LotteryPrizeRule::getPrizeCode, Collectors.counting()));
        Set<Integer> seenLevels = new HashSet<>();

        List<LotteryPrizeRuleAnalysisDTO> ruleVOList = new ArrayList<>();
        BigDecimal totalNetRate = BigDecimal.ZERO;
        BigDecimal totalWinCount = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (int i = 0; i < sorted.size(); i++) {
            LotteryPrizeRule rule = sorted.get(i);
            MatchRuleEnum matchRule = MatchRuleEnum.resolve(rule.getMatchRule());
            PrizeConfig prize = prizeMap.get(rule.getPrizeCode());

            LotteryPrizeRuleAnalysisDTO rvo = new LotteryPrizeRuleAnalysisDTO();
            rvo.setId(rule.getId());
            rvo.setPrizeLevel(rule.getPrizeLevel());
            rvo.setMatchRule(rule.getMatchRule());
            rvo.setMatchLength(rule.getMatchLength());
            rvo.setPrizeCode(rule.getPrizeCode());
            rvo.setIssueList(new ArrayList<>());
            rvo.setMatchRuleDesc(matchRule == null ? rule.getMatchRule() : matchRule.getDesc());

            checkRule(rvo, rule, matchRule, config, prizeMap, prizeCodeCount, seenLevels);
            RuleTotals totals = fillProbability(rvo, masks, i, config, prize, numberLength);
            if (prize != null) {
                rvo.setPrizeName(prize.getPrizeName());
                rvo.setPrizeType(prize.getPrizeType());
                rvo.setPrizeValue(prize.getPrizeValue());
            }

            totalNetRate = totalNetRate.add(totals.netRate());
            totalWinCount = totalWinCount.add(totals.winCount());
            totalCost = totalCost.add(totals.cost());
            ruleVOList.add(rvo);
        }
        return new RuleScan(ruleVOList, totalNetRate, totalWinCount, totalCost);
    }

    /** 一条奖级贡献给整玩法合计的三个数，未取整；算不出来的一律是 0 */
    private record RuleTotals(BigDecimal netRate, BigDecimal winCount, BigDecimal cost) {

        static final RuleTotals NONE = new RuleTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * 中奖率、预计中出注数与预计赔付。
     *
     * <p>两个中奖率不是一回事：<b>命中率</b>是「这条规则自己能匹配多少号码」，
     * <b>净中奖率</b>是「按认领顺序轮到它时还剩多少」。运营看的是后者 ——
     * 前者会把被高奖级吃掉的部分也算进去。
     *
     * <p>玩法配置缺失或没配号码长度时整套模型算不了，如实留空，不要填 0
     * （0 会被读成「这一级中不了奖」，而实际是「不知道」）。
     */
    private RuleTotals fillProbability(LotteryPrizeRuleAnalysisDTO rvo, List<RuleMask> masks, int index,
                                       LotteryConfig config, PrizeConfig prize, int numberLength) {
        if (config == null || numberLength <= 0) {
            return RuleTotals.NONE;
        }
        RuleMask mask = masks.get(index);
        rvo.setHitRate(scaleRate(PrizeRuleProbability.hitRate(mask, numberLength)));

        BigDecimal net = PrizeRuleProbability.netRate(masks, index, numberLength);
        if (net == null) {
            return RuleTotals.NONE;
        }
        rvo.setNetRate(scaleRate(net));
        // 净中奖率为 0 却不是「永不命中」，说明它被更高奖级完全吃掉了 ——
        // 这是最隐蔽的一种坏配置：字段全都合法，开奖时那一级一张票都认领不到
        if (net.signum() == 0 && !mask.never()) {
            rvo.getIssueList().add(HealthIssue.danger("LEVEL_UNREACHABLE",
                    "该奖级永远认领不到票：它的匹配条件已被更高奖级完全覆盖。"
                            + "开奖按奖级升序逐级认领、一张票只中最高一级，轮到本级时符合条件的票已被取走"));
        }

        if (config.getTotalCount() == null) {
            return new RuleTotals(net, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal winCount = net.multiply(BigDecimal.valueOf(config.getTotalCount()));
        rvo.setExpectedWinCount(scaleAmount(winCount));
        if (prize == null || prize.getPrizeValue() == null) {
            return new RuleTotals(net, winCount, BigDecimal.ZERO);
        }
        BigDecimal cost = winCount.multiply(prize.getPrizeValue());
        rvo.setExpectedCost(scaleAmount(cost));
        return new RuleTotals(net, winCount, cost);
    }

    /**
     * 玩法告警数 = 玩法级 + 所有奖级级。两级合起来数，是因为页面按这个数排序与置顶，
     * 而「某一级的奖品被停用了」对运营的紧急程度与玩法级问题没有区别。
     */
    private int countIssues(LotteryPrizeAnalysisDTO vo, Predicate<HealthIssue> level) {
        int count = (int) vo.getIssueList().stream().filter(level).count();
        for (LotteryPrizeRuleAnalysisDTO rule : vo.getRuleList()) {
            count += (int) rule.getIssueList().stream().filter(level).count();
        }
        return count;
    }

    /**
     * 单条规则的体检。
     *
     * <p>每条告警都要说清「为什么这是问题」而不只是「哪里不对」——
     * 运营看到「匹配长度 6 > 号码长度 5」并不知道后果，看到「这条规则永远不可能命中」才知道。
     */
    private void checkRule(LotteryPrizeRuleAnalysisDTO rvo, LotteryPrizeRule rule, MatchRuleEnum matchRule,
                           LotteryConfig config, Map<String, PrizeConfig> prizeMap,
                           Map<String, Long> prizeCodeCount, Set<Integer> seenLevels) {
        checkLevel(rvo.getIssueList(), rule, seenLevels);
        checkMatchRule(rvo.getIssueList(), rule, matchRule, config);
        checkPrize(rvo.getIssueList(), rule, config, prizeMap, prizeCodeCount);
    }

    /** 奖级本身：99 是保留值，重复则开奖认领顺序不确定 */
    private void checkLevel(List<HealthIssue> issues, LotteryPrizeRule rule, Set<Integer> seenLevels) {
        if (rule.getPrizeLevel() == null) {
            return;
        }
        // 奖级 99 被「未中奖」占用：用户端按 prize_level 升序排「我的号码」，冲突后无法区分
        if (rule.getPrizeLevel() == LotteryConst.PRIZE_LEVEL_NONE) {
            issues.add(HealthIssue.danger("LEVEL_RESERVED",
                    "奖级 " + LotteryConst.PRIZE_LEVEL_NONE + " 被「未中奖」占用，用户端无法区分未中奖与本奖级"));
        }
        if (!seenLevels.add(rule.getPrizeLevel())) {
            issues.add(HealthIssue.danger("LEVEL_DUPLICATED",
                    "奖级 " + rule.getPrizeLevel() + " 配置了多条规则，开奖认领顺序不确定"));
        }
    }

    /**
     * 匹配规则值非法：开奖时 {@code LotterySettleService} 会「跳过并告警」，
     * 该奖级<b>静默不发奖</b> —— 不报错、不回滚，只是这一级的票谁也没中。
     */
    private void checkMatchRule(List<HealthIssue> issues, LotteryPrizeRule rule,
                                MatchRuleEnum matchRule, LotteryConfig config) {
        if (matchRule == null) {
            issues.add(HealthIssue.danger("MATCH_RULE_INVALID",
                    "匹配规则非法值「" + rule.getMatchRule() + "」，开奖时该奖级会被整条跳过，一张奖也发不出去"));
            return;
        }
        if (matchRule == MatchRuleEnum.EXACT) {
            // 全号匹配不看匹配长度
            return;
        }
        Integer len = rule.getMatchLength();
        if (len == null || len <= 0) {
            issues.add(HealthIssue.danger("MATCH_LENGTH_INVALID",
                    "匹配长度为 " + len + "，这条规则永远不可能命中"));
        } else if (config != null && config.getNumberLength() != null && len > config.getNumberLength()) {
            issues.add(HealthIssue.danger("MATCH_LENGTH_OVERFLOW",
                    "匹配长度 " + len + " 超过号码长度 " + config.getNumberLength() + "，这条规则永远不可能命中"));
        }
    }

    /**
     * 奖品链路：<b>中奖了却发不出去，是比不中奖严重得多的事故</b> ——
     * 用户已经看到自己中了，系统也认，只是东西给不了。
     */
    private void checkPrize(List<HealthIssue> issues, LotteryPrizeRule rule, LotteryConfig config,
                            Map<String, PrizeConfig> prizeMap, Map<String, Long> prizeCodeCount) {
        if (StringUtils.isBlank(rule.getPrizeCode())) {
            issues.add(HealthIssue.danger("PRIZE_MISSING", "未绑定奖品，中奖后无奖可发"));
            return;
        }
        PrizeConfig prize = prizeMap.get(rule.getPrizeCode());
        if (prize == null) {
            issues.add(HealthIssue.danger("PRIZE_MISSING",
                    "奖品「" + rule.getPrizeCode() + "」在奖品库中不存在，派奖时会报『奖品配置不存在』，用户中了奖拿不到东西"));
        } else {
            if (prize.getStatus() != EnableStatusEnum.ENABLED) {
                issues.add(HealthIssue.danger("PRIZE_DISABLED",
                        "奖品「" + prize.getPrizeName() + "」已停用，派奖链路会拒绝发放"));
            }
            if (config != null && prize.getActivityCode() != null
                    && !prize.getActivityCode().equals(config.getActivityCode())) {
                issues.add(HealthIssue.danger("PRIZE_ACTIVITY_MISMATCH",
                        "奖品属于活动 " + prize.getActivityCode() + "，而本玩法属于活动 " + config.getActivityCode()
                                + "，跨活动引用会在派奖时找不到奖品"));
            }
        }
        if (prizeCodeCount.getOrDefault(rule.getPrizeCode(), 0L) > 1) {
            issues.add(HealthIssue.warn("PRIZE_DUPLICATED",
                    "同一个奖品被多个奖级绑定，中奖后无法判断该按哪一级的口径统计"));
        }
    }

    private static BigDecimal scaleRate(BigDecimal value) {
        return value == null ? null : value.setScale(RATE_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private static BigDecimal scaleAmount(BigDecimal value) {
        return value == null ? null : value.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }
}
