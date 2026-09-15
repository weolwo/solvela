package solvela.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.app.domain.CouponTrialRequest;
import solvela.marketing.api.MallApi;
import solvela.marketing.api.MallCommodityDetailView;
import solvela.marketing.api.MallCommoditySkuView;
import solvela.member.api.CouponQueryApi;
import solvela.member.api.CouponTrialQuery;
import solvela.member.api.CouponTrialView;
import solvela.member.api.MemberCouponView;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 券包与下单选券的<b>网关侧</b>编排。
 *
 * <h3>它只做网关该做的事</h3>
 * 把「哪件商品、哪个规格、几件」翻成「抵扣前应付多少、适用范围是什么」，
 * 然后问资产域。规则怎么算、哪张最优，全在 {@code CouponWriteOffService} 里 ——
 * 网关一个字都不该重算，否则下单页显示的和实际减的会各算各的。
 *
 * <h3>🔴 只读。核销不在这里</h3>
 * 锁定 / 确认 / 释放走 {@code CouponWriteOffApi}，那个不接到网关上。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    /**
     * 商城订单目前<b>只能抵积分</b>。
     *
     * <p>{@code CASH} 的券要抵的是现金，而全仓没有任何支付回调代码 ——
     * 抵了也没有地方能把它结算掉。那些券会带着
     * {@code DEDUCT_TARGET_MISMATCH} 出现在不可用列表里，<b>用户仍然看得见</b>，
     * 只是点不动。等假支付做出来（方案阶段 6）再放开。
     */
    private static final String DEDUCT_TARGET_SCORE = "SCORE";

    private final CouponQueryApi couponQueryApi;
    private final MallApi mallApi;

    /** 券包。一张券都没有时返回空列表，不是 null —— 新用户就是这个状态 */
    public List<MemberCouponView> listCoupons(Long memberId, String status) {
        return couponQueryApi.listCoupons(memberId, status);
    }

    /**
     * 下单页选券。
     *
     * <p>商品查不到（下架了 / 链接过期）时返回<b>空结果</b>而不是报错：
     * 用户真正要做的下一步是回去看商品，为了一个选券面板抛 404 没有意义。
     */
    public CouponTrialView trial(Long memberId, CouponTrialRequest request) {
        MallCommodityDetailView commodity = mallApi.getCommodity(request.commodityId(), memberId);
        if (commodity == null) {
            return emptyTrial();
        }
        MallCommoditySkuView sku = commodity.skus() == null ? null : commodity.skus().stream()
                .filter(item -> item.skuId().equals(request.skuId()))
                .findFirst()
                .orElse(null);
        if (sku == null || sku.pointsPrice() == null) {
            log.info("【选券】SKU {} 不在商品 {} 下，会员 {}",
                    request.skuId(), request.commodityId(), memberId);
            return emptyTrial();
        }

        /*
         * 🔴 门槛按【整单】判，所以要乘件数。
         *
         *    按单价判的话，一张「满 5000 可用」的券在「2 件 3000 分」的单子上
         *    会被判成不可用 —— 而那一单实际要付 6000 分，本来是够的。
         *    用户看到的是「我明明够了却用不了」。
         */
        BigDecimal payAmount = BigDecimal.valueOf((long) sku.pointsPrice() * request.quantityOrOne());

        return couponQueryApi.trial(new CouponTrialQuery(
                memberId, payAmount, DEDUCT_TARGET_SCORE,
                commodity.commodityCode(),
                commodity.categoryId() == null ? null : String.valueOf(commodity.categoryId()),
                null));
    }

    private static CouponTrialView emptyTrial() {
        return new CouponTrialView(List.of(), List.of(), null);
    }
}
