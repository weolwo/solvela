package solvela.ledger.coupon.writeoff;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import solvela.enums.CouponDeductTargetEnum;
import solvela.enums.CouponDiscountTypeEnum;
import solvela.enums.CouponStatusEnum;
import solvela.ledger.MemberCoupon;
import solvela.ledger.coupon.dao.MemberCouponDao;
import solvela.ledger.coupon.writeoff.domain.CouponTrialCmd;
import solvela.ledger.coupon.writeoff.domain.CouponTrialItem;
import solvela.ledger.coupon.writeoff.domain.CouponTrialResult;
import solvela.member.api.CouponQueryApi;
import solvela.member.api.CouponTrialQuery;
import solvela.member.api.CouponTrialView;
import solvela.member.api.MemberCouponView;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link CouponQueryApi} 的实现：券包与试算，<b>只读</b>。
 *
 * <h3>为什么「规则的人话」在这里拼，不让端上拼</h3>
 * 「满 100 可用，最高减 50」是四个字段组合出来的，而组合规则会变
 *（加一种折扣类型就多一条分支）。让每个端自己拼，就是让同一段易错逻辑
 * 在 C 端、管理端、将来的小程序各存一份 —— 它们一定会不一致，
 * 而不一致的表现是<b>用户在两个地方看到同一张券的不同规则</b>。
 *
 * <h3>🔴 {@code @Primary} 不是装饰</h3>
 * app-biz 进程里有<b>两个</b> {@link CouponQueryApi} 实现：本类（真实现，
 * 商城在同进程内直接用）和 {@code CouponQueryInternalController}
 *（HTTP 薄壳，给网关跨进程用）。
 *
 * <p>薄壳之所以也 implements 这个接口，是为了让<b>路径只在契约里定义一次</b>
 *（见 {@code AssetInternalController} 的注释：自己再写一遍 {@code @GetMapping}
 * 就是两份，改一处忘另一处，表现是 404 且要等联调才发现）。
 *
 * <p>代价是按接口注入会歧义。{@code @Primary} 指定「同进程调用拿真实现」——
 * 没有它，商城可能注入到那个薄壳上，于是一次本地方法调用绕成了 HTTP 自调。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class CouponQueryApiService implements CouponQueryApi {

    /** 券包的三个 tab。刻意不叫 status —— 它和 status 不是一一对应的 */
    private static final String TAB_USABLE = "USABLE";
    private static final String TAB_USED = "USED";
    private static final String TAB_INVALID = "INVALID";

    private final MemberCouponDao memberCouponDao;
    private final CouponWriteOffService couponWriteOffService;

    @Override
    public List<MemberCouponView> listCoupons(Long memberId, String status) {
        /*
         * 🔴 「可用」刻意排除了 4-锁定中。
         *
         *    那张券此刻正被某一笔订单占着，点进去用不了 ——
         *    列进可用只会让用户困惑，而它在几分钟内要么被用掉、要么被放回来。
         *
         * 「已失效」把已过期和已作废合成一档：对用户都是「这张没了」，
         *    区别在 statusDesc 里说得清楚。
         */
        List<Integer> statuses = switch (status == null ? "" : status) {
            case TAB_USABLE -> List.of(CouponStatusEnum.UNUSED.getValue());
            case TAB_USED -> List.of(CouponStatusEnum.USED.getValue());
            case TAB_INVALID -> List.of(CouponStatusEnum.EXPIRED.getValue(),
                    CouponStatusEnum.VOIDED.getValue());
            default -> List.of();
        };
        if (statuses.isEmpty()) {
            log.warn("【券包】认不出 tab {}，会员 {}", status, memberId);
            return List.of();
        }

        // 可用 tab 还要按有效期再筛一次：过期收口任务每天才跑一次，
        // 中间那段时间里 status 还是 0 但券其实已经过期了
        boolean excludeExpired = TAB_USABLE.equals(status);
        return memberCouponDao.selectWallet(memberId, statuses, excludeExpired, LocalDateTime.now())
                .stream()
                .map(CouponQueryApiService::toView)
                .toList();
    }

    @Override
    public CouponTrialView trial(CouponTrialQuery query) {
        /*
         * ⚠️ 两个应付都可以为 null（这一单没有那一侧），但不能【都】为 null ——
         *    那样一张券都用不了，调用方多半是漏传了。返回空而不是抛：
         *    试算是只读的，为它让整个下单页 500 不值得。
         */
        if (isBlank(query.payPoints()) && isBlank(query.payCash())) {
            log.error("【券试算】积分和现金都没传，会员 {} —— 调用方漏传了应付金额", query.memberId());
            return new CouponTrialView(List.of(), List.of());
        }

        CouponTrialResult result = couponWriteOffService.trial(new CouponTrialCmd(
                query.memberId(), query.payPoints(), query.payCash(),
                query.commodityRef(), query.categoryRef(), query.sceneCode()));

        return new CouponTrialView(
                result.groups().stream()
                        .map(g -> new CouponTrialView.Group(g.deductTarget().name(),
                                g.items().stream().map(CouponQueryApiService::toItem).toList()))
                        .toList(),
                result.unusable().stream().map(CouponQueryApiService::toItem).toList());
    }

    private static boolean isBlank(java.math.BigDecimal amount) {
        return amount == null || amount.signum() <= 0;
    }

    static CouponTrialView.Item toItem(CouponTrialItem item) {
        return new CouponTrialView.Item(
                item.couponId(), item.couponName(), item.discountAmount(), item.usable(),
                item.reason() == null ? null : item.reason().name(),
                // 人话版本一起带出去：调用方不该为了显示一句「未达到使用门槛」
                // 而去维护一份原因码到文案的映射 —— 那份映射一定会和枚举跑偏
                item.reason() == null ? null : item.reason().getDesc(),
                item.validEndTime(),
                item.deductTarget() == null ? null : item.deductTarget().name());
    }

    private static MemberCouponView toView(MemberCoupon coupon) {
        return new MemberCouponView(
                coupon.getId(),
                coupon.getCouponName(),
                describeRule(coupon),
                coupon.getDiscountType() == null ? null : coupon.getDiscountType().name(),
                coupon.getDiscountValue(),
                coupon.getMinAmount(),
                coupon.getMaxDiscount(),
                coupon.getDeductTarget() == null ? null : coupon.getDeductTarget().name(),
                coupon.getStatus() == null ? null : coupon.getStatus().getValue(),
                coupon.getStatus() == null ? null : coupon.getStatus().getDesc(),
                coupon.getValidEndTime(),
                coupon.getUsedTime(),
                coupon.getDiscountAmount());
    }

    /**
     * 把规则拼成一句人话。
     *
     * <p>⚠️ 没有规则的券要<b>如实说</b>，不要拼成「无门槛减 0」——
     * 那会让用户以为这是一张能用但没价值的券，然后去点它。
     * 这批券是阶段 2 之前发的，存量已经作废重发过（方案 §11.5），
     * 但降级路径仍然会产生新的。
     */
    private static String describeRule(MemberCoupon coupon) {
        if (coupon.getDiscountType() == null || coupon.getDiscountValue() == null) {
            return "该券暂无可用规则";
        }
        String unit = coupon.getDeductTarget() == CouponDeductTargetEnum.SCORE ? "积分" : "元";
        BigDecimal minAmount = coupon.getMinAmount();
        String threshold = minAmount != null && minAmount.signum() > 0
                ? "满 " + minAmount.stripTrailingZeros().toPlainString() + unit + "可用，"
                : "无门槛，";

        if (coupon.getDiscountType() == CouponDiscountTypeEnum.PERCENT) {
            String cap = coupon.getMaxDiscount() == null ? ""
                    : "，最高减 " + coupon.getMaxDiscount().stripTrailingZeros().toPlainString() + unit;
            return threshold + "减 " + coupon.getDiscountValue().stripTrailingZeros().toPlainString() + "%" + cap;
        }
        return threshold + "减 " + coupon.getDiscountValue().stripTrailingZeros().toPlainString() + unit;
    }

}
