package net.lab1024.sa.ledger.handler;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.anno.AssetStrategy;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.exception.BusinessException;
import net.lab1024.sa.enums.PrizeTypeEnum;
import net.lab1024.sa.ledger.wallet.service.MemberWalletService;
import net.lab1024.sa.risk.proposal.domain.entity.ProposalRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 积分入账策略（门面：负责把 Service 抛的领域异常翻译成 ResponseDTO）
 *
 * 钱包是「一行一种资产」，{@link MemberWalletService#executeWalletCharge} 本身就是按 assetType 泛化的，
 * 所以积分与现金的差别只有传入的枚举和锁粒度，逻辑完全复用。
 * 补这个类之前 SCORE 在 ledger 层没有任何策略，提案即使执行也会被判「不支持的奖品类型」。
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@Slf4j
@AllArgsConstructor
@Service
@AssetStrategy(PrizeTypeEnum.SCORE)
public class ScoreAssetHandler extends AbstractAssetHandler {

    private final MemberWalletService memberWalletService;

    @Override
    protected String getLockKey(ProposalRecord proposal) {
        // 锁细化到 会员+资产类型：同一个人的积分与现金互不阻塞
        return "lock:wallet_update:" + proposal.getMemberName() + ":" + PrizeTypeEnum.SCORE.name();
    }

    @Override
    protected ResponseDTO executeWithLock(ProposalRecord proposal) {
        try {
            memberWalletService.executeWalletCharge(proposal, PrizeTypeEnum.SCORE);
            log.info(">>>> [积分入账成功] 提案ID: {}", proposal.getId());
            return ResponseDTO.ok();
        } catch (BusinessException e) {
            log.error("【账务风控拦截】提案ID: {}, 错误: {}", proposal.getId(), e.getMessage());
            return ResponseDTO.userErrorParam(e.getMessage());
        } catch (DuplicateKeyException e) {
            // 资金流水的唯一索引兜底：同一提案重复入账视为幂等成功
            log.warn("【动账防重拦截】该提案已存在积分流水, 提案ID: {}", proposal.getId());
            return ResponseDTO.ok();
        }
    }
}
