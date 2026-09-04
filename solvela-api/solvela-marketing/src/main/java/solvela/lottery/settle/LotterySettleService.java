package solvela.lottery.settle;

import solvela.enums.IssueStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.lottery.LotteryConfig;
import solvela.lottery.config.service.LotteryConfigService;
import solvela.lottery.engine.MatchRuleEnum;
import solvela.lottery.issue.dao.LotteryIssueDao;
import solvela.lottery.LotteryIssue;
import solvela.lottery.issue.manager.LotteryIssueManager;
import solvela.lottery.LotteryPrizeRule;
import solvela.lottery.prizerule.manager.LotteryPrizeRuleManager;
import solvela.lottery.record.dao.LotteryRecordDao;
import solvela.lottery.settle.domain.SettleResultDTO;
import solvela.base.stat.StatRow;
import solvela.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

/**
 * 开奖核销。
 *
 * <h3>三段式状态机，其中 status=1 是可恢复的断点</h3>
 * <pre>
 * 0 待开奖 --[条件更新抢闸门：定案开奖号码]--> 1 核销中 --[分批比对完成]--> 2 已开奖
 * </pre>
 * DDL 给期号留的 {@code 1-部分开奖} 是个预留值（本系统一期只开一次奖），
 * 这里把它复用为<b>核销事务的断点续跑标记</b>：中断后重跑会接着比对，不会重复认领。
 *
 * <h3>为什么不把十万条记录捞进 Java 循环</h3>
 * 核销走集合式 SQL，一条规则一条 UPDATE。
 * {@code win_status = 0} 的守卫 + 按 prizeLevel 升序执行，
 * 就是「奖级互斥、取最高级」的集合式等价写法 ——
 * 一张中了一等奖的票必然也满足二等奖，高奖级先认走并改掉 win_status，低奖级就匹配不到它了。
 *
 * <p><b>这套判定必须与 {@link solvela.lottery.engine.TicketMatcher} 保持一致</b>，
 * 后者是匹配语义的权威定义。改任何一边都要同步另一边，单测里
 * 「EXACT 与满长 TAIL 等价」那条用例就是防两边漂移的。
 *
 * <h3>并发与幂等</h3>
 * 抢闸门用条件更新（{@code WHERE status = 0}）：两个运营同时点开奖，第二个人 rows=0 直接退出，
 * 不会出现两次核销。开奖号码在抢到闸门那一刻就定案且不再变，
 * 所以中断重跑的结果必然一致。
 *
 * @Author alaric
 * @Date 2026-07-28
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class LotterySettleService {

    private final LotteryConfigService lotteryConfigService;
    private final LotteryIssueManager lotteryIssueManager;
    private final LotteryIssueDao lotteryIssueDao;
    private final LotteryPrizeRuleManager lotteryPrizeRuleManager;
    private final LotteryRecordDao lotteryRecordDao;
    private final LotteryDispatchService lotteryDispatchService;




    /**
     * 单批认领上限。十万级记录一次性 UPDATE 会撑爆 undo log 与锁等待，
     * 2000 是「够快」与「事务够短」的折中
     */
    private static final int BATCH_SIZE = 2000;

    /**
     * 单次核销的最大批次数，防御死循环（正常情况下远用不到）。
     * 2000 × 5000 = 一千万条，已超过 9 位号码的理论上限
     */
    private static final int MAX_BATCH_ROUNDS = 5000;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 服务端摇号。
     *
     * <p>原型里是前端 {@code Math.random()} 生成的，那不能用：
     * 开奖号码是资损敏感数据，前端生成意味着运营（或抓包的人）可以自选号码。
     * 这里用 {@link SecureRandom} 在服务端产生，前端只负责展示与二次确认。
     */
    public String randomNumber(Long issueId) {
        LotteryIssue issue = lotteryIssueDao.selectById(issueId);
        if (issue == null) {
            throw new BusinessException("期号不存在");
        }
        LotteryConfig config = lotteryConfigService.getByLotteryCode(issue.getLotteryCode());
        if (config == null) {
            throw new BusinessException("彩票玩法不存在：" + issue.getLotteryCode());
        }
        StringBuilder builder = new StringBuilder(config.getNumberLength());
        for (int i = 0; i < config.getNumberLength(); i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }

    /**
     * 执行开奖核销。
     *
     * <p>三步：定案开奖号码 -> 按奖级逐级认领 -> 剩下的一律标未中奖。
     * 顺序不能反：认领是「取最高奖级」，而最高奖级的含义完全来自认领顺序。
     *
     * @param issueId       期号ID
     * @param winningNumber 开奖号码；期号已处于「核销中」时本参数被忽略（号码早已定案）
     */
    public SettleResultDTO settle(Long issueId, String winningNumber) {
        LotteryIssue issue = lotteryIssueDao.selectById(issueId);
        if (issue == null) {
            throw new BusinessException("期号不存在");
        }
        if (issue.getStatus() == IssueStatusEnum.OPENED) {
            throw new BusinessException("该期已开奖，开奖号码为 " + issue.getWinningNumber());
        }
        LotteryConfig config = lotteryConfigService.getByLotteryCode(issue.getLotteryCode());
        if (config == null) {
            throw new BusinessException("彩票玩法不存在：" + issue.getLotteryCode());
        }

        String finalNumber = decideWinningNumber(issue, config, winningNumber);
        int claimed = claimByRules(issue, finalNumber);
        int lose = markRestAsLose(issue);

        // settle_time 没有 ON UPDATE CURRENT_TIMESTAMP 兜底，必须显式写（铁律 9 的例外分支）
        lotteryIssueDao.finishSettle(issueId, IssueStatusEnum.STAGED, IssueStatusEnum.OPENED);

        StatRow summary = StatRow.of(lotteryRecordDao.settleSummary(issue.getLotteryCode(), issue.getIssueNo()));
        log.info("[彩票开奖] 期号 {} 核销完成，开奖号码 {}，中奖 {} 张、未中奖 {} 张",
                issue.getIssueNo(), finalNumber, claimed, lose);
        return new SettleResultDTO(issue.getIssueNo(), finalNumber, claimed, lose,
                summary.count("total"), summary.count("waitDispatch"));
    }

    /**
     * 定案开奖号码，并把期号推进到「核销中」。
     *
     * <p>两条路：<b>断点续跑</b>时号码早已定案，忽略入参 —— 中途换号是这条链路上最坏的情况，
     * 已经按旧号认领过的那批票会和后面的对不上，且无法回滚。
     * <b>首次开奖</b>则用条件更新抢闸门：两个人同时点，第二个人 rows=0 直接退出，不会重复核销。
     */
    private String decideWinningNumber(LotteryIssue issue, LotteryConfig config, String winningNumber) {
        if (issue.getStatus() == IssueStatusEnum.STAGED) {
            log.warn("[彩票开奖] 期号 {} 处于核销中，按已定案号码 {} 续跑",
                    issue.getIssueNo(), issue.getWinningNumber());
            return issue.getWinningNumber();
        }
        String error = checkNumber(winningNumber, config.getNumberLength());
        if (error != null) {
            throw new BusinessException(error);
        }
        int rows = lotteryIssueDao.startSettle(issue.getId(), IssueStatusEnum.WAIT,
                IssueStatusEnum.STAGED, winningNumber);
        if (rows == 0) {
            throw new BusinessException("开奖失败：该期状态已被其他操作变更，请刷新后重试");
        }
        return winningNumber;
    }

    /**
     * 按奖级<b>升序</b>逐级认领 —— 这个顺序就是「一张票只中最高一级」的全部实现。
     *
     * @return 认领出去的票数
     */
    private int claimByRules(LotteryIssue issue, String finalNumber) {
        List<LotteryPrizeRule> rules = lotteryPrizeRuleManager.lambdaQuery()
                .eq(LotteryPrizeRule::getLotteryCode, issue.getLotteryCode())
                .orderByAsc(LotteryPrizeRule::getPrizeLevel).list();

        int claimed = 0;
        for (LotteryPrizeRule rule : rules) {
            MatchRuleEnum matchRule = MatchRuleEnum.resolve(rule.getMatchRule());
            if (matchRule == null) {
                // 一条脏规则不该让整期核销失败：跳过并告警，其余奖级照常认领
                log.error("[彩票开奖] 奖级 {} 的匹配规则非法，已跳过：{}", rule.getPrizeLevel(), rule.getMatchRule());
                continue;
            }
            claimed += claimAll(issue, matchRule, rule, finalNumber);
        }
        return claimed;
    }

    /**
     * 认领之后剩下的号码一律标为未中奖。
     *
     * <p>分批扫，一轮不足一批就说明扫完了 —— 单期号码量可以到十万级，
     * 一条 UPDATE 全表扫会把行锁持有到分钟级。
     *
     * @return 标记为未中奖的票数
     */
    private int markRestAsLose(LotteryIssue issue) {
        int lose = 0;
        for (int round = 0; round < MAX_BATCH_ROUNDS; round++) {
            int n = lotteryRecordDao.markNoWin(issue.getLotteryCode(), issue.getIssueNo(), BATCH_SIZE);
            lose += n;
            if (n < BATCH_SIZE) {
                break;
            }
        }
        return lose;
    }

    /**
     * 一条规则分批认领到底
     */
    private int claimAll(LotteryIssue issue, MatchRuleEnum matchRule, LotteryPrizeRule rule, String winningNumber) {
        int total = 0;
        for (int round = 0; round < MAX_BATCH_ROUNDS; round++) {
            int n = lotteryRecordDao.claimByRule(issue.getLotteryCode(), issue.getIssueNo(),
                    matchRule.getValue(), rule.getMatchLength(), winningNumber,
                    rule.getPrizeLevel(), rule.getPrizeCode(), BATCH_SIZE);
            total += n;
            if (n < BATCH_SIZE) {
                break;
            }
        }
        if (total > 0) {
            log.info("[彩票开奖] 期号 {} 奖级 {}（{} {}位）认领 {} 张",
                    issue.getIssueNo(), rule.getPrizeLevel(), matchRule.getValue(), rule.getMatchLength(), total);
        }
        return total;
    }

    /**
     * 开奖号码合法性：长度与字符集都要校验。
     * 手工录入的路径必须服务端重校验（铁律 2），前端的 maxlength 只是防呆
     */
    private String checkNumber(String winningNumber, int numberLength) {
        if (winningNumber == null || winningNumber.length() != numberLength) {
            return "开奖号码必须是 " + numberLength + " 位";
        }
        for (int i = 0; i < winningNumber.length(); i++) {
            char c = winningNumber.charAt(i);
            if (c < '0' || c > '9') {
                return "开奖号码只能是数字：" + winningNumber;
            }
        }
        return null;
    }

    /**
     * 核销进度，供前端轮询与验收核对
     */
    public Map<String, Object> summary(Long issueId) {
        LotteryIssue issue = lotteryIssueDao.selectById(issueId);
        if (issue == null) {
            throw new BusinessException("期号不存在");
        }
        return lotteryRecordDao.settleSummary(issue.getLotteryCode(), issue.getIssueNo());
    }

    /**
     * 触发派奖（独立于核销，见 {@link LotteryDispatchService} 的说明）
     */
    public Integer dispatch(Long issueId) {
        LotteryIssue issue = lotteryIssueDao.selectById(issueId);
        if (issue == null) {
            throw new BusinessException("期号不存在");
        }
        if (issue.getStatus() != IssueStatusEnum.OPENED) {
            throw new BusinessException("该期尚未开奖完成，不能派奖");
        }
        return lotteryDispatchService.dispatchIssue(issue.getLotteryCode(), issue.getIssueNo());
    }
}
