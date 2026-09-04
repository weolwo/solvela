package solvela.ledger.handler;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import solvela.dispatch.DispatchOutcome;
import solvela.enums.PrizeTypeEnum;
import solvela.exception.BusinessException;
import solvela.ledger.wallet.service.MemberWalletService;
import solvela.risk.ProposalRecord;

/**
 * 钱包类资产的入账门面：积分与现金共用。
 *
 * <p>钱包表是<b>一行一种资产</b>，{@link MemberWalletService#executeWalletCharge} 本身
 * 就是按 assetType 泛化的 —— 所以积分与现金的差别<b>只有一个枚举值</b>。
 * 改造前这件事的表达方式是两个字段级别一模一样的类：同样的锁键拼法、同样的三段 try/catch、
 * 同样的两条注释，连「🔴 锁键必须用 member_id」都各写了一遍。
 * 现在那条规则只有一处，将来再加一种钱包资产也只是十行。
 *
 * <h3>门面在这一层的职责</h3>
 * 把 Service 抛出的<b>领域异常翻译成引擎认识的下发结果</b>。
 * {@code MemberWalletService} 只管开事务和抛异常（它不知道 DispatchOutcome 是什么），
 * {@code AssetDispatchEngine} 只认成功/失败（它不该去 catch 业务异常）——
 * 中间这一层就是干这件事的。
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@Slf4j
public abstract class WalletChargeHandler extends AbstractAssetHandler {

    @Resource
    protected MemberWalletService memberWalletService;

    /** 本 handler 负责哪种钱包资产 */
    protected abstract PrizeTypeEnum assetType();

    /**
     * 锁细化到<b>会员 + 资产类型</b>：同一个人的积分与现金互不阻塞。
     *
     * <p>🔴 锁键必须用 {@code member_id}：账号可改，改名之后同一个人的两个请求会落到
     * 两把不同的锁上，而钱包行还是同一行 —— 并发保护当场失效，且完全没有报错。
     */
    @Override
    protected final String getLockKey(ProposalRecord proposal) {
        return "lock:wallet_update:" + proposal.getMemberId() + ":" + assetType().name();
    }

    @Override
    protected final DispatchOutcome executeWithLock(ProposalRecord proposal) {
        try {
            memberWalletService.executeWalletCharge(proposal, assetType());
            log.info(">>>> [{}入账成功] 提案ID: {}", assetType().name(), proposal.getId());
            return DispatchOutcome.success();
        } catch (BusinessException e) {
            // 领域层的业务异常（账户冻结、金额非法等）翻译成失败结果，由引擎落终态
            log.error("【账务风控拦截】提案ID: {}, 错误: {}", proposal.getId(), e.getMessage());
            return DispatchOutcome.failed(e.getMessage());
        } catch (DuplicateKeyException e) {
            // 资金流水唯一索引兜底：同一提案重复入账<b>视为幂等成功</b>。
            // 判失败会让引擎把预算还回去，而钱其实已经发出去了 —— 那才是真正的账目错乱
            log.warn("【动账防重拦截】该提案已存在资金流水, 提案ID: {}", proposal.getId());
            return DispatchOutcome.success();
        }
    }
}
