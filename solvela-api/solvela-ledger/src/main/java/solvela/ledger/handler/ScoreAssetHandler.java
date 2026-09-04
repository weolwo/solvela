package solvela.ledger.handler;

import org.springframework.stereotype.Service;
import solvela.anno.AssetStrategy;
import solvela.enums.PrizeTypeEnum;

/**
 * 积分入账策略。
 *
 * <p>全部逻辑在 {@link WalletChargeHandler} —— 积分与现金在钱包这一层<b>只差一个枚举值</b>。
 *
 * <p>补这个类之前 SCORE 在 ledger 层没有任何策略，提案即使执行也会被判「不支持的奖品类型」。
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@Service
@AssetStrategy(PrizeTypeEnum.SCORE)
public class ScoreAssetHandler extends WalletChargeHandler {

    @Override
    protected PrizeTypeEnum assetType() {
        return PrizeTypeEnum.SCORE;
    }
}
