package solvela.lottery.settle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.base.common.domain.ResponseDTO;
import solvela.lottery.config.domain.entity.LotteryConfig;
import solvela.lottery.config.service.LotteryConfigService;
import solvela.lottery.engine.MatchRuleEnum;
import solvela.lottery.issue.dao.LotteryIssueDao;
import solvela.lottery.issue.domain.entity.LotteryIssue;
import solvela.lottery.issue.manager.LotteryIssueManager;
import solvela.lottery.prizerule.domain.entity.LotteryPrizeRule;
import solvela.lottery.prizerule.manager.LotteryPrizeRuleManager;
import solvela.lottery.record.dao.LotteryRecordDao;
import solvela.lottery.settle.domain.SettleResultVO;
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

    private static final int STATUS_WAIT = 0;
    private static final int STATUS_SETTLING = 1;
    private static final int STATUS_OPENED = 2;

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
    public ResponseDTO<String> randomNumber(Long issueId) {
        LotteryIssue issue = lotteryIssueDao.selectById(issueId);
        if (issue == null) {
            return ResponseDTO.userErrorParam("期号不存在");
        }
        LotteryConfig config = lotteryConfigService.getByLotteryCode(issue.getLotteryCode());
        if (config == null) {
            return ResponseDTO.userErrorParam("彩票玩法不存在：" + issue.getLotteryCode());
        }
        StringBuilder builder = new StringBuilder(config.getNumberLength());
        for (int i = 0; i < config.getNumberLength(); i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return ResponseDTO.ok(builder.toString());
    }

    /**
     * 执行开奖核销。
     *
     * @param issueId       期号ID
     * @param winningNumber 开奖号码；期号已处于「核销中」时本参数被忽略（号码早已定案）
     */
    public ResponseDTO<SettleResultVO> settle(Long issueId, String winningNumber) {
        LotteryIssue issue = lotteryIssueDao.selectById(issueId);
        if (issue == null) {
            return ResponseDTO.userErrorParam("期号不存在");
        }
        if (issue.getStatus() == STATUS_OPENED) {
            return ResponseDTO.userErrorParam("该期已开奖，开奖号码为 " + issue.getWinningNumber());
        }
        LotteryConfig config = lotteryConfigService.getByLotteryCode(issue.getLotteryCode());
        if (config == null) {
            return ResponseDTO.userErrorParam("彩票玩法不存在：" + issue.getLotteryCode());
        }

        String finalNumber;
        if (issue.getStatus() == STATUS_SETTLING) {
            // 断点续跑：号码早已定案，忽略入参，避免「中途换号」这种最坏情况
            finalNumber = issue.getWinningNumber();
            log.warn("[彩票开奖] 期号 {} 处于核销中，按已定案号码 {} 续跑", issue.getIssueNo(), finalNumber);
        } else {
            String error = checkNumber(winningNumber, config.getNumberLength());
            if (error != null) {
                return ResponseDTO.userErrorParam(error);
            }
            // 抢闸门：两个人同时点，第二个人 rows=0 直接退出，不会重复核销
            int rows = lotteryIssueDao.startSettle(issueId, STATUS_WAIT, STATUS_SETTLING, winningNumber);
            if (rows == 0) {
                return ResponseDTO.userErrorParam("开奖失败：该期状态已被其他操作变更，请刷新后重试");
            }
            finalNumber = winningNumber;
        }

        // 按奖级升序逐级认领 —— 顺序就是「取最高奖级」的保证
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

        // 剩下的一律未中奖
        int lose = 0;
        for (int round = 0; round < MAX_BATCH_ROUNDS; round++) {
            int n = lotteryRecordDao.markNoWin(issue.getLotteryCode(), issue.getIssueNo(), BATCH_SIZE);
            lose += n;
            if (n < BATCH_SIZE) {
                break;
            }
        }

        // settle_time 没有 ON UPDATE CURRENT_TIMESTAMP 兜底，必须显式写（铁律 9 的例外分支）
        lotteryIssueDao.finishSettle(issueId, STATUS_SETTLING, STATUS_OPENED);

        Map<String, Object> summary = lotteryRecordDao.settleSummary(issue.getLotteryCode(), issue.getIssueNo());
        log.info("[彩票开奖] 期号 {} 核销完成，开奖号码 {}，中奖 {} 张、未中奖 {} 张", issue.getIssueNo(), finalNumber, claimed, lose);

        return ResponseDTO.ok(new SettleResultVO(issue.getIssueNo(), finalNumber, claimed, lose,
                toLong(summary.get("total")), toLong(summary.get("waitDispatch"))));
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
    public ResponseDTO<Map<String, Object>> summary(Long issueId) {
        LotteryIssue issue = lotteryIssueDao.selectById(issueId);
        if (issue == null) {
            return ResponseDTO.userErrorParam("期号不存在");
        }
        return ResponseDTO.ok(lotteryRecordDao.settleSummary(issue.getLotteryCode(), issue.getIssueNo()));
    }

    /**
     * 触发派奖（独立于核销，见 {@link LotteryDispatchService} 的说明）
     */
    public ResponseDTO<Integer> dispatch(Long issueId) {
        LotteryIssue issue = lotteryIssueDao.selectById(issueId);
        if (issue == null) {
            return ResponseDTO.userErrorParam("期号不存在");
        }
        if (issue.getStatus() != STATUS_OPENED) {
            return ResponseDTO.userErrorParam("该期尚未开奖完成，不能派奖");
        }
        return ResponseDTO.ok(lotteryDispatchService.dispatchIssue(issue.getLotteryCode(), issue.getIssueNo()));
    }

    private long toLong(Object value) {
        return value == null ? 0L : Long.parseLong(value.toString());
    }
}
