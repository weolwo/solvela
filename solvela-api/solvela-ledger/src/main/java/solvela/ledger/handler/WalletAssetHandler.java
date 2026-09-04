package solvela.ledger.handler;

import org.springframework.stereotype.Service;
import solvela.anno.AssetStrategy;
import solvela.enums.PrizeTypeEnum;

/**
 * 现金入账策略。全部逻辑在 {@link WalletChargeHandler}。
 *
 * @Author weolwo
 */
@Service
@AssetStrategy(PrizeTypeEnum.BALANCE)
public class WalletAssetHandler extends WalletChargeHandler {

    @Override
    protected PrizeTypeEnum assetType() {
        return PrizeTypeEnum.BALANCE;
    }
}
