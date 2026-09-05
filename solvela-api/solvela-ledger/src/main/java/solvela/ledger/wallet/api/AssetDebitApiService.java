package solvela.ledger.wallet.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.code.BizErrorCode;
import solvela.enums.PrizeTypeEnum;
import solvela.exception.BusinessException;
import solvela.ledger.wallet.service.MemberWalletService;
import solvela.member.api.AssetDebitApi;
import solvela.member.api.AssetDebitCmd;
import solvela.member.api.AssetDebitReason;
import solvela.member.api.AssetDebitResult;

import java.math.BigDecimal;

/**
 * {@link AssetDebitApi} 的实现：把资产扣减暴露给<b>服务端内部</b>的调用方（今天是商城）。
 *
 * <h3>它只做一件事：把异常翻成返回值</h3>
 * {@link MemberWalletService#executeWalletDeduct} 用 {@code BusinessException} 表达
 * 余额不足、账户冻结、并发冲突这些<b>预期内</b>的拒绝 —— 那在进程内是合适的
 *（后台的人工调账也在调它，那条路径上抛异常是对的：调用方是人，需要当场看到报错）。
 *
 * <p>但跨进程之后异常一律变成 5xx，而「余额不足」不是服务端故障。
 * 所以这一层把它翻成 {@link AssetDebitResult#reason}。
 * <b>同一段逻辑，对内抛异常、对外给返回值</b>，差别只在这一层 ——
 * 与 {@code ProposalApiService} 是同一个形状。
 *
 * <h3>🔴 事务边界在调用方</h3>
 * 本类<b>没有</b> {@code @Transactional}：它必须跑在调用方的事务里，
 * 否则「扣了分但订单没落」。{@code executeWalletDeduct} 自己带了事务注解，
 * 在有外层事务时会加入（默认 REQUIRED），这正是要的。
 *
 * <p>⚠️ 也正因为如此，<b>这里绝不能 catch 之后继续往下走</b> ——
 * 翻译完就返回，让调用方决定回滚。吞掉异常会让外层事务以为一切正常。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetDebitApiService implements AssetDebitApi {

    private final MemberWalletService memberWalletService;

    @Override
    public AssetDebitResult debit(AssetDebitCmd cmd) {
        return execute(cmd, true);
    }

    @Override
    public AssetDebitResult refund(AssetDebitCmd cmd) {
        return execute(cmd, false);
    }

    private AssetDebitResult execute(AssetDebitCmd cmd, boolean deduct) {
        PrizeTypeEnum assetType = resolveAssetType(cmd.assetType());
        if (assetType == null) {
            log.error("【资产扣减】未知的资产类型 {}，bizRefId={} —— 调用方传了一个域里没有的值",
                    cmd.assetType(), cmd.bizRefId());
            return AssetDebitResult.ofReject(AssetDebitReason.UNKNOWN);
        }
        try {
            if (deduct) {
                memberWalletService.executeWalletDeduct(cmd.memberId(), assetType, cmd.amount(),
                        cmd.bizType(), cmd.bizRefId(), cmd.remark());
            } else {
                memberWalletService.executeWalletRefund(cmd.memberId(), assetType, cmd.amount(),
                        cmd.bizType(), cmd.bizRefId(), cmd.remark());
            }
            return AssetDebitResult.ofAccepted();
        } catch (BusinessException e) {
            AssetDebitReason reason = translate(e);
            if (reason == AssetDebitReason.UNKNOWN) {
                // 认不出的业务异常要留痕：它可能是一条真的需要有人看的错
                log.warn("【资产扣减】未归类的拒绝原因，bizRefId={} msg={}", cmd.bizRefId(), e.getMessage(), e);
            }
            return AssetDebitResult.ofReject(reason);
        }
    }

    /**
     * 业务异常 → 拒绝原因。
     *
     * <p>按 {@code ErrorCode} 判而不是按 message 判：message 是给人看的文案，
     * 改一个字就会让这里静默失配 —— 而失配的表现是「余额不足」被显示成
     * 「操作失败，请稍后再试」，用户完全不知道该去做什么。
     */
    private static AssetDebitReason translate(BusinessException e) {
        if (BizErrorCode.BALANCE_NOT_ENOUGH.equals(e.getErrorCode())) {
            return AssetDebitReason.BALANCE_NOT_ENOUGH;
        }
        if (BizErrorCode.ACCOUNT_BALANCE_CHANGED.equals(e.getErrorCode())) {
            // 并发冲突是可重试的，和「余额不足」不是一回事
            return AssetDebitReason.CONCURRENT_CONFLICT;
        }
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("冻结")) {
            return AssetDebitReason.WALLET_UNAVAILABLE;
        }
        if (message.contains("会员不存在")) {
            return AssetDebitReason.MEMBER_NOT_FOUND;
        }
        return AssetDebitReason.UNKNOWN;
    }

    /** 认不出的资产类型返回 null 而不是抛 —— 那是调用方的 bug，但不该表现成 500 */
    private static PrizeTypeEnum resolveAssetType(String value) {
        if (value == null) {
            return null;
        }
        for (PrizeTypeEnum type : PrizeTypeEnum.values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return null;
    }

    /** 金额必须为正。方向由调哪个方法决定，不是由正负号决定 —— 负号在日志里太容易看漏 */
    static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }
}
