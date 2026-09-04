package solvela.consumer.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.anno.PrizeStrategy;
import solvela.dispatch.DispatchOutcome;
import solvela.enums.PrizeTypeEnum;
import solvela.prize.PrizeLog;

/**
 * 优惠券派发策略。
 *
 * <p>券是<b>实例类资产</b>：面额只用于风控预算口径，真正发哪一张券由 assetRef 指明，
 * 券实例的创建在 ledger 侧的 {@code CouponAssetHandler}。
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@RequiredArgsConstructor
@PrizeStrategy(value = PrizeTypeEnum.COUPON)
@Service
public class CouponHandler implements IPrizeHandler {

    private final ProposalPrizeDispatcher dispatcher;

    private static final PrizeSpec SPEC = PrizeSpec.instance(
            PrizeTypeEnum.COUPON, "券面额",
            prizeLog -> "参与活动[" + prizeLog.getActivityCode() + "]中奖发放优惠券");

    @Override
    public DispatchOutcome dispatch(PrizeLog prizeLog) {
        return dispatcher.dispatch(prizeLog, SPEC);
    }
}
