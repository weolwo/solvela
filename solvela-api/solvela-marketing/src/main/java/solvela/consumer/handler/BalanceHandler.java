package solvela.consumer.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.anno.PrizeStrategy;
import solvela.dispatch.DispatchOutcome;
import solvela.enums.PrizeTypeEnum;
import solvela.prize.PrizeLog;

/**
 * 现金派发策略。
 *
 * <p>与积分同为<b>值类资产</b>，区别只有一处：0 元一律拒绝。
 * 现金要进风控预算口径，一笔 0 元的提案除了让预算统计失真没有任何意义。
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@RequiredArgsConstructor
@PrizeStrategy(value = PrizeTypeEnum.BALANCE)
@Service
public class BalanceHandler implements IPrizeHandler {

    private final ProposalPrizeDispatcher dispatcher;

    private static final PrizeSpec SPEC = PrizeSpec.value(
            PrizeTypeEnum.BALANCE, "派发金额", PrizeSpec.ZeroPolicy.REJECT,
            prizeLog -> "参与活动[" + prizeLog.getActivityCode() + "]中奖发放");

    @Override
    public DispatchOutcome dispatch(PrizeLog prizeLog) {
        return dispatcher.dispatch(prizeLog, SPEC);
    }
}
