package solvela.lottery.clientapi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import solvela.marketing.api.LotteryBoardView;
import solvela.prize.prizeconfig.service.PrizeConfigService;
import solvela.prize.PrizeConfig;
import solvela.marketing.api.LotteryPrizeRuleView;
import solvela.marketing.api.LotteryIssueResultView;
import solvela.lottery.prizerule.manager.LotteryPrizeRuleManager;
import solvela.lottery.issue.manager.LotteryIssueManager;
import solvela.lottery.engine.MatchRuleEnum;
import solvela.lottery.LotteryPrizeRule;
import solvela.enums.LotteryConfigStatusEnum;
import solvela.lottery.LotteryConfig;
import solvela.lottery.LotteryIssue;
import solvela.lottery.config.service.LotteryConfigService;
import solvela.lottery.record.dao.LotteryRecordDao;
import solvela.lottery.record.domain.dto.MemberTicketDTO;
import solvela.lottery.runtime.LotteryIssueLocator;
import solvela.marketing.api.LotteryIssueView;
import solvela.marketing.api.LotteryTicketView;

import java.util.List;
import java.util.Map;

/**
 * 彩票的<b>会员侧</b>：本期什么时候截止、我手里有哪些号、中了没有。
 *
 * <h3>🔴 这一层此前完全不存在</h3>
 * FPE 算号引擎、期号、号码池、中奖规则、管理端配置页、开奖与核销全都建成了，
 * 而 C 端<b>一个控制器都没有</b> —— 会员既拿不到号（{@code PrizeTypeEnum.LOTTERY}
 * 的派发策略是空的），也看不到自己有什么。整条玩法只有运营那一半。
 *
 * <h3>和 {@code TicketQueryService} 是两个类，刻意的</h3>
 * 那个是<b>引擎</b>的查询能力：按玩法查号、验真（反解 FPE 游标、验签）。
 * 本类是<b>页面</b>要的形状：跨玩法跨期、带玩法名和开奖结果、状态翻成人话。
 * 合成一个的话，验真那种带密码学细节的方法会和 C 端展示混在一起，
 * 而前者的返回里有 {@code security_sign} 这类<b>不该下发</b>的东西。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LotteryClientService {

    /** 一次最多给多少张。一个人手里几十张已经算多，给多了只是白传 */
    private static final int MAX_LIMIT = 100;

    /** 玩法被删之后，票还在用户手里 —— 给个能读的兜底而不是空白 */
    private static final String UNKNOWN_LOTTERY = "彩票";

    /**
     * 「未中奖 / 未开奖」的哨兵值。
     *
     * <p>🔴 库里 {@code prize_level} 的 DDL 注释写着「99-未中奖/未开奖」——
     * 它<b>不能</b>原样下发：端上拿到 99 会显示成「99 等奖」。
     */
    private static final int NO_PRIZE_LEVEL = 99;

    /** 活动页上往期开奖列几期。多了就该是一个独立的「历史开奖」页，而不是首屏堆一屏 */
    private static final int RECENT_ISSUE_LIMIT = 5;

    private final LotteryConfigService lotteryConfigService;
    private final LotteryIssueLocator lotteryIssueLocator;
    private final LotteryRecordDao lotteryRecordDao;
    private final LotteryPrizeRuleManager lotteryPrizeRuleManager;
    private final PrizeConfigService prizeConfigService;
    private final LotteryIssueManager lotteryIssueManager;

    /**
     * 一个玩法的当前一期。
     *
     * <p>玩法不存在或未上线 → 返回 {@code null}（对 C 端是同一件事：这儿没有）。
     * 玩法在但没有在售期 → 返回一个 {@code issueNo} 为 null 的 view，
     * 端上说「下一期敬请期待」。<b>这两种要分开</b>：前者是页面配错了，
     * 后者是正常的运营空窗。
     */
    public LotteryIssueView getCurrentIssue(String lotteryCode, Long memberId) {
        LotteryConfig config = lotteryConfigService.getByLotteryCode(lotteryCode);
        if (config == null || config.getStatus() != LotteryConfigStatusEnum.ONLINE) {
            return null;
        }
        LotteryIssue issue = lotteryIssueLocator.currentSellable(lotteryCode);
        if (issue == null) {
            return new LotteryIssueView(lotteryCode, config.getLotteryName(), null, null, null, 0);
        }
        return new LotteryIssueView(
                lotteryCode,
                config.getLotteryName(),
                issue.getIssueNo(),
                issue.getSaleEndTime(),
                issue.getPlanDrawTime(),
                countMine(lotteryCode, issue.getIssueNo(), memberId));
    }

    /** 我在这一期有几张。未登录（memberId 为 null）就是 0 —— 期号信息是公开的 */
    private int countMine(String lotteryCode, String issueNo, Long memberId) {
        if (memberId == null) {
            return 0;
        }
        Integer count = lotteryRecordDao.countMyTickets(lotteryCode, issueNo, memberId);
        return count == null ? 0 : count;
    }

    /**
     * 我的彩票号码，跨玩法跨期一起给。
     *
     * <p>排序由 SQL 保证：<b>中奖的在最前</b>，然后才是最新的 ——
     * 用户点进来最想知道的是「我中了没有」。
     */
    public List<LotteryTicketView> getMyTickets(Long memberId, int limit) {
        if (memberId == null) {
            return List.of();
        }
        int size = limit <= 0 ? MAX_LIMIT : Math.min(limit, MAX_LIMIT);
        return lotteryRecordDao.selectMyRecentTickets(memberId, size).stream()
                .map(LotteryClientService::toView)
                .toList();
    }

    /**
     * 这个活动挂的彩票玩法。<b>取第一个上线的</b>。
     *
     * <p>⚠️ 一个活动可以挂多个彩票玩法（{@code queryByActivityCode} 返回的是 List），
     * 但 C 端那一页只画一个 —— 多玩法的活动页长什么样是个产品问题，
     * 今天没有这种配置，所以取第一个而不是编一套 UI 出来。
     * 真出现了，这里会是要改的那一处。
     */
    public LotteryConfig resolveByActivity(String activityCode) {
        return lotteryConfigService.queryByActivityCode(activityCode).stream()
                .filter(c -> c.getStatus() == LotteryConfigStatusEnum.ONLINE)
                .findFirst()
                .orElse(null);
    }

    /**
     * 中奖规则，按奖级从大到小。
     *
     * <h3>规则文案由服务端拼</h3>
     * 端上做 {@code EXACT/TAIL/HEAD} 的映射表就是<b>第二份规则口径</b> ——
     * 而「怎么算中奖」这件事必须只有一个说法，否则页面上写的和开奖时用的
     * 会在某次只改了一边的时候开始不一致，而那时已经发出去一堆奖了。
     */
    public List<LotteryPrizeRuleView> getPrizeRules(String lotteryCode, String activityCode) {
        List<LotteryPrizeRule> rules = lotteryPrizeRuleManager.lambdaQuery()
                .eq(LotteryPrizeRule::getLotteryCode, lotteryCode)
                .orderByAsc(LotteryPrizeRule::getPrizeLevel)
                .list();
        if (rules.isEmpty()) {
            return List.of();
        }
        /*
         * 奖品名批量查一次，不在循环里逐个查 —— 一个玩法有 5 个奖级就是 5 次往返。
         * 用带 activityCode 的那个方法：prize_code 只在活动内唯一，
         * 不带活动跨活动复用同一编码时会命中多行并抛 TooManyResultsException。
         */
        Map<String, PrizeConfig> prizes = prizeConfigService.mapByActivityCodeAndPrizeCodes(
                activityCode, rules.stream().map(LotteryPrizeRule::getPrizeCode).toList());
        return rules.stream()
                .map(rule -> new LotteryPrizeRuleView(
                        rule.getPrizeLevel(),
                        ruleText(rule),
                        // 查不到就给 null，端上不画那一行 —— 比显示一个编码好
                        prizes.containsKey(rule.getPrizeCode())
                                ? prizes.get(rule.getPrizeCode()).getPrizeName() : null))
                .toList();
    }

    /**
     * 「怎么算中奖」的人话版。
     *
     * <p>⚠️ 不认识的 {@code match_rule} 兜底成一句模糊的话而不是抛 ——
     * 一条脏规则不该让整个活动页打不开。它同时会在日志里留痕。
     */
    private static String ruleText(LotteryPrizeRule rule) {
        MatchRuleEnum match = MatchRuleEnum.resolve(rule.getMatchRule());
        Integer length = rule.getMatchLength();
        if (match == null) {
            log.warn("【彩票规则】未知的匹配规则 {}，{} 第 {} 等奖", rule.getMatchRule(),
                    rule.getLotteryCode(), rule.getPrizeLevel());
            return "以开奖结果为准";
        }
        return switch (match) {
            case EXACT -> "号码与开奖号完全一致";
            case TAIL -> "后 " + length + " 位与开奖号一致";
            case HEAD -> "前 " + length + " 位与开奖号一致";
        };
    }

    /**
     * 往期开奖结果，新的在前。<b>只给已开出号码的期</b> ——
     * 待开奖的那一期在「本期」那一块已经说过了，重复列一遍只会让人以为开了两次。
     */
    public List<LotteryIssueResultView> getRecentResults(String lotteryCode, int limit) {
        return lotteryIssueManager.lambdaQuery()
                .eq(LotteryIssue::getLotteryCode, lotteryCode)
                .isNotNull(LotteryIssue::getWinningNumber)
                .orderByDesc(LotteryIssue::getId)
                .last("limit " + Math.min(Math.max(limit, 1), 20))
                .list().stream()
                .map(i -> new LotteryIssueResultView(
                        i.getIssueNo(), i.getWinningNumber(), i.getSettleTime()))
                .toList();
    }

    /**
     * 活动页的全部数据，一次组装完。
     *
     * <p>活动没挂上线的彩票玩法时返回 {@code null} —— 对端上就是「这一页没东西可画」。
     */
    public LotteryBoardView getBoard(String activityCode, Long memberId) {
        LotteryConfig config = resolveByActivity(activityCode);
        if (config == null) {
            return null;
        }
        String lotteryCode = config.getLotteryCode();
        LotteryIssueView issue = getCurrentIssue(lotteryCode, memberId);
        return new LotteryBoardView(
                lotteryCode,
                config.getLotteryName(),
                config.getNumberLength(),
                issue,
                getPrizeRules(lotteryCode, activityCode),
                getRecentResults(lotteryCode, RECENT_ISSUE_LIMIT),
                myTicketsOfIssue(lotteryCode, issue, memberId));
    }

    /**
     * 我在<b>当前这一期</b>的号码。
     *
     * <p>🔴 只给当前期，不给全部：活动页回答的是「我这期有什么」。
     * 把历史号码也堆上来，页面会越用越长，而「我全部的号码」在「我的彩票」那一页。
     */
    private List<LotteryTicketView> myTicketsOfIssue(String lotteryCode, LotteryIssueView issue,
                                                     Long memberId) {
        if (memberId == null || issue == null || issue.issueNo() == null) {
            return List.of();
        }
        return lotteryRecordDao.selectMyTickets(lotteryCode, issue.issueNo(), memberId).stream()
                .map(r -> {
                    /*
                     * ⚠️ 实体上的 win_status 是【枚举】(TicketStatusEnum)，而 join 查询那条路
                     *    拿到的是 Integer —— 两条路的类型不一样，所以这里先取回数值再翻。
                     *    直接把枚举传进 statusTextOf 会编译不过，那是好事：
                     *    它逼着人看一眼「这两条路为什么类型不同」。
                     */
                    Integer winStatus = r.getWinStatus() == null ? null : r.getWinStatus().getValue();
                    Integer level = r.getPrizeLevel();
                    return new LotteryTicketView(
                            lotteryCode, issue.lotteryName(), r.getIssueNo(), r.getTicketNumber(),
                            r.getObtainTime(), winStatus,
                            // 本期多半还没开奖，但仍按库里的值翻而不是写死「待开奖」：
                            // 万一用户正看着页面时这一期开了，说法要跟着变
                            statusTextOf(winStatus, level, lotteryCode, r.getIssueNo()),
                            level == null || level >= NO_PRIZE_LEVEL ? null : level,
                            null,
                            issue.planDrawTime());
                })
                .toList();
    }

    private static LotteryTicketView toView(MemberTicketDTO dto) {
        return new LotteryTicketView(
                dto.getLotteryCode(),
                StringUtils.defaultIfBlank(dto.getLotteryName(), UNKNOWN_LOTTERY),
                dto.getIssueNo(),
                dto.getTicketNumber(),
                dto.getObtainTime(),
                dto.getWinStatus(),
                statusText(dto),
                // 🔴 99 是哨兵值，不是奖级。漏到端上会显示成「99 等奖」
                dto.getPrizeLevel() == null || dto.getPrizeLevel() >= NO_PRIZE_LEVEL
                        ? null : dto.getPrizeLevel(),
                dto.getWinningNumber(),
                dto.getPlanDrawTime());
    }

    /**
     * 给用户看的一句话。
     *
     * <p>⚠️ 这里<b>不能</b>用不带 default 的 switch —— {@code win_status} 是库里的
     * {@code tinyint}，不是枚举，脏值（或者将来加的新取值）会一路传到这里。
     * 兜底成「处理中」比抛异常好：一条状态没见过的票不该让整页打不开。
     *
     * <p>相比之下 {@code MemberDeliveryService.statusText} 收的是<b>枚举</b>，
     * 那里就该用不带 default 的 switch 让新状态在编译期暴露。两边形状不同，
     * 是因为上游的类型不同，不是随手写的。
     */
    private static String statusText(MemberTicketDTO dto) {
        return statusTextOf(dto.getWinStatus(), dto.getPrizeLevel(),
                dto.getLotteryCode(), dto.getIssueNo());
    }

    /**
     * 两条下发路径（「我的彩票」与活动页）<b>共用这一份</b>。
     * 各写一遍的话，同一张票在两个页面上会有两种说法。
     */
    private static String statusTextOf(Integer winStatus, Integer prizeLevel,
                                       String lotteryCode, String issueNo) {
        if (winStatus == null) {
            return "处理中";
        }
        return switch (winStatus) {
            // 未开奖：用户要的是「什么时候能知道结果」，而那个时间在 planDrawTime 上
            case 0 -> "待开奖";
            case 1 -> "未中奖";
            case 2 -> prizeLevel == null || prizeLevel >= NO_PRIZE_LEVEL
                    ? "已中奖" : "中 " + prizeLevel + " 等奖";
            default -> {
                log.warn("【彩票】未知的中奖状态 {}，按「处理中」显示。{} 第 {} 期",
                        winStatus, lotteryCode, issueNo);
                yield "处理中";
            }
        };
    }
}
