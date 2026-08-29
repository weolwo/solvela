package solvela.ledger.handler;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.anno.AssetStrategy;
import solvela.dispatch.DispatchOutcome;
import solvela.exception.BusinessException;
import solvela.enums.PrizeTypeEnum;
import solvela.ledger.wallet.service.MemberWalletService;
import solvela.risk.ProposalRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

//门面（处理 DTO 与异常转换）
@Slf4j
@AllArgsConstructor
@Service
@AssetStrategy(PrizeTypeEnum.BALANCE)
public class WalletAssetHandler extends AbstractAssetHandler {

    private final MemberWalletService memberWalletService;

    @Override
    protected String getLockKey(ProposalRecord proposal) {
        // 钱包一行一种资产，锁细化到 会员+资产类型，不同资产互不阻塞。
        // 🔴 锁键必须用 member_id，理由同 ScoreAssetHandler：账号可改，改完两个请求会落到两把锁上。
        return "lock:wallet_update:" + proposal.getMemberId() + ":" + PrizeTypeEnum.BALANCE.name();
    }

    @Override
    protected DispatchOutcome executeWithLock(ProposalRecord proposal) {
        try {
            // 纯净的 Service 调用：本 Handler 负责的资产类型为 BALANCE（现金）
            memberWalletService.executeWalletCharge(proposal, PrizeTypeEnum.BALANCE);

            log.info(">>>> [动账成功] 提案ID: {}", proposal.getId());
            return DispatchOutcome.success();

        } catch (BusinessException e) {
            // 【分层精髓】：在这里拦截领域层的 BizException，翻译成引擎认识的下发结果
            log.error("【账务风控拦截】提案ID: {}, 错误: {}", proposal.getId(), e.getMessage());
            return DispatchOutcome.failed(e.getMessage());

        } catch (DuplicateKeyException e) {
            // 依然在这里做幂等兜底
            log.warn("【动账防重拦截】该提案已存在资金流水, 提案ID: {}", proposal.getId());
            return DispatchOutcome.success();
        }
    }
}