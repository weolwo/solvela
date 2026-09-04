package solvela.consumer.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.anno.PrizeStrategy;
import solvela.dispatch.DispatchOutcome;
import solvela.enums.PrizeTypeEnum;
import solvela.prize.PrizeLog;

/**
 * 实物派发策略。
 *
 * <p>实物是<b>实例类资产</b>：价值（如 iPhone 记 7999）只进风控预算口径，
 * 发哪一件由 assetRef 指明，物流单由 ledger 侧的 {@code PhysicalAssetHandler} 落。
 *
 * <p>补这个类之前，实物奖是一颗审批流上的地雷：实物一般配 approve_mode=1，
 * 平时卡在审批前不会走 doDispatch，所以压测一路无异常；
 * 可运营在审批工作台点「通过」的那一刻，就会抛「不支持的奖品类型: PHYSICAL」。
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@RequiredArgsConstructor
@PrizeStrategy(value = PrizeTypeEnum.PHYSICAL)
@Service
public class PhysicalPrizeHandler implements IPrizeHandler {

    private final ProposalPrizeDispatcher dispatcher;

    private static final PrizeSpec SPEC = PrizeSpec.instance(
            PrizeTypeEnum.PHYSICAL, "实物价值",
            prizeLog -> "参与活动[" + prizeLog.getActivityCode() + "]中奖发放实物：" + prizeLog.getPrizeName());

    @Override
    public DispatchOutcome dispatch(PrizeLog prizeLog) {
        return dispatcher.dispatch(prizeLog, SPEC);
    }
}
