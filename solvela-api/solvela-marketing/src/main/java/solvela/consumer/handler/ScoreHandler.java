package solvela.consumer.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.anno.PrizeStrategy;
import solvela.dispatch.DispatchOutcome;
import solvela.enums.PrizeTypeEnum;
import solvela.prize.PrizeLog;

/**
 * 积分派发策略。
 *
 * <p>积分是<b>值类资产</b>：金额即全部信息，账务侧照着数字加就行，不需要 assetRef。
 *
 * <p>唯一与其它三种奖不同的是<b>价值为 0 时判成功而不是失败</b>：
 * 「谢谢参与」这类占位奖品曾经只能配成 0 积分（{@code MARKER} 类型是后来才有的），
 * 把 0 当异常会让抽奖的兜底奖项刷出满屏失败流水，淹没真正的故障。
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@RequiredArgsConstructor
@PrizeStrategy(value = PrizeTypeEnum.SCORE)
@Service
public class ScoreHandler implements IPrizeHandler {

    private final ProposalPrizeDispatcher dispatcher;

    private static final PrizeSpec SPEC = PrizeSpec.value(
            PrizeTypeEnum.SCORE, "积分数值", PrizeSpec.ZeroPolicy.SKIP,
            prizeLog -> "参与活动[" + prizeLog.getActivityCode() + "]中奖发放积分");

    @Override
    public DispatchOutcome dispatch(PrizeLog prizeLog) {
        return dispatcher.dispatch(prizeLog, SPEC);
    }
}
