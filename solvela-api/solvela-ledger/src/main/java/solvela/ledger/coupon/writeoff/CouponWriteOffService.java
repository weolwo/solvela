package solvela.ledger.coupon.writeoff;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import solvela.coupon.CouponWriteOff;
import solvela.enums.CouponDeductTargetEnum;
import solvela.enums.CouponScopeTypeEnum;
import solvela.enums.CouponStatusEnum;
import solvela.enums.CouponWriteOffActionEnum;
import solvela.ledger.MemberCoupon;
import solvela.ledger.coupon.dao.MemberCouponDao;
import solvela.ledger.coupon.template.dao.CouponWriteOffDao;
import solvela.ledger.coupon.writeoff.domain.CouponTrialCmd;
import solvela.ledger.coupon.writeoff.domain.CouponTrialItem;
import solvela.ledger.coupon.writeoff.domain.CouponTrialResult;
import solvela.ledger.coupon.writeoff.domain.CouponUnusableReason;
import solvela.ledger.coupon.writeoff.domain.CouponWriteOffResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 券的三阶段核销：<b>试算 → 锁定 → 确认 / 释放</b>。
 *
 * <h3>🔴 为什么不能一步核销</h3>
 * 用户下单用了一张券，订单后来失败或超时取消 —— <b>券白没了</b>。
 *
 * <p>这和「商城超时取消要退积分」是<b>完全同构</b>的问题：积分有
 * {@code debit} / {@code refund} 一对，券也必须有 {@code lock} / {@code release} 一对，
 * 否则一定会出现「订单取消了但券没回来」，而且不报错 —— 只有用户会发现。
 *
 * <h3>🔴 三个动作各记一行流水，不是只记核销成功那一次</h3>
 * 只记 CONFIRM 的话行数少一半，但「锁了又释放」就查不到了 ——
 * 而券的纠纷恰恰大多发生在那个窗口里。
 *
 * <p>更要命的是<b>实际减了多少</b>：规则是「8 折最高减 50」，真正减了多少取决于
 * 订单金额。不落地的话退款不知道退多少、「本期核销金额」算不出来、财务对不了账。
 *
 * <h3>🔴 事务边界在调用方</h3>
 * 和 {@code AssetGrantApiService} / {@code AssetDebitApiService} 同一条规矩：
 * 本类<b>没有</b> {@code @Transactional}，它跑在调用方的事务里。
 * 这样「券锁了但订单没落库」不会发生。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponWriteOffService {

    private final MemberCouponDao memberCouponDao;
    private final CouponWriteOffDao couponWriteOffDao;

    // ------------------------------------------------------------------
    // 试算：不改任何状态
    // ------------------------------------------------------------------

    /**
     * 试算：这一单能用哪些券、各能减多少、用不了的<b>为什么</b>用不了。
     *
     * <h3>最优券的选法</h3>
     * 一单一券让这件事很简单：<b>对每张可用券各算一次，取最大的</b>。
     * 没有「百分比一定更划算」这种捷径 —— 150 元订单上「满100减20」减 20，
     * 「8折最高减50」减 30；500 元订单上则是 20 对 50。必须逐张算。
     *
     * <p>平局时选<b>先过期</b>的那张：用户手上的券整体价值最大化。
     * 选 id 小的或随便一张，结果是用户攒了一堆快过期的券却总在用新的。
     */
    public CouponTrialResult trial(CouponTrialCmd cmd) {
        List<MemberCoupon> candidates =
                memberCouponDao.selectUsableCandidates(cmd.memberId(), LocalDateTime.now());

        /*
         * 🔴 按券自己的 deduct_target 分组，而不是按入参挑定的一个。
         *
         *    混合支付单（积分 + 现金）上两种券都用得上，各减各的那一半 ——
         *    一单一券的前提下只有一张券，它的 deduct_target 就唯一决定了抵哪边。
         */
        Map<CouponDeductTargetEnum, List<CouponTrialItem>> usableByTarget = new EnumMap<>(CouponDeductTargetEnum.class);
        List<CouponTrialItem> unusable = new ArrayList<>();

        for (MemberCoupon coupon : candidates) {
            CouponDeductTargetEnum target = coupon.getDeductTarget();
            CouponUnusableReason reason = checkUsable(coupon, cmd);
            if (reason != null) {
                unusable.add(CouponTrialItem.unusable(coupon.getId(), coupon.getCouponCode(),
                        coupon.getCouponName(), reason, coupon.getValidEndTime(), target));
                continue;
            }
            // checkUsable 已经保证这一侧有应付，所以这里不会是 null
            BigDecimal payable = cmd.payableFor(target);
            BigDecimal discount = CouponDiscountCalculator.compute(
                    coupon.getDiscountType(), coupon.getDiscountValue(), coupon.getMaxDiscount(),
                    target, payable);
            usableByTarget.computeIfAbsent(target, k -> new ArrayList<>())
                    .add(CouponTrialItem.usable(coupon.getId(), coupon.getCouponCode(),
                            coupon.getCouponName(), discount, coupon.getValidEndTime(), target));
        }

        List<CouponTrialResult.Group> groups = new ArrayList<>();
        usableByTarget.forEach((target, items) -> {
            /*
             * 组内：抵扣额从大到小；平局选先过期的那张 ——
             * 用户手上的券整体价值最大化。选 id 小的或随便一张，
             * 结果是用户攒了一堆快过期的券却总在用新的。
             *
             * 🔴 只在组内排，【不跨组】：10 积分和 5 块钱谁更划算系统答不了。
             */
            items.sort(Comparator.comparing(CouponTrialItem::discountAmount).reversed()
                    .thenComparing(CouponTrialItem::validEndTime)
                    .thenComparing(CouponTrialItem::couponId));
            groups.add(CouponTrialResult.Group.of(target, items));
        });

        return new CouponTrialResult(List.copyOf(groups), List.copyOf(unusable));
    }

    /**
     * 这张券在这一单上能不能用。
     *
     * @return {@code null} 表示能用
     */
    private CouponUnusableReason checkUsable(MemberCoupon coupon, CouponTrialCmd cmd) {
        /*
         * 🔴 规则列为 NULL = 发这张券的时候没有对应模板，它【没有规则】。
         *
         *    和「配成了减 0」是两回事：真配成 0 的券会正常参与试算并算出 0，
         *    那是运营配错，不是系统不知道规则。两者分得开，靠的是这几列可空
         *    —— 阶段 1 曾把它们建成 NOT NULL DEFAULT，于是没规则的券在库里
         *    长得像「无门槛减 0 的现金券」，2026-09-15 改掉了。
         */
        if (coupon.getDiscountType() == null || coupon.getDiscountValue() == null
                || coupon.getDeductTarget() == null) {
            return CouponUnusableReason.NO_RULE;
        }
        /*
         * 这一单有没有它能抵的那一部分。
         *
         * 🔴 不换算：1 积分 ≠ 1 元，汇率是业务定义还会变。
         *    纯积分单上的现金券、纯现金单上的积分券，都落在这里 ——
         *    它们仍然会带着原因返回给用户看，只是点不动。
         */
        BigDecimal payable = cmd.payableFor(coupon.getDeductTarget());
        if (payable == null || payable.signum() <= 0) {
            return CouponUnusableReason.DEDUCT_TARGET_MISMATCH;
        }
        if (!scopeMatches(coupon, cmd)) {
            return CouponUnusableReason.SCOPE_MISMATCH;
        }
        /*
         * 门槛按【这一侧的】原价判：积分券比 payPoints，现金券比 payCash。
         *
         * ⚠️ 拿另一侧去比是个很容易写出来的错：一张「满 100 元可用」的现金券
         *    碰上「5000 积分 + 9.9 元」的单子，用积分那一侧比就过了门槛，
         *    然后在 9.9 元上减 10 元 —— 虽然有「抵扣不超过应付」兜着不会变负数，
         *    但那一单等于白送。
         *
         * 一单一券时「原价还是折后价」这个问题不存在（只有一次折扣）。
         * 要做叠加的话那是第一个必须先定的口径。
         */
        BigDecimal minAmount = coupon.getMinAmount();
        if (minAmount != null && payable.compareTo(minAmount) < 0) {
            return CouponUnusableReason.BELOW_MIN_AMOUNT;
        }
        return null;
    }

    /**
     * 适用范围匹配。
     *
     * <p>⚠️ {@code scope_refs} 是 json 数组文本，这里用<b>子串包含</b>判断而不是
     * 解析 json：范围明细就是一串编码，而编码里不会出现引号。
     * 判断时把目标裹上引号（{@code "DIGITAL"}）再找，避免
     * {@code CAT1} 命中 {@code CAT10}。
     */
    private boolean scopeMatches(MemberCoupon coupon, CouponTrialCmd cmd) {
        CouponScopeTypeEnum scopeType = coupon.getScopeType();
        if (scopeType == null || scopeType == CouponScopeTypeEnum.ALL) {
            return true;
        }
        String refs = coupon.getScopeRefs();
        if (StringUtils.isBlank(refs)) {
            // 限定了范围却没给明细 —— 保存时校验过，走到这里说明是绕过接口写进去的。
            // 判不可用而不是放行：放行等于把一张「本该限定」的券变成了全场通用券
            log.warn("【券试算】券 {} 范围是 {} 却没有明细，按不可用处理", coupon.getId(), scopeType);
            return false;
        }
        String target = switch (scopeType) {
            case COMMODITY -> cmd.commodityRef();
            case CATEGORY -> cmd.categoryRef();
            case EXTERNAL -> cmd.sceneCode();
            case ALL -> null;
        };
        return StringUtils.isNotBlank(target) && refs.contains("\"" + target + "\"");
    }

    // ------------------------------------------------------------------
    // 锁定 / 确认 / 释放
    // ------------------------------------------------------------------

    /**
     * 锁定：0-未使用 → 4-锁定中。
     *
     * <h3>🔴 抵扣额在这里就定死，确认时不再算一遍</h3>
     * 算两遍得出不同结果，是这类代码最典型的资损来源 —— 下单页显示减 30，
     * 支付成功后按 20 结算，用户看到的和扣掉的对不上。
     *
     * <p>所以锁定入参里就带着试算出来的抵扣额，它被写进券行和流水，
     * 确认时直接沿用。
     *
     * @param bizRefId 单据号。它同时是<b>幂等依据</b>：同一笔单重复锁定，
     *                 看到已经是自己锁的就直接放行
     */
    public CouponWriteOffResult lock(Long couponId, Long memberId, String bizType, String bizRefId,
                                     String sceneCode, BigDecimal payAmount, BigDecimal discountAmount) {
        LocalDateTime now = LocalDateTime.now();
        int rows = memberCouponDao.lockCoupon(couponId, memberId, bizRefId, discountAmount, now);
        if (rows > 0) {
            MemberCoupon coupon = memberCouponDao.selectById(couponId);
            writeFlow(coupon, CouponWriteOffActionEnum.LOCK, bizType, bizRefId, sceneCode,
                    payAmount, discountAmount, null);
            return CouponWriteOffResult.ok(discountAmount);
        }

        /*
         * 0 行有两种可能，必须分开：
         *   · 这张券已经被【这一笔】锁着了 —— 重复提交/重试，是幂等成功；
         *   · 被别人锁走了 / 已经用掉了 / 不是这个人的券 —— 是失败，别再试。
         * 混成一种的话，要么重复提交会被当失败拒掉，要么抢券失败会被当成功放行。
         */
        MemberCoupon current = memberCouponDao.selectById(couponId);
        if (current != null && current.getStatus() == CouponStatusEnum.LOCKED
                && bizRefId.equals(current.getLockedBizId())) {
            log.info("【券锁定】{} 已经被 {} 锁着了，幂等放行", couponId, bizRefId);
            return CouponWriteOffResult.alreadyDone(current.getDiscountAmount());
        }
        String state = current == null ? "券不存在" : String.valueOf(current.getStatus());
        log.warn("【券锁定】锁不上：券 {} 会员 {} 单据 {}，当前状态 {}", couponId, memberId, bizRefId, state);
        return CouponWriteOffResult.fail("券已被使用或不可用（" + state + "）");
    }

    /**
     * 确认：4-锁定中 → 1-已使用。支付 / 履约成功时调。
     *
     * <h3>⚠️ 返回失败时调用方<b>必须</b>处理，不能当没看见</h3>
     * 最可能的失败原因是<b>兜底任务已经把这张券释放了</b>（见
     * {@code CouponStuckLockReleaseJob}）—— 也就是说这一单实际上没有用券。
     * 当没看见的话，订单按打折后的金额结算，而券还躺在用户的券包里可以再用一次。
     */
    public CouponWriteOffResult confirm(Long couponId, String bizType, String bizRefId) {
        LocalDateTime now = LocalDateTime.now();
        int rows = memberCouponDao.confirmCoupon(couponId, bizRefId, now);
        if (rows > 0) {
            MemberCoupon coupon = memberCouponDao.selectById(couponId);
            CouponWriteOff lockRow = couponWriteOffDao.selectLockRow(couponId, bizRefId);
            writeFlow(coupon, CouponWriteOffActionEnum.CONFIRM, bizType, bizRefId,
                    lockRow == null ? null : lockRow.getSceneCode(),
                    lockRow == null ? BigDecimal.ZERO : lockRow.getOriginalAmount(),
                    coupon.getDiscountAmount(), null);
            return CouponWriteOffResult.ok(coupon.getDiscountAmount());
        }

        MemberCoupon current = memberCouponDao.selectById(couponId);
        if (current != null && current.getStatus() == CouponStatusEnum.USED
                && bizRefId.equals(current.getLockedBizId())) {
            // 已经确认过了。重试或重复回调走到这里，是幂等成功
            return CouponWriteOffResult.alreadyDone(current.getDiscountAmount());
        }
        String state = current == null ? "券不存在" : String.valueOf(current.getStatus());
        log.error("【券确认】确认不了：券 {} 单据 {}，当前状态 {}。"
                        + "最可能的原因是兜底任务已经把它释放了 —— 这一单实际上没有用券，"
                        + "调用方必须按「没用券」重新结算，否则券会被再用一次",
                couponId, bizRefId, state);
        return CouponWriteOffResult.fail("券不在本单的锁定中状态（" + state + "）");
    }

    /**
     * 释放：4-锁定中 → 0-未使用。订单取消 / 支付超时 / 履约失败时调。
     *
     * <p>幂等：已经放回去了就直接返回成功 —— 两条取消路径同时跑到是常态。
     */
    public CouponWriteOffResult release(Long couponId, String bizType, String bizRefId, String remark) {
        // 先把金额抄下来：释放会把券行上的 discount_amount 清掉
        MemberCoupon before = memberCouponDao.selectById(couponId);
        BigDecimal locked = before == null ? null : before.getDiscountAmount();

        int rows = memberCouponDao.releaseCoupon(couponId, bizRefId);
        if (rows > 0) {
            CouponWriteOff lockRow = couponWriteOffDao.selectLockRow(couponId, bizRefId);
            writeFlow(before, CouponWriteOffActionEnum.RELEASE, bizType, bizRefId,
                    lockRow == null ? null : lockRow.getSceneCode(),
                    lockRow == null ? BigDecimal.ZERO : lockRow.getOriginalAmount(),
                    locked == null ? BigDecimal.ZERO : locked, remark);
            log.info("【券释放】券 {} 已放回未使用，单据 {}，原因：{}", couponId, bizRefId, remark);
            return CouponWriteOffResult.ok(null);
        }

        if (before != null && before.getStatus() == CouponStatusEnum.UNUSED) {
            // 已经放回去了。两条取消路径同时跑到是常态，这是幂等成功
            return CouponWriteOffResult.alreadyDone(null);
        }
        String state = before == null ? "券不存在" : String.valueOf(before.getStatus());
        log.warn("【券释放】释放不了：券 {} 单据 {}，当前状态 {}", couponId, bizRefId, state);
        return CouponWriteOffResult.fail("券不在本单的锁定中状态（" + state + "）");
    }

    /**
     * 写一行核销流水。
     *
     * <p>⚠️ <b>不吞异常</b>：流水写不进去就整笔回滚。
     * 吞掉的话会出现「券状态变了但没有任何痕迹」，那正是这张表要消灭的东西 ——
     * 而且是最难查的一种，因为状态列看上去一切正常。
     *
     * <p>这一点和通知发送刻意相反：通知失败不该拖垮业务，流水失败必须。
     */
    private void writeFlow(MemberCoupon coupon, CouponWriteOffActionEnum action,
                           String bizType, String bizRefId, String sceneCode,
                           BigDecimal originalAmount, BigDecimal discountAmount, String remark) {
        CouponWriteOff flow = new CouponWriteOff();
        flow.setCouponId(coupon.getId());
        // 会员号冗余一列：按人查核销记录时不用回表
        flow.setMemberId(coupon.getMemberId());
        flow.setAction(action);
        flow.setBizType(bizType);
        flow.setBizRefId(bizRefId);
        flow.setSceneCode(sceneCode);
        flow.setOriginalAmount(originalAmount == null ? BigDecimal.ZERO : originalAmount);
        flow.setDiscountAmount(discountAmount == null ? BigDecimal.ZERO : discountAmount);
        // 抵扣对象也快照一份：券行上的规则将来若被订正，流水仍能自证当时抵的是什么
        flow.setDeductTarget(coupon.getDeductTarget() == null
                ? CouponDeductTargetEnum.CASH : coupon.getDeductTarget());
        flow.setRemark(remark);
        couponWriteOffDao.insert(flow);
    }
}
