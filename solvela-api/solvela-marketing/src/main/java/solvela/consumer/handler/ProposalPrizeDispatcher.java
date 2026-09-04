package solvela.consumer.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.dispatch.DispatchOutcome;
import solvela.exception.BusinessException;
import solvela.member.api.MemberProposalApi;
import solvela.member.api.ProposalResult;
import solvela.prize.PrizeConfig;
import solvela.prize.PrizeLog;
import solvela.prize.prizeconfig.service.PrizeConfigService;
import solvela.risk.proposal.domain.command.ProposalRecordAddCommand;

import java.math.BigDecimal;

/**
 * 「中奖 -> 提案」这条路本身。四种资产（积分 / 现金 / 优惠券 / 实物）共用，
 * 各自的差异由 {@link PrizeSpec} 声明。
 *
 * <h3>为什么要收成一份</h3>
 * 与 {@link ProposalSourceResolver} 同一个理由：抄四遍的东西一定会漂。
 * 改造前四份实现已经漂出了三处不一致 —— 而其中最贵的那一处曾经真的发生过：
 * 有一版把提案失败吞掉直接 return ok，于是<b>根本没入账的记录被标成了「发货成功」</b>，
 * 用户看到中奖、账上没有钱、系统里没有任何异常。现在这段只有一处，
 * 它对失败的处理也就只有一种。
 *
 * <h3>这里不动账</h3>
 * handler 只负责生成提案，防刷/预算/审批阈值全部由提案域判定，
 * 真正的资产变动由 ledger 侧的 {@code @AssetStrategy} 完成。
 * {@code externalBizNo} 是跨域幂等键 —— 同一笔奖重投一次不会发两遍。
 *
 * @Author alaric
 * @Date 2026-09-04
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ProposalPrizeDispatcher {

    private final MemberProposalApi memberProposalApi;
    private final PrizeConfigService prizeConfigService;
    private final ProposalSourceResolver proposalSourceResolver;

    /**
     * 一次中奖发放一份。奖品配置目前没有数量维度，将来支持「一次发 N 份」时改从配置读
     */
    private static final int QUANTITY_PER_PRIZE = 1;

    public DispatchOutcome dispatch(PrizeLog prizeLog, PrizeSpec spec) {
        log.info(">>>> [{}派发] 开始，LogId: {}, 奖品: {}",
                spec.assetType().name(), prizeLog.getId(), prizeLog.getPrizeCode());

        return switch (readAmount(prizeLog, spec)) {
            case Amount.Rejected rejected -> DispatchOutcome.failed(rejected.reason());
            case Amount.Skipped ignored -> DispatchOutcome.success();
            case Amount.Valid valid -> createProposal(prizeLog, spec, valid.value());
        };
    }

    /**
     * 奖品价值的三种结局：能发、不该发、不用发。
     *
     * <p>用 sealed 而不是「返回 null 表示有问题 + 一个字段带原因」：
     * 「不用发」（0 分的谢谢参与）在返回值上是成功，「不该发」是失败，
     * 两者都不带金额 —— 三种状态的字段完全不重叠，与抽奖链路里 {@code Preflight} 的做法一致。
     */
    private sealed interface Amount {

        record Valid(BigDecimal value) implements Amount {
        }

        /** 不该发：格式错误、负数，或按 {@link PrizeSpec.ZeroPolicy#REJECT} 拒绝的 0 */
        record Rejected(String reason) implements Amount {
        }

        /** 不用发：0 值且策略是 SKIP，判成功但不入账 */
        record Skipped() implements Amount {
        }
    }

    private Amount readAmount(PrizeLog prizeLog, PrizeSpec spec) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(prizeLog.getPrizeValue());
        } catch (NumberFormatException e) {
            log.error("【发奖异常】{}格式错误: {}", spec.valueLabel(), prizeLog.getPrizeValue());
            return new Amount.Rejected(spec.valueLabel() + "格式错误");
        }

        // 负数在任何一种奖上都是改包的信号，一律拦死
        if (amount.signum() < 0) {
            log.error("【重大风控拦截】{}为负！LogId: {}, 值: {}", spec.valueLabel(), prizeLog.getId(), amount);
            return new Amount.Rejected(spec.valueLabel() + "不能为负数");
        }
        if (amount.signum() == 0) {
            if (spec.zeroPolicy() == PrizeSpec.ZeroPolicy.SKIP) {
                log.info("【无需入账】奖品价值为 0（如谢谢参与），跳过提案。LogId: {}", prizeLog.getId());
                return new Amount.Skipped();
            }
            log.error("【重大风控拦截】{}为 0！LogId: {}", spec.valueLabel(), prizeLog.getId());
            return new Amount.Rejected(spec.valueLabel() + "必须大于 0");
        }
        return new Amount.Valid(amount);
    }

    /**
     * 建提案并如实上报结果。
     *
     * <p>⚠️ 提案没通过<b>必须返回失败</b>，不能吞：{@code PrizeDispatchHandler} 拿这个返回值
     * 去写 {@code t_prize_log.status}，吞掉的话一条根本没入账的记录会被标成「发货成功」。
     */
    private DispatchOutcome createProposal(PrizeLog prizeLog, PrizeSpec spec, BigDecimal amount) {
        PrizeConfig prizeConfig = prizeConfigService.getByPrizeCode(prizeLog.getPrizeCode());
        if (prizeConfig == null) {
            return DispatchOutcome.failed("奖品配置不存在");
        }

        ProposalRecordAddCommand req = new ProposalRecordAddCommand();
        req.setMemberId(prizeLog.getMemberId());
        req.setPromotionConfigId(prizeConfig.getPromotionConfigId());
        req.setAssetType(spec.assetType().name());
        if (spec.instanceAsset()) {
            /*
             * 实例类资产：光有金额发不出来，必须指明发哪一张券 / 哪一件货。
             *
             * 当前传的是 prize_code 占位 —— 券模表与实物 SKU 映射建好之后改传各自的 id，
             * 账务侧代码一行都不用动。
             *
             * assetName 同样由营销侧下传：账务侧发券时要落 t_member_coupon.coupon_name，
             * 而它不能回头查 prize_log（依赖方向单向）。不传的话那边只能退而用 remark，
             * 结果就是发出去的券全叫「提案生成成功」。
             */
            req.setAssetRef(prizeConfig.getPrizeCode());
            req.setAssetName(prizeLog.getPrizeName());
        }
        req.setAmount(amount);
        req.setQuantity(QUANTITY_PER_PRIZE);
        req.setSourceType(proposalSourceResolver.resolve(prizeLog.getActivityType()));
        // 极度关键：营销单号即跨域幂等键
        req.setSourceBizId(prizeLog.getExternalBizNo());
        req.setRemark(spec.remark().apply(prizeLog));

        try {
            ProposalResult result = memberProposalApi.createProposal(ProposalCmdMapper.toCmd(req));
            if (!result.accepted()) {
                log.warn("【发奖提案未通过】LogId: {}, 原因: {}", prizeLog.getId(), result.failReason());
                return DispatchOutcome.failed(result.failReason());
            }
            return DispatchOutcome.success(result.proposalId());
        } catch (BusinessException e) {
            // 走到这里的不再是「被风控拒了」——那已经变成 accepted=false 的返回值。
            // 剩下的是真正的意外（跨进程后就是 5xx / 连不上），如实上报为失败
            log.warn("【发奖提案异常】LogId: {}, 原因: {}", prizeLog.getId(), e.getMessage());
            return DispatchOutcome.failed(e.getMessage());
        }
    }
}
