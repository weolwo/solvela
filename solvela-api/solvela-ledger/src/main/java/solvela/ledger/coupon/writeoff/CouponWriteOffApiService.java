package solvela.ledger.coupon.writeoff;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.ledger.coupon.writeoff.domain.CouponWriteOffResult;
import solvela.member.api.CouponLockCmd;
import solvela.member.api.CouponWriteOffApi;
import solvela.member.api.CouponWriteOffCmd;
import solvela.member.api.CouponWriteOffView;


/**
 * {@link CouponWriteOffApi} 的实现：把券的三阶段核销暴露给服务端内部的调用方。
 *
 * <h3>它只做一件事：翻译</h3>
 * 业务逻辑全在 {@link CouponWriteOffService} 里。这一层存在的理由是
 * <b>契约里不能出现域内类型</b> —— 契约将来要跨进程，而
 * {@code MemberCoupon} / {@code CouponDeductTargetEnum} 是 ledger 的内部形状。
 * 把它们塞进契约，等于让商城通过后门认识了资产域。
 *
 * <p>枚举也在这里翻成字符串：枚举值域的变更在两边不是同时发版的，
 * 跨进程之后一个新增的枚举值会让老版本的调用方直接反序列化失败。
 *
 * <p>只读的那一半（券包、试算）在 {@link CouponQueryApiService} —— 拆开是为了
 * 让「网关不该拿到写能力」由类型系统表达，而不是靠谁记得。
 *
 * <h3>🔴 事务边界仍然在调用方</h3>
 * 和 {@code AssetGrantApiService} / {@code AssetDebitApiService} 同一条规矩：
 * 本类<b>没有</b> {@code @Transactional}。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponWriteOffApiService implements CouponWriteOffApi {

    private final CouponWriteOffService couponWriteOffService;

    @Override
    public CouponWriteOffView lock(CouponLockCmd cmd) {
        return toView(couponWriteOffService.lock(cmd.couponId(), cmd.memberId(), cmd.bizType(),
                cmd.bizRefId(), cmd.sceneCode(), cmd.payAmount(), cmd.discountAmount()));
    }

    @Override
    public CouponWriteOffView confirm(CouponWriteOffCmd cmd) {
        return toView(couponWriteOffService.confirm(cmd.couponId(), cmd.bizType(), cmd.bizRefId()));
    }

    @Override
    public CouponWriteOffView release(CouponWriteOffCmd cmd) {
        return toView(couponWriteOffService.release(cmd.couponId(), cmd.bizType(),
                cmd.bizRefId(), cmd.remark()));
    }

    private static CouponWriteOffView toView(CouponWriteOffResult result) {
        return new CouponWriteOffView(result.ok(), result.idempotent(),
                result.discountAmount(), result.message());
    }
}
